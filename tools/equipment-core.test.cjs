const {test}=require('node:test');const assert=require('node:assert/strict');const C=require('../app/src/main/assets/equipment.js');
const group=(id,count,watts,measurement)=>({id,label:'Module',count,watts,measurement,placement:'fixed',controller:'MPPT'});
test('an unconfigured installation has no invented solar equipment',()=>assert.deepEqual(C.totals({version:1,groups:[]}),{watts:0,modules:0}));
test('different module groups keep their controller and measurement assignment',()=>{const setup=C.normalize({version:1,groups:[group('roof','3','90','solar'),group('case',1,120,'shunt')]});assert.deepEqual(C.totals(setup),{watts:390,modules:4});assert.equal(setup.groups[1].measurement,'shunt');});
test('invalid module counts and nonfinite ratings cannot be saved',()=>{for(const g of [group('a',0,100,'solar'),group('a',1.5,100,'solar'),group('a',1,Infinity,'solar')])assert.throws(()=>C.normalize({version:1,groups:[g]}));});
test('duplicate groups are rejected and deleting every group is supported',()=>{assert.throws(()=>C.normalize({version:1,groups:[group('a',1,100,'solar'),group('a',1,100,'solar')]}));assert.deepEqual(C.normalize({version:1,groups:[]}),{version:1,groups:[]});});

test('explicit per-circuit yield factors survive normalization while invalid loss models are rejected',()=>{const g={...group('a',1,100,'solar'),yieldFactor:.65};assert.equal(C.normalize({version:1,groups:[g]}).groups[0].yieldFactor,.65);for(const yieldFactor of [0,1.1,Infinity,'bad'])assert.throws(()=>C.normalize({version:1,groups:[{...g,yieldFactor}]}));});
