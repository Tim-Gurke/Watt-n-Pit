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
    solarVoltage: {label:'Regler-Batteriespannung', group:'Solar', kind:'solar', field:'voltage', units:['V','mV'], min:10, max:15, digits:2, color:'#81baf4', hint:'Batterieseitige Spannung am SmartSolar; keine PV-Eingangsspannung.'},
    solarCurrent: {label:'Solar-Ladestrom', group:'Solar', kind:'solar', field:'current', units:['A','mA'], min:0, max:20, digits:2, color:'#e8bb57'},
    load:         {label:'Lastausgang-Strom', group:'Solar', kind:'solar', field:'load', units:['A','mA'], min:0, max:20, digits:2, color:'#e8bb57'},
    state:        {label:'Ladephase', group:'Solar', kind:'solar', field:'state', units:[''], min:0, max:255, digits:0, color:'#e8bb57', status:true},
    error:        {label:'Reglerfehler', group:'Solar', kind:'solar', field:'error', units:['Code'], min:0, max:255, digits:0, color:'#e8bb57', status:true},
    shuntRssi:    {label:'Shunt-Signal', group:'Verbindung', kind:'shunt', units:['dBm'], min:-100, max:-30, digits:0, color:'#81baf4', liveOnly:true},
    solarRssi:    {label:'Regler-Signal', group:'Verbindung', kind:'solar', units:['dBm'], min:-100, max:-30, digits:0, color:'#81baf4', liveOnly:true},
    shuntAge:     {label:'Shunt: letzter Empfang', group:'Verbindung', kind:'shunt', units:['s','min'], min:0, max:60, digits:0, color:'#81baf4', liveOnly:true},
    solarAge:     {label:'Regler: letzter Empfang', group:'Verbindung', kind:'solar', units:['s','min'], min:0, max:60, digits:0, color:'#81baf4', liveOnly:true}
  };
  const PHASES = {0:'Aus',1:'Wenig Leistung',2:'Fehler',3:'Bulk',4:'Absorption',5:'Float',6:'Storage',7:'Ausgleich',9:'Invertieren',11:'Stromversorgung',245:'Start',247:'Auto-Ausgleich',252:'Extern gesteuert'};
  const finite = x => x !== null && x !== '' && Number.isFinite(Number(x));
  const clamp = (x,a,b) => Math.max(a,Math.min(b,x));
  const numeric = (x,fallback,a,b) => finite(x) ? clamp(Number(x),a,b) : fallback;
  const color = (x,fallback) => /^#[0-9a-f]{6}$/i.test(x || '') ? x : fallback;
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
    const w=Math.round(numeric(input.w,wide?4:6,2,12));
    return {x:Math.round(numeric(input.x,0,0,12-w)), y:Math.round(numeric(input.y,0,0,1000)), w,h:Math.round(numeric(input.h,3,2,18))};
  }
  function tile(metric,id) {
    const m=CATALOG[metric] || CATALOG.soc;
    return {id:id || 'tile-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,7),metric:CATALOG[metric]?metric:'soc',title:'',unit:m.units[0],type:metric==='soc'?'gauge':'number',
      digits:m.digits,fontSize:32,color:m.color,colorMid:'#81baf4',colorEnd:'#e8bb57',adaptiveColor:true,multicolor:false,invert:false,min:m.min,max:m.max,background:'auto',opacity:100,
      period:{mode:'live',amount:6,unit:'h',aggregate:'avg',resetAt:Date.now(),from:0,to:0},layouts:{compact:position({}),wide:position({},true)}};
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
    result.opacity=numeric(input.opacity,100,0,100);result.background=input.background==='auto'?'auto':color(input.background,'auto');
    const p=input.period || {};
    result.period={mode:!m.liveOnly && ['live','last','today','reset','custom'].includes(p.mode)?p.mode:'live',amount:numeric(p.amount,6,1,10000),unit:['min','h','d'].includes(p.unit)?p.unit:'h',
      aggregate:['last','avg','min','max'].includes(p.aggregate)?p.aggregate:'avg',resetAt:numeric(p.resetAt,Date.now(),0,9e15),from:numeric(p.from,0,0,9e15),to:numeric(p.to,0,0,9e15)};
    if(m.status)result.period.aggregate='last';
    result.layouts={compact:position(input.layouts?.compact),wide:position(input.layouts?.wide,true)};
    return result;
  }
  function defaults() {
    const ids=['soc','remaining','yieldWh','solar','power','voltage','current'];
    const compact=[[0,0,6,6],[6,0,6,3],[6,3,6,3],[0,6,6,4],[6,6,6,4],[0,10,6,3],[6,10,6,3]];
    const wide=[[0,0,4,6],[4,0,4,3],[8,0,4,3],[4,3,4,3],[8,3,4,3],[0,6,6,3],[6,6,6,3]];
    return {version:1,tiles:ids.map((id,i)=>{const t=tile(id,'default-'+id);for(const [profile,list] of [['compact',compact],['wide',wide]]) {const [x,y,w,h]=list[i];t.layouts[profile]={x,y,w,h};}return t;})};
  }
  function normalize(input) {
    if (!input || !Array.isArray(input.tiles)) return defaults();
    const seen=new Set();
    return {version:1,tiles:input.tiles.slice(0,64).map(t=>normalizeTile(t,seen)).filter(Boolean)};
  }
  function windowFor(t,now=Date.now()) {
    const p=t.period;
    if(p.mode==='live' && t.type!=='chart') return null;
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
  function format(value,t) {
    if(!finite(value))return '—';
    if(t.metric==='state')return PHASES[value] || 'Code '+value;
    if(t.metric==='error')return Number(value)===0?'Kein Fehler':String(value);
    if(t.metric==='alarm')return Number(value)===0?'Kein Alarm':'0x'+Number(value).toString(16).toUpperCase();
    return Number(value).toLocaleString('de-DE',{minimumFractionDigits:t.digits,maximumFractionDigits:t.digits});
  }
  return {CATALOG,PHASES,finite,clamp,color,convert,unconvert,position,tile,defaults,normalize,windowFor,liveValue,historicalValue,format};
});
