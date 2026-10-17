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
test('auto range is optional and preserves the saved fixed bounds after reload',()=>{
  const old=C.tile('solar');delete old.scaleMode;old.max=200;
  assert.equal(C.normalize({tiles:[old]}).tiles[0].scaleMode,'fixed');
  old.scaleMode='auto';const t=C.normalize(JSON.parse(JSON.stringify({tiles:[old]}))).tiles[0];
  assert.equal(t.scaleMode,'auto');assert.equal(t.max,200);
  const auto=C.chartRange(t,[{v:3,max:6,min:0}]);assert.equal(auto.min,0);assert.equal(auto.max,6.6);assert.equal(t.max,200);
  t.scaleMode='fixed';assert.deepEqual(C.chartRange(t,[{v:3}]),{min:0,max:200,auto:false});
});
test('auto range includes original peaks even when the displayed value is an average',()=>{
  const t=C.tile('solar');t.scaleMode='auto';t.period.aggregate='avg';
  const r=C.chartRange(t,[{v:3,min:1,max:6}],100,{min:0,max:22,avg:3});
  assert.equal(r.min,0);assert.ok(r.max>=22*1.1&&r.max<30);
  assert.equal(C.chartRange(t,[{v:3,max:6}]).max,6.6);
});
test('automatic range and its labels remain meaningful after switching W to kW',()=>{
  const t=C.tile('solar');t.scaleMode='auto';const watts=C.chartRange(t,[{v:3,max:6}]);
  t.unit='kW';const kilowatts=C.chartRange(t,[{v:3,max:6}]);
  assert.equal(kilowatts.max,watts.max/1000);assert.equal(C.axisLabel(kilowatts.max,kilowatts.max-kilowatts.min),'0,0066');
});
test('signed flow charts retain zero and both charging and discharging extrema',()=>{
  const t=C.tile('power');t.scaleMode='auto';
  const r=C.chartRange(t,[{v:-3,min:-109,max:22}]);assert.ok(r.min<-109&&r.max>22);
  const negative=C.chartRange(t,[{v:-3,min:-109,max:-1}]);assert.ok(negative.min<-109);assert.equal(negative.max,0);
});
test('small changes in voltage, state of charge and temperature fill the chart',()=>{
  for(const [metric,a,b] of [['voltage',13.1,13.15],['soc',89,92],['temperature',-5,-4]]){
    const t=C.tile(metric);t.scaleMode='auto';const r=C.chartRange(t,[{v:a},{v:b}]);
    assert.ok(r.min<a&&r.max>b);assert.ok(r.max-r.min<(b-a)*1.5);
  }
  const t=C.tile('soc');t.scaleMode='auto';const full=C.chartRange(t,[{v:100}]);assert.ok(full.min<100);assert.equal(full.max,100);
});
test('zero, flat values and missing readings never produce a degenerate scale or invented samples',()=>{
  const t=C.tile('solar');t.scaleMode='auto';
  assert.deepEqual(C.chartRange(t,[{v:0}]),{min:0,max:1,auto:true});
  assert.deepEqual(C.chartRange(t,[{v:null},{v:NaN},{v:Infinity}]),{min:0,max:300,auto:false});
  for(const [metric,m] of Object.entries(C.CATALOG).filter(([,m])=>!m.status&&!m.liveOnly)){
    const tile=C.tile(metric);tile.scaleMode='auto';
    for(const value of [0,3]){const r=C.chartRange(tile,[{v:value}]);assert.ok(Number.isFinite(r.min)&&Number.isFinite(r.max)&&r.max>r.min,metric);}
  }
});
