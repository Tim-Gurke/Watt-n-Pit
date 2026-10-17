const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--single-process','--no-zygote','--disable-gpu']});
 const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Europe/Berlin'}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  const now=Date.now(),day=new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Berlin',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(now));
  window.testConfig=JSON.parse(localStorage.getItem('mock-dashboard')||'null')||{version:1,tiles:[{id:'test-solar',metric:'solar',type:'chart',min:0,max:200,scaleMode:'auto',period:{mode:'today',amount:6,unit:'h',aggregate:'last'},layouts:{compact:{x:0,y:0,w:6,h:6},wide:{x:0,y:0,w:6,h:6}}}]};
  window.mock={running:true,status:'Live',devices:[{mac:'TEST-SOLAR',name:'Testregler',kind:'solar'},{mac:'TEST-SHUNT',name:'Testshunt',kind:'shunt'}],found:[],readings:[{device:'TEST-SOLAR',t:now,solar:7,state:3},{device:'TEST-SHUNT',t:now,voltage:13.25,current:-.2,power:-2.65}],historyRevision:0,stats:{count:8},settings:{theme:'dark',capacity:100,sampleSeconds:10,miniRange:localStorage.getItem('mock-mini-range')||'sunrise',weatherPlace:{name:'Testort'}},weather:{timezone:'Europe/Berlin',updated:now,days:[{day,hours:3,code:2,sunrise:now-4*3600000}]}};
  window.calls=[];window.historyError=false;
  const energy=kind=>({points:[{t:now-300000,last:now-300000,kind,origin:'calculated',device:'calculated-'+kind,v:0,min:0,max:0,n:1,gap:true},{t:now,last:now,kind,origin:'calculated',device:'calculated-'+kind,v:kind==='solar'?3.27:1.25,min:kind==='solar'?3.27:1.25,max:kind==='solar'?3.27:1.25,n:1,gap:false}],summary:{last:kind==='solar'?3.27:1.25,coverage:.5,min:0,max:3.27}});
  window.WattPit={snapshot:()=>JSON.stringify(window.mock),dashboard:()=>JSON.stringify(window.testConfig),saveDashboard:json=>{window.testConfig=JSON.parse(json);localStorage.setItem('mock-dashboard',json);return '';},settings:json=>{Object.assign(window.mock.settings,JSON.parse(json));localStorage.setItem('mock-mini-range',window.mock.settings.miniRange);return '';},history:(from,to,metric,source)=>{window.calls.push({from,to,metric,source});return '{"points":[]}';},dashboardHistory:(from,to,metric)=>{
   if(metric==='solarEnergy'||metric==='dischargeEnergy')return JSON.stringify(energy(metric==='solarEnergy'?'solar':'shunt'));
   return window.historyError?'{"points":[],"summary":{},"error":"unavailable"}':JSON.stringify({points:[{t:now-3600000,last:now-3600000,v:1,min:1,max:1,n:1,device:'TEST-SOLAR',origin:'archive',period:3600},{t:now-600000,last:now-600000,v:3,min:3,max:6,n:1,device:'TEST-SOLAR',origin:'archive',period:3600}],summary:{last:3,avg:2,min:1,max:6}});
  },energyHistory:()=>JSON.stringify({solar:energy('solar'),battery:energy('shunt')})};
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 const solar=page.locator('[data-tile="test-solar"]'),number=()=>solar.locator('.tile-number').textContent(),curve=async()=>JSON.stringify([...(await solar.locator('.tile-sparkline path').last().getAttribute('d')).matchAll(/([ML])[\d.]+,([\d.]+)/g)].map(m=>[m[1],Number(m[2])]));
 assert.equal(await number(),'3','old chart choices changed on upgrade');const originalCurve=await curve();
 await solar.click();assert.equal(await page.locator('#tileAggregate').inputValue(),'last');
 assert.equal(await page.locator('#tilePeriodLabel').textContent(),'Kurvenzeitraum');
 await page.locator('#tileAggregate').selectOption('live');await page.locator('#saveTile').click();
 assert.equal(await number(),'7');assert.equal(await curve(),originalCurve);assert.match(await solar.locator('.tile-hint').textContent(),/Aktuell.*Verlauf: heute/);
 await page.evaluate(()=>{window.mock.readings[0].solar=0;poll();});assert.equal(await number(),'0');
 await page.evaluate(()=>{window.mock.readings[0].t=Date.now()-46000;poll();});assert.equal(await number(),'—');assert.equal(await curve(),originalCurve);assert.match(await solar.locator('.tile-hint').textContent(),/Kein Livewert/);
 await page.evaluate(()=>{window.mock.readings[0].t=Date.now();window.mock.running=false;poll();});assert.equal(await number(),'—');assert.equal(await curve(),originalCurve);
 await page.evaluate(()=>{window.mock.running=true;window.mock.readings[0].solar=7;window.historyError=true;window.mock.historyRevision++;poll();});assert.equal(await number(),'7');assert.match(await solar.locator('.tile-hint').textContent(),/Aktuell.*Verlauf nicht verfügbar/);
 await page.evaluate(()=>{window.historyError=false;window.mock.historyRevision++;poll();});
 await page.reload();assert.equal(await number(),'7');await solar.click();assert.equal(await page.locator('#tileAggregate').inputValue(),'live');assert.equal(await page.locator('#tilePeriod').inputValue(),'today');
 await page.locator('#tileAggregate').selectOption('max');await page.locator('#saveTile').click();assert.equal(await number(),'6');
 await solar.click();await page.locator('#tileAggregate').selectOption('live');await page.locator('#tileUnit').selectOption('kW');await page.locator('#saveTile').click();
 assert.equal(await page.evaluate(()=>window.testConfig.tiles[0].valueMode),'live');assert.equal(await solar.locator('.tile-sparkline').getAttribute('data-scale-max'),'0.0066');
 // All overview blocks can be moved without changing the tile layout.
 const layouts=await page.evaluate(()=>window.testConfig.tiles[0].layouts);
 await page.locator('#editDashboard').click();
 for(let i=0;i<2;i++)await page.getByRole('button',{name:'Start / Pause nach oben',exact:true}).click();
 const order=await page.locator('#overviewBlocks > [data-overview-block]').evaluateAll(nodes=>nodes.map(n=>n.dataset.overviewBlock));assert.deepEqual(order,['record','tiles','history']);
 assert.deepEqual(await page.evaluate(()=>window.testConfig.tiles[0].layouts),layouts);
 await page.locator('#editDashboard').click();assert.equal(await page.locator('#overviewOrder').isVisible(),false);
 await page.reload();assert.deepEqual(await page.locator('#overviewBlocks > [data-overview-block]').evaluateAll(nodes=>nodes.map(n=>n.dataset.overviewBlock)),order);
 // Mini history queries the chosen window and stores it independently from the large history view.
 assert.equal(await page.locator('#miniRange').inputValue(),'sunrise');assert.match(await page.locator('#miniPeriodLabel').textContent(),/SONNENAUFGANG/);
 assert.equal(await page.evaluate(()=>window.calls.at(-1).from),await page.evaluate(()=>window.mock.weather.days[0].sunrise));
 await page.locator('#miniRange').selectOption('last6h');assert.match(await page.locator('#miniPeriodLabel').textContent(),/6 STUNDEN/);
 assert.ok(Math.abs(await page.evaluate(()=>window.calls.at(-1).from)-(Date.now()-6*3600000))<2000);
 await page.reload();assert.equal(await page.locator('#miniRange').inputValue(),'last6h');
 await page.locator('#miniRange').selectOption('sunrise');await page.evaluate(()=>{window.mock.weather.days=[];poll();});assert.equal(await page.locator('#miniPeriodLabel').textContent(),'HEUTE');assert.match(await page.locator('#miniPeriodNote').textContent(),/nicht verfügbar/);
 // The new independent energy totals work as tiles and in the comparison graph, with coverage.
 await page.locator('#addTile').click();await page.locator('.metric-option[data-metric="solarEnergy"]').click();
 const energyTile=page.locator('[data-tile]').last();assert.equal(await energyTile.locator('.tile-number').textContent(),'3,27');assert.match(await energyTile.locator('.tile-hint').textContent(),/Berechnet.*50 % erfasst/);
 await energyTile.click();assert.equal(await page.locator('#tileAggregate').isDisabled(),true);assert.equal(await page.locator('#tilePeriod option[value="live"]').evaluate(el=>el.disabled),true);await page.locator('#closeModal').click();
 await page.locator('[data-page="history"]').click();await page.locator('#metricButtons [data-metric="energy"]').click();
 assert.equal(await page.locator('#chartLegend span').count(),2);assert.match(await page.locator('#energyNote').textContent(),/3,27 Wh.*50 %.*-1,25 Wh.*50 %/);assert.match(await page.locator('#energyNote').textContent(),/kein Gesamtverbrauch/);assert.ok(await page.evaluate(()=>points.some(p=>p.kind==='shunt'&&p.v===-1.25)));assert.ok(await page.evaluate(()=>scale.low< -1.25));assert.equal(await page.evaluate(()=>dischargeValue(0)),0);assert.equal(await page.evaluate(()=>dischargeValue(null)),null);
 if(process.env.WATTPIT_SCREENSHOT_DIR){fs.mkdirSync(process.env.WATTPIT_SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:path.join(process.env.WATTPIT_SCREENSHOT_DIR,'energy-comparison-dark.png'),fullPage:true});await page.locator('[data-page="overview"]').click();await page.screenshot({path:path.join(process.env.WATTPIT_SCREENSHOT_DIR,'overview-blocks-dark.png'),fullPage:true});}
 assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false);
 assert.equal(errors.length,0,errors.join('\n'));await browser.close();console.log('Independent live/chart values, zero/stale/paused/error states, persistence, block ordering, sunrise/6h selection and calculated-energy UI passed.');
})().catch(error=>{console.error(error);process.exit(1);});
