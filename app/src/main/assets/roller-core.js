(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.WattRollerCore=api;})(typeof globalThis==='object'?globalThis:this,function(){
  'use strict';
  const defaults={planCharge:false,autoSpecialUse:false,name:'Roller',mac:'',technology:'Li-Ion',capacityAh:26,nominalVoltage:48.1,reservePercent:20,targetPercent:90,idleWatts:21,conversionEfficiency:96/117,chargeWatts:96,rideKm:0,whPerKm:15,rideReservePercent:20,departure:0,returnAt:0};
  const input=(watts,p)=>watts>0?p.idleWatts+watts/p.conversionEfficiency:0;
  const displayDefaults=['stored','soc','power','charged','input','temperature'];
  const displayMetrics={
    stored:{label:'Enthaltene Energie · geschätzt',unit:'Wh'},soc:{label:'Ladezustand · BMS',unit:'%'},power:{label:'Batterieleistung · BMS',unit:'W'},
    charged:{label:'Geladen · gemessene Abschnitte',unit:'Wh'},input:{label:'Wohnwagenanteil · geschätzt',unit:'Wh'},temperature:{label:'Temperatur · BMS',unit:'°C'},
    voltage:{label:'Batteriespannung · BMS',unit:'V',digits:2},current:{label:'Batteriestrom · BMS',unit:'A',digits:2},remainingAh:{label:'Restkapazität · BMS',unit:'Ah',digits:2},fullAh:{label:'Vollkapazität · BMS',unit:'Ah',digits:2}
  };
  const sensorIndex=key=>typeof key==='string'&&/^temperature:(?:0|[1-9]\d{0,2})$/.test(key)&&Number(key.split(':')[1])<116?Number(key.split(':')[1]):-1;
  const validDisplay=key=>typeof key==='string'&&(Object.prototype.hasOwnProperty.call(displayMetrics,key)||sensorIndex(key)>=0);
  const normalizeDisplay=values=>displayDefaults.map((fallback,i)=>Array.isArray(values)&&validDisplay(values[i])?values[i]:fallback);
  function displayOptions(live={},selected=[]){
    const options=Object.entries(displayMetrics).map(([key,m])=>[key,key==='temperature'?'Temperatur · BMS · Maximum':m.label]);
    const count=Math.max(2,Math.min(116,Array.isArray(live.temperatures)?live.temperatures.length:0)),indices=new Set(Array.from({length:count},(_,i)=>i));
    for(const key of selected){const i=sensorIndex(key);if(i>=0)indices.add(i);}
    return options.concat([...indices].sort((a,b)=>a-b).map(i=>['temperature:'+i,'Temperatur · BMS · Sensor '+(i+1)]));
  }
  function displayReading(key,profile,live={},session={}){
    const sensor=sensorIndex(key),meta=sensor>=0?{label:'Temperatur · BMS · Sensor '+(sensor+1),unit:'°C'}:displayMetrics[key]||displayMetrics.stored;
    const valid=v=>typeof v==='number'&&Number.isFinite(v),temperatures=Array.isArray(live.temperatures)?live.temperatures:[],known=temperatures.filter(valid);
    let value=sensor>=0?temperatures[sensor]:key==='stored'?storedEnergy(profile,live):key==='charged'?session.batteryWh:key==='input'?session.inputWh:key==='temperature'?(known.length?Math.max(...known):null):live[key];
    return {...meta,value:valid(value)?value:null,approximate:key==='stored',label:meta.label+(key==='temperature'&&known.length>1?' · Maximum':''),title:key==='temperature'?known.map(v=>v.toLocaleString('de-DE',{maximumFractionDigits:1})+' °C').join(' / '):''};
  }
  function storedEnergy(profile,live){
    const p={...defaults,...profile},valid=n=>typeof n==='number'&&Number.isFinite(n);
    if(!valid(p.nominalVoltage)||p.nominalVoltage<=0)return null;
    if(valid(live?.remainingAh)&&valid(live?.fullAh)&&live.fullAh>0&&live.remainingAh>=0&&live.remainingAh<=live.fullAh)return live.remainingAh*p.nominalVoltage;
    return valid(live?.soc)&&live.soc>=0&&live.soc<=100&&valid(p.capacityAh)&&p.capacityAh>0?p.capacityAh*p.nominalVoltage*live.soc/100:null;
  }
  function plan(profile,live,now=Date.now()){
    const p={...defaults,...profile},capacity=p.capacityAh*p.nominalVoltage,rideWh=p.rideKm*p.whPerKm*(1+p.rideReservePercent/100),reserve=capacity*p.reservePercent/100;
    const requiredRide=rideWh+reserve,needed=Math.max(capacity*p.targetPercent/100,p.rideKm>0?requiredRide:0),available=typeof live?.soc==='number'?capacity*live.soc/100:null;
    const missing=available===null?null:Math.max(0,needed-available),hours=missing===null?null:missing/p.chargeWatts,draw=missing===null?null:missing/p.conversionEfficiency+hours*p.idleWatts;
    return {capacity,rideWh,requiredRide,needed,available,missing,hours,draw,impossible:needed>capacity,away:p.departure>0&&p.returnAt>p.departure&&now>=p.departure&&now<p.returnAt,late:p.departure>now&&hours!==null&&now+hours*3600000>p.departure,usable:available===null?null:Math.max(0,available-Math.max(reserve,requiredRide))};
  }
  function loads(state,now=Date.now()){
    if(!state?.fresh||!state.profile?.mac||!state.profile.planCharge&&!state.session?.active)return [];
    const p=state.profile,r=plan(p,state.live,now);if(r.away||r.impossible||!(r.hours>0))return [];
    const end=now+r.hours*3600000;if(p.departure>0&&p.departure<end&&p.departure>now)return [];
    return [{from:now,to:end,watts:input(p.chargeWatts,p)}];
  }
  return {defaults,input,storedEnergy,plan,loads,displayDefaults,displayMetrics,normalizeDisplay,displayOptions,displayReading};
});
