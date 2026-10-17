const {chromium}=require('playwright');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const http=require('node:http');
(async()=>{
 const assets=path.resolve(__dirname,'../app/src/main/assets');const server=http.createServer((req,res)=>{const p=path.join(assets,new URL(req.url,'http://localhost').pathname==='/'?'index.html':new URL(req.url,'http://localhost').pathname);if(!p.startsWith(assets+path.sep)||!fs.existsSync(p)){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',p.endsWith('.js')?'text/javascript':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':'text/html');res.end(fs.readFileSync(p));});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--single-process','--no-zygote','--disable-gpu']});const page=await browser.newPage({viewport:{width:360,height:800}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  const today=new Date();const days=Array.from({length:14},(_,i)=>{const d=new Date(today);d.setDate(d.getDate()+i);return {day:d.toLocaleDateString('en-CA'),code:[0,2,3,61,71,95,45][i%7],hours:i===3?null:3+i/10};});
  window.mock={running:false,status:'Pausiert',devices:[{mac:'AA:BB:CC:DD:EE:FF',name:'Test SmartSolar',kind:'solar'}],found:[],readings:[],stats:{count:0},settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10,weatherPlace:{name:'Berlin, Deutschland'}},weather:{days,timezone:'Europe/Berlin',updated:Date.now()},archive:{active:true,text:'Archiv lesen · 50 %',done:5,total:10,updated:1}};
  window.calls=[];window.historyPoints=[];window.WattPit={snapshot:()=>JSON.stringify(window.mock),history:(from,to,metric,source)=>{window.calls.push(['history',metric,source]);return JSON.stringify({points:window.historyPoints.filter(p=>p.t>=from&&p.t<=to)});},settings:json=>{Object.assign(window.mock.settings,JSON.parse(json));window.calls.push(['settings',JSON.parse(json)]);return '';},importArchive:mac=>window.calls.push(['import',mac]),cancelArchive:()=>{window.calls.push(['cancel']);window.mock.archive={active:false,text:'Abgebrochen',updated:2};},refreshWeather:()=>window.calls.push(['refresh']),weatherPlace:json=>{const p=JSON.parse(json);window.mock.settings.weatherPlace=p.off?{}:p;window.mock.weather={};return '';},searchWeather:query=>window.onWeatherSearch({query,places:[{name:'<img src=x onerror=bad()>',latitude:52,longitude:13}]})};
 });
 await page.goto(`http://127.0.0.1:${server.address().port}`);await page.locator('.weather-day').first().waitFor();assert.equal(await page.locator('.weather-day').count(),14);assert.equal(await page.locator('.weather-day svg').count(),14);assert.match(await page.locator('.weather-day').nth(3).textContent(),/— h/);assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false);
 await page.evaluate(()=>{document.getElementById('weatherDays').scrollLeft=140;});await page.waitForTimeout(2200);assert.ok(await page.evaluate(()=>document.getElementById('weatherDays').scrollLeft)>100,'poll resets weather scroll');
 const dashboard=await page.evaluate(()=>document.getElementById('dashboardBoard').innerHTML);
 for(const theme of ['light','dark']){
  await page.locator('[data-page="settings"]').click();await page.locator(`#themeButtons [data-theme="${theme}"]`).click();
  for(const palette of ['classic','blue','green','turquoise','orange','neutral']){
   await page.locator(`.palette-choice[data-palette="${palette}"]`).click();assert.equal(await page.locator('html').getAttribute('data-palette'),palette);assert.equal(await page.locator('.palette-choice[aria-pressed="true"]').count(),1);
   await page.locator('[data-page="overview"]').click();assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false);if(process.env.WATTPIT_SCREENSHOT_DIR&&theme==='light'&&palette==='green'){fs.mkdirSync(process.env.WATTPIT_SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:path.join(process.env.WATTPIT_SCREENSHOT_DIR,'weather-green-light.png')});}
   await page.locator('[data-page="settings"]').click();
  }
 }
 await page.locator('#weatherQuery').fill('Berlin');await page.locator('#weatherSearchButton').click();assert.equal(await page.locator('#weatherResults img').count(),0,'untrusted place name interpreted as HTML');await page.locator('.weather-place').click();assert.equal(await page.locator('#weatherLocation').textContent(),'<img src=x onerror=bad()>');
 await page.locator('[data-page="devices"]').click();assert.equal(await page.locator('.archive-import').isDisabled(),true);await page.locator('#archiveCancel').click();await page.waitForTimeout(2200);assert.equal(await page.locator('.archive-import').isDisabled(),false);await page.locator('.archive-import').click();assert.ok(await page.evaluate(()=>window.calls.some(c=>c[0]==='import')));
 await page.locator('[data-page="history"]').click();await page.locator('#historySource').selectOption('archive');
 await page.locator('[data-range="week"]').click();
 const view=await page.evaluate(()=>[viewFrom,viewTo,metric,source]);
 await page.evaluate(()=>{
  window.historyPoints=[{t:Date.now()-86400000,last:Date.now()-86400000,device:'TEST',kind:'solar',origin:'archive',v:12,min:12,max:12,n:1,period:1800}];
  window.mock.historyRevision=1;window.mock.stats={count:1,archiveCount:1};window.mock.archive={active:true,text:'Solarleistung · 10 % · 1 Werte übernommen',done:1,total:10,updated:3};poll();
 });
 assert.equal(await page.evaluate(()=>points.length),1,'committed archive block is not visible during import');
 assert.match(await page.locator('#historyArchiveStatus').textContent(),/Archivimport läuft/);
 assert.match(await page.locator('#historyStats').textContent(),/1 aus dem Gerätearchiv/);
 assert.deepEqual(await page.evaluate(()=>[viewFrom,viewTo,metric,source]),view,'data refresh changed the selected chart');
 await page.evaluate(()=>{window.historyPoints[0].v=17;window.mock.historyRevision=2;poll();});
 assert.equal(await page.evaluate(()=>points[0].v),17,'same row count hid a newly merged archive field');
 await page.evaluate(()=>{window.mock.archive={active:false,text:'Bluetooth getrennt. 1 Archivwerte übernommen',updated:4};poll();});
 assert.match(await page.locator('#historyArchiveStatus').textContent(),/1 Archivwerte übernommen/);
 await page.locator('[data-page="settings"]').click();await page.locator('#weatherOff').click();await page.locator('[data-page="overview"]').click();assert.equal(await page.locator('#weatherStrip').isVisible(),false);assert.equal(errors.length,0,errors.join('\n'));
 console.log('14 weather days, 12 palette modes, safe location names, archive progress/cancel/retry, source filtering and incremental history refresh without recording or row-count changes passed.');await browser.close();await new Promise(r=>server.close(r));
})().catch(e=>{console.error(e);process.exit(1)});
