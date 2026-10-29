const {test}=require('node:test'),assert=require('node:assert/strict');
const P=require('../app/src/main/assets/presence-core.js'),C=require('../app/src/main/assets/solar-core.js');
const H=C.HOUR,now=Date.parse('2026-10-29T10:00:00Z');
const plan={id:'trip',from:now+H,to:now+5*H,status:'planned'};
test('future absence uses both independent times and actual presence before departure',()=>{
 const p={state:'home',plans:[plan]};assert.equal(P.modeAt(now+.5*H,p,now),'home');assert.equal(P.modeAt(now+2*H,p,now),'away');assert.equal(P.modeAt(plan.to,p,now),'home');
 const changed={...p,plans:[{...plan,from:now+3*H}]};assert.equal(P.modeAt(now+2*H,changed,now),'home');assert.equal(P.modeAt(plan.to,changed,now),'home');assert.equal(changed.plans[0].to,plan.to);
});
test('missed departure suppresses the complete plan until clarification and leaves other future plans intact',()=>{
 const p={state:'home',plans:[{...plan,status:'pending'},{...plan,id:'next',from:now+24*H,to:now+30*H}]};assert.equal(P.modeAt(now+4*H,p,now),'home');assert.equal(P.modeAt(now+25*H,p,now),'away');
 for(const status of ['cancelled','returned','snoozed'])assert.equal(P.modeAt(now+4*H,{state:'home',plans:[{...plan,status}]},now),'home');
});
test('actual absence keeps its planned return and early actual return closes the plan',()=>{
 const active={...plan,from:now-H,status:'active'};assert.equal(P.modeAt(now+H,{state:'away',plans:[active]},now),'away');assert.equal(P.modeAt(active.to,{state:'away',plans:[active]},now),'home');assert.equal(P.modeAt(now+H,{state:'home',plans:[{...active,status:'returned'}]},now),'home');
});
test('actual absence without a known return stays provisional instead of inventing a return',()=>{assert.equal(P.modeAt(now+10*H,{state:'away',plans:[]},now),'away');});
const profile=watts=>({parts:[11,5,8].map(h=>h*watts),hours:[11,5,8],wh:24*watts,provisional:false,slotStats:[0,1,2].map(()=>({uncertainty:.25}))});
function day(){return {day:'2026-10-29',total:0,usage:profile(100),timeline:[{from:now,to:now+H,slot:1,wh:0}],reserve:{},complete:true,spread:0};}
test('departure and return within one hour use exact time fractions rather than the whole daypart',()=>{
 const d=day();P.apply(d,{home:profile(100),away:profile(10)},{state:'home',plans:[{...plan,from:now+H/2,to:now+10*H}]},now);
 assert.equal(d.usage.wh,55);assert.equal(d.usage.presenceMode,'mixed');assert.equal(d.timeline[0].loadSegments.length,2);assert.equal(d.balance,-55);
 const result=C.batteryForecast([{...d,sunset:now+H}],{days:[]},{soc:50,t:now,capacity:100,voltage:10},now);assert.ok(Math.abs(result[0].evening.soc-44.5)<1e-8);
});
test('an unlearned away profile uses a visible conservative estimate and never a fabricated zero',()=>{
 const d=day(),away={...profile(0),parts:[null,null,null]};P.apply(d,{home:profile(100),away},{state:'away',plans:[]},now);assert.equal(d.usage.wh,100);assert.equal(d.usage.awayFallback,true);assert.equal(d.usage.provisional,true);assert.equal(d.usage.returnUnknown,true);
});
test('consumption profiles remain independent and unknown observation intervals enter neither profile',()=>{
 const u=(watts)=>({wh:watts*5,duration:5*H,estimatedDuration:0,estimatedWh:0,latest:now-H});
 const row={day:'2026-10-28',slot:1,learningVersion:2,usage:u(999),usageByPresence:{home:u(20),away:u(2),unknown:u(100)}};
 assert.equal(C.consumption({usageMode:'auto'},[row],'2026-10-29',now,'home').parts[1],100);assert.equal(C.consumption({usageMode:'auto'},[row],'2026-10-29',now,'away').parts[1],10);assert.equal(row.usage.wh,4995);
 const unknown={...row,usageByPresence:{unknown:u(100)}};assert.equal(C.consumption({},[unknown],'2026-10-29',now,'home').wh,null);assert.equal(C.consumption({},[unknown],'2026-10-29',now,'away').wh,null);
});
test('legacy comparisons remain available as the original baseline without being labeled absence',()=>{
 const row={day:'2026-10-28',slot:1,learningVersion:2,usage:{wh:50,duration:5*H,estimatedDuration:0,latest:now-H}};assert.equal(C.consumption({},[row],'2026-10-29',now,'home').parts[1],50);assert.equal(C.consumption({},[row],'2026-10-29',now,'away').wh,null);
});
test('three calendar years include leap days and expire at the actual anniversary',()=>{
 assert.equal(new Date(C.learningSince(Date.parse('2024-02-29T12:00:00Z'))).toISOString(),'2021-02-28T12:00:00.000Z');assert.equal(new Date(C.learningSince(now)).toISOString(),'2023-10-29T10:00:00.000Z');
});
