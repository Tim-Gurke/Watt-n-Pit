/* Manual reload, deliberate cancellation and daily summaries; all values are fictional. */
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--disable-gpu',...(process.env.WATTPIT_BROWSER_ARGS||'').split(' ').filter(Boolean)]});
 const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Europe/Berlin',locale:'de-DE',hasTouch:true});page.setDefaultTimeout(10000);
 const errors=[];page.on('pageerror',e=>errors.push(e.message));await page.clock.setFixedTime(new Date('2026-10-22T06:22:00Z'));
 await page.addInitScript(()=>{
  const now=Date.now();window.starts=0;window.reloads=0;window.cancels=0;window.dayQueries=0;window.dayAmount=29.68;
  window.run={busy:false,background:false,runId:0,startedAt:now};window.archive={active:false,text:''};
  window.fixture={running:false,status:'Pausiert',historyRevision:1,devices:[{kind:'solar',mac:'DEMO-SOLAR',name:'Beispielregler'},{kind:'shunt',mac:'DEMO-SHUNT',name:'Beispielshunt'}],found:[],readings:[],stats:{count:300},settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10},solarDaily:[{day:'2026-10-22','Yield(Wh)':40,'Max. PV power(W)':86,'Min. battery voltage(V)':12.9,'Max. battery voltage(V)':13.4},{day:'2026-10-21','Yield(Wh)':30},{day:'2026-10-20','Yield(Wh)':0}]};
  window.finish=()=>{window.run.busy=false;window.archive={active:false,text:'Nachladen beendet.',updated:Date.now()};window.fixture.historyRevision++;poll();};
  window.begin=()=>{window.run={busy:true,background:false,runId:window.run.runId+1,startedAt:Date.now()};window.archive={active:true,device:'SmartSolar · Beispiel',text:'Fehlende Daten ergänzen',done:3,total:10};};
  window.WattPit={appVersion:()=>'1.3.10',snapshot:()=>JSON.stringify({...window.fixture,archiveBusy:window.run.busy,archive:window.archive}),syncState:()=>JSON.stringify({...window.run,cockpitNoticeAccepted:true,theme:window.fixture.settings.theme,palette:'classic',archive:window.archive}),start:()=>{window.starts++;window.fixture.running=true;},stop:()=>{window.fixture.running=false;},importMissingArchive:()=>{window.reloads++;window.begin();},cancelArchive:()=>{window.cancels++;},backgroundArchive:()=>{window.run.background=true;return '';},history:()=>'{'+'"points":[]}',dashboardHistory:()=>'{'+'"points":[],"summary":{}}',energyHistoryWindow:()=>'{'+'"solar":{},"consumption":{},"balance":{}}',dailyEnergy:()=>{throw Error('Synchronous day summaries freeze the UI');},requestHistory:json=>{
   const q=JSON.parse(json);if(q.method==='dailyEnergy')window.dayQueries++;
   const data=q.method==='dailyEnergy'?{accountingVersion:2,days:[{day:'2026-10-22',last:window.dayAmount,coverage:.99},{day:'2026-10-21',last:null,coverage:0},{day:'2026-10-20',last:0,coverage:1}]}:{points:[],summary:{}};
   setTimeout(()=>window.onHistoryResult({id:q.id,data}),30);
  },settings:json=>{Object.assign(window.fixture.settings,JSON.parse(json));return '';}};
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 await page.locator('#overviewRecord').scrollIntoViewIfNeeded();await page.locator('#overviewRecord').click();await page.evaluate(()=>poll());assert.equal(await page.evaluate(()=>window.starts),1);
 assert.equal(await page.evaluate(()=>window.reloads),0,'Play must only start recording');
 await page.locator('#reloadArchive').click();await page.locator('#syncWaiting').waitFor({state:'visible'});assert.equal(await page.evaluate(()=>window.reloads),1);
 await page.locator('#syncCancel').click();assert.equal(await page.evaluate(()=>window.cancels),0,'the first tap must never cancel immediately');
 assert.equal(await page.locator('#syncCancelChoice').isVisible(),true);assert.equal(await page.evaluate(()=>document.activeElement.id),'syncContinue');
 await page.locator('#syncContinue').click();assert.equal(await page.locator('#syncCancelChoice').isVisible(),false);assert.equal(await page.evaluate(()=>window.cancels),0);
 await page.locator('#syncCancel').click();await page.locator('#syncCancelConfirm').click();assert.equal(await page.evaluate(()=>window.cancels),1);
 await page.clock.setFixedTime(new Date('2026-10-22T06:22:02Z'));await page.evaluate(()=>window.finish());await page.locator('#reloadArchive:enabled').waitFor({state:'visible'});
 await page.locator('#reloadArchive').click();await page.locator('#syncWaiting').waitFor({state:'visible'});assert.equal(await page.evaluate(()=>window.reloads),2,'manual reload must remain available immediately after a cancelled run');
 await page.locator('#syncBackground').click();assert.equal(await page.locator('#reloadArchive').isDisabled(),true);
 await page.locator('[data-page="devices"]').click();await page.locator('#archiveCancel').click();assert.equal(await page.locator('#syncCancelChoice').isVisible(),true);assert.equal(await page.evaluate(()=>window.cancels),1,'the device-page cancellation must also ask for confirmation');
 await page.keyboard.press('Escape');await page.keyboard.press('Escape');await page.clock.setFixedTime(new Date('2026-10-22T06:22:04Z'));await page.evaluate(()=>window.finish());
 await page.locator('[data-page="history"]').click();await page.locator('#solarDailyArchive td').filter({hasText:'29,68'}).waitFor({state:'visible'});
 const row=day=>page.locator('#solarDailyArchive tr').filter({has:page.locator('td').filter({hasText:day})});
 assert.match(await row('2026-10-22').locator('td').nth(2).textContent(),/29,68.*99 % erfasst/);
 assert.match(await row('2026-10-21').locator('td').nth(2).textContent(),/—.*keine Messdaten/);
 assert.match(await row('2026-10-20').locator('td').nth(2).textContent(),/0,00.*100 % erfasst/);
 assert.equal(await page.evaluate(()=>window.dayQueries),1,'all days in one calendar month must share one asynchronous read');
 await page.evaluate(()=>{window.dayAmount=31.25;window.fixture.historyRevision++;poll();});await page.locator('#solarDailyArchive td').filter({hasText:'31,25'}).waitFor({state:'visible'});
 assert.equal(await page.evaluate(()=>window.dayQueries),2,'completion must refresh the calculated day totals');
 const output=process.env.WATTPIT_SCREENSHOT_DIR;if(output){fs.mkdirSync(output,{recursive:true});await page.locator('#toast').evaluate(el=>el.hidden=true);await page.locator('#solarDailyArchive').screenshot({path:path.join(output,'solar-daily-consumption.png')});}
 for(const viewport of [{width:320,height:800},{width:390,height:844},{width:844,height:390}]){
  await page.setViewportSize(viewport);await page.locator('[data-page="overview"]').click();
  for(const mode of ['light','dark']){
   await page.evaluate(mode=>{window.fixture.settings.theme=mode;poll();},mode);await page.locator('#reloadArchive').scrollIntoViewIfNeeded();
   const play=await page.locator('#overviewRecord').boundingBox(),reload=await page.locator('#reloadArchive').boundingBox();
   assert.ok(play.width<reload.width&&play.width>=44&&play.height>=44);assert.ok(Math.abs(play.y-reload.y)<1&&reload.x>play.x+play.width);
   assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false,'reload controls must fit both orientations and themes');
  }
 }
 await page.setViewportSize({width:390,height:844});await page.locator('#reloadArchive').scrollIntoViewIfNeeded();
 if(output){await page.locator('#toast').evaluate(el=>el.hidden=true);await page.locator('.record-actions').screenshot({path:path.join(output,'reload-controls.png')});}
 // A gesture already started on Play must not become a destructive dialog action.
 const play=await page.locator('#overviewRecord').boundingBox();await page.mouse.move(play.x+play.width/2,play.y+play.height/2);await page.mouse.down();
 await page.evaluate(()=>{window.begin();poll();});await page.mouse.up();assert.equal(await page.evaluate(()=>window.cancels),1);
 assert.deepEqual(errors,[]);await browser.close();console.log('Separate Play/reload controls, safe cancellation, immediate retry, async daily consumption, gaps/zeroes, cache refresh and both layouts/themes passed.');
})().catch(e=>{console.error(e);process.exit(1);});
