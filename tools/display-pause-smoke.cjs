const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path');
const preset=require('../app/src/main/assets/dashboard-preset.json');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox'],executablePath:process.env.WATTPIT_CHROMIUM});
 try{
  const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(({preset})=>{
   window.calls={snapshot:0,history:0,planning:0};
   const points=(from,to,value)=>({points:[{t:from,last:from,v:0,gap:true},{t:to,last:to,v:value,gap:false}],summary:{last:value,min:0,max:value,n:2,coverage:1,from,to}});
   window.WattPit={appVersion:()=>'1.7.0',isLanViewer:()=>true,lanState:()=>' {"role":"viewer","id":"plant","connected":true}',dashboard:()=>JSON.stringify(preset),saveDashboard:()=>'',settings:()=>'',solarSetup:()=>'{}',rollerSnapshot:()=>'{}',
    syncState:()=>' {"busy":false,"runId":0,"cockpitNoticeAccepted":true,"theme":"dark","palette":"classic"}',
    snapshot:()=>{window.calls.snapshot++;return JSON.stringify({lan:{id:'plant',connected:true},running:true,historyRevision:1,devices:[{mac:'solar',kind:'solar',name:'Solar'},{mac:'shunt',kind:'shunt',name:'Shunt'}],readings:[{kind:'solar',t:Date.now(),solar:140},{kind:'shunt',t:Date.now(),soc:72,voltage:13.2,power:40}],found:[],stats:{count:100},settings:{theme:'dark',capacity:100,sampleSeconds:15}});},
    requestHistory:q=>{window.calls.history++;const r=JSON.parse(q),[from,to]=r.args;const data=r.method.startsWith('energy')?{accountingVersion:2,solar:points(from,to,140),balance:points(from,to,40),consumption:points(from,to,100),mobile:points(from,to,0)}:r.method==='dailyEnergy'?{days:[]}:points(from,to,140);setTimeout(()=>window.onHistoryResult({id:r.id,data}),10);},
    requestSolarData:()=>{window.calls.planning++;setTimeout(()=>window.onSolarPlanData({analysis:{plant:'plant'},source:'test-source',observations:[],forecasts:[],updated:1}),10);}
   };
  },{preset});
  await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));await page.waitForTimeout(400);
  const config=await page.evaluate(()=>JSON.stringify(dashboard.config));await page.evaluate(()=>window.onDisplayActive({active:false}));await page.waitForTimeout(100);
  const before=await page.evaluate(()=>({...window.calls}));await page.waitForTimeout(2300);assert.deepEqual(await page.evaluate(()=>window.calls),before,'hidden display must not poll snapshots or submit calculations');
  await page.evaluate(()=>window.onSolarPlanData({analysis:{plant:'plant'},source:'test-source',observations:[],forecasts:[],updated:2}));assert.deepEqual(await page.evaluate(()=>window.calls),before,'completed background data must not render or start more work');
  await page.evaluate(()=>window.onDisplayActive({active:true}));await page.waitForTimeout(150);assert.ok((await page.evaluate(()=>window.calls.snapshot))>before.snapshot);assert.equal(await page.evaluate(()=>JSON.stringify(dashboard.config)),config);
  await page.evaluate(()=>window.onSolarPlanData({analysis:{plant:'another-plant'},source:'WRONG',observations:[],forecasts:[],updated:3}));assert.equal(await page.evaluate(()=>solar.knowledge.source),'test-source','a late result cannot replace another plant');
  assert.deepEqual(errors,[]);console.log('PASS hidden display suspends polling, calculation requests and rendering; resume retains local layout and rejects another plant.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
