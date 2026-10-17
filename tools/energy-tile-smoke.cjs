const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');

(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--single-process','--no-zygote','--disable-gpu']});
 const page=await browser.newPage({viewport:{width:390,height:1000},timezoneId:'Europe/Berlin'}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));await page.clock.setFixedTime(new Date('2026-10-17T12:42:00Z'));
 await page.addInitScript(()=>{
  const now=Date.now(),rise=Date.parse('2026-10-17T05:45:00Z');
  window.board=JSON.parse(localStorage.getItem('energy-tile-board')||'{}');window.calls=[];window.savedBackup=null;window.missing=false;window.zero=false;window.positive=false;
  window.mock={running:false,status:'Pausiert',devices:[],readings:[],found:[],historyRevision:0,stats:{count:20},equipment:{version:1,groups:[]},settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10,miniRange:'sunrise',weatherPlace:{name:'Testort',latitude:50,longitude:10}},weather:{timezone:'Europe/Berlin',days:[{day:'2026-10-17',sunrise:rise,code:2,hours:4}],updated:now}};
  const save=board=>{window.board=board;localStorage.setItem('energy-tile-board',JSON.stringify(board));};
  window.WattPit={snapshot:()=>JSON.stringify(window.mock),dashboard:()=>JSON.stringify(window.board),saveDashboard:json=>{save(JSON.parse(json));return '';},history:()=>'{"points":[]}',dashboardHistory:()=>'{"points":[],"summary":{}}',settings:json=>{Object.assign(window.mock.settings,JSON.parse(json));return '';},energyHistory:(from,to,source)=>{
   window.calls.push({from,to,source});to=Math.min(to,Date.now());
   const series=(kind,total,fraction)=>({points:[[from,0],[from+(to-from)*fraction,total/2],[to,total]].map(([t,v])=>({t,last:t,v,min:v,max:v,n:1,gap:false,kind,origin:'calculated',device:'calculated-'+kind})),summary:{from,to,last:total,coverage:kind==='solar'?.98:.97}});
   return JSON.stringify({solar:series('solar',window.zero?0:window.positive?100:40,.5),battery:window.missing?{points:[],summary:{from,to,last:null,coverage:0}}:series('shunt',window.zero?0:70,.3)});
  },chooseSettingsExport:json=>{window.savedBackup=JSON.parse(json);return '';},chooseSettingsImport:()=>window.onSettingsImportPreview({backup:window.savedBackup,name:'test-settings.json'}),importSettings:json=>{const b=JSON.parse(json);save(b.dashboard);Object.assign(window.mock.settings,b.settings,{weatherPlace:b.weatherPlace});window.mock.equipment=b.equipment;return '';}};
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 const before=await page.evaluate(()=>dashboard.config.tiles.map(t=>t.layouts));
 await page.locator('#addTile').click();await page.locator('.metric-option[data-metric="energyBalance"]').click();
 const id=await page.evaluate(()=>dashboard.config.tiles.at(-1).id),tile=page.locator(`[data-tile="${id}"]`),number=()=>tile.locator('.tile-number').textContent();
 assert.equal(await number(),'-30,00');assert.equal(await tile.locator('.tile-sparkline').count(),1);
 assert.equal(await tile.locator('.tile-sparkline').getAttribute('data-scale-max'),'0');assert.equal(await tile.locator('.tile-sparkline').getAttribute('data-scale-min'),'-33');
 assert.match(await tile.locator('.tile-hint').getAttribute('title'),/Solar: 98 %.*Batterie: 97 %/);
 assert.deepEqual(await page.evaluate(()=>dashboard.config.tiles.slice(0,-1).map(t=>t.layouts)),before);
 await page.evaluate(()=>{window.positive=true;window.mock.historyRevision++;poll();});assert.equal(await number(),'30,00');assert.ok(Number(await tile.locator('.tile-sparkline').getAttribute('data-scale-max'))>30);assert.ok(Number(await tile.locator('.tile-sparkline').getAttribute('data-scale-min'))<0);assert.equal(await tile.locator('.tile-zero-line').count(),1);
 await page.evaluate(()=>{window.positive=false;window.mock.historyRevision++;poll();});assert.equal(await number(),'-30,00');
 await tile.click();await page.locator('#tilePeriod').selectOption('sunrise');await page.locator('#saveTile').click();
 assert.match(await tile.locator('.tile-hint').textContent(),/seit Sonnenaufgang/);assert.equal(await page.evaluate(()=>window.calls.at(-1).from),Date.parse('2026-10-17T05:45:00Z'));
 await tile.click();await page.locator('#tileType').selectOption('number');await page.locator('#tileUnit').selectOption('kWh');await page.locator('#saveTile').click();
 assert.equal(await number(),'-0,03');assert.equal(await tile.locator('.tile-sparkline').count(),0);
 await page.reload();assert.equal(await number(),'-0,03');assert.match(await tile.locator('.tile-hint').textContent(),/seit Sonnenaufgang/);
 await tile.click();await page.locator('#tilePeriod').selectOption('last');await page.locator('#tileAmount').fill('3');await page.locator('#tileTimeUnit').selectOption('h');await page.locator('#saveTile').click();assert.equal(await page.evaluate(()=>window.calls.at(-1).from),await page.evaluate(()=>Date.now()-3*3600000));
 await page.evaluate(()=>{window.missing=true;window.mock.historyRevision++;poll();});assert.equal(await number(),'—');assert.match(await tile.locator('.tile-hint').textContent(),/Ertrag und Entnahme nötig/);
 await page.evaluate(()=>{window.missing=false;window.zero=true;window.mock.historyRevision++;poll();});assert.equal(await number(),'0,00');
 await page.locator('[data-page="settings"]').click();await page.locator('#exportConfiguration').click();
 const saved=await page.evaluate(()=>window.savedBackup.dashboard.tiles.at(-1));assert.equal(saved.metric,'energyBalance');assert.equal(saved.unit,'kWh');
 await page.locator('[data-page="overview"]').click();await tile.click();await page.locator('#removeTile').click();assert.equal(await tile.count(),0);
 await page.locator('[data-page="settings"]').click();await page.locator('#importConfiguration').click();await page.locator('#applyConfigurationImport').click();
 assert.deepEqual(await page.evaluate(()=>dashboard.config.tiles.at(-1)),saved);
 await page.locator('[data-page="overview"]').click();assert.equal(await number(),'0,00');
 for(const width of [390,1000]){await page.setViewportSize({width,height:1000});for(const theme of ['light','dark']){if(await page.locator('html').getAttribute('data-theme')!==theme)await page.locator('#themeToggle').click();assert.equal(await number(),'0,00');assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false);}}
 assert.deepEqual(errors,[]);await browser.close();console.log('Comparison tile: add, signed auto-scale, units, number/chart, same-window sunrise, missing/zero, persistence, portable export/import and both layouts/themes passed.');
})().catch(e=>{console.error(e);process.exit(1);});
