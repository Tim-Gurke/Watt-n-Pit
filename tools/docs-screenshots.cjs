/* Reproduce documentation images from the shipped assets. All values are fictional. */
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox',...(process.env.WATTPIT_BROWSER_ARGS||'').split(' ').filter(Boolean)],...(process.env.WATTPIT_CHROMIUM?{executablePath:process.env.WATTPIT_CHROMIUM}:{})});
 const page=await browser.newPage({viewport:{width:390,height:900},deviceScaleFactor:2,timezoneId:'Europe/Berlin',locale:'de-DE'}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.clock.setFixedTime(new Date('2026-10-17T12:42:00Z'));
 await page.addInitScript(()=>{
  const now=Date.now(),start=new Date('2026-10-17T12:00:00Z');
  const days=Array.from({length:14},(_,i)=>{const d=new Date(start);d.setUTCDate(d.getUTCDate()+i);const day=d.toISOString().slice(0,10);return {day,code:[2,3,0,1,61,2,0][i%7],hours:[4.2,2.5,7.8,6.1,0,3.5,7.2][i%7],sunrise:Date.parse(day+'T05:45:00Z')};});
  window.docConfig={};window.docBackup=null;
  window.docFixture={running:true,status:'Beispieldaten',historyRevision:1,devices:[{kind:'solar',mac:'DEMO-SOLAR',name:'SmartSolar · Beispiel',rssi:-48,seen:now,bondState:'bonded'},{kind:'shunt',mac:'DEMO-SHUNT',name:'SmartShunt · Beispiel',rssi:-50,seen:now,bondState:'bonded'}],found:[],readings:[],stats:{count:0},settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10,miniRange:'sunrise',weatherPlace:{name:'Beispielort',latitude:50,longitude:10}},weather:{timezone:'Europe/Berlin',days,updated:now},equipment:{version:1,groups:[{id:'demo-fixed',label:'Feste Module · Beispiel',count:2,watts:120,placement:'fixed',controller:'Bluetooth-Regler',measurement:'solar'},{id:'demo-mobile',label:'Mobiles Modul · Beispiel',count:1,watts:80,placement:'mobile',controller:'Zusätzlicher Regler',measurement:'shunt'}]},archive:{active:false,text:'Beispiel: Gerätearchive ergänzt.',updated:1},archiveDiagnostics:{solar:{result:'Beispiel · Archiv ergänzt',steps:['Android-Kopplung bereits vorhanden','Archivkanäle gelesen','Vorhandene Werte abgeglichen']},shunt:{result:'Beispiel · Archiv ergänzt',steps:['Android-Kopplung bereits vorhanden','Archivkanäle gelesen','Vorhandene Werte abgeglichen']}}};
  window.WattPit={snapshot:()=>JSON.stringify(window.docFixture),dashboard:()=>JSON.stringify(window.docConfig),saveDashboard:json=>{window.docConfig=JSON.parse(json);return '';},settings:json=>{Object.assign(window.docFixture.settings,JSON.parse(json));return '';},history:()=>'{"points":[]}',dashboardHistory:()=>'{"points":[],"summary":{}}',energyHistory:()=>'{"solar":{},"battery":{}}',saveEquipment:json=>{window.docFixture.equipment=JSON.parse(json);return '';},refreshWeather:()=>{},weatherPlace:json=>{const p=JSON.parse(json);window.docFixture.settings.weatherPlace=p.off?{}:p;return '';},chooseSettingsExport:json=>{window.docBackup=JSON.parse(json);return '';},chooseSettingsImport:()=>window.onSettingsImportPreview({backup:window.docBackup,name:'Watt-n-Pit-Einstellungen.json'}),importSettings:()=>'',cancelImport:()=>{},cancelArchive:()=>{},copyArchiveDiagnostics:()=>''};
 });
 const output=path.resolve(__dirname,'../docs/images');fs.mkdirSync(output,{recursive:true});
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html')+'?demo=1');
 await page.locator('.weather-day').first().waitFor();
 assert.equal(await page.locator('#demoBar').isVisible(),true);assert.equal(await page.locator('.weather-day').count(),14);assert.match(await page.locator('[data-tile="default-dischargeEnergy"] .tile-number').textContent(),/^-/);
 const screenshot=async name=>{assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false);await page.screenshot({path:path.join(output,name),animations:'disabled'});console.log(name);};
 const overview=async name=>{await page.evaluate(()=>window.scrollTo(0,0));const bottom=await page.locator('.dashboard-foot').evaluate(el=>el.getBoundingClientRect().bottom),nav=await page.locator('.bottom-nav').evaluate(el=>el.offsetHeight);await page.setViewportSize({width:390,height:Math.ceil(bottom+nav-4)});await screenshot(name);};
 await overview('overview-dark.png');
 await page.locator('#themeToggle').click();await overview('overview-light.png');
 await page.locator('#themeToggle').click();await page.setViewportSize({width:390,height:1000});await page.locator('#editDashboard').click();await overview('arrange.png');await page.locator('#editDashboard').click();
 await page.setViewportSize({width:390,height:1050});await page.locator('[data-tile="default-solar"]').click();await page.locator('#tilePeriod').selectOption('sunrise');
 assert.match(await page.locator('#tileSunriseHint').textContent(),/07:45/);await screenshot('tile-sunrise.png');await page.locator('#closeModal').click();
 await page.setViewportSize({width:390,height:1100});await page.locator('[data-page="history"]').click();await screenshot('history-dark.png');
 await page.locator('#themeToggle').click();await screenshot('history-light.png');await page.locator('#themeToggle').click();
 await page.locator('[data-metric="energy"]').click();assert.ok(await page.evaluate(()=>points.some(p=>p.kind==='shunt'&&p.v<0)));assert.equal(await page.locator('#chartLegend span').count(),3);assert.match(await page.locator('#energyNote').textContent(),/Ertrag − Entnahme:/);await screenshot('energy-history.png');
 await page.locator('[data-page="devices"]').click();await page.setViewportSize({width:390,height:1000});await screenshot('devices-archive.png');
 assert.equal(await page.locator('#deviceCards').getByText('DEMO-SOLAR',{exact:true}).count(),1);
 await page.locator('[data-page="settings"]').click();const backupCard=page.locator('#settings .settings-card').filter({has:page.locator('#exportConfiguration')});await backupCard.scrollIntoViewIfNeeded();await backupCard.screenshot({path:path.join(output,'settings-backup.png'),animations:'disabled'});console.log('settings-backup.png');
 await page.locator('#editEquipment').click();await page.locator('.remove-solar-group').last().click();await page.setViewportSize({width:390,height:1000});await screenshot('equipment-editor.png');
 await page.locator('#closeModal').click();await page.locator('[data-page="overview"]').click();await page.locator('#addTile').click();await page.locator('.metric-option[data-metric="energyBalance"]').click();
 const comparison=page.locator('.cockpit-tile').last();await comparison.click();await page.locator('summary').filter({hasText:/^Größe & Ebene$/}).click();await page.locator('#tileWidth').fill('12');await page.locator('#saveTile').click();
 assert.equal(await comparison.locator('.tile-sparkline').count(),1);await comparison.screenshot({path:path.join(output,'energy-tile.png'),animations:'disabled'});console.log('energy-tile.png');
 assert.equal(errors.length,0,errors.join('\n'));await browser.close();
 console.log('Documentation screenshots generated from 1.1.1 assets with fictional values, location and device identifiers.');
})().catch(error=>{console.error(error);process.exit(1);});
