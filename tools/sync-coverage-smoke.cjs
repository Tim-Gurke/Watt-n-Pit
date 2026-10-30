const waitForPage=require('./browser-wait.cjs');
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
(async()=>{
 const fixture=JSON.parse(fs.readFileSync(process.env.WATTPIT_COVERAGE_FIXTURE,'utf8'));
 assert.equal(fixture.complete.flow.solar.summary.coverage,1);
 assert.equal(fixture.gap.flow.solar.summary.coverage,80/120);
 assert.equal(fixture.short.flow.solar.summary.coverage,1);assert.equal(fixture.short.flow.solar.summary.interpolatedMillis,20000);
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--disable-gpu']});
 const page=await browser.newPage({viewport:{width:390,height:900},timezoneId:'Europe/Berlin'}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));await page.clock.setFixedTime(new Date(fixture.to));
 await page.addInitScript(f=>{
  window.coverageFixture=f;window.activeGap=false;window.curveTraces=[];
  const width=Object.getOwnPropertyDescriptor(HTMLCanvasElement.prototype,'width');
  Object.defineProperty(HTMLCanvasElement.prototype,'width',{...width,set(value){window.curveTraces=window.curveTraces.filter(x=>x.canvas!==this.id);return width.set.call(this,value)}});
  const proto=CanvasRenderingContext2D.prototype,begin=proto.beginPath,move=proto.moveTo,line=proto.lineTo,stroke=proto.stroke;
  proto.beginPath=function(){this.coveragePath=[];return begin.apply(this,arguments)};
  proto.moveTo=function(){this.coveragePath?.push('M');return move.apply(this,arguments)};
  proto.lineTo=function(){this.coveragePath?.push('L');return line.apply(this,arguments)};
  proto.stroke=function(){if(['historyChart','detailCanvas'].includes(this.canvas.id)&&this.lineWidth>=2&&this.lineWidth<2.3)window.curveTraces.push({canvas:this.canvas.id,path:(this.coveragePath||[]).join('')});return stroke.apply(this,arguments)};
  const period={mode:'custom',from:f.from,to:f.to,amount:2,unit:'min',aggregate:'last'},layouts={compact:{x:0,y:0,w:12,h:5},wide:{x:0,y:0,w:6,h:5}};
  window.testBoard={version:1,energyFlowVersion:1,energyPeriod:period,tiles:[{id:'coverage-solar',metric:'solar',type:'chart',period,layouts},{id:'coverage-energy',metric:'solarEnergy',type:'chart',period,layouts:{compact:{x:0,y:5,w:12,h:5},wide:{x:6,y:0,w:6,h:5}}}]};
  window.mock={running:false,status:'Test',devices:[{mac:'A',kind:'solar',name:'Testregler'}],readings:[],found:[],stats:{count:20},historyRevision:0,settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10,miniRange:'today'},cockpitNoticeAccepted:true};
  const scenario=()=>f[window.activeShort?'short':window.activeGap?'gap':'complete'];
  window.WattPit={snapshot:()=>JSON.stringify(window.mock),dashboard:()=>JSON.stringify(window.testBoard),saveDashboard:()=>'',settings:()=>'',history:()=>JSON.stringify(scenario().power),dashboardHistory:(from,to,metric)=>JSON.stringify(metric.toLowerCase().includes('energy')?scenario().flow.solar:scenario().power),energyHistory:()=>JSON.stringify({accountingVersion:2,...scenario().flow}),energyHistoryWindow:()=>JSON.stringify({accountingVersion:2,...scenario().flow})};
 },fixture);
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 const moves=async()=>((await page.locator('[data-tile="coverage-solar"] .tile-sparkline path').last().getAttribute('d')).match(/M/g)||[]).length;
 const trace=async canvas=>page.evaluate(canvas=>window.curveTraces.filter(x=>x.canvas===canvas).map(x=>x.path).join(''),canvas);
 for(const gap of [false,true]){
  await page.evaluate(gap=>{window.activeGap=gap;window.mock.historyRevision++;poll();},gap);
  await waitForPage(page,expected=>((document.querySelector('[data-tile="coverage-solar"] .tile-sparkline path:last-child')?.getAttribute('d')||'').match(/M/g)||[]).length===expected,gap?2:1);
  assert.equal(await moves(),gap?2:1,'overview lost or invented a source-boundary gap');
  assert.match(await page.locator('[data-tile="coverage-energy"] .tile-hint').textContent(),gap?/66 %/:/100 %/);
  await page.locator('[data-tile="coverage-solar"]').click();await page.locator('#tileActionDetail').click();
  assert.equal(((await trace('detailCanvas')).match(/M/g)||[]).length,gap?2:1,'detail disagrees with native coverage');
  await page.locator('#detailClose').click();await page.locator('[data-page="history"]').click();await page.locator('#metricButtons [data-metric="power"]').click();
  assert.equal(((await trace('historyChart')).match(/M/g)||[]).length,gap?2:1,'history disagrees with native coverage');
  await page.locator('#metricButtons [data-metric="energy"]').click();
  const energyMoves=((await trace('historyChart')).match(/M/g)||[]).length;
  assert.equal(energyMoves,gap?6:3,'energy plot disagrees with its Java coverage');
  await page.locator('[data-page="overview"]').click();
  if(process.env.WATTPIT_SCREENSHOT_DIR){fs.mkdirSync(process.env.WATTPIT_SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:path.join(process.env.WATTPIT_SCREENSHOT_DIR,gap?'sync-real-gap.png':'sync-complete.png'),fullPage:true});}
 }
 await page.evaluate(()=>{window.activeGap=false;window.activeShort=true;window.mock.historyRevision++;poll();});
 await waitForPage(page,()=>document.querySelector('[data-tile="coverage-energy"] .tile-hint')?.textContent.includes('kurz interpoliert'));
 assert.equal(await moves(),2,'raw measurements retain the actual reception break');
 assert.match(await page.locator('[data-tile="coverage-energy"] .tile-hint').textContent(),/83 % gemessen.*kurz interpoliert/);
 const energyPath=await page.locator('[data-tile="coverage-energy"] .tile-sparkline path').last().getAttribute('d');assert.equal((energyPath.match(/M/g)||[]).length,1,'bounded interpolation connects energy only and is labelled');
 assert.deepEqual(errors,[]);await browser.close();console.log('Actual Java coverage agrees in overview, detail, history and energy: source boundaries connected, real 40-second gap retained, short energy interpolation labelled.');
})().catch(e=>{console.error(e);process.exit(1)});
