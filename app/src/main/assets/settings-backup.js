(function(root,factory){
  const api=factory(typeof module==='object'&&module.exports?require('./dashboard-core.js'):root.WattDashboardCore,typeof module==='object'&&module.exports?require('./equipment.js'):root.WattEquipmentCore);
  if(typeof module==='object'&&module.exports)module.exports=api;
  else root.WattSettingsCore=api;
})(typeof globalThis==='object'?globalThis:this,function(D,E){
  'use strict';
  const fail=message=>{throw new Error(message||'Keine gültige Watt’n’Pit-Einstellungsdatei.');};
  function normalize(input){
    if(!input||input.format!=='wattnpit-settings'||input.version!==1)fail('Diese Datei ist keine unterstützte Watt’n’Pit-Einstellungssicherung.');
    if(JSON.stringify(input).length>256000)fail('Die Einstellungsdatei ist zu groß.');
    const s=input.settings;
    if(!s||!['light','dark'].includes(s.theme)||!['classic','blue','green','turquoise','orange','neutral'].includes(s.palette)||!['sunrise','today','last6h','last24h','yesterday','week','month'].includes(s.miniRange)||typeof s.capacity!=='number'||!Number.isFinite(s.capacity)||s.capacity<1||s.capacity>10000||![5,10,30,60].includes(s.sampleSeconds))fail('Die allgemeinen Einstellungen sind ungültig.');
    const board=input.dashboard,ids=new Set();
    if(!board||board.version!==1||!Array.isArray(board.tiles)||board.tiles.length>64)fail('Die gesicherte Übersicht ist ungültig.');
    for(const tile of board.tiles){
      if(!tile||!Object.prototype.hasOwnProperty.call(D.CATALOG,tile.metric)||!/^[a-zA-Z0-9_-]{1,90}$/.test(tile.id||'')||ids.has(tile.id))fail('Die Sicherung enthält ungültige oder nicht unterstützte Kacheln.');
      ids.add(tile.id);
      if(tile.period?.mode&&!['live','last','today','sunrise','reset','custom'].includes(tile.period.mode))fail('Ein Kachelzeitraum wird nicht unterstützt.');
    }
    const p=input.weatherPlace||{};let weatherPlace={};
    if(Object.keys(p).length){
      if(typeof p.name!=='string'||!p.name.trim()||p.name.length>180||typeof p.latitude!=='number'||typeof p.longitude!=='number'||!Number.isFinite(p.latitude)||!Number.isFinite(p.longitude)||Math.abs(p.latitude)>90||Math.abs(p.longitude)>180)fail('Der gesicherte Wetterort ist ungültig.');
      weatherPlace={name:p.name.trim(),latitude:p.latitude,longitude:p.longitude};
    }
    const result={format:'wattnpit-settings',version:1,settings:{theme:s.theme,palette:s.palette,capacity:s.capacity,sampleSeconds:s.sampleSeconds,miniRange:s.miniRange},dashboard:D.normalize(board),equipment:E.normalize(input.equipment),weatherPlace};
    if(typeof input.createdAt==='string'&&input.createdAt.length<=40&&Number.isFinite(Date.parse(input.createdAt)))result.createdAt=input.createdAt;
    return result;
  }
  function create(snapshot,dashboard,equipment){
    const s=snapshot.settings||{};
    return normalize({format:'wattnpit-settings',version:1,createdAt:new Date().toISOString(),settings:{theme:s.theme||'dark',palette:s.palette||'classic',capacity:s.capacity??100,sampleSeconds:s.sampleSeconds??10,miniRange:s.miniRange||'sunrise'},dashboard,equipment,weatherPlace:s.weatherPlace||{}});
  }
  return {normalize,create};
});
