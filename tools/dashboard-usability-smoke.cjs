const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.WATTPIT_CHROMIUM?{executablePath:process.env.WATTPIT_CHROMIUM}:{})});
 try{
  const page=await browser.newPage({viewport:{width:390,height:844},hasTouch:true}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
  await page.evaluate(()=>{localStorage.setItem('wattpit-roller',JSON.stringify({mac:'AA:BB:CC:DD:EE:FF'}));roller.render();dashboard.editing=true;dashboard.config.tiles[0].layouts.compact.y=40;dashboard.commit();});
  const tile=page.locator('[data-tile="default-soc"]');await tile.scrollIntoViewIfNeeded();const before=await page.evaluate(()=>scrollY);assert.ok(before>300);
  await page.evaluate(()=>{const t=dashboard.config.tiles[0];t.layouts.compact.w=5;dashboard.commit();});await page.waitForTimeout(80);assert.ok(Math.abs(await page.evaluate(()=>scrollY)-before)<4,'tile resize jumped scroll position');
  await page.evaluate(()=>dashboard.editor('default-soc'));await page.evaluate(()=>{document.querySelectorAll('#modal details').forEach(el=>el.open=true);document.querySelector('#modal .sheet').scrollTop=250;});
  const modalScroll=await page.locator('#modal .sheet').evaluate(e=>e.scrollTop);assert.ok(modalScroll>50);
  await page.evaluate(()=>{document.getElementById('tileMetric').value='current';document.getElementById('tileMetric').dispatchEvent(new Event('change'));});await page.waitForTimeout(60);
  assert.ok(Math.abs(await page.locator('#modal .sheet').evaluate(e=>e.scrollTop)-modalScroll)<4,'editor lost its scroll position');await page.locator('#closeModal').click();
  await page.locator('[data-page="storage"]').click();assert.equal(await page.locator('#rollerPanel').isVisible(),true);assert.equal(await page.locator('#overview #rollerPanel').count(),0);
  assert.equal(await page.locator('#rollerPanel').evaluate(e=>getComputedStyle(e).overflowY),'visible');
  await page.locator('#rollerPanel').hover();const storageScroll=await page.evaluate(()=>scrollY);await page.mouse.wheel(0,180);await page.waitForTimeout(180);assert.ok(await page.evaluate(()=>scrollY)>=storageScroll,'storage page must scroll naturally');
  await page.locator('[data-page="overview"]').click();await page.evaluate(()=>{dashboard.reset();});await page.locator('#confirmDashboardReset').click();
  assert.deepEqual(await page.evaluate(()=>dashboard.config.tiles[0].layouts.compact),{x:0,y:0,w:4,h:5});

  // Real touch input must move the page even when it starts over a chart.
  const cdp=await page.context().newCDPSession(page);
  const swipe=async(selector)=>{
   await page.locator(selector).scrollIntoViewIfNeeded();
   const box=await page.locator(selector).boundingBox(),x=Math.round(box.x+box.width/2),y=Math.round(Math.min(650,box.y+box.height*.7));
   await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x,y,id:1}]});
   for(let i=1;i<=6;i++){await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x,y:y-i*20,id:1}]});await page.waitForTimeout(25);}
   await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await page.waitForTimeout(250);
  };
  await page.evaluate(()=>{showPage('history');document.getElementById('history').style.minHeight='2000px';});
  await page.locator('#historyChart').scrollIntoViewIfNeeded();const chartScroll=await page.evaluate(()=>scrollY);
  await swipe('#historyChart');assert.ok(await page.evaluate(()=>scrollY)>chartScroll+20,'history chart trapped vertical touch scrolling');
  await page.evaluate(()=>showPage('overview'));
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  await page.evaluate(()=>{dashboard.editing=false;snapshot.devices=[{mac:'AA:BB:CC:DD:EE:FF',kind:'shunt'}];window.pullCount=0;pullReload.a.reload=()=>window.pullCount++;scrollTo(0,0);});
  await page.waitForFunction(()=>window.scrollY<=1&&pullReload.a.canStart());
  const gesture=async(dy=100,dx=0,cancel=false)=>page.evaluate(({dy,dx,cancel})=>{const el=document.getElementById('overviewHeader'),touch=(type,x,y)=>{const e=new Event(type,{bubbles:true,cancelable:true});Object.defineProperty(e,'touches',{value:type==='touchend'||type==='touchcancel'?[]:[{clientX:x,clientY:y}]});el.dispatchEvent(e);};touch('touchstart',100,100);touch('touchmove',100+dx,100+dy);touch(cancel?'touchcancel':'touchend',100+dx,100+dy);},{dy,dx,cancel});
  await gesture();assert.equal(await page.evaluate(()=>pullCount),1);await gesture(30);await gesture(100,200);await gesture(100,0,true);assert.equal(await page.evaluate(()=>pullCount),1);
  await page.evaluate(()=>lifecycle.busy=true);await gesture();assert.equal(await page.evaluate(()=>pullCount),1);await page.evaluate(()=>lifecycle.busy=false);
  assert.deepEqual(errors,[]);
  if(process.env.WATTPIT_SCREENSHOT_DIR){await page.evaluate(()=>{roller.render();showPage('storage');scrollTo(0,0);});await page.screenshot({path:path.join(process.env.WATTPIT_SCREENSHOT_DIR,'zusatzspeicher-390.png')});}
  console.log('PASS dashboard usability: editor and document scroll, separate additional-storage navigation and natural scrolling, complete reset, pull-to-refresh thresholds and guards');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
