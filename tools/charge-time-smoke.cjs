/* Automatic battery time and the approved portrait layout; fictional data only. */
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--disable-gpu']});
 const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Europe/Berlin',locale:'de-DE'});page.setDefaultTimeout(10000);
 const errors=[];page.on('pageerror',e=>errors.push(e.message));let time=Date.parse('2026-10-22T08:00:00Z');await page.clock.setFixedTime(new Date(time));
 await page.addInitScript(()=>{
  const now=Date.now(),day=new Date(now).toISOString().slice(0,10),rise=Date.parse(day+'T06:00:00Z');window.chargeQueries=[];
  window.fixture={running:true,status:'Live',historyRevision:1,devices:[{kind:'shunt',mac:'EXAMPLE-SHUNT',name:'Beispielshunt'},{kind:'solar',mac:'EXAMPLE-SOLAR',name:'Beispielregler'}],found:[],readings:[{device:'EXAMPLE-SHUNT',kind:'shunt',t:now,soc:70,current:10,voltage:13.3,power:133,ttg:480},{device:'EXAMPLE-SOLAR',kind:'solar',t:now,solar:78,voltage:13.3,current:6,yieldWh:450,state:3,error:0,load:0}],stats:{count:0},solarDaily:[],settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10,weatherPlace:{name:'Beispielort',latitude:50,longitude:10}},weather:{timezone:'Europe/Berlin',updated:now,days:Array.from({length:14},(_,i)=>({day:new Date(now+i*86400000).toISOString().slice(0,10),sunrise:rise+i*86400000,sunset:rise+11*3600000+i*86400000,code:2,hours:5}))},equipment:{version:1,groups:[]}};
  const series=(from,to,end)=>({points:Array.from({length:5},(_,i)=>({t:from+(to-from)*i/4,v:end*i/4,min:end*i/4,max:end*i/4,n:1,gap:false,device:'EXAMPLE',origin:'live'})),summary:{last:end,min:Math.min(0,end),max:Math.max(0,end),coverage:.9}});
  window.WattPit={appVersion:()=>'1.3.12',snapshot:()=>JSON.stringify(window.fixture),syncState:()=>JSON.stringify({busy:false,cockpitNoticeAccepted:true,theme:window.fixture.settings.theme,palette:'classic'}),dashboard:()=>localStorage.getItem('charge-board')||'{}',saveDashboard:json=>{localStorage.setItem('charge-board',json);return '';},settings:json=>{Object.assign(window.fixture.settings,JSON.parse(json));return '';},history:()=>'{"points":[]}',dashboardHistory:(from,to,metric)=>{window.chargeQueries.push(metric);return JSON.stringify(series(from,to,metric==='solar'?78:13.3));},energyHistoryWindow:(from,to)=>JSON.stringify({solar:series(from,to,40),consumption:series(from,to,110),balance:series(from,to,-70)})};
 });
 const advance=async(seconds,patch={})=>{
  time+=seconds*1000;await page.clock.setFixedTime(new Date(time));
  await page.evaluate(({time,patch})=>{Object.assign(window.fixture.readings[0],patch,{t:time});window.fixture.readings[1].t=time;poll();},{time,patch});
 };
 const fresh=async patch=>{await page.evaluate(()=>{window.fixture.running=false;poll();window.fixture.running=true;});await advance(1,patch);};
 const resized=async viewport=>{
  await page.setViewportSize(viewport);
  await page.evaluate(()=>new Promise(resolve=>{const observer=new ResizeObserver(()=>{observer.disconnect();requestAnimationFrame(()=>requestAnimationFrame(resolve));});observer.observe(document.getElementById('dashboardBoard'));}));
 };
 const output=process.env.WATTPIT_SCREENSHOT_DIR;if(output)fs.mkdirSync(output,{recursive:true});
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));await advance(0);
 const standard=await page.evaluate(()=>({layouts:dashboard.config.tiles.map(t=>({metric:t.metric,layouts:t.layouts})),height:dashboard.config.historyHeight}));
 assert.equal(standard.layouts.length,11);assert.equal(standard.layouts.filter(t=>t.metric==='lan').length,1);assert.deepEqual(standard.height,{compact:465,wide:380});
 assert.deepEqual(standard.layouts.find(t=>t.metric==='chargeTime').layouts.compact,{x:0,y:5,w:4,h:2.5});
 // Existing charge tiles acquire the automatic behavior without being recreated.
 assert.equal(await page.locator('[data-tile="default-chargeTime"] .tile-label').textContent(),'Restladezeit');
 assert.equal(await page.locator('[data-tile="default-chargeTime"] .tile-number').textContent(),'≈ 3,0');
 await page.locator('[data-tile="default-chargeTime"]').click();await page.locator('#tileActionSettings').click();await page.locator('#removeTile').click();
 const before=await page.evaluate(()=>structuredClone(dashboard.config.tiles));
 await page.locator('#addTile').click();await page.locator('#tileSearch').fill('Restladezeit');await page.locator('[data-metric="chargeTime"]').click();
 const id=await page.evaluate(()=>dashboard.config.tiles.find(t=>t.metric==='chargeTime').id),tile=page.locator('[data-tile="'+id+'"]');
 assert.deepEqual(await page.evaluate(()=>dashboard.config.tiles.filter(t=>t.metric!=='chargeTime')),before);
 assert.equal(await tile.locator('.tile-number').textContent(),'≈ 3,0');assert.match(await tile.locator('.tile-hint').textContent(),/Bis 100 %/);
 for(let i=0;i<10;i++)await page.evaluate(()=>poll());assert.equal(await page.evaluate(()=>dashboard.chargeEstimator.samples.length),1);
 await page.evaluate(()=>{window.fixture.settings.capacity=200;poll();});assert.equal(await tile.locator('.tile-number').textContent(),'≈ 6,0');
 await page.evaluate(()=>{window.fixture.settings.capacity=100;window.fixture.readings[0].voltage=null;poll();});assert.equal(await tile.locator('.tile-number').textContent(),'≈ 3,0');
 await advance(1,{soc:null});assert.equal(await tile.locator('.tile-number').textContent(),'—');
 await advance(1,{soc:70,current:-2});assert.equal(await tile.locator('.tile-label').textContent(),'Restladezeit');assert.equal(await tile.locator('.tile-number').textContent(),'—');assert.equal(await tile.locator('.tile-hint').textContent(),'Trendwechsel wird geprüft');
 await fresh({soc:70,current:10,ttg:480});assert.equal(await tile.locator('.tile-number').textContent(),'≈ 3,0');
 time+=46000;await page.clock.setFixedTime(new Date(time));await page.evaluate(()=>poll());assert.equal(await tile.locator('.tile-number').textContent(),'—');assert.equal(await tile.locator('.tile-label').textContent(),'Akkuzeit');
 await advance(1,{soc:100,current:1});assert.equal(await tile.locator('.tile-number').textContent(),'0,0');assert.equal(await tile.locator('.tile-hint').textContent(),'100 % laut SmartShunt');
 await fresh({soc:70,current:0,ttg:480});assert.equal(await tile.locator('.tile-number').textContent(),'—');assert.equal(await tile.locator('.tile-hint').textContent(),'Zeit derzeit nicht bestimmbar');
 await fresh({soc:70,current:10,ttg:480});
 for(let s=0;s<110;s+=10)await advance(10,{current:-2});
 assert.equal(await tile.locator('.tile-label').textContent(),'Restlaufzeit');assert.equal(await tile.locator('.tile-number').textContent(),'≈ 8,0');
 if(output){await page.locator('#toast').evaluate(el=>el.hidden=true);await tile.screenshot({path:path.join(output,'battery-time-runtime.png')});}
 await advance(1,{soc:null,ttg:null});assert.equal(await tile.locator('.tile-number').textContent(),'—');assert.equal(await tile.locator('.tile-hint').textContent(),'Restlaufzeit vom SmartShunt fehlt');
 await advance(1,{ttg:240});assert.equal(await tile.locator('.tile-number').textContent(),'≈ 4,0','runtime needs no SOC or capacity estimate');
 await advance(1,{current:10,soc:70});assert.equal(await tile.locator('.tile-label').textContent(),'Restlaufzeit');assert.equal(await tile.locator('.tile-number').textContent(),'—');
 for(let s=0;s<100;s+=10)await advance(10,{current:10});
 assert.equal(await tile.locator('.tile-label').textContent(),'Restladezeit');assert.equal(await tile.locator('.tile-number').textContent(),'≈ 3,0');
 if(output)await tile.screenshot({path:path.join(output,'battery-time-charge.png')});
 await tile.click();await page.locator('#tileActionDetail').click();
 assert.equal(await page.locator('#tileDetailTitle').textContent(),'Restladezeit');assert.equal(await page.locator('#detailReading .detail-number').textContent(),'≈ 3,0');assert.equal(await page.locator('#detailPeriod').isVisible(),false);assert.equal(await page.locator('#detailCanvas').isVisible(),false);
 assert.match(await page.locator('#detailReading').textContent(),/Netto-Batteriestrom.*fünf Minuten/);
 await page.locator('#detailSettings>summary').click();assert.equal(await page.locator('#detailType option[value="chart"]').count(),0);assert.equal(await page.locator('#detailValue option').count(),1);
 await page.locator('#detailUnit').selectOption('min');assert.equal(await page.locator('#detailReading .detail-number').textContent(),'≈ 180,0');
 await page.locator('#detailType').selectOption('gauge');assert.equal(await page.locator('#detailReading .tile-number').textContent(),'≈ 180,0');
 for(let s=0;s<110;s+=10)await advance(10,{current:-2,ttg:480});
 await page.evaluate(()=>dashboard.detail.refresh(false));assert.equal(await page.locator('#tileDetailTitle').textContent(),'Restlaufzeit');assert.equal(await page.locator('#detailReading .tile-number').textContent(),'≈ 480,0');
 await page.locator('#detailClose').click();assert.equal(await tile.locator('.tile-number').textContent(),'≈ 8,0');assert.equal(await tile.locator('.tile-unit').textContent(),'h');
 assert.equal(await page.evaluate(()=>window.chargeQueries.includes(undefined)),false);
 await fresh({soc:70,current:10,ttg:480});
 for(const viewport of [{width:320,height:800},{width:390,height:844},{width:844,height:390}]){
  await resized(viewport);
  for(const theme of ['light','dark']){
   await page.evaluate(theme=>{window.fixture.settings.theme=theme;poll();},theme);await tile.scrollIntoViewIfNeeded();
   assert.equal(await tile.locator('.tile-hint').isVisible(),true);assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false);
   assert.ok(await tile.locator('.tile-number').evaluate(el=>el.scrollWidth<=el.clientWidth+1));
  }
 }
 await page.reload();await advance(1,{current:10,soc:70,ttg:480});assert.equal(await page.locator('[data-tile="'+id+'"] .tile-number').textContent(),'≈ 3,0');
 // Resetting the standard view applies the provided portrait arrangement, without importing personal settings.
 await page.evaluate(()=>dashboard.reset());await page.locator('#confirmDashboardReset').click();
 const restored=await page.evaluate(()=>({layouts:dashboard.config.tiles.map(t=>({metric:t.metric,layouts:t.layouts})),height:dashboard.config.historyHeight}));assert.deepEqual(restored,standard);
 for(const viewport of [{width:320,height:800},{width:390,height:844},{width:844,height:390}]){
  await resized(viewport);await advance(1,{current:10,soc:70});
  for(const theme of ['light','dark']){
   await page.evaluate(theme=>{window.fixture.settings.theme=theme;poll();},theme);
   assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false);
   for(const entry of standard.layouts){const number=page.locator('[data-tile="'+(entry.metric==='chargeTime'?'default-chargeTime':'default-'+entry.metric)+'"] .tile-number');if(await number.count())assert.ok(await number.evaluate(el=>el.scrollWidth<=el.clientWidth+1),'number clips: '+entry.metric+' at '+viewport.width);}
  }
 }
 await resized({width:390,height:844});await advance(1,{current:10,soc:70});await page.locator('#toast').evaluate(el=>el.hidden=true);
 if(output){
  await page.locator('#dashboardBoard').screenshot({path:path.join(output,'portrait-1.3.12-dark.png')});
  await page.evaluate(()=>{window.fixture.settings.theme='light';poll();});
  await page.locator('#dashboardBoard').screenshot({path:path.join(output,'portrait-1.3.12-light.png')});
 }
 assert.deepEqual(errors,[]);await browser.close();console.log('Automatic battery time: existing/new tiles, stable trend changes in both directions, source runtime/missing values, balanced current, units/detail, persistence and the approved portrait layout in both themes/orientations passed.');
})().catch(e=>{console.error(e);process.exit(1);});
