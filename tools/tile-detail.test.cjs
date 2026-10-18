const {test}=require('node:test');
const assert=require('node:assert/strict');
const D=require('../app/src/main/assets/tile-detail.js');
const C=require('../app/src/main/assets/dashboard-core.js');
const B=require('../app/src/main/assets/settings-backup.js');

test('detail periods respect local calendar days and clamp future custom ends',()=>{
 const now=new Date(2026,9,18,12).getTime(),midnight=new Date(2026,9,18).getTime();
 assert.deepEqual(D.periodWindow({mode:'today'},{},now),{from:midnight,to:now,note:''});
 assert.equal(D.periodWindow({mode:'yesterday'},{},now).to,midnight);
 assert.equal(D.periodWindow({mode:'yesterday'},{},now).from,new Date(2026,9,17).getTime());
 assert.equal(D.periodWindow({mode:'week'},{},now).from,new Date(2026,9,12).getTime());
 assert.equal(D.periodWindow({mode:'month'},{},now).from,new Date(2026,8,19).getTime());
 for(const [unit,ms] of [['min',60000],['h',3600000],['d',86400000]])assert.equal(D.periodWindow({mode:'last',amount:3,unit},{},now).from,now-3*ms);
 assert.equal(D.periodWindow({mode:'custom',from:midnight,to:now+86400000},{},now).to,now);
 assert.equal(D.periodWindow({mode:'custom',from:now+1,to:now+100},{},now),null);
 assert.equal(D.periodWindow({mode:'custom',from:NaN,to:now},{},now),null);
 assert.equal(D.periodWindow({mode:'reset',resetAt:now-1000},{},now).from,now-1000);
 const weather={timezone:'Europe/Berlin',days:[{day:'2026-10-18',sunrise:midnight+7*3600000}]};
 assert.deepEqual(D.periodWindow({mode:'sunrise'},weather,now),C.miniWindow('sunrise',weather,now));
});
test('zoom anchors, minimum spans and panning stay inside the chosen period',()=>{
 const base={from:100000,to:3700000},view={...base},saved=structuredClone(base);
 assert.deepEqual(D.zoomView(view,base,.5,0),{from:100000,to:1900000});
 assert.deepEqual(D.zoomView(view,base,.5,1),{from:1900000,to:3700000});
 assert.deepEqual(D.panView({from:1000000,to:2000000},base,-9000000),{from:100000,to:1100000});
 assert.deepEqual(D.panView({from:1000000,to:2000000},base,9000000),{from:2700000,to:3700000});
 assert.equal(D.zoomView(view,base,.00001).to-D.zoomView(view,base,.00001).from,60000);
 assert.deepEqual(D.zoomView(view,base,20),base);assert.deepEqual(base,saved);assert.deepEqual(view,saved);
 assert.deepEqual(D.zoomView({from:10,to:20},{from:10,to:20},.5),{from:10,to:20});
});
test('detail conversions preserve unavailable values, gaps and signed extrema without mutating inputs',()=>{
 const tile=C.tile('dischargeEnergy');tile.unit='kWh';
 const points=[{t:20,v:100,min:80,max:150,gap:true},{t:10,v:null},{t:30,v:0}],before=structuredClone(points),original=structuredClone(tile);
 const result=D.seriesPoints(tile,points,100);
 assert.deepEqual(result.map(p=>p.v),[null,-.1,0]);assert.equal(result[1].min,-.15);assert.equal(result[1].max,-.08);assert.equal(result[1].gap,true);
 assert.deepEqual(points,before);assert.deepEqual(tile,original);
 const ah=C.tile('remainingAh');ah.unit='mAh';assert.equal(D.seriesPoints(ah,[{t:1,v:50}],200)[0].v,100000);
});
test('half-cell layouts round trip in both profiles while existing integer positions remain exact',()=>{
 const board=C.defaults(),old=structuredClone(board.tiles[1]);
 board.tiles[0].layouts={compact:{x:.5,y:1.5,w:3.5,h:5.5},wide:{x:1.5,y:2.5,w:2.5,h:3.5}};
 const restored=C.normalize(JSON.parse(JSON.stringify(board)));
 assert.deepEqual(restored.tiles[0].layouts,board.tiles[0].layouts);assert.deepEqual(restored.tiles[1],old);
 const backup={format:'wattnpit-settings',version:1,settings:{theme:'dark',palette:'classic',capacity:100,sampleSeconds:10,miniRange:'today'},dashboard:board,equipment:{version:1,groups:[]},weatherPlace:{}};
 const normalized=B.normalize(backup);assert.deepEqual(normalized.dashboard.tiles[0].layouts,board.tiles[0].layouts);
});
