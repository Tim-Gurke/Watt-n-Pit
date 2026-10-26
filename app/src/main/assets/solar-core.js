/* Forecasts and learning use declared equipment and measured intervals, never BLE writes. */
(function(root,factory){
  const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.WattSolarCore=api;
})(typeof globalThis==='object'?globalThis:this,function(){
  'use strict';
  const HOUR=3600000,DAY=24*HOUR;
  const finite=x=>typeof x==='number'&&Number.isFinite(x);
  const clamp=(x,a,b)=>Math.min(b,Math.max(a,x));
  const DIRECTIONS=['Nord','Nord/Nordost','Nordost','Ost/Nordost','Ost','Ost/Südost','Südost','Süd/Südost','Süd','Süd/Südwest','Südwest','West/Südwest','West','West/Nordwest','Nordwest','Nord/Nordwest'];
  const PARTS=['Morgens','Mittags','Abends'];
  function bearing(value){if(!finite(value))return null;return ((value%360)+360)%360;}
  function direction(value){const b=bearing(value);return b===null?'Noch bestimmen':DIRECTIONS[Math.round(b/22.5)%16];}
  function panelHeading(heading,outside){const b=bearing(heading);return b===null?null:bearing(b+(outside?180:0));}
  function geometry(group,setting){
    if(!finite(group.tilt)||group.tilt<0||group.tilt>90)return null;
    const az=group.tilt===0?180:bearing(setting?.azimuth);
    if(az===null)return null;
    return `${group.tilt}:${az-180}`; // Open-Meteo: 0 south, -90 east, +90 west.
  }
  function normalize(input={},equipment={groups:[]}){
    if(input.version!=null&&input.version!==1)throw Error('Unbekannte Aufstellung.');
    const groups={};
    for(const g of equipment.groups||[]){
      const p=input.groups?.[g.id]||{};
      if(p.azimuth!=null&&(!finite(p.azimuth)||p.azimuth<0||p.azimuth>=360))throw Error('Bitte eine gültige Himmelsrichtung wählen.');
      const shade=Array.isArray(p.shade)?p.shade:[null,null,null];
      if(shade.length!==3||shade.some(x=>x!==null&&(!Number.isInteger(x)||x<0||x>3)))throw Error('Bitte die Verschattung prüfen.');
      groups[g.id]={active:typeof p.active==='boolean'?p.active:g.placement==='mobile'?false:g.defaultActive!==false,azimuth:p.azimuth??null,shade:[...shade]};
    }
    const manual=Array.isArray(input.manual)?input.manual:[null,null,null,null];
    if(manual.length!==4||manual.some(x=>x!==null&&(!finite(x)||x<0||x>100000)))throw Error('Bitte gültige saisonale Verbrauchswerte eingeben.');
    return {version:1,groups,usageMode:input.usageMode==='manual'?'manual':'auto',manual:[...manual]};
  }
  function newSite(input,equipment){const p=normalize(input,equipment);for(const g of equipment.groups||[]){p.groups[g.id].azimuth=null;p.groups[g.id].shade=[null,null,null];if(g.placement==='mobile')p.groups[g.id].active=false;}return p;}
  const formats=new Map();
  function stamp(time,zone){
    try{if(!formats.has(zone))formats.set(zone,new Intl.DateTimeFormat('en-CA',{timeZone:zone||undefined,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',hourCycle:'h23'}));const parts=formats.get(zone).formatToParts(new Date(time)),p=Object.fromEntries(parts.map(x=>[x.type,x.value]));return {day:`${p.year}-${p.month}-${p.day}`,hour:Number(p.hour)};}catch(_){return null;}
  }
  function part(hour){return hour<11?0:hour<16?1:2;}
  function setupKey(equipment,plan,place={},source=''){
    if(!finite(place.latitude)||!finite(place.longitude))return '';
    const text=JSON.stringify([source,place.latitude.toFixed(3),place.longitude.toFixed(3),(equipment.groups||[]).map(g=>[g.id,g.count,g.watts,g.tilt,g.measurement,g.placement,plan.groups[g.id]?.active,plan.groups[g.id]?.azimuth,plan.groups[g.id]?.shade]).sort((a,b)=>a[0].localeCompare(b[0]))]);
    let a=2166136261,b=3339675911;for(let i=0;i<text.length;i++){a=Math.imul(a^text.charCodeAt(i),16777619);b=Math.imul(b^text.charCodeAt(i),2246822519);}return 'k-'+(a>>>0).toString(16).padStart(8,'0')+(b>>>0).toString(16).padStart(8,'0');
  }
  function calibrationKey(equipment,plan,place={},source=''){return setupKey({...equipment,groups:(equipment.groups||[]).filter(g=>g.measurement==='solar')},plan,place,source);}
  function median(values){const a=values.filter(finite).sort((a,b)=>a-b);return a.length?a.length%2?a[(a.length-1)/2]:(a[a.length/2-1]+a[a.length/2])/2:null;}
  function calibration(observations=[],forecasts=[],key=''){
    const output=PARTS.map(()=>({factor:1,days:0,spread:.3}));if(!key)return output;
    const predictions=new Map();
    for(const f of forecasts){if(f.key!==key||!finite(f.wh)||f.wh<20||!finite(f.issued)||f.issued>f.from-12*HOUR||f.issued<f.from-3*DAY)continue;const id=f.from+':'+f.to,old=predictions.get(id);if(!old||f.issued<old.issued)predictions.set(id,f);}
    for(let slot=0;slot<3;slot++){
      const ratios=[],days=new Set();
      for(const o of observations){const f=predictions.get(o.from+':'+o.to),eligible=o.learningVersion===2?o.solarEligible&&finite(o.solarCoverage)&&o.solarCoverage>=.98:o.mobileFree&&[o.coverage,o.solarCoverage,o.bulkCoverage].every(x=>finite(x)&&x>=.98);if((o.solarKey||o.key)!==key||o.slot!==slot||!f||!eligible||!finite(o.solar)||o.solar<0)continue;const ratio=o.solar/f.wh;if(ratio<.15||ratio>2)continue;ratios.push(ratio);days.add(o.day);}
      if(days.size<3)continue;
      const center=median(ratios),weight=Math.min(1,days.size/10),deviation=median(ratios.map(x=>Math.abs(x-center)));
      output[slot]={factor:clamp(1+(center-1)*weight,.5,1.5),days:days.size,spread:clamp((deviation||0)*1.5,.15,.6)};
    }return output;
  }
  function seasonPosition(day){const d=new Date(day+'T12:00:00Z');if(!Number.isFinite(+d))return null;return (Date.UTC(2000,d.getUTCMonth(),d.getUTCDate())-Date.UTC(2000,0,1))/DAY;}
  function seasonWeights(day){const d=seasonPosition(day);if(d===null)return [0,0,0,0];const centers=[135,227,319,44],weights=centers.map(x=>{const gap=Math.abs(d-x),distance=Math.min(gap,366-gap);return Math.max(0,1-distance/92);}),sum=weights.reduce((a,b)=>a+b,0);return weights.map(x=>sum?x/sum:0);}
  function consumption(plan,observations,day,now=Date.now()){
    const weights=seasonWeights(day);
    if(plan.usageMode==='manual'){
      const available=plan.manual.map((x,i)=>finite(x)?weights[i]:0),total=available.reduce((a,b)=>a+b,0);
      if(total>.01){const wh=plan.manual.reduce((sum,x,i)=>sum+(finite(x)?x*available[i]/total:0),0);return {wh,parts:[wh*11/24,wh*5/24,wh*8/24],days:0,manual:true,provisional:total<.99};}
    }
    const samples=[],nominal=[11,5,8],target=seasonPosition(day);
    for(const o of observations||[]){
      if(!Number.isInteger(o.slot)||o.slot<0||o.slot>2||seasonPosition(o.day)===null)continue;
      let wh,duration,estimated=0,latest;
      if(o.learningVersion===2){const u=o.usage||{};({wh,duration,latest}=u);estimated=u.estimatedDuration;if(!finite(estimated)||estimated<0||estimated>duration)continue;}
      else{if(!o.mobileFree||!finite(o.coverage)||o.coverage<.98)continue;wh=o.consumption;duration=o.to-o.from;latest=o.to;}
      if(!finite(wh)||wh<0||!finite(duration)||duration<15*60000||duration>13*HOUR||!finite(latest)||latest>now||now-latest>400*DAY)continue;
      const gap=Math.abs(seasonPosition(o.day)-target),distance=Math.min(gap,366-gap),season=Math.exp(-.5*(distance/45)**2),age=Math.max(0,(now-latest)/DAY);
      const hours=duration/HOUR,estimatedHours=estimated/HOUR,effectiveHours=hours-.6*estimatedHours;
      const estimatedWh=o.usage?.estimatedWh,split=o.learningVersion===2&&finite(estimatedWh)&&estimatedWh>=0&&estimatedWh<=wh+1e-8
        &&(estimated!==0||estimatedWh===0)&&(estimated!==duration||Math.abs(estimatedWh-wh)<1e-8);
      const rate=split?(wh-.6*Math.min(wh,estimatedWh))/effectiveHours:wh/hours;
      const weight=(.08+.92*season)*Math.exp(-age/240)*effectiveHours;
      samples.push({day:o.day,slot:o.slot,rate,hours,estimated:estimatedHours,effectiveHours,season,weight});
    }
    if(!samples.length)return {wh:null,parts:[null,null,null],days:0,manual:false,provisional:true};
    const accepted=rows=>{const typical=median(rows.map(x=>x.rate));return rows.length<7?rows:rows.filter(x=>typical===0?x.rate===0:x.rate<=typical*3);};
    const average=rows=>{if(!rows.length)return null;const clean=accepted(rows),weight=clean.reduce((s,x)=>s+x.weight,0);return weight?clean.reduce((s,x)=>s+x.rate*x.weight,0)/weight:null;};
    const fallback=average(samples),rates=nominal.map((_,slot)=>average(samples.filter(x=>x.slot===slot))),extrapolated=rates.some(x=>x===null);
    const parts=rates.map((x,i)=>(x??fallback)*nominal[i]),hours=samples.reduce((s,x)=>s+x.hours,0),estimatedHours=samples.reduce((s,x)=>s+x.estimated,0),usedDays=new Set();
    const slotStats=nominal.map((nominalHours,slot)=>{
      const rows=accepted(samples.filter(x=>x.slot===slot)),byDay=new Map();
      let hours=0,estimatedHours=0,effectiveHours=0;
      for(const x of rows){hours+=x.hours;estimatedHours+=x.estimated;effectiveHours+=x.effectiveHours;usedDays.add(x.day);const d=byDay.get(x.day)||{hours:0,estimated:0,season:x.season};d.hours+=x.hours;d.estimated+=x.estimated;byDay.set(x.day,d);}
      const days=byDay.size,comparableDays=[...byDay.values()].reduce((s,x)=>s+x.season*(1-.6*x.estimated/x.hours),0),extrapolated=rates[slot]===null;
      const provisional=extrapolated||days<3||effectiveHours<nominalHours||comparableDays<2.5;
      const uncertainty=extrapolated?.8:Math.max(provisional?.5:.25,days<3?.8-.15*(days-1):0,estimatedHours/hours*.6);
      return {slot,hours,estimatedHours,measuredHours:hours-estimatedHours,effectiveHours,days,comparableDays,extrapolated,provisional,uncertainty};
    });
    const days=usedDays.size,provisional=days<5||slotStats.some(x=>x.provisional);
    return {wh:parts.reduce((a,b)=>a+b,0),parts,days,manual:false,provisional,extrapolated,sampleHours:hours,estimatedHours,slotStats,usedHours:slotStats.reduce((s,x)=>s+x.hours,0),usedEstimatedHours:slotStats.reduce((s,x)=>s+x.estimatedHours,0),uncertainty:Math.max(...slotStats.map(x=>x.uncertainty))};
  }
  function sunshineCode(day){
    if(finite(day.hours)&&finite(day.sunrise)&&finite(day.sunset)&&day.sunset>day.sunrise){const share=clamp(day.hours/((day.sunset-day.sunrise)/HOUR),0,1);return share>=.75?0:share>=.2?2:3;}
    return day.code??null;
  }
  function forecast(weather,equipment,plan,knowledge={},now=Date.now()){
    const today=stamp(now,weather.timezone)?.day,key=calibrationKey(equipment,plan,weather.place,knowledge.source||''),corrections=calibration(knowledge.observations,knowledge.forecasts,key),out=[];
    const active=(equipment.groups||[]).filter(g=>plan.groups[g.id]?.active),mapped=new Map();
    for(const series of weather.solar||[])mapped.set(series.geometry,new Map((series.values||[]).map(p=>[p.t,p.v])));
    const byDay=new Map();for(const h of weather.hours||[]){const local=stamp(h.t-HOUR/2,weather.timezone);if(!local)continue;const list=byDay.get(local.day)||[];list.push({t:h.t,slot:part(local.hour)});byDay.set(local.day,list);}
    const dated=new Map(),counts=new Map();for(const d of weather.days||[]){counts.set(d.day,(counts.get(d.day)||0)+1);if(today&&d.day>=today&&!dated.has(d.day))dated.set(d.day,d);}
    const days=[...dated.values()].slice(0,14);
    for(let index=0;index<days.length;index++){
      const d=days[index],bins=PARTS.map(()=>({fixed:0,mobile:0,raw:0,remaining:0})),timeline=new Map((byDay.get(d.day)||[]).map(h=>[h.t,{from:h.t-HOUR,to:h.t,wh:0,slot:h.slot}])),issues=[],add=(kind,g)=>{if(!issues.some(x=>x.kind===kind&&x.group===(g?.label||g?.id||'')))issues.push({kind,group:g?.label||g?.id||''});};let complete=(equipment.groups||[]).length>0,unknownShade=false,seen=0;
      if(!complete)add('equipment');
      const windows=(weather.windows||[]).filter(w=>w.day===d.day).sort((a,b)=>a.slot-b.slot),validWindows=windows.length===3&&windows.every((w,i)=>w.slot===i&&finite(w.from)&&finite(w.to)&&w.to>w.from&&(i===0||w.from===windows[i-1].to));
      const expected=validWindows?windows.reduce((sum,w)=>sum+(w.to-w.from)/HOUR,0):null;
      let measuredComplete=active.some(g=>g.measurement==='solar')&&validWindows&&counts.get(d.day)===1;
      for(const g of active){const setting=plan.groups[g.id],geom=geometry(g,setting),series=mapped.get(geom);
        const measured=g.measurement==='solar';
        if(!finite(g.tilt)||g.tilt<0||g.tilt>90){complete=false;if(measured)measuredComplete=false;add('tilt',g);continue;}
        if(!geom){complete=false;if(measured)measuredComplete=false;add('direction',g);continue;}
        if(!series){complete=false;if(measured)measuredComplete=false;add('weather');continue;}let groupHours=0;
        if(!validWindows||counts.get(d.day)!==1){complete=false;add('time');}
        for(const {t,slot} of byDay.get(d.day)||[]){const value=series.get(t);if(!finite(value)||value<0){complete=false;if(measured)measuredComplete=false;continue;}groupHours++;const shade=setting.shade[slot];unknownShade=unknownShade||shade===null;const wh=g.count*g.watts*value/1000*.8*[1,.8,.55,.25][shade??0],isMobile=g.placement==='mobile',factor=measured?corrections[slot].factor:1;
          bins[slot][isMobile?'mobile':'fixed']+=wh*factor;if(measured)bins[slot].raw+=wh;
          bins[slot].remaining+=wh*factor*clamp((t-now)/HOUR,0,1);
          timeline.get(t).wh+=wh*factor;
        }
        if(groupHours!==expected){complete=false;if(measured)measuredComplete=false;if(validWindows)add('weather');}seen=Math.max(seen,groupHours);
      }
      if(active.length&&!seen)complete=false;
      const fixed=complete?bins.reduce((a,b)=>a+b.fixed,0):null,mobile=complete?bins.reduce((a,b)=>a+b.mobile,0):null,total=fixed===null?null:fixed+mobile,baseline=consumption(plan,knowledge.observations,d.day,now);
      const usageHours=validWindows?windows.map(w=>(w.to-w.from)/HOUR):[11,5,8],usage={...baseline,hours:usageHours,parts:baseline.parts.map((x,i)=>x===null?null:x*usageHours[i]/[11,5,8][i])};usage.wh=baseline.wh===null?null:usage.parts.reduce((a,b)=>a+b,0);
      const spread=clamp(Math.max(...corrections.map(x=>x.spread))+.025*index+(unknownShade?.2:0),.2,.8);
      out.push({...d,code:sunshineCode(d),index,complete,measuredComplete,issues,unknownShade,fixed,mobile,total,low:total===null?null:total*(1-spread),high:total===null?null:total*(1+spread),spread,timeline:[...timeline.values()],parts:bins,usage,balance:total===null||usage.wh===null?null:total-usage.wh,key,learningDays:Math.min(...corrections.map(x=>x.days))});
    }return out;
  }
  function nextDay(day){const t=Date.parse(day+'T12:00:00Z');return finite(t)?new Date(t+DAY).toISOString().slice(0,10):'';}
  function batteryForecast(days,weather,battery,now=Date.now()){
    const calendar=new Map((weather.days||[]).map(d=>[d.day,d]));
    const targets=days.map(d=>({day:d.day,evening:{at:finite(d.sunset)?d.sunset:null},morning:{at:calendar.get(nextDay(d.day))?.sunrise??null}}));
    let problem='',reasonCode='';
    if(!battery||!finite(battery.soc)||battery.soc<0||battery.soc>100||!finite(battery.t)||battery.t>now+5000||now-battery.t>45000){reasonCode='soc';problem='Für die Akkuprognose fehlt ein aktueller Ladezustand vom SmartShunt.';}
    else if(!finite(battery.capacity)||battery.capacity<=0){reasonCode='capacity';problem='Bitte die Akkukapazität in den Einstellungen hinterlegen.';}
    else if(!finite(battery.voltage)||battery.voltage<=0){reasonCode='voltage';problem='Für die Akkuprognose fehlt die Batteriespannung.';}
    const cap=problem?null:battery.capacity*battery.voltage,energy=cap===null?null:cap*battery.soc/100;
    const scenarios=[{energy,excess:0,shortfall:0},{energy,excess:0,shortfall:0},{energy,excess:0,shortfall:0}];
    const hours=[];for(const d of days){const usageSpread=d.usage.uncertainty??(d.usage.manual?.25:d.usage.provisional?.5:.25);for(const h of d.timeline||[])hours.push({...h,complete:d.complete,spread:d.spread,load:d.usage.parts[h.slot],loadHours:d.usage.hours?.[h.slot]??[11,5,8][h.slot],usageSpread:d.usage.slotStats?.[h.slot]?.uncertainty??usageSpread});}
    hours.sort((a,b)=>a.from-b.from);let cursor=now,index=0;
    const ordered=targets.flatMap(d=>['evening','morning'].map(k=>d[k])).filter(t=>finite(t.at)&&t.at>=now).sort((a,b)=>a.at-b.at);
    for(const target of ordered){
      while(!problem&&cursor<target.at){
        while(index<hours.length&&hours[index].to<=cursor)index++;const h=hours[index];
        if(!h||h.from>cursor||!h.complete||!finite(h.wh)||h.wh<0){reasonCode='weather';problem='Für die Akkuprognose fehlen durchgängige Ertragsdaten bis zu diesem Zeitpunkt.';break;}
        if(!finite(h.load)||h.load<0){reasonCode='consumption';problem='Der übliche Verbrauch wird noch aus geeigneten Messabschnitten ermittelt. Auch Nachtabschnitte bei gemeldeter mobiler Aufstellung können helfen. Eigene saisonale Vorgaben sind freiwillig.';break;}
        const end=Math.min(target.at,h.to),fraction=(end-cursor)/HOUR,load=h.load/h.loadHours*fraction,solar=h.wh*fraction;
        for(let i=0;i<3;i++){const s=scenarios[i],generation=solar*(i===1?1-h.spread:i===2?1+h.spread:1),use=load*(i===1?1+h.usageSpread:i===2?1-h.usageSpread:1),raw=s.energy+generation-use;s.excess+=Math.max(0,raw-cap);s.shortfall+=Math.max(0,-raw);s.energy=clamp(raw,0,cap);}
        cursor=end;
      }
      if(problem){target.reason=problem;target.reasonCode=reasonCode;}
      else Object.assign(target,{soc:scenarios[0].energy/cap*100,low:scenarios[1].energy/cap*100,high:scenarios[2].energy/cap*100,excessWh:scenarios[0].excess,shortfallWh:scenarios[0].shortfall,possibleShortfall:scenarios[1].shortfall>0});
    }
    for(const d of targets)for(const k of ['evening','morning']){const t=d[k];if(!finite(t.at)){t.reasonCode='sun';t.reason=k==='evening'?'Für diesen Tag fehlt der Sonnenuntergang.':'Für den nächsten Tag fehlt der Sonnenaufgang.';}else if(t.at<now){t.reasonCode='past';t.reason='Dieser Zeitpunkt liegt bereits in der Vergangenheit.';}}
    return targets;
  }
  function trainingRows(weather,equipment,plan,days,source=''){
    const key=calibrationKey(equipment,plan,weather.place,source),groups=(equipment.groups||[]).filter(g=>plan.groups[g.id]?.active&&g.measurement==='solar');
    if(!groups.length||groups.some(g=>!geometry(g,plan.groups[g.id])))return [];
    const rows=[];for(const d of days){if(!d.measuredComplete)continue;for(let slot=0;slot<3;slot++){const bounds=(weather.windows||[]).find(w=>w.day===d.day&&w.slot===slot);if(bounds&&bounds.from>weather.updated)rows.push({key,from:bounds.from,to:bounds.to,issued:weather.updated,wh:d.parts[slot].raw});}}return rows;
  }
  return {HOUR,DAY,DIRECTIONS,PARTS,bearing,direction,panelHeading,geometry,normalize,newSite,stamp,part,setupKey,calibrationKey,median,seasonWeights,consumption,calibration,sunshineCode,forecast,batteryForecast,trainingRows};
});
