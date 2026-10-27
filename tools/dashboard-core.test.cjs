const {test}=require('node:test');
const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/dashboard-core.js');

test('the plant banner migrates once into a movable singleton without shifting existing tiles',()=>{
  const old=C.defaults();old.tiles=old.tiles.filter(t=>t.metric!=='lan');delete old.lanTileVersion;
  old.tiles[0].layouts.compact={x:2,y:33,w:5,h:7};const before=structuredClone(old.tiles),upgraded=C.normalize(old);
  assert.deepEqual(upgraded.tiles.slice(0,-1),before);const lan=upgraded.tiles.at(-1);assert.equal(lan.metric,'lan');assert.equal(lan.type,'lan');assert.equal(lan.layouts.compact.y,40);assert.equal(C.windowFor(lan),null);
  lan.layouts.compact={x:4,y:6,w:8,h:5};assert.deepEqual(C.normalize(JSON.parse(JSON.stringify(upgraded))),upgraded);
  upgraded.tiles=upgraded.tiles.filter(t=>t.metric!=='lan');assert.equal(C.normalize(upgraded).tiles.some(t=>t.metric==='lan'),false);
});
test('a plant widget cannot be duplicated or shrunk until its controls become inaccessible',()=>{
  const board=C.defaults(),lan=board.tiles.find(t=>t.metric==='lan');lan.layouts.compact={x:10,y:3,w:2,h:2};board.tiles.push({...lan,id:'duplicate-lan'});
  const tiles=C.normalize(board).tiles.filter(t=>t.metric==='lan');assert.equal(tiles.length,1);assert.deepEqual(tiles[0].layouts.compact,{x:8,y:3,w:4,h:4});
});

test('automatic battery time starts in the observed direction and uses the Shunt runtime independently of charge inputs',()=>{
  const t=1000000,r={device:'a',t,current:10,soc:70,ttg:480};
  const charging=new C.BatteryTimeEstimator().update(r,100,t);assert.equal(charging.minutes,180);assert.equal(charging.mode,'charge');assert.equal(C.tileLabel(C.tile('chargeTime'),charging),'Restladezeit');
  const discharging=new C.BatteryTimeEstimator().update({...r,current:-2,soc:null},0,t);assert.equal(discharging.minutes,480);assert.equal(discharging.mode,'discharge');assert.equal(C.tileLabel(C.tile('chargeTime'),discharging),'Restlaufzeit');
  assert.equal(C.displayValue(C.tile('chargeTime'),null,r,null,{settings:{capacity:100}},discharging),480);
  assert.equal(C.liveValue('remaining',r,null,{settings:{capacity:100}},t),480);
});
test('a short reversal hides the incompatible forecast without flipping the confirmed time label',()=>{
  const e=new C.BatteryTimeEstimator(),t=1000000,read=(s,current)=>e.update({device:'a',t:t+s*1000,current,soc:70,ttg:480},100,t+s*1000);
  for(let s=0;s<=60;s+=10)read(s,10);
  for(const s of [70,80]){const r=read(s,-10);assert.equal(r.mode,'charge');assert.equal(r.minutes,null);assert.equal(r.state,'changing');}
  const resumed=read(90,10);assert.equal(resumed.mode,'charge');assert.equal(resumed.state,'charging');assert.ok(resumed.minutes>180);
});
test('sustained reversed current switches once after a confirmed trend and displays only a real Shunt time',()=>{
  const e=new C.BatteryTimeEstimator(),t=1000000;let result,flips=0,previous='charge';
  const read=(s,current,ttg=480)=>e.update({device:'a',t:t+s*1000,current,soc:70,ttg},100,t+s*1000);
  for(let s=0;s<=60;s+=10)read(s,10);
  for(let s=70;s<=180;s+=10){result=read(s,-10);if(result.mode!==previous){flips++;previous=result.mode;assert.ok(s>=100);}if(s<=90)assert.equal(result.mode,'charge');}
  assert.equal(flips,1);assert.equal(result.mode,'discharge');assert.equal(result.minutes,480);
  assert.equal(read(190,-10,null).minutes,null);assert.equal(read(200,-10,-1).minutes,null);
  assert.equal(read(210,-10,0).minutes,0);assert.equal(read(220,-10,Infinity).state,'runtimeUnavailable');
});
test('polling the same packet cannot supply the fresh confirmation needed for a time-mode switch',()=>{
  const e=new C.BatteryTimeEstimator(),t=1000000;
  e.update({t,current:-10,soc:70,ttg:480},100,t);
  e.update({t:t+30000,current:10,soc:70,ttg:480},100,t+30000);
  const packet={t:t+40000,current:10,soc:70,ttg:480};e.update(packet,100,t+40000);
  const held=e.update(packet,100,t+70000);assert.equal(held.mode,'discharge');assert.equal(held.minutes,null);assert.equal(e.samples.length,3);
  const fresh=e.update({...packet,t:t+70000},100,t+70000);assert.equal(fresh.mode,'charge');assert.equal(fresh.minutes,180);
});
test('a newly confirmed charge estimate excludes the previous discharge phase',()=>{
  const e=new C.BatteryTimeEstimator(),t=1000000;let result;
  for(let s=0;s<=300;s+=10)e.update({device:'a',t:t+s*1000,current:-20,soc:70,ttg:90},100,t+s*1000);
  for(let s=310;s<=420;s+=10)result=e.update({device:'a',t:t+s*1000,current:2,soc:70,ttg:null},100,t+s*1000);
  assert.equal(result.mode,'charge');assert.equal(result.average,2);assert.equal(result.minutes,900);
});
test('balanced current has no time forecast and a full charge is reported only in a charging phase',()=>{
  const e=new C.BatteryTimeEstimator(),t=1000000;let result=e.update({t,current:0,soc:100,ttg:480},100,t);
  assert.equal(result.mode,'idle');assert.equal(result.minutes,null);assert.equal(C.chargeHint(result),'Zeit derzeit nicht bestimmbar');assert.equal(C.tileLabel(C.tile('chargeTime'),result),'Akkuzeit');
  for(let s=10;s<=50;s+=10)result=e.update({t:t+s*1000,current:10,soc:100,ttg:null},100,t+s*1000);
  assert.equal(result.mode,'charge');assert.equal(result.minutes,0);assert.equal(result.state,'full');
  for(let s=60;s<=100;s+=10)result=e.update({t:t+s*1000,current:0,soc:100,ttg:480},100,t+s*1000);
  assert.equal(result.mode,'idle');assert.equal(result.minutes,null);
});
test('stale values, device changes and receiver gaps discard both the time mode and its pending transition',()=>{
  const t=1000000;
  for(const next of [{device:'b',t:t+10000,current:-2,soc:70,ttg:120},{device:'a',t:t+46000,current:-2,soc:70,ttg:120},{device:'a',t:t-1000,current:-2,soc:70,ttg:120}]){
    const e=new C.BatteryTimeEstimator();e.update({device:'a',t,current:10,soc:70,ttg:480},100,t);
    assert.equal(e.update(next,100,next.t).minutes,120);assert.equal(e.samples.length,1);
    const lost=e.update(next,100,next.t+45001);assert.equal(lost.mode,null);assert.equal(lost.minutes,null);assert.equal(e.samples.length,0);
  }
});
test('automatic time labels respect custom captions and missing charge or runtime inputs stay unavailable',()=>{
  const tile=C.tile('chargeTime');tile.title='Meine Akkuzeit';const t=1000000;
  for(const current of [10,-10]){const r=new C.BatteryTimeEstimator().update({t,current,soc:null,ttg:null},100,t);assert.equal(r.minutes,null);assert.equal(C.tileLabel(tile,r),'Meine Akkuzeit');}
  const board=C.defaults();board.tiles=board.tiles.slice(0,8);board.tiles[0].layouts.compact={x:1,y:3,w:5,h:4};board.tiles[0].layouts.wide={x:2,y:9,w:4,h:5};board.historyHeight={compact:410,wide:520};
  const restored=C.normalize(JSON.parse(JSON.stringify(board)));assert.deepEqual(restored,board);
});

test('charge time uses configured Ah, state of charge and net Shunt current, independent of solar watts',()=>{
  const estimate=new C.ChargeEstimator(),now=1000000,r={device:'shunt-a',t:now,soc:70,current:10},before=structuredClone(r);
  const result=estimate.update(r,100,now);assert.equal(result.minutes,180);assert.equal(result.state,'charging');
  assert.equal(estimate.update(r,200,now).minutes,360);
  const tile=C.tile('chargeTime'),snapshot={settings:{capacity:100}};
  assert.equal(C.displayValue(tile,null,r,{solar:0},snapshot,result),180);
  assert.equal(C.displayValue(tile,null,r,{solar:999},snapshot,result),180);
  assert.equal(C.convert(result.minutes,'h','chargeTime'),3);assert.equal(C.convert(result.minutes,'s','chargeTime'),10800);
  assert.deepEqual(r,before);
});
test('charge smoothing weights elapsed time and repeated UI polls cannot overweight a reading',()=>{
  const estimate=new C.ChargeEstimator(),t=1000000;
  estimate.update({device:'a',t,soc:70,current:0},100,t);
  const r={device:'a',t:t+30000,soc:70,current:10};estimate.update(r,100,t+30000);
  for(let i=31;i<=60;i++)estimate.update(r,100,t+i*1000);
  const result=estimate.update(r,100,t+60000);
  // 30 seconds ramping from 0 to 10 A, then 30 seconds at 10 A: 7.5 A.
  assert.equal(result.average,7.5);assert.equal(result.minutes,240);assert.equal(result.seconds,60);
  assert.equal(estimate.samples.length,2);
});
test('the five-minute charge window clips its leading interval and discards old samples',()=>{
  const estimate=new C.ChargeEstimator(),t=1000000;
  estimate.update({device:'a',t,soc:70,current:10},100,t);
  for(let i=1;i<=10;i++)estimate.update({device:'a',t:t+i*30000,soc:70,current:20},100,t+i*30000);
  const result=estimate.update({device:'a',t:t+300000,soc:70,current:20},100,t+315000);
  assert.equal(result.seconds,300);assert.equal(result.average,19.875);
  for(let i=11;i<=40;i++)estimate.update({device:'a',t:t+i*30000,soc:70,current:10},100,t+i*30000);
  assert.equal(estimate.update({device:'a',t:t+1200000,soc:70,current:10},100,t+1200000).minutes,180);
  assert.ok(estimate.samples.length<=12);
});
test('discharge and zero current suppress charging estimates immediately and negative intervals remain in the average',()=>{
  const e=new C.ChargeEstimator(),t=1000000;
  let result=e.update({t,soc:70,current:-10},100,t);assert.equal(result.state,'inactive');assert.equal(result.minutes,null);
  result=e.update({t:t+30000,soc:70,current:2},100,t+30000);assert.equal(result.state,'waiting');assert.equal(result.average,-4);assert.equal(result.minutes,null);
  result=e.update({t:t+60000,soc:70,current:0},100,t+60000);assert.equal(result.state,'inactive');assert.equal(C.chargeHint(result),'Wird nicht geladen');
  result=e.update({t:t+90000,soc:70,current:20},100,t+90000);assert.equal(result.state,'charging');assert.ok(result.average<20);
  assert.equal(new C.ChargeEstimator().update({t,soc:100,current:0},100,t).minutes,0);
});
test('missing, stale, future or invalid charge inputs never become zero or a previous estimate',()=>{
  const e=new C.ChargeEstimator(),t=1000000,good={t,soc:70,current:10};
  for(const [reading,capacity,now] of [[null,100,t],[{...good,soc:null},100,t],[{...good,current:null},100,t],[{...good,soc:101},100,t],[{...good,soc:-1},100,t],[good,0,t],[good,null,t],[good,NaN,t],[good,100,t+45001],[good,100,t-1],[{...good,t:null},100,t]]){
    e.update(good,100,t);const result=e.update(reading,capacity,now);
    assert.equal(result.minutes,null);assert.equal(result.state,'unavailable');assert.equal(e.samples.length,0);
  }
});
test('charge averaging starts fresh after receiver gaps, device changes, pauses or clock reversals',()=>{
  const t=1000000;
  for(const next of [{device:'b',t:t+10000,soc:70,current:5},{device:'a',t:t+46000,soc:70,current:5},{device:'a',t:t-1000,soc:70,current:5}]){
    const e=new C.ChargeEstimator();e.update({device:'a',t,soc:70,current:20},100,t);
    assert.equal(e.update(next,100,next.t).minutes,360);assert.equal(e.samples.length,1);
  }
  const e=new C.ChargeEstimator();e.update({t,soc:70,current:20},100,t);e.update(null,100,t+1);
  assert.equal(e.update({t:t+2000,soc:70,current:5},100,t+2000).minutes,360);
});
test('the optional charge tile preserves units and layout and offers live number or gauge only',()=>{
  const tile=C.tile('chargeTime');assert.equal(tile.unit,'h');assert.equal(tile.layouts.compact.h,4);
  tile.unit='min';tile.type='chart';tile.period.mode='today';
  const restored=C.normalize({energyFlowVersion:1,tiles:[tile]}).tiles[0];
  assert.equal(restored.unit,'min');assert.equal(restored.type,'number');assert.equal(restored.period.mode,'live');assert.equal(C.windowFor(restored),null);
  assert.equal(C.format(3,C.tile('chargeTime')),'≈ 3,0');assert.equal(C.format(0,C.tile('chargeTime')),'0,0');assert.equal(C.format(null,tile),'—');
  assert.equal(C.defaults().tiles.some(t=>t.metric==='chargeTime'),true);
});

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
  const result=C.normalize({energyFlowVersion:1,tiles:[t]});const saved=result.tiles[0];
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
    const saved=C.normalize({energyFlowVersion:1,tiles:[t]}).tiles[0];assert.equal(saved.period.mode,'today');assert.equal(saved.period.aggregate,'last');assert.equal(C.usesLiveValue(saved),false);
    assert.equal(C.displayValue(saved,{last:3.27},null,null,{settings:{capacity:100}}),metric==='dischargeEnergy'?-3.27:3.27);
  }
});
test('overview block order preserves all blocks once and migrates old dashboards',()=>{
  const input=C.defaults();delete input.blockOrder;assert.deepEqual(C.normalize(input).blockOrder,['tiles','history','record','roller']);
  input.blockOrder=['record','history','record','invalid','weather'];assert.deepEqual(C.normalize(input).blockOrder,['record','history','tiles','roller']);
  assert.deepEqual(C.normalize(JSON.parse(JSON.stringify(C.normalize(input)))).blockOrder,['record','history','tiles','roller']);
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
test('the 1.4.3 standard portrait overview matches the approved taller preview and preserves wide positions',()=>{
  const board=C.defaults();assert.equal(board.tiles.find(t=>t.metric==='solar').period.amount,3);
  assert.deepEqual(board.tiles.slice(0,10).map(t=>t.metric),['soc','solar','energyBalance','solarEnergy','voltage','power','weather','consumptionEnergy','state','chargeTime']);
  assert.deepEqual(board.tiles.slice(0,10).map(t=>t.layouts.compact),[{x:0,y:0,w:4,h:5},{x:4,y:0,w:4,h:7},{x:8,y:7,w:4,h:5.5},{x:8,y:0,w:4,h:7},{x:0,y:7.5,w:4,h:2.5},{x:0,y:10,w:4,h:2.5},{x:0,y:12.5,w:12,h:5},{x:4,y:7,w:4,h:5.5},{x:4,y:17.5,w:4,h:3.5},{x:0,y:5,w:4,h:2.5}]);
  assert.deepEqual(board.tiles.slice(0,8).map(t=>t.layouts.wide),[{x:0,y:0,w:2,h:4},{x:2,y:0,w:2,h:8},{x:7,y:0,w:3,h:8},{x:4,y:0,w:3,h:8},{x:0,y:4,w:2,h:2},{x:0,y:6,w:2,h:2},{x:0,y:8,w:12,h:4},{x:10,y:0,w:2,h:8}]);
  assert.deepEqual(board.historyHeight,{compact:465,wide:380});
  assert.equal(board.tiles[1].valueMode,'live');assert.equal(board.tiles[1].scaleMode,'auto');
});
test('sunrise tile periods survive reload without changing customized layouts or live selection',()=>{
  const board=C.defaults();board.blockOrder=['history','tiles','record','roller'];board.tiles[0].layouts.compact={x:2,y:17,w:8,h:6};
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
  const restored=C.normalize(JSON.parse(JSON.stringify({energyFlowVersion:1,tiles:[t]}))).tiles[0];
  assert.equal(restored.reverseSign,true);assert.deepEqual(C.fixedRange(restored),{min:-2,max:0});
  assert.deepEqual(C.chartRange(restored,[{v:5}]),{min:-2,max:0,auto:false});
  assert.equal(restored.min,0);assert.equal(restored.max,2000);
  delete t.reverseSign;assert.equal(C.normalize({energyFlowVersion:1,tiles:[t]}).tiles[0].reverseSign,true);
  const current=C.tile('current');current.reverseSign=true;
  const unchanged=C.normalize({tiles:[current]}).tiles[0];assert.equal(unchanged.reverseSign,false);assert.equal(C.signedValue(unchanged,-8.4),-8.4);
});

test('legacy weather blocks become one compact tile without moving existing layouts',()=>{
  const board=C.defaults();board.tiles=board.tiles.filter(t=>!['weather','state','chargeTime'].includes(t.metric));board.blockOrder=['record','weather','tiles','history'];board.tiles[0].layouts.compact={x:3,y:21,w:6,h:6};
  const before=structuredClone(board.tiles),migrated=C.normalize(board),weather=migrated.tiles.at(-1);
  assert.deepEqual(migrated.tiles.slice(0,-1),before);assert.equal(weather.metric,'weather');
  assert.deepEqual(weather.layouts.compact,{x:0,y:27,w:12,h:4});assert.deepEqual(weather.layouts.wide,{x:0,y:23,w:12,h:4});
  assert.deepEqual(migrated.blockOrder,['record','tiles','history','roller']);assert.deepEqual(C.normalize(JSON.parse(JSON.stringify(migrated))),migrated);
  assert.equal(C.windowFor(weather),null);
});
test('weather removal remains intentional and duplicate widgets or undersized layouts cannot break the board',()=>{
  const board=C.defaults();board.tiles=board.tiles.filter(t=>t.metric!=='weather');assert.equal(C.normalize(board).tiles.some(t=>t.metric==='weather'),false);
  const w=C.tile('weather');w.layouts.compact={x:10,y:2,w:2,h:2};board.tiles.push(w,{...w,id:'another-weather'});
  const result=C.normalize(board).tiles.filter(t=>t.metric==='weather');assert.equal(result.length,1);assert.deepEqual(result[0].layouts.compact,{x:8,y:2,w:4,h:4});
});

test('battery discharge is negative immediately for new and upgraded tiles, while a later explicit choice survives',()=>{
  assert.equal(C.tile('dischargeEnergy').reverseSign,true);
  const old=C.defaults();old.tiles.push(C.tile('dischargeEnergy','optional-discharge'));delete old.dischargeSignVersion;const t=old.tiles.find(t=>t.metric==='dischargeEnergy');t.reverseSign=false;
  const layouts=structuredClone(old.tiles.map(t=>t.layouts)),upgraded=C.normalize(old);
  assert.equal(upgraded.tiles.find(t=>t.metric==='dischargeEnergy').reverseSign,true);assert.deepEqual(upgraded.tiles.map(t=>t.layouts),layouts);
  upgraded.tiles.find(t=>t.metric==='dischargeEnergy').reverseSign=false;
  assert.equal(C.normalize(JSON.parse(JSON.stringify(upgraded))).tiles.find(t=>t.metric==='dischargeEnergy').reverseSign,false);
});

test('the energy balance uses the common-flow backend and migrates the old title without moving tiles',()=>{
 const board=C.defaults(),t=board.tiles.find(t=>t.metric==='energyBalance');
 assert.equal(C.CATALOG.energyBalance.backend,'flowBalanceEnergy');assert.equal(C.CATALOG.energyBalance.flow,'balance');assert.equal(C.CATALOG.energyBalance.comparison,undefined);
 t.title='Ertrag − Entnahme';t.unit='kWh';t.layouts.compact={x:.5,y:6.5,w:5.5,h:5.5};
 const before=structuredClone(t);
 const migrated=C.normalize(board).tiles.find(t=>t.metric==='energyBalance');
 assert.equal(migrated.title,'');assert.equal(migrated.unit,'kWh');assert.deepEqual(migrated.layouts,before.layouts);assert.deepEqual(migrated.period,before.period);
 t.title='Mein Akku';assert.equal(C.normalize(board).tiles.find(t=>t.metric==='energyBalance').title,'Mein Akku');
});

test('the Shunt energy balance tile renders signed totals and keeps zero, units and automatic chart bounds meaningful',()=>{
  const t=C.normalize({tiles:[C.tile('energyBalance')]}).tiles[0],snapshot={settings:{capacity:100}};
  for(const value of [-30,0,40,null])assert.equal(C.displayValue(t,{last:value},null,null,snapshot),value);
  const points=[{v:-30,min:-30,max:-30},{v:40,min:40,max:40}],range=C.chartRange(t,points);
  assert.ok(range.min<-30&&range.max>40);t.unit='kWh';
  assert.equal(C.convert(C.displayValue(t,{last:-30},null,null,snapshot),t.unit,t.metric),-.03);
  assert.equal(C.chartRange(t,points).min,range.min/1000);assert.equal(C.chartRange(t,points).max,range.max/1000);
  t.valueMode='live';t.period.mode='live';const restored=C.normalize({energyFlowVersion:1,tiles:[t]}).tiles[0];
  assert.equal(C.usesLiveValue(restored),false);assert.equal(restored.period.mode,'today');
});


test('upgrading the three energy tiles keeps both layouts and uses sunrise yield with full-day consumption',()=>{
  const old=C.defaults();delete old.energyFlowVersion;delete old.energyPeriod;
  const consumption=old.tiles.find(t=>t.metric==='consumptionEnergy');consumption.metric='dischargeEnergy';consumption.reverseSign=true;consumption.title='Batterieentnahme berechnet';
  consumption.layouts={compact:{x:.5,y:12.5,w:7.5,h:6},wide:{x:9.5,y:.5,w:2.5,h:8}};
  old.tiles.find(t=>t.metric==='energyBalance').title='Akkubilanz';
  const before=structuredClone(old),board=C.normalize(old),migrated=board.tiles.find(t=>t.id===consumption.id);
  assert.equal(migrated.metric,'consumptionEnergy');assert.equal(migrated.reverseSign,false);assert.equal(migrated.title,'');
  assert.deepEqual(board.tiles.map(t=>t.layouts),before.tiles.map(t=>t.layouts));assert.deepEqual(old,before);
  assert.equal(board.tiles.find(t=>t.metric==='solarEnergy').period.mode,'sunrise');
  for(const metric of ['consumptionEnergy','energyBalance'])assert.equal(board.tiles.find(t=>t.metric===metric).period.mode,'today');
  assert.equal(board.tiles.find(t=>t.metric==='energyBalance').title,'');assert.equal(board.energyFlowVersion,1);
  assert.deepEqual(C.normalize(JSON.parse(JSON.stringify(board))),board);
});
test('non-daily comparisons share a period while daily solar may start at sunrise',()=>{
  const board=C.defaults();board.energyPeriod={...board.energyPeriod,mode:'last',amount:3,unit:'h'};
  const saved=C.normalize(board);
  for(const t of saved.tiles.filter(t=>C.CATALOG[t.metric].flow))assert.deepEqual(t.period,saved.energyPeriod);
  saved.energyPeriod.mode='today';saved.tiles.find(t=>t.metric==='solarEnergy').period.mode='sunrise';
  const daily=C.normalize(saved);assert.equal(daily.tiles.find(t=>t.metric==='solarEnergy').period.mode,'sunrise');
  assert.equal(daily.tiles.find(t=>t.metric==='consumptionEnergy').period.mode,'today');
  const now=Date.parse('2026-10-18T02:00:00Z'),rise=Date.parse('2026-10-18T05:45:00Z');
  assert.equal(C.miniWindow('sunrise',{timezone:'Europe/Berlin',days:[{day:'2026-10-18',sunrise:rise}]},now).solarFrom,rise);
});

test('sunset caps the solar axis while the query still includes the entire day',()=>{
 const now=Date.parse('2026-10-18T20:00:00Z'),rise=Date.parse('2026-10-18T05:45:00Z'),set=Date.parse('2026-10-18T16:15:00Z');
 const weather={timezone:'Europe/Berlin',days:[{day:'2026-10-18',sunrise:rise,sunset:set}]},w=C.miniWindow('sunrise',weather,now);
 assert.equal(w.from,rise);assert.equal(w.to,set);assert.equal(w.solarTo,set);
 assert.equal(C.solarQueryWindow(w).to,now);assert.ok(C.solarQueryWindow(w).from<rise);
 const early=rise-60000,late=set+60000;
 const extended=C.solarPlotWindow(w,[{t:early,v:10,kind:'solar'},{t:late,v:5,kind:'solar'},{t:now,v:-50,kind:'shunt'}]);
 assert.equal(extended.from,early);assert.equal(extended.to,late);
 assert.equal(C.solarPlotWindow(w,[{t:now,v:0,kind:'solar'}]).to,set);
 assert.equal(C.solarPlotWindow(w,[{t:now,v:NaN,kind:'solar'}]).to,set);
 assert.equal(C.miniWindow('today',weather,now).to,now);
});
test('missing sunset uses a labeled fallback and unrelated custom windows keep their bounds',()=>{
 const now=Date.parse('2026-10-18T20:00:00Z'),rise=Date.parse('2026-10-18T05:45:00Z');
 for(const sunset of [null,0,NaN,rise-1,Date.parse('2026-10-19T16:15:00Z')]){
  const w=C.miniWindow('sunrise',{timezone:'Europe/Berlin',days:[{day:'2026-10-18',sunrise:rise,sunset}]},now);
  assert.equal(w.to,now);assert.match(w.note,/Sonnenuntergang nicht verfügbar/);
 }
 const custom={from:100,to:200};assert.deepEqual(C.solarPlotWindow(custom,[{t:90,v:99}]),custom);
});
test('solar energy axis uses native observed bounds without reading cumulative Wh as ongoing production',()=>{
 const w={from:100,to:200,solarFrom:100,solarTo:200,dayFrom:0,dayTo:300};
 assert.deepEqual(C.solarPlotWindow(w,[{t:300,v:200}],{from:50,to:250}),{...w,from:50,to:250});
});

test('native gap flags govern all charts across source changes and long supported intervals',()=>{
 const a={t:1000,last:1000,device:'A',origin:'live'},b={t:61000,last:61000,device:'A',origin:'archive',gap:false};
 assert.equal(C.seriesGap(a,b,15000),false);assert.equal(C.seriesGap(a,{...b,gap:true}),true);
 assert.equal(C.seriesGap(a,{...b,device:'B',gap:false}),true);assert.equal(C.seriesGap(a,{...b,gap:undefined},15000),true);
});

test('overview calendar ranges include full yesterday and seven or thirty calendar days',()=>{
  const now=new Date(2027,0,3,14,25).getTime(),midnight=new Date(2027,0,3).getTime();
  const weather={days:[{day:'2027-01-03',sunrise:now-3600000}]};
  for(const [mode,from,to] of [['yesterday',new Date(2027,0,2).getTime(),midnight],['week',new Date(2026,11,28).getTime(),now],['month',new Date(2026,11,5).getTime(),now]]){
    const window=C.miniWindow(mode,weather,now);assert.equal(window.from,from);assert.equal(window.to,to);
    assert.deepEqual(C.solarQueryWindow(window),{from,to});assert.deepEqual(C.solarPlotWindow(window,[{t:from-3600000,v:50,kind:'solar'}]),window,'old sunlight must not expand a calendar range');
  }
});
test('overview rolling hours remain exact durations',()=>{
  const now=new Date(2026,9,26,11).getTime();
  for(const hours of [6,24]){const window=C.miniWindow('last'+hours+'h',{},now);assert.equal(window.from,now-hours*3600000);assert.equal(window.to,now);}
});
test('yesterday follows local calendar boundaries across both Berlin daylight saving changes',()=>{
  const {execFileSync}=require('node:child_process');
  const result=JSON.parse(execFileSync(process.execPath,['-e',`const C=require(${JSON.stringify(require.resolve('../app/src/main/assets/dashboard-core.js'))});console.log(JSON.stringify([new Date(2026,2,30,12),new Date(2026,9,26,12)].map(d=>{const w=C.miniWindow('yesterday',{},d.getTime());return [(w.to-w.from)/3600000,new Date(w.from).getHours(),new Date(w.to).getHours()]})))`],{env:{...process.env,TZ:'Europe/Berlin'},encoding:'utf8'}));
  assert.deepEqual(result,[[23,0,0],[25,0,0]]);
});
test('overview height preserves independent profiles and safely migrates older layouts',()=>{
  const old=C.defaults();delete old.historyHeight;const migrated=C.normalize(old);assert.deepEqual(migrated.historyHeight,{compact:465,wide:380});assert.deepEqual(migrated.tiles,old.tiles);
  migrated.historyHeight={compact:456,wide:712};assert.deepEqual(C.normalize(JSON.parse(JSON.stringify(migrated))).historyHeight,migrated.historyHeight);
  migrated.historyHeight={compact:-100,wide:100000};assert.deepEqual(C.normalize(migrated).historyHeight,{compact:300,wide:1000});
  migrated.historyHeight={compact:'url(secret)',wide:null};assert.deepEqual(C.normalize(migrated).historyHeight,{compact:465,wide:380});
});
test('roller block migrates without moving tiles and keeps independent dimensions in both profiles',()=>{
 const old=C.defaults();old.blockOrder=['history','tiles','record'];delete old.rollerLayout;const layouts=structuredClone(old.tiles.map(t=>t.layouts)),m=C.normalize(old);
 assert.deepEqual(m.tiles.map(t=>t.layouts),layouts);assert.deepEqual(m.blockOrder,['history','tiles','record','roller']);
 m.blockOrder=['roller','tiles','history','record'];m.rollerLayout={compact:{width:8.5,height:310},wide:{width:5,height:890}};assert.deepEqual(C.normalize(JSON.parse(JSON.stringify(m))),m);
 m.rollerLayout.compact={width:100,height:-5};assert.deepEqual(C.normalize(m).rollerLayout.compact,{width:12,height:250});
});
