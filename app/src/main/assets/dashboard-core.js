/* Dashboard values and configuration. No Bluetooth or UI dependencies. */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.WattDashboardCore = api;
})(typeof globalThis === 'object' ? globalThis : this, function () {
  'use strict';
  const CATALOG = {
    soc:          {label:'Ladezustand', group:'Akku', kind:'shunt', field:'soc', units:['%'], min:0, max:100, digits:1, color:'#65cbb0'},
    remaining:    {label:'Restlaufzeit', group:'Akku', kind:'shunt', field:'ttg', backend:'ttg', units:['h','d','min','s'], min:0, max:1440, digits:1, color:'#81baf4', hint:'Restlaufzeit vom SmartShunt; beim Laden oft nicht verfügbar.'},
    remainingAh:  {label:'Kapazität im Akku', group:'Akku', kind:'shunt', field:'soc', backend:'soc', units:['Ah','mAh'], min:0, max:100, digits:1, color:'#65cbb0', calculated:'capacity', hint:'Rechnerisch: Ladezustand × eingestellte Batteriekapazität.'},
    remainingWh:  {label:'Energie im Akku', group:'Akku', kind:'shunt', backend:'storedEnergy', units:['Wh','kWh'], min:0, max:1400, digits:0, color:'#65cbb0', calculated:'energy', hint:'Näherung aus Kapazität, Ladezustand und momentaner Spannung; keine nutzbare Energieprognose.'},
    power:        {label:'Batteriebilanz', group:'Akku', kind:'shunt', field:'power', backend:'batteryPower', units:['W','kW'], min:-1000, max:1000, digits:0, color:'#65cbb0', hint:'+ Laden · − Entladen. Alle Quellen und Verbraucher am Shunt.'},
    voltage:      {label:'Batteriespannung', group:'Akku', kind:'shunt', field:'voltage', backend:'batteryVoltage', units:['V','mV'], min:10, max:15, digits:2, color:'#81baf4'},
    current:      {label:'Batteriestrom', group:'Akku', kind:'shunt', field:'current', backend:'batteryCurrent', units:['A','mA'], min:-100, max:100, digits:2, color:'#81baf4'},
    temperature:  {label:'Batterietemperatur', group:'Akku', kind:'shunt', field:'temperature', units:['°C','°F'], min:-10, max:60, digits:1, color:'#81baf4', hint:'Nur bei entsprechend eingerichtetem Temperatur-Eingang verfügbar.'},
    aux:          {label:'Zusatzspannung', group:'Akku', kind:'shunt', field:'aux', units:['V','mV'], min:0, max:16, digits:2, color:'#81baf4', hint:'Nur bei entsprechend eingerichtetem Shunt-Eingang verfügbar.'},
    consumed:     {label:'Entnommene Kapazität', group:'Akku', kind:'shunt', field:'consumed', units:['Ah','mAh'], min:0, max:100, digits:1, color:'#81baf4', hint:'Vom Shunt seit seiner letzten Voll-Synchronisation gezählte Ah. Optionaler Zusatzwert.'},
    alarm:        {label:'Shunt-Alarm', group:'Akku', kind:'shunt', field:'alarm', units:['Code'], min:0, max:255, digits:0, color:'#e8bb57', status:true},
    solar:        {label:'Solarleistung', group:'Solar', kind:'solar', field:'solar', units:['W','kW'], min:0, max:300, digits:0, color:'#e8bb57'},
    yieldWh:      {label:'Solarertrag heute', group:'Solar', kind:'solar', field:'yieldWh', units:['kWh','Wh'], min:0, max:2000, digits:2, color:'#e8bb57'},
    solarEnergy:  {label:'Solarertrag berechnet',group:'Energie',kind:'solar',backend:'solarEnergy',units:['Wh','kWh'],min:0,max:2000,digits:2,color:'#e8bb57',energy:true,hint:'Aus gespeicherter Solarleistung berechnet. Nur erfasste Zeitabschnitte; Aufzeichnungslücken werden ausgelassen.'},
    dischargeEnergy:{label:'Batterieentnahme berechnet',group:'Energie',kind:'shunt',backend:'dischargeEnergy',units:['Wh','kWh'],min:0,max:2000,digits:2,color:'#65cbb0',energy:true,hint:'Aus negativer Shunt-Leistung berechnet. Batterieentnahme ist kein Gesamtverbrauch: direkt genutzter Solarstrom fehlt.'},
    energyBalance:{label:'Ertrag − Entnahme',group:'Energie',kind:'balance',backend:'energyBalance',units:['Wh','kWh'],min:-2000,max:2000,digits:2,color:'#81baf4',energy:true,comparison:true,hint:'Solarertrag minus Batterieentnahme im selben Zeitraum. Vergleichswert; keine Änderung der gespeicherten Akkuenergie und kein Gesamtverbrauch.'},
    pvVoltage: {label:'PV-Eingangsspannung',group:'Solar',kind:'solar',field:'pvVoltage',backend:'pvVoltage',units:['V','mV'],min:0,max:75,digits:2,color:'#efb468',hint:'Spannung am Solareingang des Reglers; aus dem Gerätearchiv.'},
    solarTemperature: {label:'Batterietemperatur am Regler',group:'Solar',kind:'solar',field:'temperature',backend:'solarTemperature',units:['°C'],min:-20,max:60,digits:1,color:'#81baf4',hint:'Nur mit vorhandenem Temperatursensor; aus dem Gerätearchiv.'},
    solarVoltage: {label:'Regler-Batteriespannung', group:'Solar', kind:'solar', field:'voltage', units:['V','mV'], min:10, max:15, digits:2, color:'#81baf4', hint:'Batterieseitige Spannung am SmartSolar; keine PV-Eingangsspannung.'},
    solarCurrent: {label:'Solar-Ladestrom', group:'Solar', kind:'solar', field:'current', units:['A','mA'], min:0, max:20, digits:2, color:'#e8bb57'},
    load:         {label:'Lastausgang-Strom', group:'Solar', kind:'solar', field:'load', units:['A','mA'], min:0, max:20, digits:2, color:'#e8bb57'},
    state:        {label:'Ladephase', group:'Solar', kind:'solar', field:'state', units:[''], min:0, max:255, digits:0, color:'#e8bb57', status:true},
    error:        {label:'Reglerfehler', group:'Solar', kind:'solar', field:'error', units:['Code'], min:0, max:255, digits:0, color:'#e8bb57', status:true},
    shuntRssi:    {label:'Shunt-Signal', group:'Verbindung', kind:'shunt', units:['dBm'], min:-100, max:-30, digits:0, color:'#81baf4', liveOnly:true},
    solarRssi:    {label:'Regler-Signal', group:'Verbindung', kind:'solar', units:['dBm'], min:-100, max:-30, digits:0, color:'#81baf4', liveOnly:true},
    shuntAge:     {label:'Shunt: letzter Empfang', group:'Verbindung', kind:'shunt', units:['s','min'], min:0, max:60, digits:0, color:'#81baf4', liveOnly:true},
    solarAge:     {label:'Regler: letzter Empfang', group:'Verbindung', kind:'solar', units:['s','min'], min:0, max:60, digits:0, color:'#81baf4', liveOnly:true},
    weather:      {label:'Wetter · 14 Tage',group:'Wetter',kind:'weather',units:[''],min:0,max:1,digits:0,color:'#e8bb57',widget:true}
  };
  const PHASES = {0:'Aus',1:'Wenig Leistung',2:'Fehler',3:'Bulk',4:'Absorption',5:'Float',6:'Storage',7:'Ausgleich',9:'Invertieren',11:'Stromversorgung',245:'Start',247:'Auto-Ausgleich',252:'Extern gesteuert'};
  const BLOCK_ORDER=['tiles','history','record'];
  const finite = x => x !== null && x !== '' && Number.isFinite(Number(x));
  const clamp = (x,a,b) => Math.max(a,Math.min(b,x));
  const numeric = (x,fallback,a,b) => finite(x) ? clamp(Number(x),a,b) : fallback;
  const color = (x,fallback) => /^#[0-9a-f]{6}$/i.test(x || '') ? x : fallback;
  function readableColor(foreground,background,page,opacity=100,target=7) {
    const rgb=hex=>{hex=hex.trim();if(/^#[0-9a-f]{3}$/i.test(hex))hex='#'+[...hex.slice(1)].map(c=>c+c).join('');return [1,3,5].map(i=>parseInt(hex.slice(i,i+2),16));};
    const luminance=channels=>channels.map(c=>c/255).map(c=>c<=.04045?c/12.92:((c+.055)/1.055)**2.4).reduce((n,c,i)=>n+c*[.2126,.7152,.0722][i],0);
    const back=rgb(background),base=rgb(page),alpha=clamp(opacity,0,100)/100,surface=back.map((c,i)=>c*alpha+base[i]*(1-alpha)),bg=luminance(surface),original=rgb(foreground);
    const whiteRatio=1.05/(bg+.05),blackRatio=(bg+.05)/.05,end=whiteRatio>blackRatio?255:0;
    for(let step=0;step<=100;step++){
      const channels=original.map(c=>Math.round(c+(end-c)*step/100)),fg=luminance(channels),ratio=(Math.max(bg,fg)+.05)/(Math.min(bg,fg)+.05);
      if(ratio>=target||step===100)return '#'+channels.map(c=>c.toString(16).padStart(2,'0')).join('');
    }
  }
  function unitValue(value,unit) {
    if (!finite(value)) return null;
    value=Number(value);
    if (['kW','kWh','h'].includes(unit)) return value/(unit==='h'?60:1000);
    if (unit==='d') return value/1440;
    if (unit==='s') return value*60;
    if (['mA','mV','mAh'].includes(unit)) return value*1000;
    if (unit==='°F') return value*1.8+32;
    return value;
  }
  function fromUnit(value,unit) {
    if (!finite(value)) return null;
    value=Number(value);
    if (['kW','kWh','h'].includes(unit)) return value*(unit==='h'?60:1000);
    if (unit==='d') return value*1440;
    if (unit==='s') return value/60;
    if (['mA','mV','mAh'].includes(unit)) return value/1000;
    if (unit==='°F') return (value-32)/1.8;
    return value;
  }
  // Reception age has seconds as its base; remaining runtime has minutes.
  function convert(value,unit,metric) {
    return /Age$/.test(metric) ? finite(value) ? Number(value)/(unit==='min'?60:1) : null : unitValue(value,unit);
  }
  function unconvert(value,unit,metric) {
    return /Age$/.test(metric) ? finite(value) ? Number(value)*(unit==='min'?60:1) : null : fromUnit(value,unit);
  }
  function position(input={},wide=false) {
    const snap=x=>Math.round(x*2)/2,w=snap(numeric(input.w,wide?4:6,2,12));
    return {x:snap(numeric(input.x,0,0,12-w)), y:snap(numeric(input.y,0,0,1000)), w,h:snap(numeric(input.h,3,2,18))};
  }
  function tile(metric,id) {
    const m=CATALOG[metric] || CATALOG.soc;
    return {id:id || 'tile-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,7),metric:CATALOG[metric]?metric:'soc',title:'',unit:m.units[0],type:m.widget?'weather':m.energy?'chart':metric==='soc'?'gauge':'number',
      digits:m.digits,fontSize:32,color:m.color,colorMid:'#81baf4',colorEnd:'#e8bb57',adaptiveColor:true,multicolor:false,invert:false,reverseSign:metric==='dischargeEnergy',min:m.min,max:m.max,scaleMode:m.energy?'auto':'fixed',valueMode:'history',background:'auto',opacity:100,
      period:{mode:m.energy?'today':'live',amount:6,unit:'h',aggregate:m.energy?'last':'avg',resetAt:Date.now(),from:0,to:0},layouts:{compact:position(m.widget?{w:12,h:4}:{h:m.energy?6:3}),wide:position(m.widget?{w:12,h:4}:{h:m.energy?6:3},true)}};
  }
  function normalizeTile(input,seen) {
    if (!input || !CATALOG[input.metric]) return null;
    const fallback=tile(input.metric),m=CATALOG[input.metric],result={...fallback};
    result.id=/^[a-zA-Z0-9_-]{1,90}$/.test(input.id || '') && !seen.has(input.id)?input.id:fallback.id;seen.add(result.id);
    result.title=typeof input.title==='string'?input.title.slice(0,60):'';
    result.unit=m.units.includes(input.unit)?input.unit:m.units[0];
    result.type=!m.status && ['number','gauge','chart'].includes(input.type)?input.type:'number';
    result.digits=Math.round(numeric(input.digits,m.digits,0,3));result.fontSize=numeric(input.fontSize,32,18,64);
    for (const k of ['color','colorMid','colorEnd']) result[k]=color(input[k],fallback[k]);
    result.multicolor=!!input.multicolor;result.invert=!!input.invert;result.adaptiveColor=input.adaptiveColor!==false;
    result.min=numeric(input.min,m.min,-1e9,1e9);result.max=numeric(input.max,m.max,-1e9,1e9);
    if(result.max<=result.min){result.min=m.min;result.max=m.max;}
    result.scaleMode=input.scaleMode==='auto'?'auto':'fixed';
    result.valueMode=input.valueMode==='live'?'live':'history';
    result.reverseSign=!!m.energy&&input.reverseSign===true;
    result.opacity=numeric(input.opacity,100,0,100);result.background=input.background==='auto'?'auto':color(input.background,'auto');
    const p=input.period || {};
    result.period={mode:!m.liveOnly && ['live','last','today','sunrise','reset','custom'].includes(p.mode)?p.mode:'live',amount:numeric(p.amount,6,1,10000),unit:['min','h','d'].includes(p.unit)?p.unit:'h',
      aggregate:['last','avg','min','max'].includes(p.aggregate)?p.aggregate:'avg',resetAt:numeric(p.resetAt,Date.now(),0,9e15),from:numeric(p.from,0,0,9e15),to:numeric(p.to,0,0,9e15)};
    if(m.status)result.period.aggregate='last';
    if(m.energy){result.valueMode='history';result.period.aggregate='last';if(result.period.mode==='live')result.period.mode='today';}
    result.layouts={compact:position(input.layouts?.compact),wide:position(input.layouts?.wide,true)};
    if(m.widget){result.type='weather';result.period.mode='live';for(const p of Object.values(result.layouts)){p.w=Math.max(4,p.w);p.h=Math.max(4,p.h);p.x=Math.min(p.x,12-p.w);}}
    return result;
  }
  function defaults() {
    const ids=['soc','solar','energyBalance','solarEnergy','voltage','power','weather','dischargeEnergy'];
    const layouts={
      compact:[[0,0,4,6],[4,0,8,6],[0,6,6,6],[6,6,6,6],[0,12,4,3],[0,15,4,3],[0,18,12,4],[4,12,8,6]],
      wide:[[0,0,2,4],[2,0,2,8],[7,0,3,8],[4,0,3,8],[0,4,2,2],[0,6,2,2],[0,8,12,4],[10,0,2,8]]
    };
    return {version:1,dischargeSignVersion:1,blockOrder:[...BLOCK_ORDER],tiles:ids.map((id,i)=>{
      const t=tile(id,'default-'+id);
      for(const profile of ['compact','wide']){const [x,y,w,h]=layouts[profile][i];t.layouts[profile]={x,y,w,h};}
      if(id==='soc'){t.fontSize=20;t.scaleMode='auto';}
      if(id==='solar'){t.type='chart';t.scaleMode='auto';t.valueMode='live';t.period.mode='sunrise';}
      if(id==='solarEnergy')t.period.mode='sunrise';
      if(id==='power')t.title='Aktuell';
      return t;
    })};
  }
  function normalize(input) {
    if (!input || !Array.isArray(input.tiles)) return defaults();
    const seen=new Set();
    const order=Array.isArray(input.blockOrder)?input.blockOrder.filter(k=>BLOCK_ORDER.includes(k)):[];
    let weatherSeen=false;
    const tiles=input.tiles.slice(0,64).map(t=>normalizeTile(t,seen)).filter(t=>{if(!t)return false;if(t.metric!=='weather')return true;if(weatherSeen)return false;weatherSeen=true;return true;});
    if(Array.isArray(input.blockOrder)&&input.blockOrder.includes('weather')&&!weatherSeen&&tiles.length<64){
      const w=tile('weather',seen.has('default-weather')?undefined:'default-weather');
      for(const profile of ['compact','wide'])w.layouts[profile].y=tiles.length?Math.max(...tiles.map(t=>t.layouts[profile].y+t.layouts[profile].h)):0;
      tiles.push(w);
    }
    if(input.dischargeSignVersion!==1)for(const t of tiles)if(t.metric==='dischargeEnergy')t.reverseSign=true;
    return {version:1,dischargeSignVersion:1,blockOrder:[...new Set([...order,...BLOCK_ORDER])],tiles};
  }
  // Compare the two calculated cumulative curves at common timestamps only.
  // Missing coverage stays a break; neither source is extended outside its data.
  function energyBalance(solar={},battery={}) {
    solar=solar||{};battery=battery||{};
    const empty={points:[],summary:{last:null}};
    for(const key of ['from','to'])if(finite(solar.summary?.[key])&&finite(battery.summary?.[key])&&Number(solar.summary[key])!==Number(battery.summary[key]))return empty;
    const prepare=series=>{
      const out=[];
      for(const p of [...(Array.isArray(series.points)?series.points:[])].filter(p=>p&&finite(p.t)).sort((a,b)=>a.t-b.t)){
        const point={t:Number(p.t),v:finite(p.v)&&Number(p.v)>=0?Number(p.v):null,gap:!!p.gap};
        if(out.length&&out[out.length-1].t===point.t){point.gap=point.gap||out[out.length-1].gap;out[out.length-1]=point;}else out.push(point);
      }return out;
    };
    const a=prepare(solar),b=prepare(battery);
    const at=(series,t)=>{
      let lo=0,hi=series.length;
      while(lo<hi){const mid=(lo+hi)>>1;if(series[mid].t<t)lo=mid+1;else hi=mid;}
      const right=series[lo];if(right?.t===t)return right.v==null?null:right;
      const left=series[lo-1];
      return left&&right&&!right.gap&&left.v!=null&&right.v!=null?{v:left.v+(right.v-left.v)*(t-left.t)/(right.t-left.t),gap:false}:null;
    };
    const times=[...new Set([...a,...b].map(p=>p.t))].sort((a,b)=>a-b),points=[];
    let gap=true;
    for(const t of times){const x=at(a,t),y=at(b,t);if(!x||!y){gap=true;continue;}
      const v=x.v-y.v||0;points.push({t,last:t,v,min:v,max:v,n:1,gap:gap||x.gap||y.gap,kind:'balance',device:'calculated-balance',origin:'calculated'});gap=false;
    }
    const x=solar.summary?.last,y=battery.summary?.last;
    return {points,summary:{last:finite(x)&&finite(y)&&x>=0&&y>=0?Number(x)-Number(y)||0:null}};
  }
  function windowFor(t,now=Date.now(),weather={}) {
    if(CATALOG[t.metric].widget)return null;
    const p=t.period;
    if(p.mode==='live' && t.type!=='chart') return null;
    if(p.mode==='sunrise')return miniWindow('sunrise',weather,now);
    let from,to=now;
    if(p.mode==='today'){const d=new Date(now);d.setHours(0,0,0,0);from=d.getTime();}
    else if(p.mode==='reset')from=p.resetAt;
    else if(p.mode==='custom'){from=p.from;to=Math.min(p.to,now);}
    else from=now-(p.mode==='live'?6*3600000:p.amount*({min:60000,h:3600000,d:86400000}[p.unit]));
    return finite(from) && finite(to) && from>=0 && to>from ? {from,to} : {from:now,to:now};
  }
  function liveValue(metric,shunt,solar,snapshot,now=Date.now()) {
    const m=CATALOG[metric],r=m.kind==='shunt'?shunt:solar;
    if(/Age$|Rssi$/.test(metric)) {
      const config=snapshot.devices.find(d=>d.kind===m.kind);
      if(!config || !snapshot.running)return null;
      if(/Age$/.test(metric)){const last=snapshot.readings.find(d=>d.device===config.mac);return last?Math.max(0,(now-last.t)/1000):null;}
      return r && finite(config.rssi)?config.rssi:null;
    }
    if(!r)return null;
    const capacity=snapshot.settings.capacity || 100;
    if(metric==='remainingAh')return finite(r.soc)?r.soc/100*capacity:null;
    if(metric==='remainingWh')return finite(r.soc)&&finite(r.voltage)?r.soc/100*capacity*r.voltage:null;
    const v=r[m.field];
    return finite(v)?metric==='consumed'?Math.abs(v):Number(v):null;
  }
  function historicalValue(metric,value,capacity) {
    if(!finite(value))return null;
    return (metric==='remainingAh'?Number(value)/100:metric==='remainingWh'?Number(value):Number(value))*(['remainingAh','remainingWh'].includes(metric)?capacity:1);
  }
  function usesLiveValue(t) { return !CATALOG[t.metric].energy&&(t.type==='chart'?t.valueMode==='live':t.period.mode==='live'); }
  function signedValue(t,value) { return finite(value)?Number(value)===0?0:Number(value)*(CATALOG[t.metric].energy&&t.reverseSign?-1:1):null; }
  function fixedRange(t) {
    const min=convert(t.min,t.unit,t.metric),max=convert(t.max,t.unit,t.metric);
    return CATALOG[t.metric].energy&&t.reverseSign?{min:signedValue(t,max),max:signedValue(t,min)}:{min,max};
  }
  function displayValue(t,summary,shunt,solar,snapshot) {
    return signedValue(t,usesLiveValue(t)?liveValue(t.metric,shunt,solar,snapshot):historicalValue(t.metric,summary?.[t.period.aggregate],snapshot.settings.capacity));
  }
  function format(value,t) {
    if(!finite(value))return '—';
    if(t.metric==='state')return PHASES[value] || 'Code '+value;
    if(t.metric==='error')return Number(value)===0?'Kein Fehler':String(value);
    if(t.metric==='alarm')return Number(value)===0?'Kein Alarm':'0x'+Number(value).toString(16).toUpperCase();
    return Number(value).toLocaleString('de-DE',{minimumFractionDigits:t.digits,maximumFractionDigits:t.digits});
  }
  /** Uses actual extrema, including peaks hidden by chart buckets; manual bounds remain stored. */
  function chartRange(t,points=[],capacity=100,summary={}) {
    const fixed={...fixedRange(t),auto:false};
    if(t.scaleMode!=='auto')return fixed;
    const values=[summary.min,summary.max,...points.flatMap(p=>[p.min,p.max,p.v])].filter(finite).map(v=>signedValue(t,historicalValue(t.metric,v,capacity)));
    if(!values.length)return fixed;
    const dataLow=Math.min(...values),dataHigh=Math.max(...values);
    const upper=v=>{const step=10**(Math.floor(Math.log10(v))-1);return Number((Math.ceil(v/step-1e-9)*step).toPrecision(12));};
    let low,high;
    if(['solar','yieldWh','solarEnergy','dischargeEnergy','energyBalance','solarCurrent','load','remaining','remainingAh','remainingWh','consumed','power','current'].includes(t.metric)){
      low=dataLow<0?-upper(-dataLow*1.1):0;high=dataHigh>0?upper(dataHigh*1.1):dataLow<0?0:1;
    }else{
      const span=dataHigh-dataLow||Math.max(Math.abs(dataHigh)*.02,.1),step=10**(Math.floor(Math.log10(span))-1),pad=span*.1;
      low=Number((Math.floor((dataLow-pad)/step+1e-9)*step).toPrecision(12));high=Number((Math.ceil((dataHigh+pad)/step-1e-9)*step).toPrecision(12));
      if(dataLow>=0&&CATALOG[t.metric].min>=0)low=Math.max(0,low);
      if(t.metric==='soc'&&dataLow>=0&&dataHigh<=100)high=Math.min(100,high);
    }
    return {min:convert(low,t.unit,t.metric),max:convert(high,t.unit,t.metric),auto:true};
  }
  function axisLabel(value,span){
    const digits=Math.max(0,Math.min(8,1-Math.floor(Math.log10(Math.abs(span)||1))));
    return Number(value).toLocaleString('de-DE',{maximumFractionDigits:digits});
  }
  function miniWindow(mode,weather={},now=Date.now()) {
    const midnight=new Date(now);midnight.setHours(0,0,0,0);
    if(mode==='last6h')return {from:now-6*3600000,to:now,label:'Letzte 6 Stunden',note:''};
    const today={from:midnight.getTime(),to:now,label:'Heute',note:''};
    if(mode==='today')return today;
    try {
      const dayFormat=new Intl.DateTimeFormat('en-CA',{timeZone:weather.timezone||undefined,year:'numeric',month:'2-digit',day:'2-digit'});
      const day=dayFormat.format(new Date(now)),rise=(weather.days||[]).find(d=>d.day===day)?.sunrise;
      if(finite(rise)&&Number(rise)>0&&dayFormat.format(new Date(Number(rise)))===day){
        if(Number(rise)<now)return {from:Number(rise),to:now,label:'Seit Sonnenaufgang',note:'Sonnenaufgang am gewählten Wetterort.'};
        return {...today,note:'Heute: Der Sonnenaufgang am Wetterort steht noch bevor.'};
      }
    }catch(_){}
    return {...today,note:'Heute: Sonnenaufgang nicht verfügbar. Wetterort wählen oder Wetter aktualisieren.'};
  }
  return {CATALOG,PHASES,BLOCK_ORDER,finite,clamp,color,readableColor,convert,unconvert,position,tile,defaults,normalize,energyBalance,windowFor,liveValue,historicalValue,usesLiveValue,signedValue,fixedRange,displayValue,format,chartRange,axisLabel,miniWindow};
});
