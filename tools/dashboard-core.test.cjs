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
  for(const [metric,m] of Object.entries(C.CATALOG).filter(([,m])=>!m.status&&!m.liveOnly&&!m.widget)){
    const tile=C.tile(metric);tile.scaleMode='auto';
    for(const value of [0,3]){const r=C.chartRange(tile,[{v:value}]);assert.ok(Number.isFinite(r.min)&&Number.isFinite(r.max)&&r.max>r.min,metric);}
  }
});
test('current chart values are independent of the stored window and aggregation',()=>{
  const t=C.tile('solar');t.type='chart';t.period.mode='today';t.period.aggregate='max';t.valueMode='live';
  const snapshot={settings:{capacity:100}},summary={last:3,max:22},now=Date.now(),window=C.windowFor(t,now);
  assert.equal(C.displayValue(t,summary,null,{solar:7},snapshot),7);
  assert.equal(C.displayValue(t,summary,null,{solar:0},snapshot),0);
  assert.equal(C.displayValue(t,summary,null,null,snapshot),null);
  assert.equal(C.displayValue(t,{},null,{solar:7},snapshot),7);
  assert.deepEqual(C.windowFor(t,now),window);
  t.valueMode='history';assert.equal(C.displayValue(t,summary,null,{solar:7},snapshot),22);
});
test('live selection is saved without migrating historical tile choices',()=>{
  const old=C.tile('solar');delete old.valueMode;old.type='chart';old.period.mode='today';old.period.aggregate='last';
  const saved=C.normalize({tiles:[old]}).tiles[0];assert.equal(saved.valueMode,'history');assert.equal(saved.period.aggregate,'last');
  saved.valueMode='live';assert.equal(C.normalize(JSON.parse(JSON.stringify({tiles:[saved]}))).tiles[0].valueMode,'live');
});
test('all numeric charts use their own current field and unit',()=>{
  const snapshot={settings:{capacity:150},devices:[],readings:[]};
  for(const [metric,reading,expected] of [['solar',{solar:7},7],['current',{current:-.2},-.2],['voltage',{voltage:13.15},13.15],['soc',{soc:87},87],['remainingAh',{soc:50},75]]){
    const t=C.tile(metric);t.type='chart';t.valueMode='live';t.period.mode='today';
    const shunt=C.CATALOG[metric].kind==='shunt';assert.equal(C.displayValue(t,{last:999},shunt?reading:null,shunt?null:reading,snapshot),expected);
  }
});
test('calculated energy is a historical total and cannot pretend to be live',()=>{
  for(const metric of ['solarEnergy','dischargeEnergy']){
    const t=C.tile(metric);t.valueMode='live';t.period.mode='live';t.period.aggregate='avg';
    const saved=C.normalize({tiles:[t]}).tiles[0];assert.equal(saved.period.mode,'today');assert.equal(saved.period.aggregate,'last');assert.equal(C.usesLiveValue(saved),false);
    assert.equal(C.displayValue(saved,{last:3.27},null,null,{settings:{capacity:100}}),metric==='dischargeEnergy'?-3.27:3.27);
  }
});
test('overview block order preserves all blocks once and migrates old dashboards',()=>{
  const input=C.defaults();delete input.blockOrder;assert.deepEqual(C.normalize(input).blockOrder,['tiles','history','record']);
  input.blockOrder=['record','history','record','invalid','weather'];assert.deepEqual(C.normalize(input).blockOrder,['record','history','tiles']);
  assert.deepEqual(C.normalize(JSON.parse(JSON.stringify(C.normalize(input)))).blockOrder,['record','history','tiles']);
});
test('sunrise range uses the selected location date and falls back visibly for missing or future sunrise',()=>{
  const now=Date.parse('2026-10-17T10:00:00Z'),rise=Date.parse('2026-10-17T05:48:00Z'),weather={timezone:'Europe/Berlin',days:[{day:'2026-10-17',sunrise:rise}]};
  assert.equal(C.miniWindow('sunrise',weather,now).from,rise);
  assert.equal(C.miniWindow('sunrise',weather,now).label,'Seit Sonnenaufgang');
  assert.equal(C.miniWindow('last6h',weather,now).from,now-6*3600000);
  assert.equal(C.miniWindow('today',weather,now).from,new Date(new Date(now).setHours(0,0,0,0)).getTime());
  assert.match(C.miniWindow('sunrise',{},now).note,/nicht verfügbar/);
  assert.match(C.miniWindow('sunrise',weather,rise-1).note,/noch bevor/);
  assert.match(C.miniWindow('sunrise',{...weather,days:[{day:'2026-10-16',sunrise:rise-86400000}]},now).note,/nicht verfügbar/);
  assert.match(C.miniWindow('sunrise',{timezone:'invalid'},now).note,/nicht verfügbar/);
});
test('readable numbers handle shorthand white, custom dark surfaces and transparency',()=>{
  const luminance=hex=>[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)/255).map(c=>c<=.04045?c/12.92:((c+.055)/1.055)**2.4).reduce((v,c,i)=>v+c*[.2126,.7152,.0722][i],0);
  const contrast=(a,b)=>(Math.max(luminance(a),luminance(b))+.05)/(Math.min(luminance(a),luminance(b))+.05);
  for(const fg of ['#e8bb57','#65cbb0','#81baf4','#ffffff','#000000']){
    assert.ok(contrast(C.readableColor(fg,'#fff','#f4f6f9'), '#ffffff')>=7);
    assert.ok(contrast(C.readableColor(fg,'#18303b','#f4f6f9'), '#18303b')>=7);
    assert.ok(contrast(C.readableColor(fg,'#18303b','#f4f6f9',0), '#f4f6f9')>=7);
  }
  assert.notEqual(C.readableColor('#e8bb57','#fff','#f4f6f9'),'#ffffff');
});
test('the standard overview follows the requested seven-tile arrangement',()=>{
  const board=C.defaults();
  assert.deepEqual(board.tiles.filter(t=>t.metric!=='weather').map(t=>t.metric),['soc','solar','dischargeEnergy','solarEnergy','voltage','remaining','power']);
  assert.deepEqual(board.tiles.filter(t=>t.metric!=='weather').map(t=>t.layouts.compact),[{x:0,y:0,w:6,h:6},{x:6,y:0,w:6,h:5},{x:0,y:6,w:6,h:5},{x:6,y:5,w:6,h:6},{x:0,y:11,w:4,h:4},{x:4,y:11,w:4,h:4},{x:8,y:11,w:4,h:4}]);
  assert.equal(board.tiles[1].valueMode,'live');assert.equal(board.tiles[1].scaleMode,'auto');
});
test('sunrise tile periods survive reload without changing customized layouts or live selection',()=>{
  const board=C.defaults();board.blockOrder=['history','tiles','record'];board.tiles[0].layouts.compact={x:2,y:17,w:8,h:6};
  for(const tile of board.tiles.filter(t=>['solar','solarEnergy','dischargeEnergy'].includes(t.metric)))tile.period.mode='sunrise';
  const saved=C.normalize(JSON.parse(JSON.stringify(board)));
  assert.deepEqual(saved,board);
});
test('power and calculated-energy tiles query the same sunrise boundary as the mini chart',()=>{
  const now=Date.parse('2026-10-17T11:00:00Z'),rise=Date.parse('2026-10-17T05:48:00Z'),weather={timezone:'Europe/Berlin',days:[{day:'2026-10-17',sunrise:rise}]};
  for(const metric of ['solar','solarEnergy','dischargeEnergy']){
    const tile=C.tile(metric);tile.period.mode='sunrise';const w=C.windowFor(tile,now,weather);
    assert.equal(w.from,rise);assert.equal(w.to,now);assert.equal(w.label,'Seit Sonnenaufgang');
  }
});
test('a missing sunrise never silently becomes a live tile or uses yesterday',()=>{
  const tile=C.tile('solarEnergy');tile.period.mode='sunrise';const now=Date.parse('2026-10-18T11:00:00Z');
  const weather={timezone:'Europe/Berlin',days:[{day:'2026-10-17',sunrise:Date.parse('2026-10-17T05:48:00Z')}]},w=C.windowFor(tile,now,weather);
  assert.equal(w.label,'Heute');assert.equal(w.from,new Date(new Date(now).setHours(0,0,0,0)).getTime());assert.match(w.note,/nicht verfügbar/);
  assert.equal(C.usesLiveValue(tile),false);assert.equal(tile.period.mode,'sunrise');
});

test('reversed energy totals and charts retain zero and unavailable values without changing source data',()=>{
  const snapshot={settings:{capacity:100}},points=[{v:0,min:0,max:0},{v:5,min:3,max:5}];
  for(const metric of ['solarEnergy','dischargeEnergy']){
    const t=C.tile(metric);t.reverseSign=true;
    assert.equal(C.displayValue(t,{last:5},null,null,snapshot),-5);
    assert.equal(C.displayValue(t,{last:0},null,null,snapshot),0);
    assert.equal(C.displayValue(t,{last:null},null,null,snapshot),null);
    assert.equal(C.signedValue(t,null),null);
    assert.deepEqual(C.chartRange(t,points,100,{min:0,max:5}),{min:-5.5,max:0,auto:true});
    assert.ok(C.chartRange(t,[{v:0}],100,{min:0,max:0}).max>C.chartRange(t,[{v:0}],100,{min:0,max:0}).min);
    t.unit='kWh';assert.deepEqual(C.chartRange(t,points,100,{min:0,max:5}),{min:-.0055,max:0,auto:true});
  }
  assert.deepEqual(points,[{v:0,min:0,max:0},{v:5,min:3,max:5}]);
});
test('sign reversal survives reload and reflects fixed bounds while existing and non-energy tiles keep their values',()=>{
  const t=C.tile('dischargeEnergy');t.reverseSign=true;t.unit='kWh';t.scaleMode='fixed';
  const restored=C.normalize(JSON.parse(JSON.stringify({tiles:[t]}))).tiles[0];
  assert.equal(restored.reverseSign,true);assert.deepEqual(C.fixedRange(restored),{min:-2,max:0});
  assert.deepEqual(C.chartRange(restored,[{v:5}]),{min:-2,max:0,auto:false});
  assert.equal(restored.min,0);assert.equal(restored.max,2000);
  delete t.reverseSign;assert.equal(C.normalize({tiles:[t]}).tiles[0].reverseSign,true);
  const current=C.tile('current');current.reverseSign=true;
  const unchanged=C.normalize({tiles:[current]}).tiles[0];assert.equal(unchanged.reverseSign,false);assert.equal(C.signedValue(unchanged,-8.4),-8.4);
});

test('legacy weather blocks become one compact tile without moving existing layouts',()=>{
  const board=C.defaults();board.tiles=board.tiles.filter(t=>t.metric!=='weather');board.blockOrder=['record','weather','tiles','history'];board.tiles[0].layouts.compact={x:3,y:21,w:6,h:6};
  const before=structuredClone(board.tiles),migrated=C.normalize(board),weather=migrated.tiles.at(-1);
  assert.deepEqual(migrated.tiles.slice(0,-1),before);assert.equal(weather.metric,'weather');
  assert.deepEqual(weather.layouts.compact,{x:0,y:27,w:12,h:4});assert.deepEqual(weather.layouts.wide,{x:0,y:15,w:12,h:4});
  assert.deepEqual(migrated.blockOrder,['record','tiles','history']);assert.deepEqual(C.normalize(JSON.parse(JSON.stringify(migrated))),migrated);
  assert.equal(C.windowFor(weather),null);
});
test('weather removal remains intentional and duplicate widgets or undersized layouts cannot break the board',()=>{
  const board=C.defaults();board.tiles=board.tiles.filter(t=>t.metric!=='weather');assert.equal(C.normalize(board).tiles.some(t=>t.metric==='weather'),false);
  const w=C.tile('weather');w.layouts.compact={x:10,y:2,w:2,h:2};board.tiles.push(w,{...w,id:'another-weather'});
  const result=C.normalize(board).tiles.filter(t=>t.metric==='weather');assert.equal(result.length,1);assert.deepEqual(result[0].layouts.compact,{x:8,y:2,w:4,h:4});
});

test('battery discharge is negative immediately for new and upgraded tiles, while a later explicit choice survives',()=>{
  assert.equal(C.tile('dischargeEnergy').reverseSign,true);
  const old=C.defaults();delete old.dischargeSignVersion;const t=old.tiles.find(t=>t.metric==='dischargeEnergy');t.reverseSign=false;
  const layouts=structuredClone(old.tiles.map(t=>t.layouts)),upgraded=C.normalize(old);
  assert.equal(upgraded.tiles.find(t=>t.metric==='dischargeEnergy').reverseSign,true);assert.deepEqual(upgraded.tiles.map(t=>t.layouts),layouts);
  upgraded.tiles.find(t=>t.metric==='dischargeEnergy').reverseSign=false;
  assert.equal(C.normalize(JSON.parse(JSON.stringify(upgraded))).tiles.find(t=>t.metric==='dischargeEnergy').reverseSign,false);
});
