const {test}=require('node:test'),assert=require('node:assert/strict');
const Bridge=require('../app/src/main/assets/read-bridge.js');
test('LAN viewers read asynchronously, refresh on revision changes and reject stale in-flight results',()=>{
 const requests=[];const b=new Bridge({isLanViewer:()=>true,history:()=>{throw Error('Blocking LAN query');},requestHistory:json=>requests.push(JSON.parse(json))},()=>{});
 assert.equal(JSON.parse(b.bridge.history(0,100000,'power','all')).pending,true);const first=requests[0];
 b.invalidate();b.bridge.history(0,100000,'power','all');b.result({id:first.id,data:{points:[{v:999}]}});
 assert.equal(requests.length,2);assert.equal(b.cache.size,0);b.result({id:requests[1].id,data:{points:[{v:12}]}});
 assert.equal(JSON.parse(b.bridge.history(0,100000,'power','all')).points[0].v,12);
});
test('calendar-day consumption is asynchronous even without a running import and preserves unknown and zero',()=>{
 const requests=[],ready=[];const b=new Bridge({dailyEnergy:()=>{throw Error('Synchronous daily query');},requestHistory:json=>requests.push(JSON.parse(json))},method=>ready.push(method));
 assert.equal(JSON.parse(b.bridge.dailyEnergy(0,100000,1,10)).pending,true);assert.equal(requests.length,1);
 const days=[{day:'2026-10-20',last:null,coverage:0},{day:'2026-10-21',last:0,coverage:1}];b.result({id:requests[0].id,data:{days}});
 assert.deepEqual(JSON.parse(b.bridge.dailyEnergy(0,100000,1,10)).days,days);assert.equal(requests.length,1);assert.deepEqual(ready,['dailyEnergy']);
 assert.equal(JSON.parse(b.bridge.dailyEnergy(0,100000,2,10)).pending,true);assert.equal(requests.length,2,'newly imported data must invalidate the old day summaries');
});
function setup(){const requests=[],calls=[];let notifications=0;const raw={history:(...args)=>{calls.push(args);return JSON.stringify({points:[{t:args[0],v:42}]});},energyHistoryWindow:()=>JSON.stringify({solar:{summary:{last:100}},consumption:{summary:{last:150}},balance:{summary:{last:-50}}}),requestHistory:json=>requests.push(JSON.parse(json)),settings:json=>json};const b=new Bridge(raw,()=>notifications++);return {b,requests,calls,notifications:()=>notifications};}
test('ordinary reads and settings preserve the native interface',()=>{const {b,calls}=setup();assert.equal(JSON.parse(b.bridge.history(0,100000,'power','all')).points[0].v,42);assert.equal(calls.length,1);assert.equal(b.bridge.settings('ok'),'ok');});
test('busy energy requests are asynchronous and never invent zero Wh',()=>{const {b,requests,calls}=setup();b.state({busy:true,runId:1});const result=JSON.parse(b.bridge.energyHistoryWindow(0,100000,'all',1000,90000));assert.equal(result.pending,true);for(const key of ['solar','consumption','balance']){assert.equal(result[key].summary.last,undefined);assert.equal(result[key].pending,true);}assert.equal(requests.length,1);assert.equal(calls.length,0);});
test('one request is in flight and duplicate queries share its eventual result',()=>{const {b,requests,notifications}=setup();b.state({busy:true,runId:1});for(let i=0;i<4;i++)b.bridge.history(0,100000,'power','all');assert.equal(requests.length,1);b.result({id:requests[0].id,data:{points:[{v:7}]}});assert.equal(notifications(),1);assert.equal(JSON.parse(b.bridge.history(0,100000,'power','all')).points[0].v,7);assert.equal(requests.length,1);});
test('a nearby complete response stays visible until the asynchronous refresh finishes',()=>{const {b,requests}=setup();b.bridge.history(10000,100000,'power','all');b.state({busy:true,runId:1});assert.equal(JSON.parse(b.bridge.history(20000,110000,'power','all')).points[0].v,42);assert.equal(requests.length,1);});
test('requests are bounded and only one native query is submitted at a time',()=>{const {b,requests}=setup();b.state({busy:true,runId:1});for(let i=0;i<100;i++)b.bridge.history(i*60000,i*60000+60000,'power','all');assert.equal(requests.length,1);assert.equal(b.queue.size,24);b.result({id:requests[0].id,data:{points:[]}});assert.equal(requests.length,2);assert.equal(b.queue.size,23);});
test('late responses from a finished or previous run cannot overwrite current data',()=>{const {b,requests,notifications}=setup();b.state({busy:true,runId:1});b.bridge.history(0,100000,'power','all');const old=requests[0];b.state({busy:false,runId:1});b.state({busy:true,runId:2});b.bridge.history(0,100000,'power','all');b.result({id:old.id,data:{points:[{v:999}]}});assert.equal(notifications(),0);assert.equal(requests.length,2);assert.equal(JSON.parse(b.bridge.history(0,100000,'power','all')).pending,true);});
test('a failed read remains an explicit error and completion returns to fresh ordinary reads',()=>{const {b,requests,calls}=setup();b.state({busy:true,runId:1});b.bridge.history(0,100000,'power','all');b.result({id:requests[0].id,data:{error:'Lesefehler'}});assert.equal(JSON.parse(b.bridge.history(0,100000,'power','all')).error,'Lesefehler');b.state({busy:false,runId:1});assert.equal(JSON.parse(b.bridge.history(0,100000,'power','all')).points[0].v,42);assert.equal(calls.length,1);});

test('an import starting between status poll and read cannot cache an empty synchronous result',()=>{const {b,requests}=setup();b.raw.history=()=>'{"syncing":true}';b.raw.syncState=()=>'{"busy":true,"runId":1}';assert.equal(JSON.parse(b.bridge.history(0,100000,'power','all')).pending,true);assert.equal(requests.length,1);assert.equal(b.cache.size,0);});

function viewer(){const requests=[];let plant='one';const b=new Bridge({isLanViewer:()=>true,lanState:()=>JSON.stringify({id:plant}),requestHistory:q=>requests.push(JSON.parse(q))},()=>{});return {b,requests,plant:id=>plant=id,reply:data=>b.result({id:requests.at(-1).id,data})};}
test('revision refresh retains the last success until a complete replacement arrives',()=>{
 const {b,requests,reply}=viewer();b.read('history',[0,100000,'power','all'],'power-day');reply({points:[{v:12}]});
 b.invalidate();let value=JSON.parse(b.read('history',[0,100000,'power','all'],'power-day'));assert.equal(value.points[0].v,12);assert.equal(value.pending,true);assert.equal(value.stale,true);assert.equal(requests.length,2);
 reply({points:[{v:37}]});value=JSON.parse(b.read('history',[0,100000,'power','all'],'power-day'));assert.equal(value.points[0].v,37);assert.equal(value.stale,undefined);assert.equal(value.pending,undefined);
});
test('an error retains valid points and remains explicit; a successful empty replacement clears them',()=>{
 const {b,reply}=viewer();b.read('history',[0,100000,'power','all'],'same-view');reply({points:[{v:12}]});b.invalidate();b.read('history',[0,100000,'power','all'],'same-view');reply({error:'Hauptgerät nicht erreichbar'});
 let value=JSON.parse(b.read('history',[0,100000,'power','all'],'same-view'));assert.equal(value.points[0].v,12);assert.equal(value.pending,false);assert.equal(value.stale,true);assert.match(value.refreshError,/nicht erreichbar/);assert.equal(value.error,undefined);
 b.invalidate();b.read('history',[0,100000,'power','all'],'same-view');reply({points:[]});value=JSON.parse(b.read('history',[0,100000,'power','all'],'same-view'));assert.deepEqual(value.points,[]);assert.equal(value.stale,undefined);
});
test('a moving scoped live window survives a long refresh, while another metric or period never inherits it',()=>{
 const {b,reply}=viewer();b.read('history',[0,100000,'power','all'],'last-hour');reply({points:[{v:12}]});
 const retained=JSON.parse(b.read('history',[600000,700000,'power','all'],'last-hour'));assert.equal(retained.points[0].v,12);assert.equal(retained.stale,true);
 assert.deepEqual(JSON.parse(b.read('history',[600000,700000,'voltage','all'],'last-hour')).points,[]);
 assert.deepEqual(JSON.parse(b.read('history',[600000,700000,'power','all'],'another-period')).points,[]);
});
test('previous energy summaries and calendar-day zero/unknown values survive refresh with explicit stale flags',()=>{
 const {b,reply}=viewer();const energy={solar:{summary:{last:20}},consumption:{summary:{last:30}},balance:{summary:{last:-10}}};b.read('energyHistoryWindow',[0,100000,'all',1000,90000],'flow');reply(energy);b.invalidate();
 const flow=JSON.parse(b.read('energyHistoryWindow',[0,110000,'all',1000,100000],'flow'));for(const key of ['solar','consumption','balance']){assert.equal(flow[key].summary.last,energy[key].summary.last);assert.equal(flow[key].stale,true);assert.equal(flow[key].pending,true);}
});
test('calendar-day refresh ignores cache-busting revisions without losing measured zero or unknown days',()=>{
 const {b,reply}=viewer(),days=[{day:'2026-10-20',last:null},{day:'2026-10-21',last:0},{day:'2026-10-22',last:29.68}];b.read('dailyEnergy',[0,100000,1,1],'month');reply({days});b.invalidate();
 const value=JSON.parse(b.read('dailyEnergy',[0,100000,2,2],'month'));assert.deepEqual(value.days,days);assert.equal(value.stale,true);assert.equal(value.pending,true);
});
test('switching plant drops the previous source and rejects its late response',()=>{
 const {b,requests,reply,plant}=viewer();b.read('history',[0,100000,'power','all'],'day');reply({points:[{v:12}]});b.invalidate();b.read('history',[0,100000,'power','all'],'day');const previous=requests.at(-1);
 plant('two');const value=JSON.parse(b.read('history',[0,100000,'power','all'],'day'));assert.deepEqual(value.points,[]);b.result({id:previous.id,data:{points:[{v:999}]}});assert.deepEqual(JSON.parse(b.read('history',[0,100000,'power','all'],'day')).points,[]);reply({points:[{v:2}]});assert.equal(JSON.parse(b.read('history',[0,100000,'power','all'],'day')).points[0].v,2);
});
test('slow queries coalesce moving requests for the same logical view',()=>{
 const {b,requests,reply}=viewer();b.read('history',[0,100000,'power','all'],'day');
 for(let i=1;i<=50;i++)b.read('history',[i*10000,i*10000+100000,'power','all'],'day');assert.equal(requests.length,1);assert.equal(b.queue.size,1);reply({points:[{v:12}]});assert.equal(requests.length,2);assert.equal(requests[1].args[0],500000);
});
