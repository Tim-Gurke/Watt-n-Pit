/* First-launch acknowledgement and responsive import wait state. Fictional devices only. */
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--single-process','--no-zygote','--disable-gpu']});
 const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Europe/Berlin'}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.clock.setFixedTime(new Date('2026-10-18T20:00:00Z'));
 await page.addInitScript(()=>{
  const now=Date.now(),rise=Date.parse('2026-10-18T05:45:00Z'),set=Date.parse('2026-10-18T16:15:00Z');
  window.busy=false;window.ackFailure=false;window.cancelCalls=0;window.heavyCalls=0;window.flowCalls=[];
  window.archive={active:true,device:'SmartSolar · Beispiel',text:'Archiv wird gelesen',done:2,total:10,updated:1};
  window.fixture={running:false,devices:[],found:[],readings:[],historyRevision:0,stats:{count:4},settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10},weather:{timezone:'Europe/Berlin',days:[{day:'2026-10-18',sunrise:rise,sunset:set}]}};
  const heavy=()=>{if(window.busy)throw Error('Expensive bridge called during import');window.heavyCalls++;};
  window.WattPit={syncState:()=>JSON.stringify({busy:window.busy,cockpitNoticeAccepted:localStorage.getItem('notice')==='yes',archive:window.archive,theme:window.fixture.settings.theme,palette:'classic'}),acknowledgeCockpit:()=>{if(window.ackFailure)return 'Speichern fehlgeschlagen';localStorage.setItem('notice','yes');return '';},cancelArchive:()=>{window.cancelCalls++;},snapshot:()=>{heavy();return JSON.stringify(window.fixture);},history:()=>{heavy();return '{"points":[]}';},dashboardHistory:()=>{heavy();return '{"points":[],"summary":{}}';},energyHistoryWindow:(from,to,source,solarFrom,solarTo)=>{
   heavy();window.flowCalls.push({from,to,source,solarFrom,solarTo});
   const s=(kind,total)=>({points:[{t:kind==='solar'?rise-60000:from,v:0},{t:kind==='solar'?set+60000:to,v:total}],summary:{last:total,from:kind==='solar'?rise-60000:from,to:kind==='solar'?set+60000:to,coverage:1}});
   return JSON.stringify({solar:s('solar',100),consumption:s('consumption',160),balance:s('balance',-60)});
  },settings:json=>{Object.assign(window.fixture.settings,JSON.parse(json));return '';}};
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 assert.equal(await page.locator('#cockpitNotice').isVisible(),true);assert.equal(await page.evaluate(()=>window.heavyCalls),0);
 await page.keyboard.press('Escape');assert.equal(await page.locator('#cockpitNotice').isVisible(),true);
 await page.reload();assert.equal(await page.locator('#cockpitNotice').isVisible(),true,'unconfirmed notice was persisted');
 await page.evaluate(()=>window.ackFailure=true);await page.locator('#cockpitUnderstood').click();assert.equal(await page.locator('#cockpitNotice').isVisible(),true);assert.match(await page.locator('#cockpitNoticeError').textContent(),/fehlgeschlagen/);
 await page.evaluate(()=>window.ackFailure=false);await page.locator('#cockpitUnderstood').click();assert.equal(await page.locator('#activityOverlay').isVisible(),false);
 assert.equal(await page.evaluate(()=>localStorage.getItem('notice')),'yes');
 const flow=await page.evaluate(()=>window.flowCalls.at(-1));assert.equal(flow.solarTo,Date.parse('2026-10-18T16:15:00Z'));assert.ok(flow.from<flow.solarFrom);assert.ok(flow.to>flow.solarTo);
 await page.reload();assert.equal(await page.locator('#activityOverlay').isVisible(),false);
 await page.evaluate(()=>{openModal('Bearbeitung','<input id="pendingEdit" value="Unfertige Änderung">');window.busy=true;poll();});
 const calls=await page.evaluate(()=>window.heavyCalls);assert.equal(await page.locator('#syncWaiting').isVisible(),true);assert.equal(await page.locator('main').evaluate(el=>el.inert),true);
 await page.keyboard.press('Tab');assert.equal(await page.evaluate(()=>document.activeElement.id),'syncCancel');
 for(const viewport of [{width:390,height:844},{width:740,height:360},{width:320,height:740}]){
  await page.setViewportSize(viewport);assert.equal(await page.locator('#activityOverlay').evaluate(el=>el.scrollWidth>innerWidth),false);
  await page.locator('#syncCancel').scrollIntoViewIfNeeded();assert.ok((await page.locator('#syncCancel').boundingBox()).height>=44);
 }
 assert.equal(await page.locator('#syncWaitingProgress').getAttribute('value'),'2');assert.match(await page.locator('#syncDevice').textContent(),/SmartSolar/);
 await page.evaluate(()=>{window.archive={active:false,text:'Erstes Gerät fertig'};poll();});assert.equal(await page.locator('#syncWaiting').isVisible(),true,'overlay disappeared between devices');assert.equal(await page.locator('#syncWaitingProgress').getAttribute('value'),null);
 await page.evaluate(()=>{window.archive={active:true,device:'SmartShunt · Beispiel',text:'Verbindung',done:0,total:0};poll();});assert.match(await page.locator('#syncDevice').textContent(),/SmartShunt/);
 assert.equal(await page.evaluate(()=>window.heavyCalls),calls,'history was recomputed during import');
 await page.locator('#syncCancel').click();assert.equal(await page.evaluate(()=>window.cancelCalls),1);assert.equal(await page.locator('#syncCancel').isDisabled(),true);
 await page.evaluate(()=>{window.busy=false;window.archive={active:false,text:'Import abgebrochen. Werte bleiben gespeichert.'};window.fixture.historyRevision++;poll();});
 assert.equal(await page.locator('#activityOverlay').isVisible(),false);assert.equal(await page.locator('#pendingEdit').inputValue(),'Unfertige Änderung');assert.equal(await page.locator('#modal').isVisible(),true);assert.equal(await page.evaluate(()=>document.body.style.overflow),'hidden');
 await page.locator('#closeModal').click();await page.locator('[data-page="settings"]').click();await page.locator('#capacitySetting').fill('125');
 await page.evaluate(()=>{window.busy=true;poll();});await page.evaluate(()=>{window.busy=false;window.archive={active:false,text:'Verbindungsfehler. Beim nächsten Import wird ergänzt.'};poll();});
 assert.equal(await page.locator('#capacitySetting').inputValue(),'125','sync lost an unsaved edit');
 for(const theme of ['light','dark']){
  await page.evaluate(t=>{window.fixture.settings.theme=t;window.busy=true;poll();},theme);assert.equal(await page.locator('html').getAttribute('data-theme'),theme);
  assert.equal(await page.locator('#syncWaiting').isVisible(),true);await page.evaluate(()=>{window.busy=false;poll();});
 }
 assert.equal(errors.length,0,errors.join('\n'));await browser.close();console.log('First-launch notice, per-device progress, responsive cancellation, queue continuity, bridge throttling and edit restoration passed.');
})().catch(e=>{console.error(e);process.exit(1);});
