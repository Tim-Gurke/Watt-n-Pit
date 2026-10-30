const {test}=require('node:test');
const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/settings-backup.js');
const D=require('../app/src/main/assets/dashboard-core.js');
const sample=()=>C.create({settings:{theme:'light',palette:'green',capacity:150,sampleSeconds:30,miniRange:'sunrise',weatherPlace:{name:'Testort',latitude:48,longitude:8}}},D.defaults(),{version:1,groups:[{id:'test-group',label:'Testmodule',count:2,watts:90,placement:'fixed',controller:'Regler',measurement:'solar'}]});
test('PWM loss model selection and custom totals round trip without changing either handset layout',()=>{
 for(const model of [{controller:'PWM',yieldFactor:.8},{controller:'PWM',yieldFactor:.65},{controller:'PWM',controllerType:'mppt',yieldMode:'manual',yieldFactor:.8}]){
  const backup=sample(),layout=structuredClone(backup.dashboard);Object.assign(backup.equipment.groups[0],model);if(!model.yieldMode)delete backup.equipment.groups[0].yieldMode;
  const restored=C.normalize(JSON.parse(JSON.stringify(backup))),again=C.normalize(JSON.parse(JSON.stringify(restored)));
  assert.deepEqual(restored,again);assert.deepEqual(restored.dashboard,layout);
  assert.equal(restored.equipment.groups[0].yieldFactor,model.yieldFactor===.8&&model.yieldMode===undefined ? .6 : model.yieldFactor);
 }
});
test('a JSON round trip preserves every portable setting, tile layout and source assignment',()=>{
  const backup=sample();backup.dashboard.tiles.push(D.tile('dischargeEnergy','optional-discharge'));backup.dashboard.tiles[1].period.mode='sunrise';backup.dashboard.tiles[1].color='#123456';backup.dashboard.tiles.find(t=>t.metric==='dischargeEnergy').reverseSign=true;backup.dashboard.blockOrder=['record','tiles','history','roller'];
  assert.deepEqual(C.normalize(JSON.parse(JSON.stringify(backup))),backup);
});
test('exports and imports cannot transfer connection secrets, identities or measurements',()=>{
  const snapshot={settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10,miniRange:'today',key:'PRIVATE_SENTINEL',background:'PRIVATE_SENTINEL',runId:'PRIVATE_SENTINEL','cockpitNotice-v1':'PRIVATE_SENTINEL'},devices:[{mac:'PRIVATE_SENTINEL',secret:'PRIVATE_SENTINEL',pin:'PRIVATE_SENTINEL'}],readings:[{power:789}]};
  const backup=C.create(snapshot,D.defaults(),{version:1,groups:[]});assert.ok(!JSON.stringify(backup).includes('PRIVATE_SENTINEL'));
  backup.devices=snapshot.devices;backup.readings=snapshot.readings;backup.settings.key='PRIVATE_SENTINEL';backup.settings.background='PRIVATE_SENTINEL';backup.runId='PRIVATE_SENTINEL';backup.dashboard.keys='PRIVATE_SENTINEL';backup.dashboard.tiles[0].secret='PRIVATE_SENTINEL';backup.weatherPlace={name:'Testort',latitude:48,longitude:8,key:'PRIVATE_SENTINEL'};
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
  const backup=sample();backup.dashboard.tiles.push(D.tile('dischargeEnergy','optional-discharge'));backup.dashboard.tiles.find(t=>t.metric==='dischargeEnergy').reverseSign=false;
  assert.equal(C.normalize(JSON.parse(JSON.stringify(backup))).dashboard.tiles.find(t=>t.metric==='dischargeEnergy').reverseSign,false);
  delete backup.dashboard.dischargeSignVersion;
  assert.equal(C.normalize(backup).dashboard.tiles.find(t=>t.metric==='dischargeEnergy').reverseSign,true);
});

test('battery balance tile settings and both layouts survive a portable backup round trip',()=>{
  const backup=sample(),tile=D.tile('energyBalance','comparison-test');
  tile.unit='kWh';tile.type='number';tile.title='Mein Energievergleich';tile.period={...backup.dashboard.energyPeriod};
  tile.layouts={compact:{x:2,y:24,w:8,h:6},wide:{x:4,y:30,w:4,h:4}};
  backup.dashboard.tiles.push(tile);
  const restored=C.normalize(JSON.parse(JSON.stringify(backup)));
  assert.deepEqual(restored.dashboard.tiles.at(-1),tile);assert.deepEqual(restored.dashboard.tiles.slice(0,-1),backup.dashboard.tiles.slice(0,-1));
});

test('all overview periods and independent history heights survive portable backup round trips',()=>{
  for(const miniRange of ['sunrise','today','last6h','last24h','yesterday','week','month']){
    const backup=sample();backup.settings.miniRange=miniRange;backup.dashboard.historyHeight={compact:450,wide:700};
    const restored=C.normalize(JSON.parse(JSON.stringify(backup)));assert.equal(restored.settings.miniRange,miniRange);assert.deepEqual(restored.dashboard.historyHeight,{compact:450,wide:700});
  }
  const backup=sample();backup.settings.miniRange='unknown';assert.throws(()=>C.normalize(backup));
});

test('solar geometry, directions, shade and voluntary seasons transfer without activating a mobile module or copying learning',()=>{
 const backup=sample();backup.equipment.groups[0].tilt=45;backup.equipment.groups.push({...backup.equipment.groups[0],id:'case',placement:'mobile',measurement:'shunt'});
 backup.solarSetup={version:1,usageMode:'manual',manual:[100,null,200,300],groups:{'test-group':{active:true,azimuth:157.5,shade:[0,1,2]},case:{active:true,azimuth:180,shade:[0,0,0]}},observations:['PRIVATE_SENTINEL'],forecasts:['PRIVATE_SENTINEL']};
 const clean=C.normalize(backup);assert.equal(clean.equipment.groups[0].tilt,45);assert.equal(clean.solarSetup.groups['test-group'].azimuth,157.5);assert.deepEqual(clean.solarSetup.manual,[100,null,200,300]);assert.equal(clean.solarSetup.groups.case.active,false);assert.ok(!JSON.stringify(clean).includes('PRIVATE_SENTINEL'));assert.deepEqual(C.normalize(clean),clean);
});
test('old backups upgrade to an unknown current setup without inventing mounting angles or consumption',()=>{
 const backup=sample();delete backup.solarSetup;delete backup.equipment.groups[0].tilt;const clean=C.normalize(backup);assert.equal(clean.equipment.groups[0].tilt,null);assert.equal(clean.solarSetup.groups['test-group'].azimuth,null);assert.deepEqual(clean.solarSetup.manual,[null,null,null,null]);
});
test('roller block size and order round trip without including connection details',()=>{
 const b=sample();b.dashboard.blockOrder=['roller','tiles','history','record'];b.dashboard.rollerLayout={compact:{width:9,height:450},wide:{width:6,height:800}};
 const restored=C.normalize(JSON.parse(JSON.stringify(b)));assert.deepEqual(restored.dashboard.rollerLayout,b.dashboard.rollerLayout);assert.deepEqual(restored.dashboard.blockOrder,b.dashboard.blockOrder);assert.equal(JSON.stringify(restored).includes('AA:BB'),false);
});
