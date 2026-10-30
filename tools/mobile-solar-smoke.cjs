const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const fs=require('node:fs');

(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--disable-gpu']});
 const page=await browser.newPage({viewport:{width:390,height:1000},timezoneId:'Europe/Berlin'}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));await page.clock.setFixedTime(new Date('2026-10-30T09:30:00Z'));
 await page.addInitScript(()=>{
  window.mobileMode='on';window.oldHost=false;window.offAt=null;window.mobileTestTheme='dark';
  const points=(from,to,kind,total)=>({points:[{t:from,last:from,v:0,min:0,max:0,n:1,gap:true,kind,origin:'calculated',device:'model-'+kind},{t:to,last:to,v:total,min:total,max:total,n:1,gap:false,kind,origin:'calculated',device:'model-'+kind}],summary:{last:total,min:Math.min(0,total),max:Math.max(0,total),n:2,coverage:1,from,to}});
  window.WattPit={appVersion:()=>'1.6.10',dashboard:()=>localStorage.getItem('mobile-board')||'{}',saveDashboard:json=>{localStorage.setItem('mobile-board',json);return '';},settings:()=>'',history:()=>'{"points":[]}',
   snapshot:()=>JSON.stringify({running:true,status:'Live',devices:[{kind:'solar',mac:'solar'},{kind:'shunt',mac:'shunt'}],readings:[{kind:'solar',device:'solar',t:Date.now(),solar:155,state:3},{kind:'shunt',device:'shunt',t:Date.now(),power:49,voltage:13.35,current:49/13.35,soc:71}],found:[],stats:{count:20},settings:{theme:window.mobileTestTheme,palette:'classic',capacity:100,sampleSeconds:10}}),
   dashboardHistory:(from,to,metric)=>{if(metric!=='mobileSolarPower')return '{"points":[],"summary":{}}';const v=window.mobileMode==='unknown'?null:window.mobileMode==='on'?60:0;return JSON.stringify({points:v===null?[]:[{t:from,v,gap:true},{t:to,v,gap:false}],summary:{last:v,live:v,liveAt:Date.now(),min:v,max:v,avg:v,estimated:true,coverage:v===null?0:1}});},
   energyHistoryWindow:(from,to)=>{const hours=(to-from)/3600000,unknown=window.mobileMode==='unknown',active=window.mobileMode==='on'||window.offAt!==null,modelled=active?Math.max(0,Math.min(to,window.offAt??to)-from):0,mobile=unknown?null:60*modelled/3600000;
    const solar=points(from,to,'solar',155*hours),battery=points(from,to,'balance',49*hours),m=points(from,to,'mobile',mobile),devices=points(from,to,'consumption',unknown?null:106*hours+mobile);m.summary.modelledMillis=modelled;devices.summary.modelledMillis=modelled;
    return JSON.stringify({accountingVersion:window.oldHost?1:2,solar,consumption:devices,balance:battery,mobile:m});}
  };
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 const before=await page.evaluate(()=>JSON.stringify(dashboard.config.tiles.map(t=>[t.id,t.layouts,t.title])));
 for(const metric of ['mobileSolarPower','mobileSolarEnergy']){await page.locator('#addTile').click();await page.locator(`.metric-option[data-metric="${metric}"]`).click();}
 assert.equal(await page.evaluate(()=>JSON.stringify(dashboard.config.tiles.slice(0,-2).map(t=>[t.id,t.layouts,t.title]))),before,'adding both tiles must leave all existing layouts and captions unchanged');
 const ids=await page.evaluate(()=>Object.fromEntries(['mobileSolarPower','mobileSolarEnergy','consumptionEnergy','energyBalance'].map(metric=>[metric,dashboard.config.tiles.find(t=>t.metric===metric).id])));
 const tile=metric=>page.locator(`[data-tile="${ids[metric]}"]`);
 assert.equal(await tile('mobileSolarPower').locator('.tile-number').textContent(),'60');assert.match(await tile('mobileSolarPower').locator('.tile-hint').textContent(),/Geschätzt/);assert.match(await tile('mobileSolarPower').locator('.tile-unit').textContent(),/geschätzt/);assert.equal(await tile('mobileSolarPower').locator('.tile-estimate-tag').isVisible(),true);
 assert.match(await tile('mobileSolarEnergy').locator('.tile-hint').textContent(),/Geschätzt/);assert.equal(await tile('consumptionEnergy').locator('.tile-label').textContent(),'Geräteverbrauch');assert.equal(await tile('energyBalance').locator('.tile-label').textContent(),'Akkubilanz');
 const energyBefore=await tile('mobileSolarEnergy').locator('.tile-number').textContent();
 await page.evaluate(()=>{window.offAt=Date.now();window.mobileMode='off';dashboard.cache.clear();poll();});
 assert.equal(await tile('mobileSolarPower').locator('.tile-number').textContent(),'0');assert.equal(await tile('mobileSolarEnergy').locator('.tile-number').textContent(),energyBefore,'switching off must retain mobile energy already produced today');
 await page.evaluate(()=>{window.mobileMode='none';window.offAt=null;dashboard.cache.clear();poll();});
 assert.equal(await tile('mobileSolarPower').locator('.tile-number').textContent(),'0');assert.equal(await tile('mobileSolarEnergy').locator('.tile-number').textContent(),'0,00');assert.doesNotMatch(await tile('consumptionEnergy').locator('.tile-hint').textContent(),/Geschätzt/);
 await page.evaluate(()=>{window.mobileMode='unknown';dashboard.cache.clear();poll();});assert.equal(await tile('mobileSolarPower').locator('.tile-number').textContent(),'—');assert.equal(await tile('consumptionEnergy').locator('.tile-number').textContent(),'—');assert.notEqual(await tile('energyBalance').locator('.tile-number').textContent(),'—');
 await page.evaluate(()=>{window.mobileMode='on';window.oldHost=true;dashboard.cache.clear();poll();});assert.equal(await tile('consumptionEnergy').locator('.tile-number').textContent(),'—');assert.match(await tile('consumptionEnergy').locator('.tile-hint').textContent(),/Hauptgerät.*aktualisieren/);
 await page.evaluate(()=>{window.oldHost=false;dashboard.cache.clear();poll();});
 await tile('mobileSolarPower').click();await page.locator('#tileActionDetail').click();assert.match(await page.locator('#detailNote').textContent(),/Geschätzt/);await page.locator('#detailSettings>summary').click();await page.locator('#detailValue').selectOption('live');assert.equal(await page.locator('.detail-number').textContent(),'60');await page.locator('#detailClose').click();
 const output=process.env.WATTPIT_SCREENSHOT_DIR;if(output)fs.mkdirSync(output,{recursive:true});
 for(const width of [360,844])for(const theme of ['dark','light']){await page.setViewportSize({width,height:width===844?500:1000});await page.evaluate(theme=>{window.mobileTestTheme=theme;poll();},theme);await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));assert.equal(await page.locator('html').getAttribute('data-theme'),theme);const overflow=await page.evaluate(()=>({body:document.body.scrollWidth,width:innerWidth,elements:[...document.querySelectorAll('body *')].filter(e=>e.getBoundingClientRect().right>innerWidth+1).slice(0,8).map(e=>({tag:e.tagName,id:e.id,cls:e.className,right:e.getBoundingClientRect().right}))}));assert.ok(overflow.body<=overflow.width,JSON.stringify({viewport:width,theme,...overflow}));assert.equal(await tile('mobileSolarPower').locator('.tile-label').textContent(),'Solarleistung mobil');if(output&&width===360){await tile('mobileSolarPower').scrollIntoViewIfNeeded();await page.screenshot({path:path.join(output,`mobile-solar-${theme}.png`)});}}
 await page.reload();assert.equal(await page.evaluate(()=>dashboard.config.tiles.filter(t=>['mobileSolarPower','mobileSolarEnergy'].includes(t.metric)).length),2);assert.deepEqual(errors,[]);
 await browser.close();console.log('Mobile W/Wh tiles, complete device load, off/absent/unknown states, retained earlier yield, old-host protection, details, saved layouts and both themes passed.');
})().catch(e=>{console.error(e);process.exit(1);});
