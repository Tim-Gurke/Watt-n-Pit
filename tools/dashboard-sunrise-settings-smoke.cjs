const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--single-process','--no-zygote','--disable-gpu']});
 const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Europe/Berlin'}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  const now=Date.now(),day=new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Berlin',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(now)),rise=now-4*3600000;
  window.calls=[];window.exported=null;window.applyCount=0;window.rejectImport=false;
  window.mock={running:true,status:'Live',devices:[{mac:'TEST-SOLAR',name:'Testregler',kind:'solar',secret:'CONNECTION_SENTINEL'}],readings:[{device:'TEST-SOLAR',t:now,solar:10}],found:[],stats:{count:77},historyRevision:0,equipment:{version:1,groups:[]},settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10,miniRange:'sunrise',weatherPlace:{name:'Testort',latitude:48,longitude:8}},weather:{timezone:'Europe/Berlin',days:[{day,sunrise:rise,code:2,hours:3}],updated:now}};
  window.testConfig=JSON.parse(localStorage.getItem('sunrise-board')||'{}');
  window.WattPit={snapshot:()=>JSON.stringify(window.mock),dashboard:()=>JSON.stringify(window.testConfig),saveDashboard:json=>{window.testConfig=JSON.parse(json);localStorage.setItem('sunrise-board',json);return '';},history:()=>'{"points":[]}',dashboardHistory:(from,to,metric)=>{
   window.calls.push({from,to,metric});const energy=['solarEnergy','dischargeEnergy'].includes(metric),after=from>=rise-.5,total=metric==='solarEnergy'?(after?8:17):after?5:14,max=energy?total:after?6:99;
   return JSON.stringify({summary:{last:energy?total:3,avg:energy?total:3,min:0,max,coverage:.98},points:[{t:from,last:from,v:0,min:0,max:0,n:1,device:'test-'+metric,kind:metric==='dischargeEnergy'?'shunt':'solar',origin:energy?'calculated':'archive',period:3600,gap:false},{t:Math.min(now,to),last:Math.min(now,to),v:energy?total:3,min:energy?total:3,max,n:1,device:'test-'+metric,kind:metric==='dischargeEnergy'?'shunt':'solar',origin:energy?'calculated':'archive',period:3600,gap:false}]});
  },settings:json=>{Object.assign(window.mock.settings,JSON.parse(json));return '';},chooseSettingsExport:json=>{window.exported=JSON.parse(json);return '';},chooseSettingsImport:()=>window.onSettingsImportPreview({backup:window.exported,name:'test-settings.json'}),importSettings:json=>{
   if(window.rejectImport)return 'Sicherung konnte nicht gespeichert werden.';
   const b=JSON.parse(json);window.applyCount++;window.testConfig=b.dashboard;localStorage.setItem('sunrise-board',JSON.stringify(b.dashboard));Object.assign(window.mock.settings,b.settings,{weatherPlace:b.weatherPlace});window.mock.equipment=b.equipment;return '';
  }};
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 const tile=metric=>page.locator(`[data-tile="default-${metric}"]`),value=metric=>tile(metric).locator('.tile-number').textContent();
 const initial=await page.evaluate(()=>window.WattDashboardCore.defaults());
 assert.equal(await value('solar'),'10');assert.equal(await value('solarEnergy'),'17,00');assert.equal(await value('dischargeEnergy'),'-14,00');
 const soc=await tile('soc').boundingBox(),power=await tile('solar').boundingBox(),energy=await tile('solarEnergy').boundingBox(),discharge=await tile('dischargeEnergy').boundingBox();
 assert.equal(soc.y,power.y);assert.ok(soc.x<power.x&&discharge.y>soc.y&&energy.y>power.y);
 for(const metric of ['solar','solarEnergy','dischargeEnergy']){
  await tile(metric).click();await page.locator('#tilePeriod').selectOption('sunrise');assert.match(await page.locator('#tileSunriseHint').textContent(),/Ab .*Uhr/);await page.locator('#saveTile').click();
  assert.match(await tile(metric).locator('.tile-hint').textContent(),/seit Sonnenaufgang/);
  const call=await page.evaluate(metric=>window.calls.filter(c=>c.metric===metric).at(-1),metric);assert.equal(call.from,await page.evaluate(()=>window.mock.weather.days[0].sunrise));
 }
 assert.equal(await value('solar'),'10');assert.equal(await value('solarEnergy'),'8,00');assert.equal(await value('dischargeEnergy'),'-5,00');
 assert.equal(await tile('solar').locator('.tile-sparkline').getAttribute('data-scale-max'),'6.6');
 assert.deepEqual(await page.evaluate(()=>window.testConfig.tiles.map(t=>t.layouts)),initial.tiles.map(t=>t.layouts),'period selection moved tiles');
 await tile('dischargeEnergy').click();await page.locator('#tileReverseSign').check();await page.locator('#saveTile').click();
 assert.equal(await value('dischargeEnergy'),'-5,00');assert.equal(await tile('dischargeEnergy').locator('.tile-sparkline').getAttribute('data-scale-max'),'0');assert.equal(await tile('dischargeEnergy').locator('.tile-sparkline').getAttribute('data-scale-min'),'-5.5');
 await tile('dischargeEnergy').click();assert.equal(await page.locator('#tileReverseSign').isChecked(),true);await page.locator('summary').filter({hasText:/^Skala$/}).click();await page.locator('#tileScaleMode').selectOption('fixed');assert.equal(await page.locator('#tileMin').inputValue(),'-2000');assert.equal(await page.locator('#tileMax').inputValue(),'0');await page.locator('#saveTile').click();assert.equal(await value('dischargeEnergy'),'-5,00');assert.equal(await tile('dischargeEnergy').locator('.tile-sparkline').getAttribute('data-scale-min'),'-2000');
 // Confirm the period and its totals after a restart, without changing the layout.
 await page.reload();for(const metric of ['solar','solarEnergy','dischargeEnergy'])assert.match(await tile(metric).locator('.tile-hint').textContent(),/seit Sonnenaufgang/);
 assert.equal(await value('dischargeEnergy'),'-5,00');const board=await page.evaluate(()=>structuredClone(window.testConfig));
 // Export the real UI state, not a manually composed backup. Secrets and samples stay out.
 await page.locator('[data-page="settings"]').click();await page.locator('#exportConfiguration').click();
 const backup=await page.evaluate(()=>structuredClone(window.exported));assert.deepEqual(backup.dashboard,board);assert.equal(backup.settings.capacity,100);assert.equal(backup.weatherPlace.name,'Testort');assert.ok(!JSON.stringify(backup).includes('CONNECTION_SENTINEL'));assert.equal(backup.devices,undefined);assert.equal(backup.readings,undefined);
 // A cancelled preview and failed storage leave the current settings untouched.
 await page.evaluate(()=>{window.exported.settings.capacity=150;window.exported.settings.theme='light';window.exported.settings.palette='green';});
 await page.locator('#importConfiguration').click();assert.equal(await page.evaluate(()=>window.applyCount),0);await page.locator('#cancelConfigurationImport').click();assert.equal(await page.evaluate(()=>window.mock.settings.capacity),100);
 await page.locator('#importConfiguration').click();await page.evaluate(()=>window.rejectImport=true);await page.locator('#applyConfigurationImport').click();assert.equal(await page.evaluate(()=>window.applyCount),0);assert.match(await page.locator('#configurationError').textContent(),/nicht gespeichert/);await page.locator('#cancelConfigurationImport').click();
 // Bad files cannot apply a partial import; labels in the preview are escaped.
 await page.evaluate(()=>{const bad=structuredClone(window.exported);bad.dashboard.tiles[0].metric='not-supported';window.onSettingsImportPreview({backup:bad,name:'bad.json'});});assert.equal(await page.locator('#modal').isVisible(),false);assert.equal(await page.evaluate(()=>window.applyCount),0);
 await page.evaluate(()=>{window.rejectImport=false;window.onSettingsImportPreview({backup:window.exported,name:'<img src=x onerror=bad()>.json'});});assert.equal(await page.locator('#modal img').count(),0);await page.locator('#applyConfigurationImport').click();
 assert.equal(await page.evaluate(()=>window.applyCount),1);assert.equal(await page.locator('#capacitySetting').inputValue(),'150');assert.equal(await page.locator('html').getAttribute('data-theme'),'light');assert.equal(await page.locator('html').getAttribute('data-palette'),'green');assert.equal(await page.evaluate(()=>window.mock.devices[0].secret),'CONNECTION_SENTINEL');assert.equal(await page.evaluate(()=>window.mock.stats.count),77);assert.deepEqual(await page.evaluate(()=>window.testConfig),board);
 // Missing weather has an explicit fallback and remains a stored sunrise preference.
 await page.locator('[data-page="overview"]').click();await page.evaluate(()=>{window.mock.weather={};poll();});assert.match(await tile('solarEnergy').locator('.tile-hint').textContent(),/heute \(Ersatz\)/);await tile('solarEnergy').click();assert.match(await page.locator('#tileSunriseHint').textContent(),/nicht verfügbar.*ersatzweise/);assert.equal(await page.locator('#tilePeriod').inputValue(),'sunrise');await page.locator('#closeModal').click();
 if(process.env.WATTPIT_SCREENSHOT_DIR){fs.mkdirSync(process.env.WATTPIT_SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:path.join(process.env.WATTPIT_SCREENSHOT_DIR,'sunrise-settings-light.png'),fullPage:true});}
 assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false);assert.equal(errors.length,0,errors.join('\n'));await browser.close();console.log('Requested layout, sunrise queries and energy totals, restart, backup export/preview/cancel/error/apply, secret exclusion and visible fallback passed.');
})().catch(error=>{console.error(error);process.exit(1);});
