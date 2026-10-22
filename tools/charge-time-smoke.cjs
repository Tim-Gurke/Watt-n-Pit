/* Restladezeit UI; synthetic Shunt readings only, no Bluetooth connection. */
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--disable-gpu']});
 const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Europe/Berlin',locale:'de-DE'});page.setDefaultTimeout(10000);
 const errors=[];page.on('pageerror',e=>errors.push(e.message));await page.clock.setFixedTime(new Date('2026-10-22T08:00:00Z'));
 await page.addInitScript(()=>{
  const now=Date.now();window.chargeQueries=[];
  window.fixture={running:true,status:'Live',historyRevision:1,devices:[{kind:'shunt',mac:'EXAMPLE-SHUNT',name:'Beispielshunt'}],found:[],readings:[{device:'EXAMPLE-SHUNT',kind:'shunt',t:now,soc:70,current:10,voltage:13.3,power:133}],stats:{count:0},solarDaily:[],settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10},equipment:{version:1,groups:[]}};
  window.WattPit={appVersion:()=>'1.3.11',snapshot:()=>JSON.stringify(window.fixture),syncState:()=>JSON.stringify({busy:false,cockpitNoticeAccepted:true,theme:window.fixture.settings.theme,palette:'classic'}),dashboard:()=>localStorage.getItem('charge-board')||'{}',saveDashboard:json=>{localStorage.setItem('charge-board',json);return '';},settings:json=>{Object.assign(window.fixture.settings,JSON.parse(json));return '';},history:()=>'{"points":[]}',dashboardHistory:(from,to,metric)=>{window.chargeQueries.push(metric);return '{"points":[],"summary":{}}';},energyHistoryWindow:()=>'{"solar":{"points":[],"summary":{}},"consumption":{"points":[],"summary":{}},"balance":{"points":[],"summary":{}}}'};
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 const before=await page.evaluate(()=>structuredClone(dashboard.config.tiles));
 await page.locator('#addTile').click();await page.locator('#tileSearch').fill('Restladezeit');await page.locator('[data-metric="chargeTime"]').click();
 const id=await page.evaluate(()=>dashboard.config.tiles.find(t=>t.metric==='chargeTime').id),tile=page.locator(`[data-tile="${id}"]`);
 const resized=async viewport=>{await page.setViewportSize(viewport);await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));};
 assert.deepEqual(await page.evaluate(()=>dashboard.config.tiles.filter(t=>t.metric!=='chargeTime')),before,'adding the tile must preserve the existing dashboard');
 assert.equal(await tile.locator('.tile-number').textContent(),'≈ 3,0');assert.match(await tile.locator('.tile-hint').textContent(),/Bis 100 %/);
 for(let i=0;i<10;i++)await page.evaluate(()=>poll());assert.equal(await page.evaluate(()=>dashboard.chargeEstimator.samples.length),1);
 await page.evaluate(()=>{window.fixture.settings.capacity=200;poll();});assert.equal(await tile.locator('.tile-number').textContent(),'≈ 6,0');
 await page.evaluate(()=>{window.fixture.settings.capacity=100;window.fixture.readings[0].voltage=null;poll();});assert.equal(await tile.locator('.tile-number').textContent(),'≈ 3,0');
 // Missing measurements must clear the smoothing buffer and never imply zero hours.
 await page.evaluate(()=>{window.fixture.readings[0].soc=null;poll();});assert.equal(await tile.locator('.tile-number').textContent(),'—');
 await page.evaluate(()=>{Object.assign(window.fixture.readings[0],{t:Date.now(),soc:70,current:-2});poll();});assert.equal(await tile.locator('.tile-number').textContent(),'—');assert.equal(await tile.locator('.tile-hint').textContent(),'Wird nicht geladen');
 assert.equal(await tile.locator('.tile-hint').isVisible(),true);
 await page.evaluate(()=>{window.fixture.running=false;poll();});assert.equal(await tile.locator('.tile-number').textContent(),'—');
 await page.evaluate(()=>{window.fixture.running=true;Object.assign(window.fixture.readings[0],{t:Date.now(),soc:70,current:10});poll();});assert.equal(await tile.locator('.tile-number').textContent(),'≈ 3,0');
 await page.clock.setFixedTime(new Date('2026-10-22T08:00:46Z'));await page.evaluate(()=>poll());assert.equal(await tile.locator('.tile-number').textContent(),'—');assert.match(await tile.locator('.tile-hint').textContent(),/Kein aktueller Shuntwert/);
 await page.evaluate(()=>{Object.assign(window.fixture.readings[0],{t:Date.now(),soc:100,current:0});poll();});assert.equal(await tile.locator('.tile-number').textContent(),'0,0');assert.equal(await tile.locator('.tile-hint').textContent(),'100 % laut SmartShunt');
 await page.evaluate(()=>{window.fixture.readings=[];poll();window.fixture.readings=[{device:'EXAMPLE-SHUNT',t:Date.now(),soc:70,current:10}];poll();});
 await tile.click();await page.locator('#tileActionDetail').click();
 assert.equal(await page.locator('#detailReading .detail-number').textContent(),'≈ 3,0');assert.equal(await page.locator('#detailPeriod').isVisible(),false);assert.equal(await page.locator('#detailCanvas').isVisible(),false);assert.equal(await page.locator('#detailComparisons').isVisible(),false);
 assert.match(await page.locator('#detailReading').textContent(),/Netto-Ladestrom.*fünf Minuten/);
 await page.locator('#detailSettings>summary').click();assert.equal(await page.locator('#detailType option[value="chart"]').count(),0);assert.equal(await page.locator('#detailValue option').count(),1);
 await page.locator('#detailUnit').selectOption('min');assert.equal(await page.locator('#detailReading .detail-number').textContent(),'≈ 180,0');
 await page.locator('#detailType').selectOption('gauge');assert.equal(await page.locator('#detailReading .tile-number').textContent(),'≈ 180,0');
 await page.locator('#detailClose').click();assert.equal(await tile.locator('.tile-number').textContent(),'≈ 3,0');assert.equal(await tile.locator('.tile-unit').textContent(),'h');
 assert.equal(await page.evaluate(()=>window.chargeQueries.includes(undefined)),false,'no undefined backend may be queried for the live-only estimate');
 for(const viewport of [{width:320,height:800},{width:390,height:844},{width:844,height:390}]){
  await resized(viewport);
  for(const theme of ['light','dark']){
   await page.evaluate(theme=>{window.fixture.settings.theme=theme;window.fixture.readings[0].t=Date.now();poll();},theme);await tile.scrollIntoViewIfNeeded();
   assert.equal(await tile.locator('.tile-hint').isVisible(),true);assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false);
   assert.ok(await tile.locator('.tile-number').evaluate(el=>el.scrollWidth<=el.clientWidth+1),'estimate must fit the tile');
  }
 }
 await resized({width:390,height:844});await tile.scrollIntoViewIfNeeded();
 const output=process.env.WATTPIT_SCREENSHOT_DIR;if(output){fs.mkdirSync(output,{recursive:true});await page.locator('#toast').evaluate(el=>el.hidden=true);await tile.screenshot({path:path.join(output,'charge-time.png')});}
 await page.reload();assert.equal(await page.locator(`[data-tile="${id}"] .tile-number`).textContent(),'≈ 3,0');
 assert.deepEqual(errors,[]);await browser.close();console.log('Restladezeit: picker, net current/capacity, missing and stale readings, charging/full states, live-only detail, units/gauge, saved layout and both themes/orientations passed.');
})().catch(e=>{console.error(e);process.exit(1);});
