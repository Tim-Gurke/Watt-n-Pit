(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.WattPresenceCore=api;})(typeof globalThis==='object'?globalThis:this,function(){
  'use strict';
  const HOUR=3600000,valid=p=>p&&Number.isFinite(p.from)&&Number.isFinite(p.to)&&p.to>p.from;
  const plans=p=>(p.plans||[]).filter(x=>valid(x)&&['planned','active'].includes(x.status)).sort((a,b)=>a.from-b.from);
  function modeAt(t,p,now){
    const current=p.state==='away'?'away':'home';
    if(t<now){let state='home';for(const e of p.history||[]){if(e.at>t)break;state=e.state==='away'?'away':'home';}return state;}
    const active=plans(p),trip=active.find(x=>x.from<=t&&t<x.to);if(trip)return 'away';
    if(current==='away'){
      const returning=active.find(x=>x.status==='active'&&x.to>now);
      return returning&&t>=returning.to?'home':'away';
    }
    return 'home';
  }
  function apply(day,profiles,p,now){
    if(!p||!p.state||!day.timeline?.length)return day;
    const parts=[0,0,0],durations={home:0,away:0},allCuts=[now];
    for(const plan of p.plans||[])if(valid(plan))allCuts.push(plan.from,plan.to);
    for(const e of p.history||[])allCuts.push(e.at);
    let fallback=false,missing=false;
    for(const h of day.timeline){
      const cuts=[h.from,...allCuts.filter(t=>t>h.from&&t<h.to),h.to].sort((a,b)=>a-b);h.loadSegments=[];
      for(let i=1;i<cuts.length;i++){
        const from=cuts[i-1],to=cuts[i];if(to<=from)continue;
        const mode=modeAt((from+to)/2,p,now);let profile=profiles[mode],value=profile.parts[h.slot],uncertainty=profile.slotStats?.[h.slot]?.uncertainty??(profile.provisional?.5:.25);
        if(mode==='away'&&!Number.isFinite(value)){profile=profiles.home;value=profile.parts[h.slot];uncertainty=Math.max(.8,uncertainty);fallback=true;}
        const watts=Number.isFinite(value)?value/profile.hours[h.slot]:null;if(watts===null)missing=true;
        h.loadSegments.push({from,to,watts,uncertainty,mode});parts[h.slot]+=watts===null?0:watts*(to-from)/HOUR;durations[mode]+=to-from;
      }
    }
    const mode=durations.away===0?'home':durations.home===0?'away':'mixed',basis=mode==='away'&&!fallback?profiles.away:profiles.home;
    day.usage={...basis,parts:missing?[null,null,null]:parts,wh:missing?null:parts.reduce((a,b)=>a+b,0),presenceMode:mode,awayFallback:fallback,provisional:basis.provisional||fallback||p.state==='unknown',returnUnknown:p.state==='away'&&!plans(p).some(x=>x.status==='active'&&x.to>now),presenceUnknown:p.state==='unknown'};
    day.balance=day.total===null||day.usage.wh===null?null:day.total-day.usage.wh;return day;
  }
  return {modeAt,apply};
});
