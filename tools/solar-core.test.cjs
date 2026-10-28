const {test}=require('node:test'),assert=require('node:assert/strict');
const C=require('../app/src/main/assets/solar-core.js'),E=require('../app/src/main/assets/equipment.js');
const H=C.HOUR,D=C.DAY,now=Date.parse('2026-10-23T12:00:00Z');
test('learning coverage reports actual hours per daypart instead of equating partial days with full days',()=>{
 const p={usageMode:'auto'},start=Date.parse('2026-10-22T00:00:00Z');
 const rows=[{day:'2026-10-22',slot:0,learningVersion:2,usage:{wh:20,duration:2*H,estimatedDuration:0,latest:start+2*H}},{day:'2026-10-22',slot:2,learningVersion:2,usage:{wh:30,duration:H,estimatedDuration:H,latest:start+20*H}}];
 const u=C.consumption(p,rows,'2026-10-23',now);
 assert.equal(u.days,1);assert.equal(u.usedHours,3);assert.equal(u.usedEstimatedHours,1);
 assert.deepEqual(u.slotStats.map(s=>[s.hours,s.estimatedHours,s.days,s.extrapolated]),[[2,0,1,false],[0,0,0,true],[1,1,1,false]]);
});
test('a rejected consumption outlier is not counted among evaluated daypart hours',()=>{
 const rows=[];for(let i=1;i<=7;i++){const day='2026-10-'+String(i).padStart(2,'0'),start=Date.parse(day+'T00:00:00Z');rows.push({day,slot:0,learningVersion:2,usage:{wh:i===7?1000:10,duration:H,estimatedDuration:0,latest:start+H}});}
 const u=C.consumption({usageMode:'auto'},rows,'2026-10-23',now);assert.equal(u.slotStats[0].hours,6);assert.equal(u.slotStats[0].days,6);assert.equal(u.usedHours,6);
});
test('many measured nights cannot promote one estimated midday to a settled seasonal daypart',()=>{
 const rows=[];
 for(let i=15;i<=21;i++)for(const slot of [0,2]){const day=`2026-10-${i}`,start=Date.parse(day+'T00:00:00Z'),hours=slot===0?8:6;rows.push({day,slot,learningVersion:2,usage:{wh:hours*10,duration:hours*H,estimatedDuration:0,estimatedWh:0,latest:start+(slot===0?hours:24)*H}});}
 rows.push({day:'2026-10-22',slot:1,learningVersion:2,usage:{wh:60,duration:3*H,estimatedDuration:3*H,estimatedWh:60,latest:Date.parse('2026-10-22T14:00:00Z')}});
 const u=C.consumption({usageMode:'auto'},rows,'2026-10-23',now);
 assert.equal(u.days,8);assert.equal(u.extrapolated,false);assert.equal(u.provisional,true);
 assert.equal(u.slotStats[0].provisional,false);assert.equal(u.slotStats[2].provisional,false);
 assert.equal(u.slotStats[1].days,1);assert.equal(u.slotStats[1].provisional,true);assert.equal(u.slotStats[1].uncertainty,.8);
 assert.equal(u.parts[1],null);assert.equal(u.wh,null);assert.deepEqual(u.pendingSlots,[1]);
 assert.ok(Math.abs(u.slotStats[1].candidateWh-100)<1e-8,'the original midday estimate stays visible without an unsupported night-based replacement');
});
test('measured and estimated Wh in one daypart get separate weights without changing their raw totals',()=>{
 const usage={wh:100,measuredWh:20,estimatedWh:80,duration:4*H,estimatedDuration:2*H,latest:now-H};
 const u=C.consumption({usageMode:'auto'},[{day:'2026-10-23',slot:1,learningVersion:2,usage}],'2026-10-23',now);
 assert.ok(Math.abs(u.slotStats[1].candidateWh-(20+.4*80)/(2+.4*2)*5)<1e-8);
 assert.equal(u.slotStats[1].measuredHours,2);assert.equal(u.slotStats[1].estimatedHours,2);
 assert.equal(usage.wh,100);assert.equal(usage.estimatedWh,80);
});
test('multiple fragments from one date do not count as several comparable days',()=>{
 const rows=Array.from({length:8},(_,i)=>({day:'2026-10-22',slot:1,learningVersion:2,usage:{wh:10,duration:H,estimatedDuration:0,latest:now-(i+1)*H}}));
 const s=C.consumption({usageMode:'auto'},rows,'2026-10-23',now).slotStats[1];assert.equal(s.days,1);assert.equal(s.provisional,true);assert.equal(s.uncertainty,.8);
});
test('three sufficiently observed estimated middays support a forecast without shrinking a repeated real load to the night rate',()=>{
 const rows=Array.from({length:3},(_,i)=>{const day=`2026-10-${20+i}`;return {day,slot:1,learningVersion:2,usage:{wh:400,duration:5*H,estimatedDuration:5*H,estimatedWh:400,latest:Date.parse(day+'T16:00:00Z')}};});
 const result=C.consumption({usageMode:'auto'},rows,'2026-10-23',now);assert.deepEqual(result.pendingSlots,[]);assert.equal(result.parts[1],400);assert.equal(result.slotStats[1].days,3);assert.ok(result.slotStats[1].uncertainty>=.6);
});
test('confirmed historical estimates need more effective time and keep their uncertainty after several days',()=>{
 const rows=Array.from({length:6},(_,i)=>{const day=`2026-10-${15+i}`;return {day,slot:1,learningVersion:2,usage:{wh:50,duration:5*H,estimatedDuration:5*H,estimatedWh:50,reconstructedDuration:5*H,reconstructedWh:50,latest:Date.parse(day+'T16:00:00Z')}};});
 const weak=C.consumption({usageMode:'auto'},rows.slice(0,3),'2026-10-23',now);assert.equal(weak.parts[1],null);assert.equal(weak.slotStats[1].reconstructedHours,15);
 const enough=C.consumption({usageMode:'auto'},rows,'2026-10-23',now);assert.ok(Math.abs(enough.parts[1]-50)<1e-8);assert.ok(enough.slotStats[1].uncertainty>=.8);assert.equal(enough.provisional,true);
});
test('the diagnostic pattern uses historical middays and does not let one high partial day dominate them',()=>{
 const rows=[];for(let i=15;i<=20;i++){const day=`2026-10-${i}`;rows.push({day,slot:1,learningVersion:2,usage:{wh:50,duration:5*H,estimatedDuration:5*H,estimatedWh:50,reconstructedDuration:5*H,reconstructedWh:50,latest:Date.parse(day+'T16:00:00Z')}});}
 rows.push({day:'2026-10-22',slot:1,learningVersion:2,usage:{wh:207.3943,duration:2.5784686*H,estimatedDuration:2.5784686*H,estimatedWh:207.3943,latest:now-H}});
 const result=C.consumption({usageMode:'auto'},rows,'2026-10-23',now);assert.ok(Math.abs(result.parts[1]-50)<1e-8);assert.equal(result.slotStats[1].days,6);assert.ok(result.slotStats[1].rejected.outlier>2.5);
});
test('exclusion reasons remain available even if no learning sample survives',()=>{
 const from=now-D,rows=[{day:'2026-10-22',slot:1,from,to:from+5*H,learningVersion:2,usage:{wh:0,duration:0,estimatedDuration:0,latest:0,rejected:{unknownSetup:5*H}}}];
 const result=C.consumption({usageMode:'auto'},rows,'2026-10-23',now);assert.equal(result.wh,null);assert.equal(result.slotStats[1].rejected.unknownSetup,5);assert.equal(result.usedHours,0);
});
test('repeated new habits can change the forecast even when the old seasonal profile calls them outliers',()=>{
 const rows=[];for(const age of [60,59,58,57,56,55,54,3,2,1,0]){const at=now-age*D,day=new Date(at).toISOString().slice(0,10);rows.push({day,slot:1,learningVersion:2,usage:{wh:age>14?50:400,duration:5*H,estimatedDuration:0,estimatedWh:0,latest:at}});}
 const u=C.consumption({usageMode:'auto'},rows,'2026-10-23',now);assert.ok(u.parts[1]>250&&u.parts[1]<400);assert.equal(u.slotStats[1].recentDays,4);assert.ok(u.slotStats[1].recentInfluence>.6);assert.equal(u.slotStats[1].rejected.outlier||0,0);assert.ok(u.slotStats[1].uncertainty>.4);
});
test('one exceptional recent day cannot replace the established consumption profile',()=>{
 const rows=[];for(const age of [26,25,24,23,22,21,20,0]){const at=now-age*D,day=new Date(at).toISOString().slice(0,10);rows.push({day,slot:1,learningVersion:2,usage:{wh:age?50:400,duration:5*H,estimatedDuration:0,estimatedWh:0,latest:at}});}
 const u=C.consumption({usageMode:'auto'},rows,'2026-10-23',now);assert.ok(Math.abs(u.parts[1]-50)<1e-8);assert.equal(u.slotStats[1].recentInfluence,0);assert.equal(u.slotStats[1].rejected.outlier,5);
});
test('two tiny fragments on other dates do not qualify one exceptional full day as a new habit',()=>{
 const rows=[];for(const age of [26,25,24,23,22,21,20,2,1,0]){const at=now-age*D,day=new Date(at).toISOString().slice(0,10),hours=age===1||age===2?.25:5;rows.push({day,slot:1,learningVersion:2,usage:{wh:(age===0?80:10)*hours,duration:hours*H,estimatedDuration:0,estimatedWh:0,latest:at}});}
 const u=C.consumption({usageMode:'auto'},rows,'2026-10-23',now);assert.equal(u.slotStats[1].recentInfluence,0);assert.ok(Math.abs(u.parts[1]-50)<1e-8);
});
test('the previous matching season remains available within 400 days while older learning evidence expires',()=>{
 const run=day=>C.consumption({usageMode:'auto'},observations(day,600),'2026-10-23',now);
 assert.equal(run('2025-10-24').wh,600);assert.equal(run('2025-06-01').wh,null);
});
const equipment=(extra=[])=>E.normalize({version:1,groups:[{id:'roof',label:'Dach',count:2,watts:100,tilt:30,placement:'fixed',measurement:'solar',controller:'MPPT'},...extra]});
const plan=eq=>C.normalize({groups:{roof:{azimuth:180,shade:[0,0,0]}}},eq);
function weather(from=Date.parse('2026-10-23T00:00:00Z'),length=14,hoursPerDay=24){
 const days=[],windows=[],hours=[],values=[];
 for(let i=0;i<length;i++){const start=from+i*D,day=new Date(start).toISOString().slice(0,10);days.push({day,code:3,hours:9,sunrise:start+7*H,sunset:start+18*H,radiation:1500});for(let slot=0;slot<3;slot++)windows.push({day,slot,from:start+[0,11,16][slot]*H,to:start+[11,16,hoursPerDay][slot]*H});for(let j=1;j<=hoursPerDay;j++){const t=start+j*H;hours.push({t});values.push({t,v:500});}}
 return {version:4,place:{name:'Beispiel',latitude:52.52,longitude:13.41},updated:now,timezone:'UTC',days,windows,hours,solar:[{geometry:'30:0',values}]};
}
function observations(day,total,overrides={}){const start=Date.parse(day+'T00:00:00Z');return [0,1,2].map(slot=>({day,slot,from:start+[0,11,16][slot]*H,to:start+[11,16,24][slot]*H,consumption:total*[11,5,8][slot]/24,solar:100,coverage:1,solarCoverage:1,bulkCoverage:1,mobileFree:true,key:'k-0123456789abcdef',...overrides}));}
test('16 bearings wrap correctly and an outside sighting points to the opposite panel face',()=>{assert.equal(C.DIRECTIONS.length,16);assert.equal(C.direction(112.5),'Ost/Südost');assert.equal(C.direction(-22.5),'Nord/Nordwest');assert.equal(C.panelHeading(350,true),170);assert.equal(C.panelHeading(350,false),350);assert.equal(C.panelHeading(null,true),null);});
test('provider angles use south zero; horizontal panels need no heading and legacy tilt stays unknown',()=>{for(const [heading,provider] of [[0,-180],[90,-90],[180,0],[270,90]])assert.equal(C.geometry({tilt:35},{azimuth:heading}),`35:${provider}`);assert.equal(C.geometry({tilt:0},{}),'0:0');assert.equal(C.geometry({},{azimuth:180}),null);assert.equal(C.geometry({tilt:90},{}),null);assert.equal(E.normalize({version:1,groups:[{...equipment().groups[0],tilt:undefined}]}).groups[0].tilt,null);});
test('unconfigured directions, shade and mobile activation are never silently invented',()=>{const eq=equipment([{...equipment().groups[0],id:'case',placement:'mobile',defaultActive:true}]),p=C.normalize({},eq);assert.equal(p.groups.roof.active,true);assert.equal(p.groups.case.active,false);assert.equal(p.groups.roof.azimuth,null);assert.deepEqual(p.groups.roof.shade,[null,null,null]);for(const bad of [{groups:{roof:{azimuth:360}}},{groups:{roof:{shade:[0,1]}}},{manual:[1,-1,null,null]}])assert.throws(()=>C.normalize(bad,eq));});
test('a new stand resets headings and shade, switches the mobile circuit off and preserves seasonal preferences',()=>{const eq=equipment([{...equipment().groups[0],id:'case',placement:'mobile'}]),p=plan(eq);p.groups.case.active=true;p.usageMode='manual';p.manual=[100,200,300,400];const next=C.newSite(p,eq);assert.equal(next.groups.case.active,false);assert.equal(next.groups.roof.azimuth,null);assert.deepEqual(next.groups.roof.shade,[null,null,null]);assert.deepEqual(next.manual,p.manual);assert.equal(p.groups.roof.azimuth,180);});
test('setup keys are stable across group order and change for source, site, shadow, heading or mobile activation',()=>{const eq=equipment([{...equipment().groups[0],id:'case',placement:'mobile'}]),p=plan(eq),place=weather().place,key=C.setupKey(eq,p,place,'source');assert.match(key,/^k-[a-f0-9]{16}$/);assert.equal(C.setupKey({...eq,groups:[...eq.groups].reverse()},p,place,'source'),key);for(const change of [(e,p,l)=>p.groups.case.active=true,(e,p,l)=>p.groups.roof.shade[1]=2,(e,p,l)=>p.groups.roof.azimuth=90,(e,p,l)=>l.longitude+=1,(e)=>e.groups[0].tilt=40]){const e=structuredClone(eq),q=structuredClone(p),l={...place};change(e,q,l);assert.notEqual(C.setupKey(e,q,l,'source'),key);}assert.notEqual(C.setupKey(eq,p,place,'new-source'),key);assert.equal(C.setupKey(eq,p,{}),'');});
test('sunshine relative to daylight chooses a representative solar symbol instead of the worst daily weather code',()=>{const d=weather().days[0];assert.equal(C.sunshineCode(d),0);assert.equal(C.sunshineCode({...d,hours:5}),2);assert.equal(C.sunshineCode({...d,hours:1}),3);assert.equal(C.sunshineCode({code:65}),65);});
test('hourly preceding-hour irradiance converts W/m² to Wh once and includes the closing midnight',()=>{const eq=equipment(),p=plan(eq),days=C.forecast(weather(),eq,p,{},now);assert.equal(days.length,14);assert.equal(days[0].fixed,1920);assert.deepEqual(days[0].parts.map(x=>x.fixed),[880,400,640]);assert.equal(days[13].total,1920);assert.equal(days[0].mobile,0);assert.equal(days[0].usage.wh,null);assert.equal(days[0].balance,null);});
test('missing readings and absent geometry suppress totals while a measured zero and deliberately inactive installation remain zero',()=>{const eq=equipment(),p=plan(eq);for(const change of [w=>w.solar[0].values[0].v=null,w=>w.solar[0].values.pop(),w=>w.solar=[]]){const w=weather();change(w);assert.equal(C.forecast(w,eq,p,{},now).some(x=>!x.complete),true);}const w=weather();w.solar[0].values.forEach(x=>x.v=0);assert.equal(C.forecast(w,eq,p,{},now)[0].total,0);p.groups.roof.active=false;assert.equal(C.forecast(weather(),eq,p,{},now)[0].total,0);assert.equal(C.forecast(weather(),{groups:[]},C.normalize({},{}),{},now)[0].total,null);});
test('per-part shade reduces only its own interval, and unknown shade increases the honest range',()=>{const eq=equipment(),p=plan(eq);p.groups.roof.shade=[0,1,3];let d=C.forecast(weather(),eq,p,{},now)[0];assert.deepEqual(d.parts.map(x=>x.fixed),[880,320,160]);const known=d.high-d.low;p.groups.roof.shade=[null,null,null];d=C.forecast(weather(),eq,p,{},now)[0];assert.ok(d.high-d.low>known);assert.equal(d.unknownShade,true);});
test('unmeasured mobile generation is one demand relief and never changes the measured consumption baseline',()=>{const eq=equipment([{...equipment().groups[0],id:'case',placement:'mobile',measurement:'shunt',watts:50,count:1}]),p=plan(eq);p.groups.case={active:true,azimuth:180,shade:[0,0,0]};const obs=observations('2026-10-22',3000),d=C.forecast(weather(),eq,p,{observations:obs},now)[0];assert.equal(d.fixed,1920);assert.equal(d.mobile,480);assert.equal(d.usage.wh,3000);assert.equal(d.balance,-600);assert.equal(C.trainingRows(weather(),eq,p,[d],'source').length,1);p.groups.case.active=false;assert.equal(C.forecast(weather(),eq,p,{observations:obs},now)[0].balance,-1080);});
test('Berlin autumn 25-hour day and spring 23-hour day count every actual interval exactly once',()=>{for(const [start,end,date] of [['2026-10-24T22:00:00Z','2026-10-25T23:00:00Z','2026-10-25'],['2026-03-28T23:00:00Z','2026-03-29T22:00:00Z','2026-03-29']]){const from=Date.parse(start),to=Date.parse(end),w=weather(from,1);w.timezone='Europe/Berlin';w.days[0].day=date;w.hours=[];w.solar[0].values=[];for(let t=from+H;t<=to;t+=H){w.hours.push({t});w.solar[0].values.push({t,v:500});}w.windows=[{day:date,slot:0,from,to:to-13*H},{day:date,slot:1,from:to-13*H,to:to-8*H},{day:date,slot:2,from:to-8*H,to}];const eq=equipment(),d=C.forecast(w,eq,plan(eq),{},from+12*H)[0];assert.equal(d.total,(to-from)/H*80);assert.equal(d.complete,true);}});
test('legacy measured intervals stay usable individually; unknown quality, unclassified mobile charging and future rows stay out',()=>{const eq=equipment(),p=plan(eq);for(const rows of [[],observations('2026-10-22',100,{coverage:.9}),observations('2026-10-22',100,{coverage:undefined}),observations('2026-10-22',100,{mobileFree:false}),observations('2026-10-24',100)])assert.equal(C.consumption(p,rows,'2026-10-23',now).wh,null);assert.equal(C.consumption(p,observations('2026-10-22',0),'2026-10-23',now).wh,0);});
test('optional seasonal inputs interpolate smoothly and missing manual seasons fall back to automatic learning',()=>{const p=plan(equipment());p.usageMode='manual';p.manual=[100,200,300,400];const a=C.consumption(p,[],'2026-05-31',now),b=C.consumption(p,[],'2026-06-01',now);assert.ok(Math.abs(a.wh-b.wh)<4);assert.equal(a.manual,true);p.manual=[null,null,null,null];assert.equal(C.consumption(p,observations('2026-10-22',600),'2026-10-23',now).wh,600);});
test('seasonal learning uses prior winter evidence with continuous transitions and marks unseen seasons provisional',()=>{const p=plan(equipment()),rows=[];for(let i=1;i<=8;i++){rows.push(...observations(`2026-01-${String(i).padStart(2,'0')}`,1200),...observations(`2026-07-${String(i).padStart(2,'0')}`,300));}const winter=C.consumption(p,rows,'2026-12-15',now),summer=C.consumption(p,rows,'2026-07-15',now);assert.ok(winter.wh>summer.wh*2);const a=C.consumption(p,rows,'2026-10-01',now),b=C.consumption(p,rows,'2026-10-02',now);assert.ok(Math.abs(a.wh-b.wh)<15);assert.equal(C.consumption(p,observations('2026-07-01',300),'2026-12-15',now).provisional,true);});
test('solar corrections require several forecasted days and reject gaps, curtailment, wrong setup and retrospective forecasts',()=>{const key='k-0123456789abcdef',obs=[],forecasts=[];for(let i=1;i<=5;i++){const o=observations(`2026-10-${String(i).padStart(2,'0')}`,100,{solar:50})[1];obs.push(o);forecasts.push({key,from:o.from,to:o.to,issued:o.from-D,wh:100});}assert.equal(C.calibration(obs.slice(0,2),forecasts,key)[1].factor,1);assert.equal(C.calibration(obs,forecasts,key)[1].factor,.75);for(const invalid of [{coverage:.5},{solarCoverage:.5},{bulkCoverage:.5},{bulkCoverage:undefined},{mobileFree:false},{key:'different'}])assert.equal(C.calibration(obs.map(o=>({...o,...invalid})),forecasts,key)[1].factor,1);assert.equal(C.calibration(obs,forecasts.map(f=>({...f,issued:f.from+1})),key)[1].factor,1);});
test('training preserves issue time, original raw yield and setup and never learns from the corrected forecast itself',()=>{const eq=equipment(),p=plan(eq),w=weather(),days=C.forecast(w,eq,p,{},now),rows=C.trainingRows(w,eq,p,days,'source');assert.ok(rows.length>30&&rows.length<=42);assert.ok(rows.every(r=>r.issued===w.updated&&r.from>r.issued));assert.equal(rows.find(r=>r.from===w.windows[4].from).wh,400);});

test('unavailable forecasts identify missing tilt, heading or provider readings separately',()=>{
 const eq=equipment(),p=plan(eq),w=weather();eq.groups[0].tilt=null;
 assert.deepEqual(C.forecast(w,eq,p,{},now)[0].issues.map(i=>i.kind),['tilt']);eq.groups[0].tilt=30;p.groups.roof.azimuth=null;
 assert.deepEqual(C.forecast(w,eq,p,{},now)[0].issues.map(i=>i.kind),['direction']);p.groups.roof.azimuth=180;w.solar=[];
 assert.deepEqual(C.forecast(w,eq,p,{},now)[0].issues.map(i=>i.kind),['weather']);
});
test('an old cache with duplicate day labels yields a time diagnostic and one day button per date',()=>{
 const eq=equipment(),p=plan(eq),w=weather();w.days.splice(1,0,{...w.days[0]});w.windows.push(...w.windows.slice(0,3));
 const days=C.forecast(w,eq,p,{},now);assert.equal(days.length,14);assert.equal(new Set(days.map(d=>d.day)).size,14);assert.equal(days[0].total,null);assert.ok(days[0].issues.some(i=>i.kind==='time'));assert.equal(days[1].total,1920);
});

function reserveFixture(){
 const eq=equipment(),p=plan(eq),w=weather();p.usageMode='manual';p.manual=[240,240,240,240];
 for(const v of w.solar[0].values){const hour=new Date(v.t-H/2).getUTCHours();v.v=hour>=7&&hour<18?500:0;}
 const days=C.forecast(w,eq,p,{},now),battery={soc:90,capacity:100,voltage:10,t:now};
 return {eq,p,w,days,battery};
}
test('sunset reserve caps every hour; surplus from a full battery cannot pay for the following night',()=>{
 const {w,days,battery}=reserveFixture(),[d]=C.batteryForecast(days,w,battery,now);
 assert.equal(d.evening.at,w.days[0].sunset);assert.equal(d.evening.soc,100);assert.equal(d.evening.excessWh,320);
 assert.equal(d.morning.at,w.days[1].sunrise);assert.equal(d.morning.soc,87);assert.equal(d.morning.excessWh,320);
 assert.ok(d.morning.low<=87&&d.morning.high>=87);assert.ok(d.morning.high<=100);
});
test('reserve starts from the current SOC, uses the remaining fraction of an hour and ignores earlier generation',()=>{
 const f=reserveFixture(),at=now+H/2,run=w=>C.batteryForecast(C.forecast(w,f.eq,f.p,{},at),w,{...f.battery,soc:0,t:at},at)[0];
 const baseline=run(f.w);assert.equal(baseline.evening.soc,38.5);
 for(const v of f.w.solar[0].values)if(v.t<=at)v.v=10000;
 assert.equal(run(f.w).evening.soc,baseline.evening.soc,'past yield is already contained in measured SOC');
});
test('direct solar consumption can hold a full battery without manufacturing additional reserve',()=>{
 const f=reserveFixture();f.p.manual=[1920,1920,1920,1920];f.w.solar[0].values.forEach(v=>v.v=500);
 const d=C.batteryForecast(C.forecast(f.w,f.eq,f.p,{},now),f.w,{...f.battery,soc:100},now)[0];
 assert.equal(d.evening.soc,100);assert.equal(d.morning.soc,100);assert.equal(d.morning.excessWh,0);
});
test('reserve keeps missing SOC, stale readings, capacity, consumption, weather and solar events explicitly unknown',()=>{
 const f=reserveFixture(),run=(battery=f.battery,days=f.days,w=f.w)=>C.batteryForecast(days,w,battery,now)[0];
 for(const b of [null,{...f.battery,soc:null},{...f.battery,soc:101},{...f.battery,t:now-45001}]){const r=run(b);assert.equal(r.evening.soc,undefined);assert.match(r.evening.reason,/Ladezustand/);assert.equal(r.evening.reasonCode,'soc');}
 assert.match(run({...f.battery,capacity:0}).evening.reason,/Kapazität|Akkukapazität/);
 assert.match(run({...f.battery,voltage:null}).evening.reason,/Spannung|Batteriespannung/);
 const waiting=run(f.battery,C.forecast(f.w,f.eq,plan(f.eq),{},now));assert.match(waiting.evening.reason,/Verbrauch/);assert.equal(waiting.evening.reasonCode,'consumption');
 const gap=structuredClone(f.w);gap.solar[0].values.find(v=>v.t===now+H).v=null;
 const missing=run(f.battery,C.forecast(gap,f.eq,f.p,{},now),gap);assert.equal(missing.evening.soc,undefined);assert.match(missing.evening.reason,/Ertragsdaten/);
 const noSun=structuredClone(f.days);noSun[0].sunset=null;assert.match(run(f.battery,noSun).evening.reason,/Sonnenuntergang/);
 const noRise=structuredClone(f.w);noRise.days[1].sunrise=null;assert.match(run(f.battery,f.days,noRise).morning.reason,/Sonnenaufgang/);
});
test('a measured zero forecast stays zero and reports a depleted interval even after later solar charging',()=>{
 const f=reserveFixture();f.w.solar[0].values.forEach(v=>v.v=0);let days=C.forecast(f.w,f.eq,f.p,{},now);
 let d=C.batteryForecast(days,f.w,{...f.battery,soc:0},now)[0];assert.equal(d.evening.soc,0);assert.equal(d.evening.possibleShortfall,true);assert.ok(d.morning.shortfallWh>0);
 f.w.solar[0].values.forEach(v=>{if(v.t>now+3*H)v.v=500;});days=C.forecast(f.w,f.eq,f.p,{},now);
 d=C.batteryForecast(days,f.w,{...f.battery,soc:0},now)[0];assert.ok(d.evening.soc>0);assert.ok(d.evening.shortfallWh>0,'recharging does not erase an earlier outage');
});
test('reserve includes an unmetered mobile circuit exactly once and only after explicit activation',()=>{
 const f=reserveFixture(),eq=equipment([{...equipment().groups[0],id:'case',count:1,placement:'mobile',measurement:'shunt'}]),p=plan(eq);p.usageMode='manual';p.manual=[240,240,240,240];p.groups.case.azimuth=180;p.groups.case.shade=[0,0,0];
 const run=()=>C.batteryForecast(C.forecast(f.w,eq,p,{},now),f.w,{...f.battery,soc:0},now)[0].evening.soc;
 assert.equal(run(),42);p.groups.case.active=true;assert.equal(run(),66);assert.deepEqual(p.manual,[240,240,240,240]);
});
test('sunrise uses its actual instant through autumn DST and a later target keeps its own chronological result',()=>{
 const at=Date.parse('2026-10-24T16:00:00Z'),end=Date.parse('2026-10-26T00:00:00Z'),eq=equipment(),p=plan(eq),w=weather(Date.parse('2026-10-24T00:00:00Z'),3);p.usageMode='manual';p.manual=[240,240,240,240];
 w.timezone='Europe/Berlin';w.days[0].sunset=at;w.days[1].sunrise=Date.parse('2026-10-25T06:00:00Z');
 // A constant 10 W load across the repeated 02:00 is 140 Wh over 14 real hours.
 const timeline=[];for(let t=at;t<end;t+=H)timeline.push({from:t,to:t+H,wh:0,slot:0});
 const days=[{day:'2026-10-24',sunset:at,complete:true,spread:.3,usage:{parts:[110,50,80],manual:true},timeline}];
 const r=C.batteryForecast(days,w,{soc:100,capacity:100,voltage:10,t:at},at)[0];assert.equal(r.evening.soc,100);assert.equal(r.morning.soc,86);assert.equal(r.morning.at,Date.parse('2026-10-25T06:00:00Z'));
});
test('a past sunset is not presented as a prediction; the next sunrise can still be estimated',()=>{
 const f=reserveFixture(),at=Date.parse('2026-10-23T20:00:00Z'),days=C.forecast(f.w,f.eq,f.p,{},at),d=C.batteryForecast(days,f.w,{...f.battery,t:at},at)[0];
 assert.equal(d.evening.soc,undefined);assert.match(d.evening.reason,/Vergangenheit/);assert.equal(d.morning.soc,79);
});
test('reserve uncertainty follows the daypart being consumed instead of unrelated sparse midday evidence',()=>{
 const at=Date.parse('2026-10-23T00:00:00Z'),weather={days:[{day:'2026-10-23',sunset:at+H}]};
 const days=[{day:'2026-10-23',sunset:at+H,complete:true,spread:0,usage:{parts:[110,50,80],hours:[11,5,8],uncertainty:.8,slotStats:[{uncertainty:.25},{uncertainty:.8},{uncertainty:.25}]},timeline:[{from:at,to:at+H,wh:0,slot:0}]}];
 const r=C.batteryForecast(days,weather,{soc:50,capacity:100,voltage:10,t:at},at)[0].evening;
 assert.equal(r.soc,49);assert.equal(r.low,48.75);assert.equal(r.high,49.25);
});
test('wide uncertainty during a change in habits cannot turn lower household demand into invented charging',()=>{
 const at=Date.parse('2026-10-23T00:00:00Z'),weather={days:[{day:'2026-10-23',sunset:at+H}]},days=[{day:'2026-10-23',sunset:at+H,complete:true,spread:0,usage:{parts:[110,50,80],slotStats:[{uncertainty:2}]},timeline:[{from:at,to:at+H,wh:0,slot:0}]}];
 const r=C.batteryForecast(days,weather,{soc:50,capacity:100,voltage:10,t:at},at)[0].evening;assert.equal(r.soc,49);assert.equal(r.high,50);assert.equal(r.low,47);
});
test('partial mobile days learn without complete-day veto; missing dayparts remain provisional',()=>{
 const p=plan(equipment()),rows=observations('2026-10-22',240,{mobileFree:false,learningVersion:2});
 rows[0].usage={wh:20,duration:2*H,estimatedDuration:0,latest:rows[0].from+2*H};
 rows[1].usage={wh:60,duration:3*H,estimatedDuration:3*H,latest:rows[1].from+3*H};
 rows[2].usage={wh:0,duration:0,estimatedDuration:0,latest:0};
 const r=C.consumption(p,rows,'2026-10-23',now);assert.equal(r.days,1);assert.equal(r.sampleHours,5);assert.equal(r.estimatedHours,3);assert.equal(r.provisional,true);assert.equal(r.extrapolated,true);
 assert.equal(r.parts[0],110);assert.equal(r.parts[1],null);assert.equal(r.slotStats[1].candidateWh,100);assert.ok(r.parts[2]>80&&r.parts[2]<160);
 const night=C.consumption(p,rows.slice(0,1),'2026-10-23',now);assert.equal(night.wh,240);assert.equal(night.uncertainty,.8);
 const future=structuredClone(rows);future[0].usage.latest=now+1;future[1].usage.duration=0;assert.equal(C.consumption(p,future,'2026-10-23',now).wh,null);
});
test('mobile estimates carry less weight than measured consumption and do not overwrite the raw residual',()=>{
 const p=plan(equipment()),a=observations('2026-10-21',0,{mobileFree:false,learningVersion:2})[1],b=observations('2026-10-22',0,{mobileFree:false,learningVersion:2})[1];
 a.usage={wh:50,duration:5*H,estimatedDuration:0,latest:a.to};b.consumption=-50;b.usage={wh:100,duration:5*H,estimatedDuration:5*H,latest:b.to};
 const r=C.consumption(p,[a,b],'2026-10-23',now);assert.ok(r.wh>240&&r.wh<360);assert.equal(b.consumption,-50);
});
test('unmetered mobile switching keeps the measured solar calibration key and original fixed forecast',()=>{
 const eq=equipment([{...equipment().groups[0],id:'case',placement:'mobile',measurement:'shunt',count:1}]),p=plan(eq),w=weather(),key=C.calibrationKey(eq,p,w.place,'s');
 p.groups.case.active=true;p.groups.case.azimuth=null;
 assert.equal(C.calibrationKey(eq,p,w.place,'s'),key);
 let days=C.forecast(w,eq,p,{},now);assert.equal(days[0].complete,false);assert.equal(days[0].measuredComplete,true);assert.ok(C.trainingRows(w,eq,p,days,'s').length>30);
 p.groups.case.azimuth=180;days=C.forecast(w,eq,p,{},now);assert.equal(C.trainingRows(w,eq,p,days,'s')[0].wh,640);
 eq.groups[1].measurement='solar';assert.notEqual(C.calibrationKey(eq,p,w.place,'s'),key);days=C.forecast(w,eq,p,{},now);assert.equal(C.trainingRows(w,eq,p,days,'s')[0].wh,960);
});
test('independently measured solar can calibrate during mobile operation; limited or unknown phases cannot',()=>{
 const key='k-0123456789abcdef',obs=[],predictions=[];
 for(let i=1;i<=5;i++){const o=observations(`2026-10-${String(i).padStart(2,'0')}`,0,{solar:50,mobileFree:false,learningVersion:2,solarKey:key,solarEligible:true,coverage:0})[1];obs.push(o);predictions.push({key,from:o.from,to:o.to,issued:o.from-D,wh:100});}
 assert.equal(C.calibration(obs,predictions,key)[1].factor,.75);
 for(const solarEligible of [false,undefined])assert.equal(C.calibration(obs.map(o=>({...o,solarEligible})),predictions,key)[1].factor,1);
});
test('learned rates and forecast loads use real DST duration instead of treating a 25-hour day as 24',()=>{
 const eq=equipment(),p=plan(eq),from=Date.parse('2026-10-24T22:00:00Z'),to=Date.parse('2026-10-25T23:00:00Z'),w=weather(from,1,25);w.timezone='Europe/Berlin';w.days[0].day='2026-10-25';w.windows=[{day:'2026-10-25',slot:0,from,to:to-13*H},{day:'2026-10-25',slot:1,from:to-13*H,to:to-8*H},{day:'2026-10-25',slot:2,from:to-8*H,to}];
 const obs=observations('2026-10-22',240),d=C.forecast(w,eq,p,{observations:obs},from)[0];assert.equal(d.usage.wh,250);assert.deepEqual(d.usage.parts,[120,50,80]);assert.deepEqual(d.usage.hours,[12,5,8]);
 const morning=observations('2026-10-22',240)[0];morning.to+=H;morning.consumption=120;assert.equal(C.consumption(p,[morning],'2026-10-23',now).wh,240);
});
test('planned roller charging is subtracted once from the remaining battery forecast',()=>{
 const f=reserveFixture();for(const h of f.days[0].timeline)h.wh=0;
 const at=f.days[0].sunset-3600000,battery={...f.battery,t:at,soc:90};
 const baseline=C.batteryForecast(f.days,f.w,battery,at)[0].evening;
 const charged=C.batteryForecast(f.days,f.w,battery,at,[{from:at,to:at+3600000,watts:138}])[0].evening;
 assert.ok(Math.abs(baseline.soc-charged.soc-13.8)<1e-8);
});

function referenceHistory(eq,p,key,count=5){
 const hw=C.systemKey(eq,'s'),obs=[],forecasts=[];
 for(let i=1;i<=count;i++){const o=observations(`2026-10-${String(i).padStart(2,'0')}`,100,{solar:50,solarKey:key,systemKey:hw,portableEligible:true,learningVersion:2,solarEligible:true})[1];obs.push(o);forecasts.push({key,systemKey:hw,from:o.from,to:o.to,issued:o.from-D,wh:100});}
 return {obs,forecasts,hw};
}
test('hardware identity travels across site, heading and shade but changes with panel or controller losses',()=>{
 const eq=equipment(),p=plan(eq),hw=C.systemKey(eq,'s');p.groups.roof.azimuth=90;p.groups.roof.shade=[3,3,3];assert.equal(C.systemKey(eq,'s'),hw);
 const replacement=structuredClone(eq);replacement.groups[0].controller='Replacement MPPT';assert.notEqual(C.systemKey(replacement,'s'),hw);replacement.groups[0].controller=eq.groups[0].controller;replacement.groups[0].yieldFactor=.7;assert.notEqual(C.systemKey(replacement,'s'),hw);assert.notEqual(C.systemKey(eq,'another-plant'),hw);
});
test('unshaded hardware experience supplies only a bounded provisional prior at a new site',()=>{
 const eq=equipment(),p=plan(eq),old='k-0123456789abcdef',fresh='k-fedcba9876543210',h=referenceHistory(eq,p,old);
 assert.equal(C.calibration(h.obs.slice(0,4),h.forecasts,fresh,h.hw,now)[1].factor,1);
 const result=C.calibration(h.obs,h.forecasts,fresh,h.hw,now)[1];assert.equal(result.basis,'equipment');assert.equal(result.days,5);assert.equal(result.factor,.875);assert.equal(result.mobileFactor,1);
 for(const invalid of [{portableEligible:false},{solarEligible:false},{systemKey:'different'}])assert.equal(C.calibration(h.obs.map(o=>({...o,...invalid})),h.forecasts,fresh,h.hw,now)[1].factor,1);
 assert.equal(C.calibration(h.obs,h.forecasts,fresh,h.hw,now+181*D)[1].factor,1);
});
test('new local comparisons replace the portable prior and mobile reference is smaller than fixed calibration',()=>{
 const eq=equipment(),p=plan(eq),key='k-0123456789abcdef',h=referenceHistory(eq,p,key),r=C.calibration(h.obs,h.forecasts,key,h.hw,now)[1];
 assert.equal(r.basis,'site');assert.equal(r.factor,.75);assert.equal(r.mobileFactor,.875);assert.equal(r.mobileDays,5);
 const shaded=C.calibration(h.obs.map(o=>({...o,portableEligible:false})),h.forecasts,key,h.hw,now)[1];assert.equal(shaded.factor,.75);assert.equal(shaded.mobileFactor,1);
});
test('mobile reference changes only forecasted unmetered contribution and is withheld for currently shaded fixed panels',()=>{
 const eq=equipment([{...equipment().groups[0],id:'case',count:1,placement:'mobile',measurement:'shunt'}]),p=plan(eq),w=weather();p.groups.case={active:true,azimuth:180,shade:[0,0,0]};
 const key=C.calibrationKey(eq,p,w.place,'s'),h=referenceHistory(eq,p,key),knowledge={source:'s',observations:h.obs,forecasts:h.forecasts};
 const base=C.forecast(w,eq,p,{},now)[0],learned=C.forecast(w,eq,p,knowledge,now)[0];assert.equal(learned.parts[1].mobile,base.parts[1].mobile*.875);assert.equal(learned.parts[1].raw,base.parts[1].raw);assert.ok(learned.calibration[1].mobileApplied);
 p.groups.roof.shade[1]=2;const shaded=C.forecast(w,eq,p,knowledge,now)[0];assert.equal(shaded.parts[1].mobile,base.parts[1].mobile);assert.equal(shaded.calibration[1].mobileApplied,false);
});
test('explicit per-circuit loss factors alter modeled yield and training baselines without fabricating readings',()=>{
 const eq=equipment(),p=plan(eq),w=weather(),before=C.forecast(w,eq,p,{},now)[0];eq.groups[0].yieldFactor=.6;const after=C.forecast(w,eq,p,{},now)[0];assert.equal(after.total,before.total*.75);assert.notEqual(C.calibrationKey(eq,p,w.place),C.calibrationKey(equipment(),p,w.place));
});
