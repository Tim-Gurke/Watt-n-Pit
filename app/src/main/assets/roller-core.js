(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.WattRollerCore=api;})(typeof globalThis==='object'?globalThis:this,function(){
  'use strict';
  const defaults={planCharge:false,name:'Roller',mac:'',technology:'Li-Ion',capacityAh:26,nominalVoltage:48.1,reservePercent:20,targetPercent:90,idleWatts:21,conversionEfficiency:96/117,chargeWatts:96,rideKm:0,whPerKm:15,rideReservePercent:20,departure:0,returnAt:0};
  const input=(watts,p)=>watts>0?p.idleWatts+watts/p.conversionEfficiency:0;
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
  return {defaults,input,plan,loads};
});
