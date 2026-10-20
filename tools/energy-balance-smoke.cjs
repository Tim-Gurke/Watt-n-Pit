const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');

(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--disable-gpu']});
 const page=await browser.newPage({viewport:{width:390,height:1100},timezoneId:'Europe/Berlin'}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));await page.clock.setFixedTime(new Date('2026-10-17T12:42:00Z'));
 await page.addInitScript(()=>{
  window.missingBattery=false;window.missingSolar=false;window.zeroEnergy=false;window.energyCalls=[];
  window.WattPit={snapshot:()=>JSON.stringify({running:false,status:'Pausiert',devices:[],readings:[],found:[],stats:{count:20},settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10}}),history:()=>'{"points":[]}',settings:()=>'',energyHistory:(from,to,source)=>{
   window.energyCalls.push({from,to,source});if(source==='csv')return '{}';to=Math.min(to,Date.now());
   const data=(kind,total,fraction)=>{const points=[[from,0],[from+(to-from)*fraction,total/2],[to,total]].map(([t,v])=>({t,last:t,v,min:v,max:v,n:1,gap:false,kind,origin:'calculated',device:'calculated-'+kind}));return {points,summary:{from,to,last:total,coverage:1}};};
   return JSON.stringify({solar:window.missingBattery||window.missingSolar?{points:[],summary:{from,to,last:null,coverage:0}}:data('solar',window.zeroEnergy?0:40,.5),balance:window.missingBattery||window.missingBattery||window.missingSolar?{points:[],summary:{from,to,last:null,coverage:0}}:data('balance',window.zeroEnergy?0:-30,.4),consumption:window.missingBattery||window.missingBattery||window.missingSolar?{points:[],summary:{from,to,last:null,coverage:0}}:data('consumption',window.zeroEnergy?0:70,.3)});
  }};
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 await page.locator('[data-page="history"]').click();await page.locator('[data-metric="energy"]').click();
 assert.equal(await page.locator('#energyOptions').isVisible(),true);assert.equal(await page.locator('#energyBalance').isChecked(),true);
 assert.equal(await page.locator('#chartLegend span').count(),3);assert.match(await page.locator('#energyNote').textContent(),/Bilanz: -30,00 Wh/);
 assert.equal(await page.evaluate(()=>points.filter(p=>p.kind==='balance').at(-1).v),-30);
 const target=await page.evaluate(()=>({x:scale.left+(Math.min(viewTo,Date.now())-viewFrom)/(viewTo-viewFrom)*scale.plotW,y:scale.top+scale.plotH/2}));
 const canvas=await page.locator('#historyChart').boundingBox();await page.mouse.click(canvas.x+target.x,canvas.y+target.y);
 assert.equal(await page.locator('#chartTooltip').isVisible(),true);assert.match(await page.locator('#chartTooltip').textContent(),/Bilanz: -30,00 Wh/);
 assert.match(await page.locator('#chartTooltip').textContent(),/Solarertrag: 40,00 Wh/);assert.match(await page.locator('#chartTooltip').textContent(),/Verbrauch: 70,00 Wh/);
 const calls=await page.evaluate(()=>window.energyCalls.length);await page.locator('#energyBalance').uncheck();assert.equal(await page.locator('#chartLegend span').count(),2);assert.equal(await page.evaluate(()=>points.some(p=>p.kind==='balance')),false);assert.equal(await page.evaluate(()=>window.energyCalls.length),calls);
 assert.equal(await page.locator('#chartTooltip').isVisible(),false);
 await page.locator('[data-metric="power"]').click();assert.equal(await page.locator('#energyOptions').isVisible(),false);
 await page.locator('[data-metric="energy"]').click();assert.equal(await page.locator('#energyBalance').isChecked(),false);await page.locator('#energyBalance').check();assert.equal(await page.locator('#chartLegend span').count(),3);
 await page.locator('#historySource').selectOption('csv');assert.equal(await page.locator('#chartLegend span').count(),0);assert.match(await page.locator('#energyNote').textContent(),/Bilanz: — Wh/);
 await page.locator('#historySource').selectOption('all');await page.evaluate(()=>{window.missingBattery=true;loadHistory();});assert.equal(await page.locator('#chartLegend span').count(),0);assert.match(await page.locator('#energyNote').textContent(),/Bilanz: — Wh/);
 await page.evaluate(()=>{window.missingBattery=false;window.missingSolar=true;loadHistory();});assert.equal(await page.locator('#chartLegend span').count(),0);assert.match(await page.locator('#energyNote').textContent(),/Bilanz: — Wh/);
 await page.evaluate(()=>{window.missingSolar=false;window.zeroEnergy=true;loadHistory();});assert.equal(await page.locator('#chartLegend span').count(),3);assert.match(await page.locator('#energyNote').textContent(),/Bilanz: 0,00 Wh/);assert.equal(await page.evaluate(()=>Object.is(energyData.balance.summary.last,-0)),false);
 for(const width of [390,1000]){await page.setViewportSize({width,height:1100});for(const theme of ['light','dark']){if(await page.locator('html').getAttribute('data-theme')!==theme)await page.locator('#themeToggle').click();assert.equal(await page.locator('#energyBalance').isVisible(),true);assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false);}}
 assert.deepEqual(errors,[]);await browser.close();console.log('Energy flow from common Solar/Shunt intervals, three-series tooltip, toggle without requery, source filters, missing inputs, measured zero, light/dark and narrow/wide views passed.');
})().catch(e=>{console.error(e);process.exit(1);});
