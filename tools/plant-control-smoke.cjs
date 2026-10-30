const waitForPage=require('./browser-wait.cjs');
const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const preset=require('../app/src/main/assets/dashboard-preset.json');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.WATTPIT_CHROMIUM?{executablePath:process.env.WATTPIT_CHROMIUM}:{})});
 try{for(const theme of ['dark','light']){
  const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(({theme,preset})=>{
   window.host={revision:1,capacity:100,equipment:{version:1,groups:[{id:'mobile',label:'Mobiles Modul',count:1,watts:100,placement:'mobile',measurement:'shunt',tilt:45,defaultActive:false}]},setup:{version:1,groups:{mobile:{active:false,azimuth:180,shade:[0,0,0]}},usageMode:'auto',manual:[null,null,null,null]},roller:{fresh:true,status:'BMS verbunden',profile:{mac:'AA:BB:CC:DD:EE:FF',name:'Testroller'},live:{soc:70,remainingAh:18,fullAh:26,voltage:50,current:1.9,power:95},session:{active:false},sessionRevision:0}};
   window.requests=[];window.fail=false;
   const snapshot=()=>({running:true,controlRevision:host.revision,historyRevision:host.revision,devices:[],readings:[],found:[],stats:{count:0},settings:{theme,palette:'classic',capacity:host.capacity,sampleSeconds:10,solarSetup:host.setup,weatherPlace:{name:"Testort",latitude:49,longitude:8}},equipment:host.equipment,weather:{},lan:{connected:true}});
   window.finishControl=error=>{
    const q=window.requests.at(-1);
    if(!error){const a=q.args;if(q.action==='settings')host.capacity=JSON.parse(a[0]).capacity;if(q.action==='saveEquipment')host.equipment=JSON.parse(a[0]);if(q.action==='saveSolarSetup'||q.action==='saveSolarSite')host.setup=JSON.parse(a[0]);if(q.action==='saveRoller')host.roller.profile=JSON.parse(a[0]);host.revision++;}
    window.onPlantResult({id:q.id,...(error?{error}:{ok:true})});
   };
   window.WattPit={appVersion:()=> '1.6.3',isLanViewer:()=>true,controlRevision:()=>host.revision,requestPlantCommand:q=>requests.push(JSON.parse(q)),refreshLan:()=>{},lanState:()=>JSON.stringify({role:'viewer',name:'Testanlage',connected:true}),snapshot:()=>JSON.stringify(snapshot()),syncState:()=>JSON.stringify({busy:false,cockpitNoticeAccepted:true,theme,palette:'classic'}),dashboardPreset:()=>JSON.stringify(preset),dashboard:()=>JSON.stringify(preset),saveDashboard:()=>'',solarSetup:()=>JSON.stringify(host.setup),rollerSnapshot:()=>JSON.stringify(host.roller),rollerDevices:()=> '[]',settings:()=>'',saveEquipment:()=>{throw Error('A viewer must use the acknowledged command');},saveSolarSetup:()=>{throw Error('A viewer must use the acknowledged command');},saveSolarSite:()=>{throw Error('A viewer must use the acknowledged command');},saveRoller:()=>{throw Error('A viewer must use the acknowledged command');},weatherPlace:()=>{throw Error('A viewer must use the acknowledged command');},requestHistory:q=>{q=JSON.parse(q);setTimeout(()=>window.onHistoryResult({id:q.id,data:q.method==='dailyEnergy'?{accountingVersion:2,days:[]}:{points:[],summary:{}}}),1);}};
  },{theme,preset});
  await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
  assert.deepEqual(await page.evaluate(()=>dashboard.config.tiles.map(t=>t.layouts)),preset.tiles.map(t=>t.layouts));
  await page.locator('[data-page="settings"]').click();
  for(const id of ['saveSettings','editEquipment','solarSetupSettings','weatherOff','rollerSettings'])assert.equal(await page.locator('#'+id).isEnabled(),true,id);
  await page.locator('#capacitySetting').fill('120');await page.locator('#saveSettings').click();
  assert.equal(await page.evaluate(()=>host.capacity),100);assert.match(await page.locator('#plantActionStatus').textContent(),/erwartet/);
  await page.evaluate(()=>finishControl());await waitForPage(page,()=>!controls.pending);assert.equal(await page.evaluate(()=>snapshot.settings.capacity),120);assert.match(await page.locator('#plantActionStatus').textContent(),/bestätigt/);
  await page.locator('#capacitySetting').fill('135');await page.locator('#saveSettings').click();await page.evaluate(()=>finishControl('Hauptgerät nicht erreichbar.'));assert.equal(await page.evaluate(()=>host.capacity),120);assert.match(await page.locator('#plantActionStatus').textContent(),/nicht erreichbar/);
  await page.locator('#editEquipment').click();await page.locator('[data-field="watts"]').fill('110');await page.locator('#saveSolarEquipment').click();assert.equal(await page.evaluate(()=>requests.at(-1).action),'saveEquipment');assert.equal(await page.locator('#modal').isVisible(),true);await page.evaluate(()=>finishControl());await page.locator('#modal').waitFor({state:'hidden'});assert.equal(await page.evaluate(()=>host.equipment.groups[0].watts),110);
  await page.locator('#solarSetupSettings').click();await page.locator('[data-solar-active]').check();await page.locator('#solarSetupSave').click();assert.equal(await page.evaluate(()=>requests.at(-1).action),'saveSolarSite');await page.evaluate(()=>finishControl());await page.locator('#modal').waitFor({state:'hidden'});assert.equal(await page.evaluate(()=>host.setup.groups.mobile.active),true);
  await page.locator('#weatherOff').click();assert.equal(await page.evaluate(()=>requests.at(-1).action),'weatherPlace');await page.evaluate(()=>finishControl());
  await page.locator('[data-page="storage"]').click();assert.match(await page.locator('#rollerStored').textContent(),/865,8 Wh/);
  await page.locator('#rollerConfigure').click();await page.locator('#roller-targetPercent').fill('85');await page.locator('#rollerSave').click();assert.equal(await page.evaluate(()=>requests.at(-1).action),'saveRoller');await page.evaluate(()=>finishControl());await page.locator('#modal').waitFor({state:'hidden'});assert.equal(await page.evaluate(()=>host.roller.profile.targetPercent),85);
  for(const viewport of [{width:320,height:800},{width:844,height:390}]){
   await page.setViewportSize(viewport);await page.locator('[data-page="overview"]').click();await page.waitForTimeout(100);
   assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false);
   const large=await page.evaluate(()=>[...document.querySelectorAll('body *')].filter(e=>e.getClientRects().length&&e.childNodes.length&&[...e.childNodes].some(n=>n.nodeType===3&&n.textContent.trim())&&parseFloat(getComputedStyle(e).fontSize)>20.1).map(e=>({id:e.id,tag:e.tagName,size:getComputedStyle(e).fontSize})));assert.deepEqual(large,[]);
  }
  assert.deepEqual(errors,[]);await page.close();
 }
 console.log('PASS acknowledged viewer settings, equipment, mobile setup, weather and roller profile; current stored Wh; approved layout; font maximum; 320/844px and both themes.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
