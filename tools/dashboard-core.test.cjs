const {test}=require('node:test');
const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/dashboard-core.js');

test('runtime can be displayed in seconds, hours and days without changing the Shunt value',()=>{
  assert.equal(C.convert(480,'h','remaining'),8);
  assert.equal(C.convert(480,'d','remaining'),1/3);
  assert.equal(C.convert(480,'s','remaining'),28800);
  assert.equal(C.convert(120,'min','shuntAge'),2);
  assert.equal(C.unconvert(32,'°F','temperature'),0);
  assert.equal(C.convert(100,'°F','temperature'),212);
  assert.equal(C.convert(0,'kW','power'),0);
  assert.equal(C.convert(null,'kW','power'),null);
});
test('capacity and energy respect the configured battery size and missing voltage',()=>{
  const snapshot={settings:{capacity:150},devices:[],readings:[]};
  assert.ok(Math.abs(C.liveValue('remainingAh',{soc:76.4},null,snapshot)-114.6)<1e-8);
  assert.equal(C.liveValue('remainingWh',{soc:50,voltage:13},null,snapshot),975);
  assert.equal(C.liveValue('remainingWh',{soc:50,voltage:null},null,snapshot),null);
  assert.equal(C.liveValue('temperature',{temperature:null},null,snapshot),null);
});
test('customization preserves intentional overlaps and other tile positions after a reload',()=>{
  const settings=C.defaults(),before=structuredClone(settings.tiles[1].layouts);
  settings.tiles[0].layouts.compact={x:6,y:3,w:6,h:6};
  const result=C.normalize(JSON.parse(JSON.stringify(settings)));
  assert.deepEqual(result.tiles[0].layouts.compact,{x:6,y:3,w:6,h:6});
  assert.deepEqual(result.tiles[1].layouts,before);
  assert.deepEqual(C.normalize({version:1,tiles:[]}).tiles,[]);
});
test('an unavailable or malformed configuration cannot create invalid scales or executable style values',()=>{
  const t=C.tile('power');t.min=NaN;t.max=-Infinity;t.background='url(https://example.com)';t.color='red;display:none';t.layouts.compact={x:999,y:-1,w:100,h:0};
  const result=C.normalize({tiles:[t]});const saved=result.tiles[0];
  assert.equal(saved.background,'auto');assert.equal(saved.color,C.CATALOG.power.color);
  assert.ok(Number.isFinite(saved.min)&&saved.max>saved.min);
  assert.equal(saved.layouts.compact.x,0);assert.equal(saved.layouts.compact.w,12);assert.equal(saved.layouts.compact.y,0);
});
test('zero readings stay numeric and unavailable readings are never substituted with zero',()=>{
  assert.equal(C.format(0,{metric:'current',digits:2}),'0,00');
  assert.equal(C.format(null,{metric:'current',digits:2}),'—');
  assert.equal(C.liveValue('consumed',{consumed:-23.6},null,{settings:{capacity:100}}),23.6);
});
test('tile windows follow local midnight and a stored reset timestamp',()=>{
  const t=C.tile('power'),now=new Date(2026,9,15,12,0).getTime();t.period.mode='today';
  assert.deepEqual(C.windowFor(t,now),{from:new Date(2026,9,15).getTime(),to:now});
  t.period.mode='reset';t.period.resetAt=now-10000;assert.equal(C.windowFor(t,now).from,now-10000);
  t.period.mode='custom';t.period.from=now+100;t.period.to=now+1000;assert.deepEqual(C.windowFor(t,now),{from:now,to:now});
});
