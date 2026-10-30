/* Reported regression: late multi-line status text must not displace chart content. */
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Europe/Berlin',isMobile:true,deviceScaleFactor:2}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));await page.clock.setFixedTime(new Date('2026-10-30T17:27:00Z'));
  await page.addInitScript(()=>{
   window.fixtureTheme='dark';window.late=false;window.revision=0;window.hold=false;window.queue=[];
   const points=(from,to,total)=>[{t:from,v:0,gap:true},{t:from+(to-from)*.3,v:total*.4,gap:false},{t:from+(to-from)*.5,v:total*.4,gap:true},{t:to,v:total,gap:false}];
   const data=q=>{const [from,to,metric]=q.args,series=total=>({points:points(from,to,total),summary:{last:total,min:0,max:total,n:4,coverage:window.late?.79:1,modelledMillis:to-from,inconsistentMillis:window.late?3600000:0,adjustedMillis:window.late?3600000:0,limitedMillis:window.late?3600000:0,missingPriorMillis:window.late?60000:0,abnormalMillis:window.late?60000:0,from,to}});
    if(q.method.startsWith('energy'))return {accountingVersion:2,solar:series(429.93),consumption:series(332.16),balance:series(252.32),mobile:series(126.89)};
    return {points:points(from,to,100),summary:{last:100,min:0,max:180,n:4}};
   };
   window.drain=()=>{while(window.queue.length){const q=window.queue.shift();window.onHistoryResult({id:q.id,data:data(q)});}};
   window.WattPit={appVersion:()=>'1.6.11',isLanViewer:()=>true,displayScale:()=>Number(localStorage.getItem('fixture-scale')||100),saveDisplayScale:n=>{localStorage.setItem('fixture-scale',n);return '';},
    dashboard:()=>localStorage.getItem('fixture-board')||'{}',saveDashboard:s=>{localStorage.setItem('fixture-board',s);return '';},settings:()=>'',history:()=>'{"points":[]}',dashboardHistory:()=>{throw Error('Blocking viewer read');},energyHistoryWindow:()=>{throw Error('Blocking viewer read');},dailyEnergy:()=>{throw Error('Blocking viewer read');},
    snapshot:()=>JSON.stringify({running:true,status:'Live',historyRevision:window.revision,lan:{id:'fixture-plant',connected:true},devices:[{kind:'solar',mac:'solar'},{kind:'shunt',mac:'shunt'}],readings:[{device:'solar',kind:'solar',t:Date.now(),solar:0,state:5},{device:'shunt',kind:'shunt',t:Date.now(),soc:97.6,power:-22,current:-22/13.24,voltage:13.24}],found:[],stats:{count:100},settings:{theme:window.fixtureTheme,palette:'classic',capacity:100,sampleSeconds:10},weather:{timezone:'Europe/Berlin',days:[{day:'2026-10-30',sunrise:Date.parse('2026-10-30T06:10:25Z'),sunset:Date.parse('2026-10-30T16:08:10Z')} ]}}),
    requestHistory:s=>{const q=JSON.parse(s);if(window.hold)window.queue.push(q);else setTimeout(()=>window.onHistoryResult({id:q.id,data:data(q)}),15);}
   };
  });
  await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
  await page.evaluate(()=>{
   const metrics=['solar','solarEnergy','mobileSolarEnergy','consumptionEnergy','energyBalance','soc'];
   dashboard.config.tiles=metrics.map((metric,i)=>{const t=WattDashboardCore.tile(metric,'layout-'+metric);t.type=metric==='soc'?'gauge':'chart';t.period.mode='sunrise';t.scaleMode='auto';for(const profile of ['compact','wide'])t.layouts[profile]={x:i%3*4,y:Math.floor(i/3)*6,w:4,h:6};return t;});
   dashboard.config.energyPeriod={...dashboard.config.tiles[1].period};dashboard.commit();
  });
  await page.waitForFunction(()=>document.querySelector('[data-tile="layout-consumptionEnergy"] .tile-number')?.textContent==='332,16').catch(async error=>{console.error(await page.evaluate(()=>({tiles:[...document.querySelectorAll('.cockpit-tile')].map(e=>({id:e.dataset.tile,text:e.textContent})),config:dashboard?.config,pending:window.queue})),errors);throw error;});
  const layout=await page.evaluate(()=>JSON.stringify(dashboard.config.tiles.map(t=>[t.id,t.layouts])));
  await page.waitForFunction(()=>[...document.querySelectorAll('.type-chart.cockpit-tile')].every(e=>e.querySelector('.tile-sparkline')));
  const geometry=()=>page.evaluate(()=>[...document.querySelectorAll('.type-chart.cockpit-tile')].map(el=>{
   const rect=selector=>el.querySelector(selector).getBoundingClientRect().toJSON(),hint=el.querySelector('.tile-hint'),zoom=el.getBoundingClientRect().width/el.offsetWidth;
   return {id:el.dataset.tile,card:el.getBoundingClientRect().toJSON(),head:rect('.tile-head'),reading:rect('.tile-reading'),curve:rect('.tile-sparkline'),axis:rect('.tile-chart-range'),hint:rect('.tile-hint'),maxHint:parseFloat(getComputedStyle(hint).lineHeight)*2*zoom};
  }));
  const check=async()=>{for(const g of await geometry()){
   assert.ok(g.reading.top>=g.head.bottom-.8,JSON.stringify(g));assert.ok(g.curve.height>0&&g.curve.top>=g.reading.bottom-.8,JSON.stringify(g));
   assert.ok(g.axis.top>=g.curve.bottom-.8,JSON.stringify(g));assert.ok(g.hint.top>=g.axis.bottom-.8,JSON.stringify(g));
   assert.ok(g.hint.height<=g.maxHint+1&&g.hint.bottom<=g.card.bottom+1,JSON.stringify(g));
  }};
  await check();
  // A later viewer refresh adds exactly the long warning/status combination from the report.
  await page.evaluate(()=>{window.late=true;window.hold=true;window.revision++;poll();});
  await check();await page.evaluate(()=>{window.drain();window.hold=false;});
  await page.waitForFunction(()=>document.querySelector('[data-tile="layout-consumptionEnergy"] .tile-hint')?.textContent.includes('auffällige Messwerte'));await check();
  assert.match(await page.locator('[data-tile="layout-consumptionEnergy"] .tile-hint').textContent(),/Teilsumme/);
  const output=process.env.WATTPIT_SCREENSHOT_DIR;if(output)fs.mkdirSync(output,{recursive:true});
  for(const width of [360,412,844])for(const theme of ['dark','light'])for(const scale of [50,100,150]){
   await page.setViewportSize({width,height:width===844?500:960});
   await page.evaluate(({theme,scale})=>{window.fixtureTheme=theme;poll();WattDisplay.apply(scale);}, {theme,scale});
   await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));await check();
   const bounds=await page.evaluate(expectedWidth=>({width:innerWidth,scroll:document.documentElement.scrollWidth,nav:document.querySelector('.bottom-nav').getBoundingClientRect().toJSON(),profile:dashboard.profile,overflow:[...document.querySelectorAll('body *')].filter(e=>e.getClientRects().length&&getComputedStyle(e).position!=='absolute'&&e.getBoundingClientRect().right>expectedWidth+2).slice(0,8).map(e=>({tag:e.tagName,id:e.id,cls:e.className,right:e.getBoundingClientRect().right}))}),width);
   assert.ok(bounds.scroll<=bounds.width+1,JSON.stringify({width,theme,scale,...bounds}));assert.equal(bounds.profile,width>=700?'wide':'compact');assert.ok(Math.abs(bounds.nav.right-width)<1,JSON.stringify(bounds));
   if(output&&width===360&&scale===100)await page.screenshot({path:path.join(output,`display-late-${theme}.png`),fullPage:true});
   // A real sheet remains reachable and does not move the saved tiles.
   await page.locator('[data-tile="layout-consumptionEnergy"]').click();await page.locator('#tileActionDetail').click();
   await page.waitForFunction(()=>document.getElementById('detailNote').textContent.includes('Teilsumme'));
   const canvas=await page.locator('#detailCanvas').boundingBox();await page.locator('#detailCanvas').scrollIntoViewIfNeeded();
   await page.locator('#detailCanvas').click({position:{x:Math.max(20,canvas.width*.6),y:40}});assert.equal(await page.locator('#detailTooltip').isVisible(),true);
   await page.locator('#detailClose').click();
  }
  assert.equal(await page.evaluate(()=>JSON.stringify(dashboard.config.tiles.map(t=>[t.id,t.layouts]))),layout);
  await page.locator('[data-page="settings"]').click();await page.locator('#displayScale').evaluate(e=>{e.value='50';e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));});
  assert.equal(await page.locator('#displayScaleValue').textContent(),'50 %');await page.reload();assert.equal(await page.evaluate(()=>WattDisplay.value),50);
  await page.locator('[data-page="settings"]').click();await page.locator('#resetDisplayScale').click();assert.equal(await page.evaluate(()=>WattDisplay.value),100);
  assert.deepEqual(errors,[]);await page.close();
  // Device-local settings must not be supplied by another handset or the LAN host.
  const other=await browser.newPage();await other.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));assert.equal(await other.evaluate(()=>WattDisplay.value),100);await other.close();
  console.log('PASS late viewer status remains within two lines; headings, values, plots and axes stay separate; 50–150% in both themes and layouts, details/selection, local persistence and handset isolation.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
