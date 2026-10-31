/* Regression for the reported viewer flicker and monthly day-archive reads. Synthetic data. */
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path');
const preset=require('../app/src/main/assets/dashboard-preset.json');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.WATTPIT_CHROMIUM?{executablePath:process.env.WATTPIT_CHROMIUM}:{})});
 try{for(const theme of ['dark','light']){
  const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Europe/Berlin'}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  const until=async fn=>{for(let i=0;i<200;i++){if(await page.evaluate(fn))return;await page.waitForTimeout(50);}throw Error('History retention UI did not reach the expected state');};
  await page.clock.setFixedTime(new Date('2026-10-27T19:00:00Z'));
  await page.addInitScript(({preset,theme})=>{
   window.revision=1;window.auto=true;window.pendingReads=[];window.amount=640;window.dayAmount=29.68;
   const history=q=>{
    const [from,to,metric]=q.args,points=[{t:from,v:10,kind:'shunt',device:'shunt',origin:'archive',period:10},{t:(from+to)/2,v:20,kind:'shunt',device:'shunt',origin:'archive',period:10},{t:to,v:30,kind:'shunt',device:'shunt',origin:'archive',period:10}];
    if(q.method==='dailyEnergy'){window.lastDaily=q;return {accountingVersion:2,days:[{day:'2026-10-27',last:window.dayAmount,coverage:.99},{day:'2026-10-26',last:0,coverage:1}]};}
    if(q.method.startsWith('energy')){const series=last=>({points:points.map((p,i)=>({...p,v:i*last/2})),summary:{last,n:3,coverage:1,measuredCoverage:1,from,to}});return {accountingVersion:2,solar:series(410),consumption:series(window.amount),balance:series(410-window.amount)};}
    return {points:points.map(p=>({...p,v:metric==='voltage'?13.1:p.v})),summary:{last:30,min:10,max:30,n:3}};
   };
   window.drain=error=>{let limit=0;while(window.pendingReads.length){if(++limit>100)throw Error('Unbounded read loop');const q=window.pendingReads.shift();window.onHistoryResult({id:q.id,data:error?{error:'Hauptgerät nicht erreichbar'}:history(q)});}};
   window.WattPit={appVersion:()=> '1.6.4',isLanViewer:()=>true,lanState:()=>JSON.stringify({id:'plant',role:'viewer',name:'Testanlage',connected:true}),snapshot:()=>JSON.stringify({running:true,historyRevision:window.revision,lan:{id:'plant',connected:true},devices:[{mac:'shunt',kind:'shunt',name:'Test-Shunt'},{mac:'solar',kind:'solar',name:'Test-Solar'}],readings:[{device:'shunt',kind:'shunt',t:Date.now(),soc:62,voltage:13.15,current:-2,power:-26},{device:'solar',kind:'solar',t:Date.now(),solar:0,state:0}],found:[],stats:{count:100},settings:{theme,palette:'classic',capacity:100,sampleSeconds:10,miniRange:'last6h'},solarDaily:[{day:'2026-10-27','Yield(Wh)':410},{day:'2026-10-26','Yield(Wh)':0}]}),syncState:()=>JSON.stringify({busy:false,runId:0,cockpitNoticeAccepted:true,theme,palette:'classic'}),dashboardPreset:()=>JSON.stringify(preset),dashboard:()=>JSON.stringify(preset),saveDashboard:()=>'',settings:()=>'',solarSetup:()=> '{}',rollerSnapshot:()=> '{}',history:()=>{throw Error('Blocking viewer read');},dashboardHistory:()=>{throw Error('Blocking viewer read');},energyHistoryWindow:()=>{throw Error('Blocking viewer read');},dailyEnergy:()=>{throw Error('Blocking viewer read');},requestHistory:json=>{const q=JSON.parse(json);if(window.auto)setTimeout(()=>window.onHistoryResult({id:q.id,data:history(q)}),10);else window.pendingReads.push(q);}};
  },{preset,theme});
  await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
  const consumption=page.locator('.cockpit-tile').filter({has:page.locator('.tile-label',{hasText:'Geräteverbrauch'})});
  await until(()=>miniPoints.length===3&&[...document.querySelectorAll('.tile-label')].some(e=>e.textContent==='Geräteverbrauch'&&e.closest('.cockpit-tile').querySelector('.tile-number').textContent==='640,00'));
  await page.evaluate(()=>{const tile=dashboard.config.tiles.find(t=>t.metric==='consumptionEnergy');if(!dashboard.detail)dashboard.detail=new WattTileDetail(dashboard);dashboard.detail.open(tile.id);});
  await until(()=>document.querySelector('#detailReading .detail-number')?.textContent==='640,00');
  await page.evaluate(()=>{window.auto=false;window.revision++;poll();dashboard.detail.refresh(false);});
  assert.equal(await page.locator('#detailReading .detail-number').textContent(),'640,00');assert.match(await page.locator('#detailNote').textContent(),/Letzter Stand/);await page.locator('#detailClose').click();
  await page.evaluate(()=>{window.auto=false;window.revision++;poll();});
  assert.equal(await consumption.locator('.tile-number').textContent(),'640,00');assert.equal(await page.evaluate(()=>miniPoints.length),3);assert.match(await consumption.locator('.tile-hint').textContent(),/Letzter Stand/);
  await page.evaluate(()=>window.drain(true));await page.waitForTimeout(120);assert.equal(await consumption.locator('.tile-number').textContent(),'640,00');assert.match(await consumption.locator('.tile-hint').textContent(),/Aktualisierung fehlgeschlagen/);
  await page.clock.setFixedTime(new Date('2026-10-27T19:10:00Z'));await page.evaluate(()=>poll());
  assert.equal(await consumption.locator('.tile-number').textContent(),'640,00');assert.equal(await page.evaluate(()=>miniPoints.length),3);
  await page.evaluate(()=>{window.amount=660;window.drain(false);});await until(()=>[...document.querySelectorAll('.tile-label')].some(e=>e.textContent==='Geräteverbrauch'&&e.closest('.cockpit-tile').querySelector('.tile-number').textContent==='660,00'));
  await page.locator('[data-page="history"]').click();await page.evaluate(()=>{poll();window.drain(false);});await page.waitForTimeout(120);
  const row=day=>page.locator('#solarDailyArchive tr').filter({has:page.locator('td').filter({hasText:day})});
  assert.match(await row('2026-10-27').locator('td').nth(2).textContent(),/29,68/);assert.match(await row('2026-10-26').locator('td').nth(2).textContent(),/0,00/);
  assert.equal(await page.evaluate(()=>window.lastDaily.args[1]>Date.now()+86400000),true,'the real UI requests the complete calendar month, which the LAN host must accept');
  await page.evaluate(()=>{window.revision++;poll();});
  assert.equal(await page.evaluate(()=>points.length),3);assert.match(await page.locator('#historyReadNote').textContent(),/Letzter Stand/);assert.match(await row('2026-10-27').locator('td').nth(2).textContent(),/29,68.*letzter Stand/);
  await page.evaluate(()=>window.drain(true));await page.waitForTimeout(120);assert.equal(await page.evaluate(()=>points.length),3);assert.match(await row('2026-10-27').locator('td').nth(2).textContent(),/29,68.*Aktualisierung fehlgeschlagen/);
  await page.locator('[data-metric="voltage"]').click();assert.deepEqual(await page.evaluate(()=>points),[],'a different metric must not inherit the old power curve');await page.evaluate(()=>window.drain(false));await page.waitForTimeout(120);assert.equal(await page.evaluate(()=>points[0].v),13.1);
  assert.deepEqual(errors,[]);await page.close();
 }
 console.log('PASS viewer retention during revision changes, 10-minute delays and errors; atomic replacement; daily month-end query, zero values and explicit stale status; metric isolation; both themes.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
