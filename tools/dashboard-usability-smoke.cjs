const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.WATTPIT_CHROMIUM?{executablePath:process.env.WATTPIT_CHROMIUM}:{})});
 try{
  const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
  await page.evaluate(()=>{localStorage.setItem('wattpit-roller',JSON.stringify({mac:'AA:BB:CC:DD:EE:FF'}));roller.render();dashboard.editing=true;dashboard.config.tiles[0].layouts.compact.y=40;dashboard.commit();});
  const tile=page.locator('[data-tile="default-soc"]');await tile.scrollIntoViewIfNeeded();const before=await page.evaluate(()=>scrollY);assert.ok(before>300);
  await page.evaluate(()=>{const t=dashboard.config.tiles[0];t.layouts.compact.w=5;dashboard.commit();});await page.waitForTimeout(80);assert.ok(Math.abs(await page.evaluate(()=>scrollY)-before)<4,'tile resize jumped scroll position');
  await page.evaluate(()=>dashboard.editor('default-soc'));await page.evaluate(()=>{document.querySelectorAll('#modal details').forEach(el=>el.open=true);document.querySelector('#modal .sheet').scrollTop=250;});
  const modalScroll=await page.locator('#modal .sheet').evaluate(e=>e.scrollTop);assert.ok(modalScroll>50);
  await page.evaluate(()=>{document.getElementById('tileMetric').value='current';document.getElementById('tileMetric').dispatchEvent(new Event('change'));});await page.waitForTimeout(60);
  assert.ok(Math.abs(await page.locator('#modal .sheet').evaluate(e=>e.scrollTop)-modalScroll)<4,'editor lost its scroll position');await page.locator('#closeModal').click();
  await page.evaluate(()=>{dashboard.config.blockOrder=['roller','tiles','history','record'];dashboard.commit();});
  assert.equal(await page.locator('#overviewBlocks > div').first().getAttribute('data-overview-block'),'roller');
  await page.evaluate(()=>dashboard.rollerEditor());await page.evaluate(()=>{document.getElementById('rollerBlockWidth').value='8';document.getElementById('rollerBlockHeight').value='360';});await page.locator('#saveRollerLayout').click();
  assert.deepEqual(await page.evaluate(()=>dashboard.config.rollerLayout.compact),{width:8,height:360});assert.equal(await page.locator('#rollerPanel').evaluate(e=>Math.round(e.getBoundingClientRect().height)),360);
  await page.setViewportSize({width:900,height:420});await page.waitForTimeout(100);assert.equal(await page.evaluate(()=>dashboard.profile),'wide');assert.deepEqual(await page.evaluate(()=>dashboard.config.rollerLayout.wide),{width:12,height:650});
  await page.reload();assert.deepEqual(await page.evaluate(()=>dashboard.config.rollerLayout.compact),{width:8,height:360});assert.equal(await page.locator('#overviewBlocks > div').first().getAttribute('data-overview-block'),'roller');
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>{dashboard.reset();});await page.locator('#confirmDashboardReset').click();
  assert.deepEqual(await page.evaluate(()=>dashboard.config.tiles[0].layouts.compact),{x:0,y:0,w:4,h:5});assert.deepEqual(await page.evaluate(()=>dashboard.config.rollerLayout.compact),{width:12,height:650});
  await page.evaluate(()=>{dashboard.editing=false;snapshot.devices=[{mac:'AA:BB:CC:DD:EE:FF',kind:'shunt'}];window.pullCount=0;pullReload.a.reload=()=>window.pullCount++;scrollTo(0,0);});
  const gesture=async(dy=100,dx=0,cancel=false)=>page.evaluate(({dy,dx,cancel})=>{const el=document.getElementById('overviewHeader'),touch=(type,x,y)=>{const e=new Event(type,{bubbles:true,cancelable:true});Object.defineProperty(e,'touches',{value:type==='touchend'||type==='touchcancel'?[]:[{clientX:x,clientY:y}]});el.dispatchEvent(e);};touch('touchstart',100,100);touch('touchmove',100+dx,100+dy);touch(cancel?'touchcancel':'touchend',100+dx,100+dy);},{dy,dx,cancel});
  await gesture();assert.equal(await page.evaluate(()=>pullCount),1);await gesture(30);await gesture(100,200);await gesture(100,0,true);assert.equal(await page.evaluate(()=>pullCount),1);
  await page.evaluate(()=>lifecycle.busy=true);await gesture();assert.equal(await page.evaluate(()=>pullCount),1);await page.evaluate(()=>lifecycle.busy=false);
  assert.deepEqual(errors,[]);
  if(process.env.WATTPIT_SCREENSHOT_DIR){await page.evaluate(()=>{roller.render();dashboard.config.blockOrder=['roller','tiles','history','record'];dashboard.commit();scrollTo(0,0);});await page.screenshot({path:path.join(process.env.WATTPIT_SCREENSHOT_DIR,'roller-positioned-390.png')});}
  console.log('PASS dashboard usability: editor and document scroll, roller order/size/reload/orientations, complete reset, pull-to-refresh thresholds and guards');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
