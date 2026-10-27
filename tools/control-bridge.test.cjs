const {test}=require('node:test'),assert=require('node:assert/strict');
const Control=require('../app/src/main/assets/control-bridge');
test('remote settings stay pending until an authenticated native result, and duplicate taps do not dispatch twice',async()=>{
 const requests=[],events=[];let local=0;const b=new Control({isLanViewer:()=>true,controlRevision:()=>4,requestPlantCommand:q=>requests.push(JSON.parse(q)),saveEquipment:()=>local++,settings:()=>''},s=>events.push(s));
 const promise=b.bridge.saveEquipment('{}');assert.equal(requests.length,1);assert.equal(local,0);let resolved=false;promise.then(()=>resolved=true);await Promise.resolve();assert.equal(resolved,false);
 assert.match(await b.bridge.saveEquipment('{}'),/abwarten/);assert.equal(requests.length,1);
 b.result({id:999,ok:true});assert.equal(resolved,false);b.result({id:requests[0].id,ok:true});assert.equal(await promise,'');assert.match(events.at(-1),/bestätigt/);
});
test('remote failure remains an error and presentation settings stay local',async()=>{
 const requests=[],local=[];const b=new Control({isLanViewer:()=>true,controlRevision:()=>8,requestPlantCommand:q=>requests.push(JSON.parse(q)),settings:q=>{local.push(q);return '';}},()=>{});
 assert.equal(b.bridge.settings('{"theme":"light"}'),'');assert.equal(requests.length,0);assert.equal(local.length,1);
 const pending=b.bridge.settings('{"capacity":100}');b.result({id:requests[0].id,error:'Hauptgerät nicht erreichbar'});assert.equal(await pending,'Hauptgerät nicht erreichbar');assert.equal(local.length,1);
});
test('standalone methods preserve their synchronous native contract',()=>{
 const b=new Control({isLanViewer:()=>false,saveSolarSetup:q=>'validated:'+q},()=>{});assert.equal(b.bridge.saveSolarSetup('{}'),'validated:{}');
});
