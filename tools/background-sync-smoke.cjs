/* Fictional native service: survives Activity reload; never uses a synchronous DB read during import. */
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--disable-gpu']});
 const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Europe/Berlin',hasTouch:true});page.setDefaultTimeout(10000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const until=async predicate=>{const deadline=Date.now()+10000;while(!await page.evaluate(predicate)){if(Date.now()>deadline)throw Error('Expected asynchronous UI state was not reached');await page.waitForTimeout(40);}};
 await page.clock.setFixedTime(new Date('2026-10-19T11:00:00Z'));
 await page.addInitScript(()=>{
  const now=Date.now();window.run=JSON.parse(sessionStorage.getItem('native-run')||'null')||{busy:false,background:false,runId:0,startedAt:now};
  window.saveRun=()=>sessionStorage.setItem('native-run',JSON.stringify(window.run));window.cancelCalls=0;window.asyncCalls=[];window.syncCalls=0;window.finished=false;
  window.archive={active:true,device:'SmartSolar · Beispiel',text:'Archiv wird gelesen',done:3,total:10,updated:1};
  window.fixture={running:false,devices:[],found:[],readings:[],historyRevision:0,stats:{count:20},settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10},weather:{timezone:'Europe/Berlin',days:[{day:'2026-10-19',sunrise:Date.parse('2026-10-19T05:45:00Z'),sunset:Date.parse('2026-10-19T16:15:00Z')}]}};
  const data=(method,args)=>{
   const [from,to]=args,series=(kind,total)=>({points:[{t:from,v:0,kind},{t:to,v:total,kind}],summary:{from,to,last:total,min:Math.min(0,total),max:Math.max(0,total),coverage:1}});
   if(method.startsWith('energy'))return {solar:series('solar',window.finished?120:100),consumption:series('consumption',window.finished?180:150),balance:series('balance',window.finished?-60:-50)};
   return {points:[{t:from,v:10,min:10,max:10,kind:'solar'},{t:to,v:20,min:20,max:20,kind:'solar'}],summary:{last:20,min:10,max:20}};
  };
  const sync=(method,args)=>{if(window.run.busy)throw Error('Synchronous database query during active sync');window.syncCalls++;return JSON.stringify(data(method,args));};
  window.WattPit={syncState:()=>JSON.stringify({...window.run,cockpitNoticeAccepted:true,archive:window.archive,theme:window.fixture.settings.theme,palette:'classic'}),backgroundArchive:id=>{if(id!==window.run.runId||!window.run.busy)return 'Lauf beendet';window.run.background=true;window.saveRun();return '';},snapshot:()=>JSON.stringify({...window.fixture,syncing:window.run.busy,archiveBusy:window.run.busy,archive:window.archive}),history:(...args)=>sync('history',args),dashboardHistory:(...args)=>sync('dashboardHistory',args),energyHistoryWindow:(...args)=>sync('energyHistoryWindow',args),requestHistory:json=>{const req=JSON.parse(json);window.asyncCalls.push(req);setTimeout(()=>window.onHistoryResult({id:req.id,data:data(req.method,req.args)}),100);},cancelArchive:()=>{window.cancelCalls++;},settings:json=>{Object.assign(window.fixture.settings,JSON.parse(json));return '';}};
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 const layouts=await page.evaluate(()=>JSON.stringify(dashboard.config.tiles.map(t=>t.layouts)));
 await page.evaluate(()=>{openModal('Eingabe','<input id="pendingText" value="Bleibt erhalten">');Object.assign(window.run,{busy:true,runId:1,background:false,startedAt:Date.now()});window.saveRun();poll();});
 assert.equal(await page.locator('#syncWaiting').isVisible(),true);assert.equal(await page.locator('#syncBackground').isVisible(),true);
 await page.locator('#syncBackground').click();assert.equal(await page.locator('#activityOverlay').isVisible(),false);assert.equal(await page.locator('#pendingText').inputValue(),'Bleibt erhalten');assert.equal(await page.locator('#syncCompact').isVisible(),true);assert.equal(await page.locator('main').evaluate(el=>el.inert),false);
 await page.locator('#closeModal').click();
 const calls=await page.evaluate(()=>window.syncCalls);
 await page.locator('[data-page="history"]').click();await page.locator('[data-range="week"]').click();await page.locator('[data-metric="energy"]').click();
 await until(()=>!historyPending&&energyData?.solar?.summary?.last===100);
 assert.equal(await page.evaluate(()=>window.syncCalls),calls);assert.ok(await page.evaluate(()=>window.asyncCalls.length)>0,'new periods did not load asynchronously');
 assert.match(await page.locator('#syncCompactTitle').textContent(),/vorläufig/);
 await page.evaluate(()=>{window.archive={active:false,text:'Erstes Gerät fertig'};poll();});assert.equal(await page.locator('#activityOverlay').isVisible(),false);assert.equal(await page.locator('#syncCompact').isVisible(),true);
 await page.evaluate(()=>{window.archive={active:true,device:'SmartShunt · Beispiel',text:'Verbinden',done:0,total:0};poll();});assert.match(await page.locator('#syncCompactDetail').textContent(),/SmartShunt/);
 await page.locator('#syncOpen').click();assert.equal(await page.locator('#syncWaiting').isVisible(),true);assert.equal(await page.locator('#syncWaitingProgress').getAttribute('value'),null);
 await page.evaluate(()=>poll());assert.equal(await page.locator('#syncWaiting').isVisible(),true,'review closed on next poll');
 await page.keyboard.press('Escape');assert.equal(await page.locator('#syncWaiting').isVisible(),false);
 await page.locator('[data-metric="voltage"]').click();await page.locator('#syncOpen').click();await page.waitForTimeout(400);await page.keyboard.press('Escape');await until(()=>!historyPending);assert.ok(await page.evaluate(()=>points.length)>0,'a result arriving behind the progress dialog remained stuck loading');
 await page.reload();await page.locator('#syncCompact').waitFor({state:'visible'});assert.equal(await page.locator('#activityOverlay').isVisible(),false,'Activity reload lost the native per-run choice');assert.equal(await page.locator('#syncCompact').isVisible(),true);
 await page.locator('[data-page="settings"]').click();await page.locator('#capacitySetting').fill('125');
 for(const viewport of [{width:320,height:800},{width:740,height:360},{width:390,height:844}]){
  await page.setViewportSize(viewport);assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false);
  for(const theme of ['light','dark']){
   await page.evaluate(t=>{window.fixture.settings.theme=t;poll();},theme);assert.equal(await page.locator('html').getAttribute('data-theme'),theme);
   await page.locator('#syncOpen').click();await page.locator('#syncBackground').scrollIntoViewIfNeeded();await page.locator('#syncBackground').click();assert.equal(await page.locator('#activityOverlay').isVisible(),false);
  }
 }
 await page.locator('#syncOpen').click();await page.locator('#syncCancel').click();assert.equal(await page.evaluate(()=>window.cancelCalls),0);await page.locator('#syncCancelConfirm').click();assert.equal(await page.evaluate(()=>window.cancelCalls),1);assert.equal(await page.locator('#syncCancel').isDisabled(),true);
 await page.evaluate(()=>{Object.assign(window.run,{busy:false,background:false});window.saveRun();window.archive={active:false,text:'Abgebrochen. Gespeicherte Daten bleiben erhalten.'};window.finished=true;window.fixture.historyRevision++;poll();});
 assert.equal(await page.locator('#syncCompact').isVisible(),false);assert.equal(await page.locator('#activityOverlay').isVisible(),false);assert.equal(await page.locator('#capacitySetting').inputValue(),'125');
 await page.evaluate(()=>{Object.assign(window.run,{busy:true,background:false,runId:2,startedAt:Date.now()});window.saveRun();poll();});assert.equal(await page.locator('#syncWaiting').isVisible(),true,'next run reused background choice');
 await page.locator('#syncBackground').click();await page.evaluate(()=>{Object.assign(window.run,{busy:false,background:false});window.archive={active:false,text:'Synchronisation abgeschlossen.'};window.fixture.historyRevision++;poll();});
 await page.locator('[data-page="overview"]').click();assert.equal(await page.locator('[data-tile="default-solarEnergy"] .tile-number').textContent(),'120,00');
 assert.equal(await page.evaluate(()=>JSON.stringify(dashboard.config.tiles.map(t=>t.layouts))),layouts);
 assert.equal(errors.length,0,errors.join('\n'));await browser.close();console.log('Per-run background choice, reload/device continuity, asynchronous periods, responsive status/cancel, completion, unsaved inputs and both orientations/themes passed.');
})().catch(e=>{console.error(e);process.exit(1);});
