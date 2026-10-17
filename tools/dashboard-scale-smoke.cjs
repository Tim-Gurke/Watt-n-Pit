const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--single-process','--no-zygote','--disable-gpu']});
 const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  const layout=(x,y)=>({compact:{x,y,w:6,h:6},wide:{x,y,w:6,h:6}});
  window.testConfig=JSON.parse(localStorage.getItem('mock-native-dashboard')||'null')||{version:1,tiles:[
   {id:'test-solar',metric:'solar',type:'chart',min:0,max:200,period:{mode:'last',amount:2,unit:'h',aggregate:'avg'},layouts:layout(0,0)},
   {id:'test-voltage',metric:'voltage',type:'chart',min:10,max:15,period:{mode:'last',amount:2,unit:'h',aggregate:'avg'},layouts:layout(6,0)},
   {id:'test-current',metric:'current',type:'chart',min:-100,max:100,period:{mode:'last',amount:2,unit:'h',aggregate:'avg'},layouts:layout(0,6)}]};
  const now=Date.now(),point=(v,min=v,max=v,i=0)=>({t:now-6600000+i*1200000,last:now-6600000+i*1200000,v,min,max,n:1,device:'TEST',kind:'solar',origin:'archive',period:1200});
  window.series={solar:Array.from({length:6},(_,i)=>point(3,0,6,i)),batteryVoltage:Array.from({length:6},(_,i)=>point(13.1+i*.01,13.1+i*.01,13.1+i*.01,i)),batteryCurrent:Array.from({length:6},(_,i)=>point(-.2+i*.06,-.2+i*.06,-.2+i*.06,i))};
  window.mock={running:false,status:'Pausiert',devices:[],found:[],readings:[],stats:{count:6},historyRevision:0,settings:{theme:'dark',capacity:100,sampleSeconds:10}};
  window.WattPit={snapshot:()=>JSON.stringify(window.mock),dashboard:()=>JSON.stringify(window.testConfig),saveDashboard:json=>{window.testConfig=JSON.parse(json);localStorage.setItem('mock-native-dashboard',json);return '';},history:()=>'{"points":[]}',dashboardHistory:(from,to,metric)=>{const points=window.series[metric]||[],summary=points.length?{avg:points.reduce((n,p)=>n+p.v,0)/points.length,min:Math.min(...points.map(p=>p.min)),max:Math.max(...points.map(p=>p.max)),last:points.at(-1).v,n:points.length}:{};return JSON.stringify({points,summary});}};
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 const tile=id=>page.locator(`[data-tile="test-${id}"]`),svg=id=>tile(id).locator('.tile-sparkline');
 const layouts=await page.evaluate(()=>window.testConfig.tiles.map(t=>t.layouts));
 assert.equal(await svg('solar').getAttribute('data-scale-max'),'200');
 for(const id of ['solar','voltage','current']){
  await tile(id).click();await page.locator('summary').filter({hasText:/^Skala$/}).click();
  await page.locator('#tileScaleMode').selectOption('auto');
  assert.equal(await page.locator('#tileMin').isDisabled(),true);assert.equal(await page.locator('#tileMax').isDisabled(),true);
  await page.locator('#saveTile').click();
 }
 assert.equal(await svg('solar').getAttribute('data-scale-max'),'6.6');
 const curve=await svg('solar').locator('path').nth(1).getAttribute('d');
 assert.ok(Math.min(...[...curve.matchAll(/,([\d.]+)/g)].map(m=>Number(m[1])))<40,'few watts remain stuck at the baseline');
 assert.ok(Number(await svg('voltage').getAttribute('data-scale-min'))>13);assert.ok(Number(await svg('voltage').getAttribute('data-scale-max'))<13.2);
 assert.ok(Number(await svg('current').getAttribute('data-scale-min'))<-.2);assert.ok(Number(await svg('current').getAttribute('data-scale-max'))>.1);
 assert.equal(await svg('current').locator('.tile-zero-line').count(),1);assert.equal(await svg('solar').locator('.tile-zero-line').count(),0);
 assert.match(await tile('solar').locator('.tile-chart-range').textContent(),/Auto.*6,6 W/);
 assert.equal(await page.evaluate(()=>window.testConfig.tiles[0].max),200,'auto mode overwrites the manual scale');
 if(process.env.WATTPIT_SCREENSHOT_DIR){fs.mkdirSync(process.env.WATTPIT_SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:path.join(process.env.WATTPIT_SCREENSHOT_DIR,'automatic-scales-dark.png'),fullPage:true});}
 await page.evaluate(()=>{window.series.solar[1].max=18;window.mock.historyRevision++;poll();});
 assert.equal(await svg('solar').getAttribute('data-scale-max'),'20','a later peak does not expand the axis');
 await page.reload();assert.equal(await svg('solar').getAttribute('data-scale-max'),'6.6','auto mode was lost after restart');
 assert.deepEqual(await page.evaluate(()=>window.testConfig.tiles.map(t=>t.layouts)),layouts);
 await tile('solar').click();await page.locator('#tileUnit').selectOption('kW');await page.locator('#saveTile').click();
 assert.equal(await svg('solar').getAttribute('data-scale-max'),'0.0066');assert.match(await tile('solar').locator('.tile-chart-range').textContent(),/0,0066 kW/);
 await tile('solar').click();await page.locator('summary').filter({hasText:/^Skala$/}).click();await page.locator('#tileScaleMode').selectOption('fixed');
 assert.equal(await page.locator('#tileMax').isDisabled(),false);assert.equal(await page.locator('#tileMax').inputValue(),'0.2');await page.locator('#saveTile').click();
 assert.equal(await svg('solar').getAttribute('data-scale-max'),'0.2');assert.doesNotMatch(await tile('solar').locator('.tile-chart-range').textContent(),/Auto/);
 await page.evaluate(()=>{window.series.solar=[];window.mock.historyRevision++;poll();});assert.equal(await tile('solar').locator('.tile-chart-empty').textContent(),'Keine gespeicherten Werte');
 assert.equal(errors.length,0,errors.join('\n'));
 await browser.close();console.log('Automatic solar/voltage/current scales, visible low power, original peaks, growth, units, persistence, manual restoration and empty history passed.');
})().catch(e=>{console.error(e);process.exit(1);});
