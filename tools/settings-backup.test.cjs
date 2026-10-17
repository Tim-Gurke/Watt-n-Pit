const {test}=require('node:test');
const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/settings-backup.js');
const D=require('../app/src/main/assets/dashboard-core.js');
const sample=()=>C.create({settings:{theme:'light',palette:'green',capacity:150,sampleSeconds:30,miniRange:'sunrise',weatherPlace:{name:'Testort',latitude:48,longitude:8}}},D.defaults(),{version:1,groups:[{id:'test-group',label:'Testmodule',count:2,watts:90,placement:'fixed',controller:'Regler',measurement:'solar'}]});
test('a JSON round trip preserves every portable setting, tile layout and source assignment',()=>{
  const backup=sample();backup.dashboard.tiles[1].period.mode='sunrise';backup.dashboard.tiles[1].color='#123456';backup.dashboard.tiles.find(t=>t.metric==='dischargeEnergy').reverseSign=true;backup.dashboard.blockOrder=['record','tiles','history'];
  assert.deepEqual(C.normalize(JSON.parse(JSON.stringify(backup))),backup);
});
test('exports and imports cannot transfer connection secrets, identities or measurements',()=>{
  const snapshot={settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10,miniRange:'today',key:'PRIVATE_SENTINEL'},devices:[{mac:'PRIVATE_SENTINEL',secret:'PRIVATE_SENTINEL',pin:'PRIVATE_SENTINEL'}],readings:[{power:789}]};
  const backup=C.create(snapshot,D.defaults(),{version:1,groups:[]});assert.ok(!JSON.stringify(backup).includes('PRIVATE_SENTINEL'));
  backup.devices=snapshot.devices;backup.readings=snapshot.readings;backup.settings.key='PRIVATE_SENTINEL';backup.dashboard.keys='PRIVATE_SENTINEL';backup.dashboard.tiles[0].secret='PRIVATE_SENTINEL';backup.weatherPlace={name:'Testort',latitude:48,longitude:8,key:'PRIVATE_SENTINEL'};
  assert.ok(!JSON.stringify(C.normalize(backup)).includes('PRIVATE_SENTINEL'));
});
test('foreign files, unsupported versions and incomplete dashboards cannot reset the current board',()=>{
  for(const mutate of [b=>b.format='another-app',b=>b.version=2,b=>delete b.dashboard.tiles,b=>b.dashboard.version=2,b=>b.dashboard.tiles[0].metric='future-unknown',b=>b.dashboard.tiles[0].metric='constructor',b=>b.dashboard.tiles[0].period.mode='unknown']){
    const backup=sample();mutate(backup);assert.throws(()=>C.normalize(backup));
  }
});
test('invalid settings or equipment fail before a configuration can be applied',()=>{
  for(const mutate of [b=>b.settings.capacity=null,b=>b.settings.capacity=Infinity,b=>b.settings.sampleSeconds=0,b=>b.settings.palette='unknown',b=>b.equipment.groups[0].count=0,b=>b.weatherPlace.latitude=91]){
    const backup=sample();mutate(backup);const before=structuredClone(backup);assert.throws(()=>C.normalize(backup));assert.deepEqual(backup,before);
  }
});
test('empty layouts, an unconfigured solar system and weather off remain intentional',()=>{
  const backup=sample();backup.dashboard.tiles=[];backup.equipment.groups=[];backup.weatherPlace={};
  const restored=C.normalize(JSON.parse(JSON.stringify(backup)));assert.equal(restored.dashboard.tiles.length,0);assert.equal(restored.equipment.groups.length,0);assert.deepEqual(restored.weatherPlace,{});
});
test('old settings backups retain custom values while moving weather into the dashboard',()=>{
  const backup=sample();backup.dashboard.tiles=backup.dashboard.tiles.filter(t=>t.metric!=='weather');backup.dashboard.blockOrder=['weather','record','tiles','history'];
  const positions=backup.dashboard.tiles.map(t=>t.layouts),restored=C.normalize(backup);
  assert.deepEqual(restored.dashboard.tiles.slice(0,-1).map(t=>t.layouts),positions);assert.equal(restored.dashboard.tiles.at(-1).metric,'weather');
  restored.dashboard.tiles.at(-1).layouts.compact={x:0,y:2,w:6,h:4};restored.dashboard.tiles.at(-1).title='Meine Sonne';
  assert.deepEqual(C.normalize(JSON.parse(JSON.stringify(restored))),restored);
});
test('settings preserve an explicit positive discharge choice after the negative-default migration',()=>{
  const backup=sample();backup.dashboard.tiles.find(t=>t.metric==='dischargeEnergy').reverseSign=false;
  assert.equal(C.normalize(JSON.parse(JSON.stringify(backup))).dashboard.tiles.find(t=>t.metric==='dischargeEnergy').reverseSign,false);
  delete backup.dashboard.dischargeSignVersion;
  assert.equal(C.normalize(backup).dashboard.tiles.find(t=>t.metric==='dischargeEnergy').reverseSign,true);
});
