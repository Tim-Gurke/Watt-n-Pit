const fs=require('node:fs'),assert=require('node:assert/strict'),C=require('../app/src/main/assets/solar-core.js');
const f=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
for(const t of f.keys){assert.equal(t.key,C.setupKey(t.equipment,t.plan,t.place,'source'),JSON.stringify(t));assert.equal(t.solar,C.calibrationKey(t.equipment,t.plan,t.place,'source'));assert.equal(t.hardware,C.systemKey(t.equipment,'source'));}
const days=C.forecast(f.weather,f.equipment,f.plan,{source:'source',observations:[],forecasts:[]},f.weather.updated);
const rows=C.trainingRows(f.weather,f.equipment,f.plan,days,'source');assert.ok(rows.length>0);assert.deepEqual(f.nativeRows,rows);
console.log('PASS native setup, solar and hardware identifiers match JS, including coordinate rounding and controller losses');
console.log('PASS display-off prospective baseline rows exactly match the shipped forecast training engine');
