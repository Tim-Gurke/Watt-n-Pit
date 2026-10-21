const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--disable-gpu']});
 const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Europe/Berlin',hasTouch:true}),errors=[];
 page.setDefaultTimeout(8000);page.on('pageerror',e=>errors.push(e.message));await page.clock.setFixedTime(new Date('2026-10-26T11:00:00Z'));
 await page.addInitScript(()=>{
  const now=Date.now(),rise=Date.parse('2026-10-18T05:48:00Z');window.saves=0;window.calls=[];window.missing=false;
  window.mock={running:true,status:'Live',devices:[{mac:'SHUNT',kind:'shunt',name:'Testshunt'},{mac:'SOLAR',kind:'solar',name:'Testregler'}],readings:[{device:'SHUNT',t:now,soc:76.5,voltage:12.92,power:-109,current:-8.4},{device:'SOLAR',t:now,solar:5}],found:[],historyRevision:0,stats:{count:400},equipment:{version:1,groups:[]},settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10,miniRange:'today',weatherPlace:{name:'Testort',latitude:50,longitude:10}},weather:{timezone:'Europe/Berlin',updated:now,days:Array.from({length:14},(_,i)=>({day:'2026-10-'+String(18+i).padStart(2,'0'),sunrise:rise+i*86400000,code:2,hours:5}))}};
  const series=(from,to,metric)=>{
   const points=window.missing?[]:Array.from({length:241},(_,i)=>{const v=metric==='solar'&&from+(to-from)*i/240<rise?0:metric==='voltage'?12.9+i/1000:metric==='soc'?76-i/100:metric==='dischargeEnergy'?i/3:metric==='solarEnergy'?i/4:Math.sin(i/14)*15+20;return {t:from+(to-from)*i/240,v,min:v===0?0:v-1,max:v===0?0:v+1,n:1,device:'test',origin:'live'};});
   return {points,summary:{from,to,last:points.at(-1)?.v??null,avg:20,min:4,max:36,coverage:1}};
  };
  window.miniCalls=[];window.mock.settings.miniRange=localStorage.getItem('mini-range')||'today';
  window.WattPit={snapshot:()=>JSON.stringify(window.mock),dashboard:()=>localStorage.getItem('detail-board')||'{}',saveDashboard:json=>{if(window.failSave)return 'Test: Speichern fehlgeschlagen';window.saves++;localStorage.setItem('detail-board',json);return '';},settings:json=>{Object.assign(window.mock.settings,JSON.parse(json));localStorage.setItem('mini-range',window.mock.settings.miniRange);return '';},history:(from,to,metric)=>{window.miniCalls.push({from,to,metric});return JSON.stringify({points:Array.from({length:241},(_,i)=>['solar','shunt'].map(kind=>{const v=kind==='solar'?Math.max(0,Math.sin(i/10))*100:Math.sin(i/14)*40-10,t=from+(to-from-1)*i/240;return {t,last:t,v,min:v,max:v,n:1,period:Math.ceil((to-from)/240000),kind,device:kind==='solar'?'SOLAR':'SHUNT',origin:'archive'};})).flat()});},dashboardHistory:(from,to,metric)=>{window.calls.push({from,to,metric});return JSON.stringify(series(from,to,metric));},energyHistory:(from,to)=>JSON.stringify({solar:series(from,to,'solarEnergy'),consumption:series(from,to,'consumptionEnergy'),balance:series(from,to,'batteryEnergy')})};
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 const periods=['sunrise','today','yesterday','week','month','last6h','last24h'];
 assert.deepEqual(await page.locator('#miniRange option').evaluateAll(options=>options.map(o=>o.value)),periods);
 const expected={yesterday:['2026-10-24T22:00:00Z','2026-10-25T23:00:00Z'],week:['2026-10-19T22:00:00Z','2026-10-26T11:00:00Z'],month:['2026-09-26T22:00:00Z','2026-10-26T11:00:00Z'],last24h:['2026-10-25T11:00:00Z','2026-10-26T11:00:00Z']};
 for(const [mode,bounds] of Object.entries(expected)){
  await page.locator('#miniRange').selectOption(mode);
  const query=await page.evaluate(()=>window.miniCalls.at(-1));assert.deepEqual([query.from,query.to],bounds.map(Date.parse));assert.equal(query.metric,'power');
  assert.ok((await page.locator('#miniPeriodLabel').textContent()).length>0);
 }
 await page.locator('#miniRange').selectOption('month');await page.reload();assert.equal(await page.locator('#miniRange').inputValue(),'month');
 const card=page.locator('#miniHistory'),handle=page.locator('#miniHistoryResize');assert.equal(await handle.isVisible(),false);
 const verifyCurves=async()=>{
  const colors=await page.locator('#miniChart').evaluate(el=>{const ctx=el.getContext('2d'),data=ctx.getImageData(0,0,el.width,el.height).data;let solar=0,battery=0;for(let y=25;y<el.height-40;y++)for(let x=60;x<el.width-15;x++){const i=(y*el.width+x)*4,r=data[i],g=data[i+1],b=data[i+2];if(r>150&&g>70&&g<200&&b<140)solar++;if(g>120&&g>r*1.2&&b>r*1.1)battery++;}return {solar,battery};});
  assert.ok(colors.solar>30&&colors.battery>30,'both measured curves must remain painted '+JSON.stringify(colors));
 };await verifyCurves();
 const drag=async(delta)=>{
  await card.evaluate(el=>el.scrollIntoView({block:'center'}));const box=await handle.boundingBox();
  await page.mouse.move(box.x+box.width/2,box.y+box.height/2);await page.mouse.down();await page.mouse.move(box.x+box.width/2,box.y+box.height/2+delta,{steps:8});await page.mouse.up();
 };
 await page.locator('#editDashboard').click();assert.equal(await handle.isVisible(),true);
 const before=await page.evaluate(()=>({tiles:dashboard.config.tiles,calls:window.miniCalls.length,height:dashboard.config.historyHeight.compact}));
 await drag(110);assert.equal(await card.evaluate(el=>el.offsetHeight),before.height+110);assert.equal(await page.evaluate(()=>dashboard.config.historyHeight.compact),430);
 assert.equal(await page.evaluate(()=>window.miniCalls.length),before.calls,'height adjustment must redraw without requesting history');
 assert.deepEqual(await page.evaluate(()=>dashboard.config.tiles),before.tiles,'history resizing must not move measurement tiles');await verifyCurves();
 assert.equal(await page.locator('#miniChart').evaluate(el=>el.height),await page.locator('#miniChart').evaluate(el=>Math.round(el.getBoundingClientRect().height*devicePixelRatio)),'canvas must match its resized pixels');
 // An interrupted touch restores the previous height and does not save a partial drag.
 await card.evaluate(el=>el.scrollIntoView({block:'center'}));let box=await handle.boundingBox();const cdp=await page.context().newCDPSession(page),touch={x:Math.round(box.x+22),y:Math.round(box.y+22),id:1};
 const saves=await page.evaluate(()=>window.saves);await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[touch]});await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{...touch,y:touch.y-50}]});assert.equal(await card.evaluate(el=>el.offsetHeight),380);
 await cdp.send('Input.dispatchTouchEvent',{type:'touchCancel',touchPoints:[]});assert.equal(await card.evaluate(el=>el.offsetHeight),430);assert.equal(await page.evaluate(()=>window.saves),saves);
 await page.setViewportSize({width:1100,height:900});await page.locator('#layoutProfile').filter({hasText:'Breite Ansicht'}).waitFor();assert.equal(await card.evaluate(el=>el.offsetHeight),380);
 await drag(90);assert.equal(await card.evaluate(el=>el.offsetHeight),470);await handle.focus();await page.keyboard.press('ArrowDown');assert.equal(await card.evaluate(el=>el.offsetHeight),490);
 await page.keyboard.press('Home');assert.equal(await card.evaluate(el=>el.offsetHeight),300);await page.keyboard.press('End');assert.equal(await card.evaluate(el=>el.offsetHeight),1000);
 await page.evaluate(()=>window.failSave=true);await page.keyboard.press('ArrowUp');assert.equal(await card.evaluate(el=>el.offsetHeight),1000,'failed storage must restore the saved height');await page.evaluate(()=>window.failSave=false);
 await page.locator('#editDashboard').click();assert.equal(await handle.isVisible(),false);
 await page.reload();assert.equal(await card.evaluate(el=>el.offsetHeight),1000);await page.setViewportSize({width:390,height:844});await page.locator('#layoutProfile').filter({hasText:'Schmale Ansicht'}).waitFor();assert.equal(await card.evaluate(el=>el.offsetHeight),430);
 for(const width of [320,360,390,700,832,1100]){
  await page.setViewportSize({width,height:844});assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false,'overview overflow '+width);
  const bounds=await page.locator('#miniChart').boundingBox(),outer=await card.boundingBox();assert.ok(bounds.height>=80&&bounds.y+bounds.height<=outer.y+outer.height,'chart escaped its card');
 }
 if(process.env.WATTPIT_SCREENSHOT_DIR){fs.mkdirSync(process.env.WATTPIT_SCREENSHOT_DIR,{recursive:true});await page.setViewportSize({width:390,height:844});await page.locator('#editDashboard').click();await card.evaluate(el=>el.scrollIntoView({block:'center'}));await verifyCurves();await card.screenshot({path:path.join(process.env.WATTPIT_SCREENSHOT_DIR,'mini-history-resized.png')});}
 assert.deepEqual(errors,[]);await browser.close();console.log('Overview calendar ranges, local database queries, touch cancel, height drag, keyboard bounds, storage failure, separate profiles and reload persistence passed.');
})().catch(e=>{console.error(e);process.exit(1);});

