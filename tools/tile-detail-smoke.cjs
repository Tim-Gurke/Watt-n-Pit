const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--disable-gpu']});
 const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Europe/Berlin',hasTouch:true}),errors=[];
 page.setDefaultTimeout(8000);page.on('pageerror',e=>errors.push(e.message));await page.clock.setFixedTime(new Date('2026-10-18T10:00:00Z'));
 await page.addInitScript(()=>{
  const now=Date.now(),rise=Date.parse('2026-10-18T05:48:00Z');window.saves=0;window.calls=[];window.missing=false;
  window.mock={running:true,status:'Live',devices:[{mac:'SHUNT',kind:'shunt',name:'Testshunt'},{mac:'SOLAR',kind:'solar',name:'Testregler'}],readings:[{device:'SHUNT',t:now,soc:76.5,voltage:12.92,power:-109,current:-8.4},{device:'SOLAR',t:now,solar:5}],found:[],historyRevision:0,stats:{count:400},equipment:{version:1,groups:[]},settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10,miniRange:'today',weatherPlace:{name:'Testort',latitude:50,longitude:10}},weather:{timezone:'Europe/Berlin',updated:now,days:Array.from({length:14},(_,i)=>({day:'2026-10-'+String(18+i).padStart(2,'0'),sunrise:rise+i*86400000,code:2,hours:5}))}};
  const series=(from,to,metric)=>{
   const points=window.missing?[]:Array.from({length:241},(_,i)=>{const v=metric==='solar'&&from+(to-from)*i/240<rise?0:metric==='voltage'?12.9+i/1000:metric==='soc'?76-i/100:metric==='dischargeEnergy'?i/3:metric==='solarEnergy'?i/4:Math.sin(i/14)*15+20;return {t:from+(to-from)*i/240,v,min:v===0?0:v-1,max:v===0?0:v+1,n:1,device:'test',origin:'live'};});
   return {points,summary:{from,to,last:points.at(-1)?.v??null,avg:20,min:4,max:36,coverage:1}};
  };
  window.WattPit={snapshot:()=>JSON.stringify(window.mock),dashboard:()=>localStorage.getItem('detail-board')||'{}',saveDashboard:json=>{window.saves++;localStorage.setItem('detail-board',json);return '';},settings:json=>{Object.assign(window.mock.settings,JSON.parse(json));return '';},history:()=>'{"points":[]}',dashboardHistory:(from,to,metric)=>{window.calls.push({from,to,metric});return JSON.stringify(series(from,to,metric));},energyHistory:(from,to)=>JSON.stringify({accountingVersion:2,solar:series(from,to,'solarEnergy'),consumption:series(from,to,'consumptionEnergy'),balance:series(from,to,'batteryEnergy')})};
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 const source=await page.evaluate(()=>({config:JSON.stringify(dashboard.config),saved:localStorage.getItem('detail-board'),saves:window.saves}));
 const verifyUnchanged=async()=>assert.deepEqual(await page.evaluate(()=>({config:JSON.stringify(dashboard.config),saved:localStorage.getItem('detail-board'),saves:window.saves})),source);
 const open=async(id='default-solar')=>{await page.locator(`[data-tile="${id}"]`).click();assert.equal(await page.locator('#tileActionSettings').isVisible(),true);await page.locator('#tileActionDetail').click();assert.equal(await page.locator('#tileDetail').isVisible(),true);};
 await open();assert.equal(await page.locator('#detailPeriod').inputValue(),'sunrise');assert.equal(await page.locator('#detailValue').inputValue(),'live');
 assert.equal(await page.locator('#detailCanvas').getAttribute('data-from'),String(Date.parse('2026-10-18T05:48:00Z')));
 for(const mode of ['today','yesterday','week','month','last','reset']){await page.locator('#detailPeriod').selectOption(mode);assert.equal(await page.locator('#detailError').isVisible(),false);}
 await page.locator('#detailPeriod').selectOption('last');await page.locator('#detailAmount').fill('3');await page.locator('#detailTimeUnit').selectOption('h');
 assert.equal(await page.locator('#detailCanvas').getAttribute('data-from'),String(Date.parse('2026-10-18T07:00:00Z')));
 await page.locator('#detailPeriod').selectOption('custom');await page.locator('#detailFrom').fill('2026-10-18T08:00');await page.locator('#detailTo').fill('2026-10-18T11:00');await page.locator('#detailApplyPeriod').click();
 assert.equal(await page.locator('#detailCanvas').getAttribute('data-from'),String(Date.parse('2026-10-18T06:00:00Z')));assert.equal(await page.locator('#detailCanvas').getAttribute('data-to'),String(Date.parse('2026-10-18T09:00:00Z')));
 await page.locator('#detailFrom').fill('2026-10-19T08:00');await page.locator('#detailApplyPeriod').click();assert.equal(await page.locator('#detailError').isVisible(),true);
 await page.locator('#detailPeriod').selectOption('today');
 await page.locator('#detailSettings>summary').click();await page.locator('#detailUnit').selectOption('kW');await page.locator('#detailType').selectOption('gauge');await page.locator('#detailDigits').selectOption('3');await page.locator('#detailColor').fill('#b887ee');await page.locator('#detailValue').selectOption('max');
 await page.locator('#detailScale').selectOption('fixed');await page.locator('#detailMin').fill('-1');await page.locator('#detailMax').fill('2');await page.locator('#detailMax').press('Tab');
 assert.equal(await page.locator('#detailReading .tile-gauge').count(),1);await page.locator('#detailScale').selectOption('auto');await page.locator('#detailType').selectOption('chart');await page.locator('#detailUnit').selectOption('W');await page.locator('#detailSettings>summary').click();
 await page.locator('#detailComparisons>summary').click();await page.locator('[data-compare="power"]').check();assert.equal(await page.locator('#detailCanvas').getAttribute('data-units'),'W');
 await page.locator('[data-compare="voltage"]').check();assert.equal(await page.locator('#detailCanvas').getAttribute('data-units'),'W,V');await page.locator('#detailComparisons>summary').click();
 const view=()=>page.evaluate(()=>({...dashboard.detail.view}));const full=await view();await page.locator('#detailZoomIn').click();assert.equal((await view()).to-(await view()).from,(full.to-full.from)/2);
 await page.locator('#detailCanvas').scrollIntoViewIfNeeded();let box=await page.locator('#detailCanvas').boundingBox(),before=await view();
 await page.mouse.move(box.x+box.width*.5,box.y+50);await page.mouse.down();await page.mouse.move(box.x+box.width*.7,box.y+50,{steps:8});await page.mouse.up();assert.ok((await view()).from<before.from,'drag did not pan');
 before=await view();await page.keyboard.down('Control');await page.mouse.wheel(0,-160);await page.keyboard.up('Control');await page.waitForTimeout(100);assert.ok((await view()).to-(await view()).from<before.to-before.from,'wheel did not zoom');
 const cdp=await page.context().newCDPSession(page);box=await page.locator('#detailCanvas').boundingBox();const y=Math.round(box.y+65),cx=Math.round(box.x+box.width/2);before=await view();
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:cx-30,y,id:1},{x:cx+30,y,id:2}]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:cx-60,y,id:1},{x:cx+60,y,id:2}]});await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});assert.ok((await view()).to-(await view()).from<before.to-before.from,'pinch did not zoom');
 await page.locator('#detailReset').click();assert.deepEqual(await view(),full);await verifyUnchanged();
 // A selected point stays visible and can be scrubbed without panning the time window.
 await page.locator('#detailCanvas').scrollIntoViewIfNeeded();box=await page.locator('#detailCanvas').boundingBox();
 const imageBefore=await page.locator('#detailCanvas').evaluate(el=>el.toDataURL());
 await page.mouse.click(box.x+box.width*.55,box.y+80);
 assert.equal(await page.locator('#detailTooltip').isVisible(),true);assert.ok(Number(await page.locator('#detailCanvas').getAttribute('data-selected-points'))>=2);
 assert.notEqual(await page.locator('#detailCanvas').evaluate(el=>el.toDataURL()),imageBefore,'selected point and vertical line were not painted');
 const selectedBefore=Number(await page.locator('#detailCanvas').getAttribute('data-selected-time')),tooltipBefore=await page.locator('#detailTooltip').textContent();
 const selectionView=await view(),sy=Math.round(box.y+80),sx=Math.round(box.x+box.width*.55);
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:sx,y:sy,id:1}]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:sx+60,y:sy,id:1}]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
 assert.ok(Number(await page.locator('#detailCanvas').getAttribute('data-selected-time'))>selectedBefore,'touch drag did not move the selection');
 assert.notEqual(await page.locator('#detailTooltip').textContent(),tooltipBefore);assert.deepEqual(await view(),selectionView,'selection drag panned instead');
 const kept=await page.locator('#detailCanvas').getAttribute('data-selected-time');await page.setViewportSize({width:832,height:390});
 assert.equal(await page.locator('#detailCanvas').getAttribute('data-selected-time'),kept,'rotation lost selection');
 await page.evaluate(()=>dashboard.detail.refresh(false));assert.equal(await page.locator('#detailTooltip').isVisible(),true,'refresh erased selection');
 await page.locator('#detailClearSelection').click();assert.equal(await page.locator('#detailTooltip').isVisible(),false);assert.equal(await page.locator('#detailCanvas').getAttribute('data-selected-time'),'');await verifyUnchanged();

 for(const width of [320,360,390,700,832,1100]){
  await page.setViewportSize({width,height:844});assert.equal(await page.evaluate(()=>document.getElementById('tileDetail').scrollWidth>innerWidth),false,'detail overflow '+width);
 }
 if(process.env.WATTPIT_SCREENSHOT_DIR){fs.mkdirSync(process.env.WATTPIT_SCREENSHOT_DIR,{recursive:true});await page.locator('#tileDetail').evaluate(el=>el.scrollTop=0);await page.screenshot({path:path.join(process.env.WATTPIT_SCREENSHOT_DIR,'detail-comparison.png')});}
 await page.evaluate(()=>window.handleBack());assert.equal(await page.locator('#tileDetail').isVisible(),false);assert.equal(await page.evaluate(()=>document.body.style.overflow),'');await verifyUnchanged();
 for(const viewport of [{width:320,height:800},{width:360,height:800},{width:390,height:844},{width:700,height:960},{width:844,height:1100},{width:640,height:360},{width:760,height:360},{width:832,height:390},{width:1100,height:844}]){
  const {width,height}=viewport,landscape=width>height;
  await page.setViewportSize(viewport);
  await page.locator(landscape?'body.overview-landscape':'body:not(.overview-landscape)').waitFor();
  for(const theme of ['light','dark']){
   if(await page.locator('html').getAttribute('data-theme')!==theme)await page.locator('#themeToggle').click();
   assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false,'overview overflow '+width);
   for(const id of ['addTile','layoutProfile','editDashboard','liveStatus']){
    assert.equal(await page.locator('header #'+id).count(),landscape?1:0,id+' in wrong orientation');
    assert.equal(await page.locator('#'+id).count(),1,'duplicate '+id);
   }
   const h=await page.locator('header').boundingBox();assert.ok(h.height<=(landscape&&width<760?76:54),'header too high: '+width+' '+h.height);
   if(landscape){
    for(const id of ['addTile','layoutProfile','editDashboard','liveStatus']){
     const box=await page.locator('#'+id).boundingBox();assert.ok(box.y>=h.y&&box.y+box.height<=h.y+h.height,id+' outside header');
     if(width>=760)assert.ok(Math.abs(box.y+box.height/2-h.y-h.height/2)<1,id+' not in the single header row');
    }
   }else{
    assert.equal(await page.locator('#overview>.pagehead').count(),1);
    assert.equal(await page.locator('#welcome+#dashboardToolbar').count(),1);
   }
  }
  if(process.env.WATTPIT_SCREENSHOT_DIR&&[390,1100].includes(width)){await page.evaluate(()=>window.scrollTo(0,0));await page.screenshot({path:path.join(process.env.WATTPIT_SCREENSHOT_DIR,'overview-'+width+'.png')});}
 }
 await verifyUnchanged();
 // Controls leave the header on other pages, even when rotating there.
 await page.locator('[data-page="settings"]').click();assert.equal(await page.locator('header #addTile').count(),0);
 await page.setViewportSize({width:390,height:844});await page.setViewportSize({width:832,height:390});
 assert.equal(await page.locator('#addTile').isVisible(),false);
 await page.locator('[data-page="overview"]').click();assert.equal(await page.locator('header #addTile').count(),1);
 await page.setViewportSize({width:390,height:844});
 await page.locator('#overview>#overviewHeader').waitFor();
 await verifyUnchanged();
 // The settings choice remains the regular persistent editor.
 await page.locator('[data-tile="default-solar"]').click();await page.locator('#tileActionSettings').click();await page.locator('#tileTitle').fill('Meine Solarleistung');await page.locator('#saveTile').click();
 assert.equal(await page.evaluate(()=>dashboard.config.tiles.find(t=>t.id==='default-solar').title),'Meine Solarleistung');
 // Half-cell keyboard movement and pointer resizing persist separately per profile.
 for(const width of [390,1100]){
  await page.setViewportSize({width,height:1000});await page.locator('#editDashboard').click();const profile=width<700?'compact':'wide';
  const soc=page.locator('[data-tile="default-soc"]'),origin=await page.evaluate(p=>({...dashboard.config.tiles[0].layouts[p]}),profile);
  await soc.focus();await page.keyboard.press('ArrowRight');await page.keyboard.press('ArrowDown');
  const moved=await page.evaluate(p=>({...dashboard.config.tiles[0].layouts[p]}),profile);assert.equal(moved.x,origin.x+.5);assert.equal(moved.y,origin.y+.5);
  const power=page.locator('[data-tile="default-power"]'),original=await page.evaluate(p=>({...dashboard.config.tiles.find(t=>t.id==='default-power').layouts[p]}),profile);
  await power.locator('.tile-resize').scrollIntoViewIfNeeded();const handle=await power.locator('.tile-resize').boundingBox(),dx=await page.locator('#dashboardBoard').evaluate(el=>el.clientWidth/24),dy=profile==='wide'?15:13;
  await page.mouse.move(handle.x+handle.width/2,handle.y+handle.height/2);await page.mouse.down();await page.mouse.move(handle.x+handle.width/2+dx,handle.y+handle.height/2+dy,{steps:6});await page.mouse.up();
  const resized=await page.evaluate(p=>({...dashboard.config.tiles.find(t=>t.id==='default-power').layouts[p]}),profile);assert.equal(resized.w,original.w+.5);assert.equal(resized.h,original.h+.5);await page.locator('#editDashboard').click();
 }
 const saved=await page.evaluate(()=>dashboard.config);await page.reload();assert.deepEqual(await page.evaluate(()=>dashboard.config),saved);
 await open('default-consumptionEnergy');await page.locator('#detailSettings>summary').click();assert.equal(await page.locator('#detailReverse').count(),0);await page.locator('#detailUnit').selectOption('kWh');await page.keyboard.press('Escape');assert.deepEqual(await page.evaluate(()=>dashboard.config),saved);
 await page.evaluate(()=>{window.missing=true;window.mock.historyRevision++;poll();});await open();assert.equal(await page.locator('#detailReading .detail-number').textContent(),'5');await page.locator('#detailSettings>summary').click();await page.locator('#detailValue').selectOption('last');assert.equal(await page.locator('#detailReading .detail-number').textContent(),'—');await page.locator('#detailClose').click();
 assert.deepEqual(errors,[]);await browser.close();console.log('Portrait layout, landscape-only header, rotation/navigation, both fine-grid profiles, temporary detail settings, all periods, comparisons, independent units, zoom/pan/pinch, back/escape and persistence passed.');
})().catch(e=>{console.error(e);process.exit(1);});
