const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
const preset=require('../app/src/main/assets/dashboard-preset.json');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.WATTPIT_CHROMIUM?{executablePath:process.env.WATTPIT_CHROMIUM}:{})});
 try{for(const theme of ['dark','light']){
  const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Europe/Berlin'}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.clock.setFixedTime(new Date('2026-10-29T10:00:00Z'));
  await page.addInitScript(({preset,theme})=>{
   window.state={state:'home',mode:'auto',plans:[],pending:[],revision:1};window.savedBoard=JSON.stringify(preset);
   window.WattPit={appVersion:()=> '1.6.7',isLanViewer:()=>false,lanState:()=>'{"role":"host","hosting":true}',dashboardPreset:()=>JSON.stringify(preset),dashboard:()=>window.savedBoard,saveDashboard:()=>'',snapshot:()=>JSON.stringify({running:false,devices:[],readings:[],found:[],stats:{count:0},settings:{theme,palette:'classic',capacity:100,sampleSeconds:10,miniRange:'today'}}),syncState:()=>JSON.stringify({busy:false,cockpitNoticeAccepted:true,theme,palette:'classic'}),settings:()=>'',solarSetup:()=> '{}',rollerSnapshot:()=> '{}',presenceSnapshot:()=>JSON.stringify(window.state),savePresence:mode=>{window.state.mode=mode;return '';},saveAbsence:json=>{const p=JSON.parse(json);window.state.plans=[{...p,id:p.id||'00000000-0000-0000-0000-000000000001',status:'planned'}];window.state.pending=[];window.state.revision++;return '';},resolveAbsence:(id,action)=>{const p=window.state.plans.find(p=>p.id===id);p.status=action==='cancel'?'cancelled':'snoozed';window.state.pending=[];window.state.revision++;return '';}};
  },{preset,theme});
  await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
  await page.locator('[data-page="settings"]').click();await page.locator('#presenceSettings').click();
  assert.match(await page.locator('#presenceLive').textContent(),/Bewohnt.*WLAN/);
  await page.locator('#presenceAdd').click();const originalReturn=await page.locator('#presenceTo').inputValue();await page.locator('#presenceFrom').fill('2026-10-29T14:00');assert.equal(await page.locator('#presenceTo').inputValue(),originalReturn);await page.locator('#presenceSave').click();
  assert.equal(await page.evaluate(()=>window.state.plans.length),1);assert.equal(await page.locator('[data-presence-edit]').textContent(),'Ändern');
  await page.locator('[data-presence-edit]').click();const keepReturn=await page.locator('#presenceTo').inputValue();await page.locator('#presenceFrom').fill('2026-10-29T16:00');assert.equal(await page.locator('#presenceTo').inputValue(),keepReturn);await page.locator('#presenceSave').click();await page.locator('#closeModal').click();
  await page.evaluate(()=>{state.plans[0].status='pending';state.pending=[state.plans[0]];state.revision++;poll();});
  assert.equal(await page.locator('#modalTitle').textContent(),'Du bist offenbar noch da');assert.deepEqual(await page.locator('.presence-choices button').allTextContents(),['Ändern','Absagen','Fahre gleich']);
  await page.locator('#presenceSoon').click();assert.equal(await page.locator('#modal').isHidden(),true);assert.equal(await page.evaluate(()=>state.plans[0].status),'snoozed');
  await page.evaluate(()=>{state.plans[0].status='pending';state.pending=[state.plans[0]];state.revision++;poll();});await page.locator('#presenceChange').click();assert.equal(await page.locator('#presenceTo').inputValue(),keepReturn);await page.locator('#presenceBack').click();await page.locator('#presenceReview').click();
  assert.equal(await page.evaluate(()=>Math.max(...[...document.querySelectorAll('#modal *')].map(e=>parseFloat(getComputedStyle(e).fontSize)))||0)<=20,true);
  if(process.env.WATTPIT_SCREENSHOT_DIR){fs.mkdirSync(process.env.WATTPIT_SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:path.join(process.env.WATTPIT_SCREENSHOT_DIR,`presence-${theme}.png`),fullPage:true});}
  await page.locator('#presenceCancel').click();assert.equal(await page.evaluate(()=>state.plans[0].status),'cancelled');
  assert.equal(await page.evaluate(()=>JSON.stringify(dashboard.config.tiles.map(t=>t.layouts))),JSON.stringify(preset.tiles.map(t=>t.layouts)));
  assert.deepEqual(errors,[]);await page.close();
 }console.log('PASS presence settings, independent departure/return, automatic still-here prompt, all three answers, both themes and preserved local layout.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
