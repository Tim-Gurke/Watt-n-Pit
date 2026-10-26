'use strict';
const $ = id => document.getElementById(id);
const deviceNative=window.WattPit||null;
if(typeof deviceNative?.appVersion==='function'){
  const version=deviceNative.appVersion();
  document.querySelectorAll('[data-app-version]').forEach(element=>element.textContent=version);
}
let readRepaint,readsDirty=false;
function repaintReads(){
  if(!readsDirty||lifecycle?.blocked)return;
  readsDirty=false;dashboard?.cache.clear();lastMiniLoad=0;
  if(page==='overview'){renderOverview();loadMini(true);}
  if(page==='history')loadHistory();
  dashboard?.detail?.refresh(false);
}
const reads=deviceNative&&window.WattReadBridge?new window.WattReadBridge(deviceNative,method=>{
  if(method==='dailyEnergy'){if(page==='history')renderDaily();return;}
  readsDirty=true;clearTimeout(readRepaint);readRepaint=setTimeout(repaintReads,80);
}):null;
const native=reads?.bridge||deviceNative;
window.onHistoryResult=result=>reads?.result(result);
const dataTime=()=>lifecycle?.busy?lifecycle.startedAt||Date.now():Date.now();
const icons = {
  sun:'<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.4 1.4m11.2 11.2L19 19M5 19l1.4-1.4M17.6 6.4 19 5"/>',
  moon:'<path d="M20 14a8.2 8.2 0 0 1-10-10A8.5 8.5 0 1 0 20 14Z"/>',
  battery:'<rect x="3" y="6" width="17" height="12" rx="3"/><path d="M22 10v4M7 12h9m-4-4v8"/>',
  solar:'<path d="m5 9-2 10h18L19 9ZM6 14h12M10 9l-1 10m5-10 1 10M12 2v3M5 4l2 2m12-2-2 2"/>',
  overview:'<rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/>',
  history:'<path d="M3 3v18h18M6 15l4-5 4 3 6-8"/>',
  devices:'<path d="M7 7l10 10-5 5V2l5 5L7 17"/><path d="M5 5v2m14 10v2"/>',
  settings:'<circle cx="12" cy="12" r="3.5"/><polygon points="20.00,12.00 19.96,12.78 21.81,13.95 21.57,14.90 21.24,15.83 20.82,16.71 18.65,16.44 18.18,17.08 17.66,17.66 17.08,18.18 17.56,20.31 16.71,20.82 15.83,21.24 14.90,21.57 13.56,19.85 12.78,19.96 12.00,20.00 11.22,19.96 10.05,21.81 9.10,21.57 8.17,21.24 7.29,20.82 7.56,18.65 6.92,18.18 6.34,17.66 5.82,17.08 3.69,17.56 3.18,16.71 2.76,15.83 2.43,14.90 4.15,13.56 4.04,12.78 4.00,12.00 4.04,11.22 2.19,10.05 2.43,9.10 2.76,8.17 3.18,7.29 5.35,7.56 5.82,6.92 6.34,6.34 6.92,5.82 6.44,3.69 7.29,3.18 8.17,2.76 9.10,2.43 10.44,4.15 11.22,4.04 12.00,4.00 12.78,4.04 13.95,2.19 14.90,2.43 15.83,2.76 16.71,3.18 16.44,5.35 17.08,5.82 17.66,6.34 18.18,6.92 20.31,6.44 20.82,7.29 21.24,8.17 21.57,9.10 19.85,10.44 19.96,11.22"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',
  import:'<path d="M12 3v12m-4-4 4 4 4-4M4 16v5h16v-5"/>',
  play:'<path d="m8 4 12 8-12 8Z"/>',
  pause:'<path d="M8 5v14M16 5v14"/>',
};
const icon = name => `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${icons[name] || icons.battery}</svg>`;
const escapeHtml = text => String(text == null ? '' : text).replace(/[&<>"']/g,c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const num = (value, digits=0) => value == null || !Number.isFinite(Number(value)) ? '—' : Number(value).toLocaleString('de-DE',{minimumFractionDigits:digits,maximumFractionDigits:digits});
const valueUnit = (value,unit,digits=0) => `${num(value,digits)} <small>${unit}</small>`;
function formatRemaining(minutes) { const total=Math.max(0,Math.round(minutes)),h=Math.floor(total/60),m=total%60; return `${h ? h+' <small>h</small> ' : ''}${m||!h ? m+' <small>min</small>' : ''}`.trim(); }
const clock = t => new Date(t).toLocaleTimeString('de-DE',{hour:'2-digit',minute:'2-digit'});
const date = t => new Date(t).toLocaleDateString('de-DE',{day:'2-digit',month:'2-digit'});
let snapshot = {running:false,status:'Aufzeichnung pausiert',devices:[],found:[],readings:[],stats:{count:0},settings:{theme:'dark',capacity:100,sampleSeconds:10}};
let page = 'overview', demo = false, lastEvent = 0, settingsDirty = false, lastMiniLoad = 0, miniPoints = [], miniWindow = null, miniKey = '', energyData = null;
let toastTimer, previousToast = '', previousToastTime = 0, deviceRenderKey = '';
let metric = 'power', source = 'all', range = 'today', baseFrom, baseTo, viewFrom, viewTo, points = [], manualY = null, scale = null, loadTimer, selectedPoint = null;
let currentImport = null, lastHistoryRevision=-1, historyPending=false, archiveRequestedUntil=0;
let dashboard = null, extras = null, equipment = null, configuration = null, lifecycle = null, solar = null, roller = null;
const overviewLandscape = window.matchMedia('(orientation: landscape)');

function syncOverviewHeader() {
  const compact = page === 'overview' && overviewLandscape.matches;
  const header = document.querySelector('.topbar'), head = $('overviewHeader'), toolbar = $('dashboardToolbar');
  header.classList.toggle('with-overview',compact);
  document.body.classList.toggle('overview-landscape',compact);
  // Move the same controls so handlers, state and focus survive rotation.
  if (compact) {
    header.insertBefore(head,$('themeToggle'));
    header.insertBefore(toolbar,$('themeToggle'));
  } else {
    $('overview').prepend(head);
    $('welcome').after(toolbar);
  }
}
overviewLandscape.addListener(syncOverviewHeader);
syncOverviewHeader();

function toast(message) {
  if (!message || (message === previousToast && Date.now()-previousToastTime < 4000)) return;
  previousToast = message; previousToastTime = Date.now();
  $('toast').textContent = message; $('toast').hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => $('toast').hidden = true,5500);
}
function showPage(next) {
  dashboard?.detail?.close();
  page = next;
  syncOverviewHeader();
  document.querySelectorAll('.page').forEach(el => el.classList.toggle('active',el.id===page));
  document.querySelectorAll('.bottom-nav button').forEach(el => el.classList.toggle('active',el.dataset.page===page));
  window.scrollTo(0,0);
  if (page === 'history') loadHistory();
  dashboard?.updateChargeEstimate();
  extras?.render();equipment?.render();roller?.render();
  if (page === 'overview') loadMini(true);
  if (page === 'devices') renderDevices(true);
  if (page === 'settings') renderSettings();
}
function theme(next,save=true) {
  document.documentElement.dataset.theme = next;
  $('themeToggle').innerHTML = icon(next === 'dark' ? 'sun' : 'moon');
  snapshot.settings.theme = next;
  document.querySelectorAll('#themeButtons button').forEach(b => b.classList.toggle('selected',b.dataset.theme===next));
  if (save && native) { const error = native.settings(JSON.stringify({theme:next})); if(error) toast(error); }
  if (save && !native) localStorage.setItem('wattpit-preview-theme',next);
  if (page === 'history') drawHistory();
  if (page === 'overview') {drawMini();dashboard?.update();}
}
function setDemo(enabled) {
  demo = enabled; $('demoBar').hidden = !enabled; lastMiniLoad = 0;
  $('settingsDemo').textContent = enabled ? 'Demo verlassen' : 'Demo ansehen';
  dashboard?.clearCache(); renderOverview(); loadMini(true); if(page==='history') loadHistory();
}
function reading(kind) {
  if (demo) return kind === 'shunt' ? {kind, t:Date.now(),soc:76.4,voltage:12.94,current:-2.2,power:-28.468,ttg:480,consumed:-23.6,temperature:null,aux:null,alarm:0}
    : {kind,t:Date.now(),voltage:13.04,current:10.5,solar:138,yieldWh:760,state:3,error:0,load:0};
  const config = snapshot.devices.find(d => d.kind === kind);
  if (!config || config.error || !snapshot.running) return null;
  const data = snapshot.readings.find(r => r.device === config.mac);
  return data && Date.now()-data.t < 45000 ? data : null;
}
function renderOverview() {
  const shunt = reading('shunt'), solar = reading('solar');
  $('todayLabel').textContent = new Date().toLocaleDateString('de-DE',{weekday:'long',day:'numeric',month:'long'}).toUpperCase();
  $('welcome').hidden = snapshot.devices.length > 0 || demo;
  const status = $('liveStatus'), any = shunt || solar;
  status.className = 'status-chip' + (demo ? ' warning' : any ? ' live' : snapshot.running ? ' warning' : '');
  status.querySelector('span').textContent = demo ? 'Demo' : any ? 'Live' : snapshot.running ? 'Warte auf Geräte' : 'Pausiert';
  const direction = shunt?.current == null ? 'Noch kein Livewert' : shunt.current > .03 ? 'Akku wird geladen' : shunt.current < -.03 ? 'Akku wird entladen' : 'Bilanz ausgeglichen';
  $('batteryState').innerHTML = `<i class="dot ${shunt?.alarm ? 'orange' : 'mint'}"></i>${shunt?.alarm ? `Shunt-Alarm 0x${shunt.alarm.toString(16).toUpperCase()}` : direction}`;
  $('lastSeen').textContent = shunt ? (demo ? 'Beispieldaten' : `Stand ${clock(shunt.t)}`) : 'Kein Empfang';
  $('overviewRecord').classList.toggle('active',snapshot.running && !demo);
  $('overviewRecord').disabled = demo;
  $('recordIcon').innerHTML = icon(snapshot.running ? 'pause' : 'play');
  $('recordText').textContent = demo ? 'Demo · keine echten Messwerte' : snapshot.running ? 'Aufzeichnung pausieren' : 'Aufzeichnung starten';
  $('overviewRecord').setAttribute('aria-label',$('recordText').textContent);$('overviewRecord').title=$('recordText').textContent;
  const loading=!!snapshot.archiveBusy||!!snapshot.archive?.active||!!lifecycle?.busy;
  $('reloadArchive').disabled=demo||!snapshot.devices.length||loading||Date.now()<archiveRequestedUntil;
  $('reloadArchiveText').textContent=loading?'Nachladen läuft …':'Daten nachladen';
  dashboard?.update();
}
function renderDevices(force=false) {
  const key = JSON.stringify([snapshot.devices.map(d=>({...d,seen:!!d.seen,fresh:snapshot.running && Date.now()-d.seen<45000})),snapshot.found.map(f=>({...f,seen:0})),snapshot.running,snapshot.status]);
  if (!force && key === deviceRenderKey) return;
  deviceRenderKey = key;
  $('deviceCards').innerHTML = snapshot.devices.length ? snapshot.devices.map(d => {
    const fresh = snapshot.running && !d.error && d.seen && Date.now()-d.seen<45000;
    return `<article class="card device-card"><div class="device-title"><div class="device-type"><div class="tile-icon ${d.kind==='solar'?'orange':'mint'}">${icon(d.kind==='solar'?'solar':'battery')}</div><div><h2>${escapeHtml(d.name)}</h2><p class="muted">${d.kind==='solar'?'Solarladeregler':'Batteriewächter'}${d.rssi != null ? ` · ${d.rssi} dBm` : ''}</p></div></div><button class="textbutton edit-device" data-mac="${escapeHtml(d.mac)}">Bearbeiten</button></div><div class="device-meta"><code>${escapeHtml(d.mac)}</code><span class="${fresh?'mint':''}">${fresh?'● Live':'○ Kein Liveempfang'}</span></div>${d.bondState?`<p class="bond-state muted">${({bonded:'Android-Kopplung vorhanden',bonding:'Android-Kopplung läuft · PIN bestätigen',none:'Noch nicht mit Android gekoppelt · PIN beim Archivimport',unknown:'Android-Kopplung noch nicht geprüft'})[d.bondState]||''}</p>`:''}${d.error?`<p class="device-error">${escapeHtml(d.error)}</p>`:''}<button class="quiet archive-import" ${snapshot.archive?.active||demo?'disabled':''} data-mac="${escapeHtml(d.mac)}">Gerätearchiv ergänzen</button></article>`;
  }).join('') : '<div class="empty-devices"><h2>Platz für deine Energieanlage.</h2><p>Starte die Suche oder füge SmartSolar und SmartShunt manuell hinzu.</p></div>';
  $('deviceCards').querySelectorAll('.edit-device').forEach(b => b.onclick = () => deviceForm(snapshot.devices.find(d => d.mac===b.dataset.mac)));
  $('scanStatus').textContent = snapshot.running ? snapshot.status : 'Die Suche startet mit der Aufzeichnung.';
  $('scanButton').textContent = snapshot.running ? 'Pausieren' : 'Suche starten';
  const found = snapshot.found.filter(f => !snapshot.devices.some(d => d.mac===f.mac));
  $('foundDevices').innerHTML = found.length ? found.map(f => `<div class="found-device"><div><strong>${escapeHtml(f.name)}</strong><p>${f.kind==='solar'?'SmartSolar':'SmartShunt'} · ${escapeHtml(f.mac)} · ${f.rssi} dBm</p></div><button class="use-found" data-mac="${escapeHtml(f.mac)}">Schlüssel<br>eintragen</button></div>`).join('') : `<p class="muted" style="margin-top:16px">${snapshot.running?'Noch kein weiteres Gerät mit Sofortanzeige empfangen.':'Zum Suchen Bluetooth und Sofortanzeige einschalten.'}</p>`;
  $('foundDevices').querySelectorAll('.use-found').forEach(b => b.onclick = () => deviceForm(snapshot.found.find(d => d.mac===b.dataset.mac),true));
}
function renderSettings() {
  if (!settingsDirty) { $('capacitySetting').value = snapshot.settings.capacity; $('sampleSetting').value = snapshot.settings.sampleSeconds; }
  document.querySelectorAll('#themeButtons button').forEach(b => b.classList.toggle('selected',b.dataset.theme===snapshot.settings.theme));
}
function poll() {
  if(lifecycle?.poll())return;
  if (native) {
    try {
      const result = JSON.parse(native.snapshot());
      if(result.syncing&&lifecycle?.poll())return;
      if (result.devices) snapshot = result;
      if(snapshot.historyRevision!=null&&snapshot.historyRevision!==lastHistoryRevision){lastHistoryRevision=snapshot.historyRevision;dashboard?.clearCache();lastMiniLoad=0;if(page==='history'&&!demo)loadHistory();}
      if (snapshot.eventId && snapshot.eventId !== lastEvent) { toast(snapshot.event); lastEvent = snapshot.eventId; }
      if (document.documentElement.dataset.theme !== snapshot.settings.theme) theme(snapshot.settings.theme,false);
    } catch (error) { console.error('Snapshot unavailable',error); }
  }
  extras?.render();equipment?.render();roller?.render();
  if (page === 'overview') { renderOverview(); loadMini(); }
  if (page === 'devices') renderDevices();
  if (page === 'settings') renderSettings();
  if (page === 'history') {
    renderDaily();
    const count = snapshot.stats?.count || 0;
    $('historyStats').innerHTML = `<span class="info-symbol">i</span><p>${demo?'Demo zeigt erfundene Beispieldaten.':count?`${num(count)} gespeicherte Messpunkte${snapshot.stats.archiveCount?` · ${num(snapshot.stats.archiveCount)} aus dem Gerätearchiv`:''}${snapshot.stats.from?` · ab ${date(snapshot.stats.from)}`:''}. Der Export enthält alle gespeicherten Daten.`:'Noch keine gespeicherten Messpunkte. Starte die Aufzeichnung oder ergänze das Gerätearchiv.'}</p>`;
  }
  repaintReads();
}
function toggleRecord() {
  if (!native) { toast('Bluetooth-Empfang ist in der Android-App verfügbar.'); return; }
  if (snapshot.running) native.stop(); else native.start();
  setTimeout(poll,400);
}
function reloadMissingData(){
  if(demo||!snapshot.devices.length||lifecycle?.busy||snapshot.archiveBusy||Date.now()<archiveRequestedUntil)return;
  if(!native?.importMissingArchive){toast('Daten nachladen ist in der Android-App verfügbar.');return;}
  archiveRequestedUntil=Date.now()+1500;renderOverview();native.importMissingArchive();setTimeout(poll,250);
}
let modalReturnScroll=0;
function openModal(title,content) {
  const sheet=$('modal').querySelector('.sheet'),keep=!$('modal').hidden&&$('modalTitle').textContent===title,position=keep?sheet.scrollTop:0;
  const expanded=keep?new Map([...sheet.querySelectorAll('details')].map(el=>[el.querySelector('summary')?.textContent,el.open])):null;
  if($('modal').hidden)modalReturnScroll=window.scrollY;
  document.dispatchEvent(new Event('watt-modal-close'));
  $('modalTitle').textContent = title; $('modalBody').innerHTML = content; $('modal').hidden = false;
  document.body.style.overflow = 'hidden';
  if(expanded)sheet.querySelectorAll('details').forEach(el=>{const key=el.querySelector('summary')?.textContent;if(expanded.has(key))el.open=expanded.get(key);});
  sheet.scrollTop=position;$('closeModal').focus({preventScroll:true});
  if(keep)requestAnimationFrame(()=>{if(!$('modal').hidden&&$('modalTitle').textContent===title)sheet.scrollTop=position;});
}
function closeModal() {
  if (currentImport?.busy) return;
  if (currentImport && native) native.cancelImport();
  document.dispatchEvent(new Event('watt-modal-close'));
  currentImport = null; $('modal').hidden = true; $('modalBody').innerHTML = ''; document.body.style.overflow = '';window.scrollTo(0,modalReturnScroll);
}
window.handleBack = () => {
  if(lifecycle?.back())return true;
  if (!$('modal').hidden) { closeModal(); return true; }
  if (dashboard?.detail?.active) { dashboard.detail.close(); return true; }
  if (page !== 'overview') { showPage('overview'); return true; }
  return false;
};
function deviceForm(device={},found=false) {
  const original = found ? '' : device.mac || '';
  openModal(original?'Gerät bearbeiten':'Gerät einrichten',`
    <p class="muted">Übernimm die Details der Sofortanzeige aus VictronConnect. Watt’n’Pit speichert den Schlüssel verschlüsselt auf diesem Telefon.</p>
    <label class="form-field">Gerätetyp<select id="deviceKind"><option value="shunt">SmartShunt · Batterie</option><option value="solar">SmartSolar · Solar</option></select></label>
    <label class="form-field">Name<input id="deviceName" maxlength="80" value="${escapeHtml(device.name||'')}" placeholder="z. B. SmartShunt 300 A"></label>
    <label class="form-field">Bluetooth-MAC-Adresse<input id="deviceMac" class="monospace" autocapitalize="characters" autocomplete="off" spellcheck="false" maxlength="32" value="${escapeHtml(device.mac||'')}" placeholder="AA:BB:CC:DD:EE:FF"></label>
    <label class="form-field">Verschlüsselungsschlüssel<input id="deviceKey" type="password" class="monospace" autocomplete="off" spellcheck="false" maxlength="80" placeholder="${original?'Leer lassen, um den Schlüssel zu behalten':'32 Hex-Zeichen aus VictronConnect'}"><small>Je Gerät ein eigener Schlüssel. Nicht die Bluetooth-PIN.</small></label>
    <p id="deviceError" class="form-error" hidden></p><div class="form-actions">${original?'<button class="quiet" id="removeDevice">Entfernen</button>':''}<button class="primary" id="saveDevice">Gerät speichern</button></div>`);
  $('deviceKind').value = device.kind || 'shunt';
  $('saveDevice').onclick = () => {
    if (!native) { toast('Geräte werden in der Android-App eingerichtet.'); return; }
    const error = native.saveDevice(JSON.stringify({original,kind:$('deviceKind').value,name:$('deviceName').value,mac:$('deviceMac').value,key:$('deviceKey').value}));
    if (error) { $('deviceError').textContent=error; $('deviceError').hidden=false; return; }
    closeModal(); poll(); renderDevices(true);
  };
  if (original) $('removeDevice').onclick = () => {
    openModal('Gerät entfernen?',`<p class="muted">${escapeHtml(device.name)} wird aus der Liveanzeige entfernt. Seine gespeicherten Messwerte bleiben im Verlauf.</p><div class="form-actions"><button class="quiet" id="keepDevice">Behalten</button><button class="primary" id="confirmRemove">Gerät entfernen</button></div>`);
    $('keepDevice').onclick=closeModal;
    $('confirmRemove').onclick=()=>{const error=native.removeDevice(original);if(error){toast(error);return;}closeModal();poll();renderDevices(true);};
  };
}

const metrics = {power:{title:'Solar & Batteriebilanz',unit:'W'},soc:{title:'Ladezustand',unit:'%'},voltage:{title:'Batteriespannung',unit:'V'},current:{title:'Strom am Gerät',unit:'A'},yieldWh:{title:'Tagesertrag des SmartSolar',unit:'Wh'},energy:{title:'Ertrag, Verbrauch & Bilanz',unit:'Wh'}};
function selectRange(next) {
  range=next; const now=new Date(), end=new Date(now); end.setHours(24,0,0,0);
  const begin=new Date(now);begin.setHours(0,0,0,0);
  if(next==='yesterday'){end.setDate(end.getDate()-1);begin.setDate(begin.getDate()-1);}
  if(next==='week')begin.setDate(begin.getDate()-6);
  if(next==='month')begin.setDate(begin.getDate()-29);
  if(next==='custom') {
    $('customRange').hidden=false;
    if(!$('rangeFrom').value){$('rangeFrom').value=localDateTime(baseFrom || begin.getTime());$('rangeTo').value=localDateTime(baseTo || end.getTime());}
  } else {$('customRange').hidden=true;setWindow(begin.getTime(),end.getTime());}
  document.querySelectorAll('#rangeButtons button').forEach(b=>b.classList.toggle('selected',b.dataset.range===next));
}
function localDateTime(t) { const d=new Date(t);return new Date(t-d.getTimezoneOffset()*60000).toISOString().slice(0,16); }
function setWindow(from,to) {
  if(!Number.isFinite(from)||!Number.isFinite(to)||to<=from){toast('Bitte einen gültigen Zeitraum wählen.');return;}
  baseFrom=from;baseTo=to;viewFrom=from;viewTo=to;manualY=null;selectedPoint=null;
  if(page==='history')loadHistory();
}
function sampleDemo(from,to,chosen) {
  const stride=Math.max(300000,Math.ceil((to-from)/550)), out=[];
  for(let t=from;t<=Math.min(to,Date.now());t+=stride){
    const d=new Date(t),hour=d.getHours()+d.getMinutes()/60;
    const pv=Math.max(0,Math.sin((hour-7)/12*Math.PI))*168*(.88+.12*Math.sin(t/3000000));
    const power=pv*.9-(hour>18&&hour<22?65:28);
    const soc=Math.min(98,Math.max(30,69-8*Math.sin((hour-5)/24*2*Math.PI)));
    for(const kind of (chosen==='soc'?['shunt']:chosen==='yieldWh'?['solar']:['solar','shunt'])){
      let value=chosen==='power'?(kind==='solar'?pv:power):chosen==='soc'?soc:chosen==='voltage'?(kind==='solar'?13.25:13.08)+.2*Math.sin(hour/12*Math.PI):chosen==='current'?(kind==='solar'?pv/13.3:power/13.1):Math.max(0,Math.min(860,(hour-7)*76));
      out.push({t,last:t,kind,origin:'live',device:'demo-'+kind,v:value,min:value,max:value,n:1});
    }
  }
  return out;
}
function loadHistory() {
  if(lifecycle?.blocked||!viewFrom)return;
  clearTimeout(loadTimer);
  energyData=null;historyPending=false;
  try{
    if(metric==='energy'){
      const sun=window.WattDashboardCore.miniWindow('sunrise',snapshot.weather||{},dataTime()),solarFrom=range==='today'?Math.max(viewFrom,sun.solarFrom??viewFrom):viewFrom,solarTo=range==='today'?sun.solarTo??viewTo:viewTo;
      energyData=demo?(source==='csv'?{}:sampleEnergyDemo(viewFrom,viewTo,solarFrom,solarTo)):native?.energyHistoryWindow?JSON.parse(native.energyHistoryWindow(Math.floor(viewFrom),Math.ceil(viewTo),source,Math.floor(solarFrom),Math.ceil(solarTo))):native?.energyHistory?JSON.parse(native.energyHistory(Math.floor(viewFrom),Math.ceil(viewTo),source,Math.floor(solarFrom))):{};
      if(energyData.error)throw new Error();historyPending=!!energyData.pending;
      points=energyPlotPoints(energyData);
    }else if(demo)points=source==='csv'?[]:sampleDemo(viewFrom,viewTo,metric);
    else{const data=native?JSON.parse(native.history(Math.floor(viewFrom),Math.ceil(viewTo),metric,source)):{};if(data.error)throw new Error();points=data.points||[];historyPending=!!data.pending;}
  }catch(error){points=[];energyData=null;toast('Der Verlauf konnte nicht geladen werden.');}
  $('historyReadNote').hidden=!historyPending;$('historyReadNote').textContent=historyPending?'Daten werden im Hintergrund geladen …':'';
  $('energyNote').hidden=metric!=='energy';
  $('energyOptions').hidden=metric!=='energy';
  $('energyNote').textContent=metric==='energy'?energyDescription(energyData):'';
  $('chartTitle').textContent=metrics[metric].title;
  $('chartWindow').textContent=`${date(viewFrom)} ${clock(viewFrom)} – ${date(viewTo)} ${clock(viewTo)}`;
  selectedPoint=null;$('chartTooltip').hidden=true;drawHistory();
}
function delayedHistory(){clearTimeout(loadTimer);loadTimer=setTimeout(loadHistory,300);}
function loadMini(force=false) {
  if(lifecycle?.blocked)return;
  const mode=native?snapshot.settings.miniRange||'today':localStorage.getItem('wattpit-mini-range')||'today';
  miniWindow=window.WattDashboardCore.miniWindow(mode,snapshot.weather,dataTime());
  $('miniRange').value=mode;$('miniPeriodLabel').textContent=miniWindow.label.toLocaleUpperCase('de-DE');
  $('miniPeriodNote').textContent=miniWindow.note;$('miniPeriodNote').hidden=!miniWindow.note;
  const key=[mode,Math.floor(miniWindow.from/60000)].join(':');
  if(!force&&key===miniKey&&Date.now()-lastMiniLoad<20000)return;
  miniKey=key;lastMiniLoad=Date.now();
  const query=window.WattDashboardCore.solarQueryWindow(miniWindow);
  try{const data=demo?{points:sampleDemo(query.from,query.to,'power')}:native?JSON.parse(native.history(query.from,query.to,'power','all')):{};miniPoints=data.points||[];if(data.pending){$('miniPeriodNote').textContent='Daten werden im Hintergrund geladen …';$('miniPeriodNote').hidden=false;}}
  catch(error){miniPoints=[];}
  miniWindow=window.WattDashboardCore.solarPlotWindow(miniWindow,miniPoints);
  drawMini();
}
function energyPlotPoints(data){
  return [...(data?.solar?.points||[]),...(data?.consumption?.points||[]),...($('energyBalance').checked?data?.balance?.points||[]:[])].sort((a,b)=>a.t-b.t);
}
function energyDescription(data){
  const describe=(series,label)=>{const s=series?.summary||{},coverage=Math.floor((s.coverage||0)*100);return `${label}: ${num(s.last,2)} Wh · ${coverage} % der Zeit abgedeckt${s.interpolatedMillis>0?' · davon '+num(s.interpolatedMillis/60000,1)+' min kurz interpoliert':''}`;};
  return describe(data?.solar,'Solarertrag')+' · '+describe(data?.consumption,'Verbrauch')+' · '+describe(data?.balance,'Bilanz')+'. + Überschuss · − Defizit.';
}
function sampleEnergyDemo(from,to,solarFrom=from,solarTo=to){
  return dashboard.demoFlow(null,{from,to:Math.min(to,Date.now())},solarFrom,solarTo);
}
function seriesOf(data) {
  const groups=new Map();
  for(const p of data){const key=p.device+':'+p.origin;if(!groups.has(key))groups.set(key,{kind:p.kind,origin:p.origin,device:p.device,points:[]});groups.get(key).points.push(p);}
  return [...groups.values()];
}
function seriesLabel(s) {
  if(s.kind==='balance')return 'Bilanz';
  if(s.kind==='consumption')return 'Verbrauch';
  if(s.origin==='calculated')return s.kind==='solar'?'Solarertrag':'Batterieentnahme berechnet';
  const name=snapshot.devices.find(d=>d.mac===s.device)?.name || (s.kind==='solar'?'SmartSolar':'SmartShunt');
  return name + (s.origin==='csv'?' · CSV':s.origin==='archive'?' · Archiv':'');
}
function canvasChart(canvas,data,from,to,chosen,mini=false) {
  const bounds=canvas.getBoundingClientRect(), width=Math.max(1,bounds.width),height=bounds.height;
  const ratio=window.devicePixelRatio||1;
  canvas.width=Math.round(width*ratio);canvas.height=Math.round(height*ratio);
  const ctx=canvas.getContext('2d');ctx.scale(ratio,ratio);
  const style=getComputedStyle(document.documentElement),muted=style.getPropertyValue('--muted').trim(),border=style.getPropertyValue('--border').trim();
  const mint=style.getPropertyValue('--mint').trim(),orange=style.getPropertyValue('--orange').trim(),blue=style.getPropertyValue('--blue').trim();
  const left=chosen==='yieldWh'?51:43,right=12,top=18,bottom=30;
  const plotW=width-left-right,plotH=height-top-bottom;
  const visible=data.filter(p=>p.t>=from&&p.t<=to);
  let low=0,high=100;
  if(visible.length){low=Math.min(...visible.map(p=>p.min));high=Math.max(...visible.map(p=>p.max));
    if(chosen==='power'||chosen==='current'||chosen==='energy'){low=Math.min(low,0);high=Math.max(high,0);}
    const span=Math.max(high-low,chosen==='voltage'?.3:chosen==='current'?1:10);low-=span*.12;high+=span*.12;
    if(chosen==='soc'){low=Math.max(0,Math.floor(low/10)*10);high=Math.min(100,Math.ceil(high/10)*10);}
    if(chosen==='yieldWh')low=Math.max(0,low);
  }
  if(!mini&&manualY){low=manualY.min;high=manualY.max;}
  if(high<=low)high=low+1;
  const x=t=>left+(t-from)/(to-from)*plotW,y=v=>top+(high-v)/(high-low)*plotH;
  ctx.font='10px -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif';ctx.lineWidth=1;
  for(let i=0;i<=4;i++){const v=low+(high-low)*(4-i)/4,py=top+plotH*i/4;ctx.strokeStyle=border;ctx.beginPath();ctx.moveTo(left,py);ctx.lineTo(width-right,py);ctx.stroke();ctx.fillStyle=muted;ctx.textAlign='right';ctx.fillText(num(v,chosen==='voltage'?1:chosen==='current'?1:0),left-7,py+3);}
  ctx.textAlign='left';ctx.fillStyle=muted;ctx.fillText(metrics[chosen].unit,left,10);
  for(let i=0;i<=3;i++){const t=from+(to-from)*i/3;ctx.textAlign=i===0?'left':i===3?'right':'center';ctx.fillText(to-from>2*86400000?date(t):clock(t),x(t),height-9);}
  if(!visible.length){ctx.font='12px -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif';ctx.textAlign='center';ctx.fillStyle=muted;ctx.fillText('Noch keine Werte in diesem Zeitraum.',left+plotW/2,top+plotH/2);return {left,right,plotW,plotH,top,low,high,width,height,x,y,series:[]};}
  ctx.save();ctx.beginPath();ctx.rect(left,top,plotW,plotH);ctx.clip();
  const series=seriesOf(visible);
  for(const s of series){
    const color=s.kind==='balance'?blue:s.kind==='solar'?orange:mint;
    const bucket=(to-from)/600;
    const gapLimit=Math.max(bucket*3,s.origin==='csv'?45*60000:45000,...s.points.map(p=>(p.period||0)*1500));
    const segments=[];let segment=[];
    for(const p of s.points){if(segment.length && window.WattDashboardCore.seriesGap(segment[segment.length-1],p,gapLimit)){segments.push(segment);segment=[];}segment.push(p);}if(segment.length)segments.push(segment);
    for(const segment of segments){
      if(segment.some(p=>p.n>1)){ctx.beginPath();segment.forEach((p,i)=>i?ctx.lineTo(x(p.t),y(p.max)):ctx.moveTo(x(p.t),y(p.max)));[...segment].reverse().forEach(p=>ctx.lineTo(x(p.t),y(p.min)));ctx.closePath();ctx.globalAlpha=.16;ctx.fillStyle=color;ctx.fill();ctx.globalAlpha=1;}
      ctx.strokeStyle=color;ctx.lineWidth=mini?2:2.2;ctx.setLineDash(s.kind==='balance'?[6,3]:s.origin==='csv'?[5,4]:[]);ctx.beginPath();segment.forEach((p,i)=>i?ctx.lineTo(x(p.t),y(p.v)):ctx.moveTo(x(p.t),y(p.v)));ctx.stroke();ctx.setLineDash([]);
      if(segment.length===1){ctx.beginPath();ctx.arc(x(segment[0].t),y(segment[0].v),2.5,0,Math.PI*2);ctx.fillStyle=color;ctx.fill();}
    }
  }
  if(selectedPoint&&!mini){ctx.strokeStyle=muted;ctx.setLineDash([3,4]);ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(x(selectedPoint.t),top);ctx.lineTo(x(selectedPoint.t),top+plotH);ctx.stroke();ctx.setLineDash([]);}
  ctx.restore();return {left,right,plotW,plotH,top,low,high,width,height,x,y,series};
}
function drawHistory(){
  if(page!=='history')return;
  scale=canvasChart($('historyChart'),points,viewFrom,viewTo,metric);
  $('chartLegend').innerHTML=scale.series.map(s=>`<span><i class="dot ${s.kind==='balance'?'blue':s.kind==='solar'?'orange':'mint'}"></i>${escapeHtml(seriesLabel(s))}${s.origin==='csv'||s.kind==='balance'?' · gestrichelt':''}</span>`).join('');
}
function drawMini(){if(page==='overview'&&miniWindow)canvasChart($('miniChart'),miniPoints,miniWindow.from,Math.max(miniWindow.from+1,miniWindow.to),'power',true);}
function clampView(from,to){const span=Math.min(baseTo-baseFrom,Math.max(60000,to-from));let start=Math.max(baseFrom,Math.min(from,baseTo-span));viewFrom=start;viewTo=start+span;selectedPoint=null;$('chartTooltip').hidden=true;drawHistory();delayedHistory();}
function zoom(factor,anchor=.5){const span=(viewTo-viewFrom)*factor,center=viewFrom+(viewTo-viewFrom)*anchor;clampView(center-span*anchor,center+span*(1-anchor));}
function scaleForm(){
  openModal('Werteskala',`<p class="muted">Passe den sichtbaren Wertebereich in ${metrics[metric].unit} an. Die Messwerte bleiben unverändert.</p><label class="form-field">Minimum<input id="scaleMin" type="number" step="any" value="${numRaw(manualY?.min ?? scale?.low ?? 0)}"></label><label class="form-field">Maximum<input id="scaleMax" type="number" step="any" value="${numRaw(manualY?.max ?? scale?.high ?? 100)}"></label><div class="form-actions"><button class="quiet" id="autoScale">Automatisch</button><button class="primary" id="applyScale">Skala anwenden</button></div>`);
  $('autoScale').onclick=()=>{manualY=null;closeModal();drawHistory();};
  $('applyScale').onclick=()=>{const min=Number($('scaleMin').value),max=Number($('scaleMax').value);if(!Number.isFinite(min)||!Number.isFinite(max)||min>=max){toast('Das Maximum muss größer als das Minimum sein.');return;}manualY={min,max};closeModal();drawHistory();};
}
const numRaw = value => Math.round(value*100)/100;
const pointers=new Map();let gesture=null, dragDistance=0;
$('historyChart').addEventListener('pointerdown',event=>{
  const c=$('historyChart');c.setPointerCapture(event.pointerId);pointers.set(event.pointerId,{x:event.clientX,y:event.clientY});dragDistance=0;
  const list=[...pointers.values()];gesture={from:viewFrom,to:viewTo,x:event.clientX,y:event.clientY,distance:list.length>1?Math.hypot(list[1].x-list[0].x,list[1].y-list[0].y):0,anchor:list.length>1?Math.min(1,Math.max(0,((list[0].x+list[1].x)/2-c.getBoundingClientRect().left-(scale?.left||43))/(scale?.plotW||250))):.5};
});
$('historyChart').addEventListener('pointermove',event=>{
  if(!pointers.has(event.pointerId)||!gesture)return;pointers.set(event.pointerId,{x:event.clientX,y:event.clientY});
  const list=[...pointers.values()];if(event.pointerType==='touch'&&list.length===1&&Math.abs(event.clientY-gesture.y)>Math.abs(event.clientX-gesture.x)){dragDistance=100;return;}dragDistance=Math.max(dragDistance,Math.abs(event.clientX-gesture.x));
  if(list.length>1 && gesture.distance>0){const distance=Math.hypot(list[1].x-list[0].x,list[1].y-list[0].y);const span=(gesture.to-gesture.from)*gesture.distance/Math.max(distance,5),center=gesture.from+(gesture.to-gesture.from)*gesture.anchor;dragDistance=100;clampView(center-span*gesture.anchor,center+span*(1-gesture.anchor));}
  else if(list.length===1){const delta=(event.clientX-gesture.x)/(scale?.plotW||250)*(gesture.to-gesture.from);clampView(gesture.from-delta,gesture.to-delta);}
});
function releasePointer(event){
  if(!pointers.has(event.pointerId))return;
  if(pointers.size===1&&dragDistance<6&&scale){const px=event.clientX-$('historyChart').getBoundingClientRect().left;const time=viewFrom+(px-scale.left)/scale.plotW*(viewTo-viewFrom);const nearest=[...points].sort((a,b)=>Math.abs(a.t-time)-Math.abs(b.t-time))[0];if(nearest){selectedPoint=nearest;const nearby=seriesOf(points.filter(p=>Math.abs(p.t-nearest.t)<Math.max(1000,(viewTo-viewFrom)/500))).map(s=>s.points.reduce((a,b)=>Math.abs(a.t-nearest.t)<=Math.abs(b.t-nearest.t)?a:b));$('chartTooltip').innerHTML=`<strong>${date(nearest.t)} ${clock(nearest.t)}</strong><br>${nearby.slice(0,4).map(p=>`${escapeHtml(seriesLabel(p))}: ${num(p.v,['energy','voltage','current'].includes(metric)?2:1)} ${metrics[metric].unit}${p.period?`<br><span class="muted">Archivauflösung ${num(p.period)} s</span>`:''}${p.n>1?`<br><span class="muted">Ø aus ${p.n} Werten · ${num(p.min,1)}–${num(p.max,1)} ${metrics[metric].unit}</span>`:''}`).join('<br>')}`;$('chartTooltip').hidden=false;drawHistory();}}
  pointers.delete(event.pointerId);gesture=null;
}
$('historyChart').addEventListener('pointerup',releasePointer);
$('historyChart').addEventListener('pointercancel',event=>{pointers.delete(event.pointerId);gesture=null;});

function renderDaily() {
 let el=$('solarDailyArchive'); if(!el){el=document.createElement('article');el.id='solarDailyArchive';el.className='card';$('historyStats').after(el);}
 const rows=snapshot.solarDaily||[];el.hidden=!rows.length;
 const months=new Map(),consumption=new Map(),waiting=new Set();
 if(!demo&&native?.dailyEnergy)for(const row of rows){
  if(!/^\d{4}-\d{2}-\d{2}$/.test(row.day))continue;const month=row.day.slice(0,7);if(months.has(month))continue;
  const start=new Date(month+'-01T00:00:00'),end=new Date(start);end.setMonth(end.getMonth()+1);
  if(start.getTime()>=dataTime())continue;
  try{const data=JSON.parse(native.dailyEnergy(start.getTime(),end.getTime(),snapshot.historyRevision||0,Math.floor(dataTime()/30000)));
   months.set(month,data);if(data.pending)waiting.add(month);for(const day of data.days||[])consumption.set(day.day,day);
  }catch(_){months.set(month,{});}
 }
 el.innerHTML='<h2>Solar-Tagesarchiv</h2><p class="muted">Ertrag: Tageswerte des Solarladereglers. Verbrauch: aus Solar- und Shunt-Messungen berechnet, einschließlich der Nacht. Messlücken bleiben unberechnet; heute ist vorläufig.</p><div style="overflow-x:auto" tabindex="0" aria-label="Tagesarchiv, weitere Spalten seitlich erreichbar"><table><thead><tr><th>Tag</th><th>Ertrag Wh</th><th>Verbrauch Wh</th><th>Maximum W</th><th>Batterie min/max V</th></tr></thead><tbody>'+rows.map(r=>{
  const day=consumption.get(r.day),known=day?.last!=null&&Number.isFinite(Number(day.last));
  const amount=known?num(day.last,2):waiting.has(r.day.slice(0,7))?'…':'—';
  const coverage=known?`${Math.floor(Math.max(0,Math.min(1,day.coverage||0))*100)} % erfasst${day.interpolatedMillis>0?' · kurz interpoliert':''}`:waiting.has(r.day.slice(0,7))?'wird berechnet':'keine Messdaten';
  return `<tr><td>${escapeHtml(r.day)}</td><td>${num(r['Yield(Wh)'])}</td><td>${amount}<span class="daily-coverage">${coverage}</span></td><td>${num(r['Max. PV power(W)'])}</td><td>${num(r['Min. battery voltage(V)'],2)} / ${num(r['Max. battery voltage(V)'],2)}</td></tr>`;
 }).join('')+'</tbody></table></div>';
}
function chooseImport(){if(native)native.chooseImport();else toast('CSV-Dateien werden in der Android-App importiert.');}
function chooseExport(){if(native)native.chooseExport();else toast('Der Export ist in der Android-App verfügbar.');}
const mappingLabels={pvVoltage:'PV-Eingangsspannung',auxRaw:'Zusatzeingang (Rohwert)',period:'Archivauflösung (Sekunden)',timestamp:'Datum & Zeit',date:'Datum separat',clock:'Zeit separat',voltage:'Batteriespannung',current:'Batteriestrom',power:'Batterieleistung',soc:'Ladezustand / SOC',solar:'Solarleistung',yieldWh:'Tagesertrag',ttg:'Restlaufzeit',consumed:'Entnommene Ah',temperature:'Temperatur',aux:'Zusatzspannung',load:'Laststrom',state:'Ladephase (Code)',error:'Fehlercode',alarm:'Alarm (Bitmaske)'};
window.onImportPreview = result => {
  currentImport={...result,busy:false};
  const nativeFile=result.map.kind!=null && result.map.device!=null;
  const fields=Object.keys(mappingLabels),basic=['timestamp','date','clock','voltage','current','soc','power','solar'];
  const mapRow=field=>`<div class="map-row"><label for="map-${field}">${mappingLabels[field]}</label><select id="map-${field}" data-field="${field}"><option value="-1">Nicht vorhanden</option>${result.headers.map((h,i)=>`<option value="${i}">${escapeHtml(h)}</option>`).join('')}</select><div id="example-${field}" class="map-example"></div></div>`;
  openModal('Verlauf importieren',`<p class="muted"><strong>${escapeHtml(result.name)}</strong><br>${num(result.rows)} Zeilen gefunden. Prüfe die Zuordnung, bevor die Daten übernommen werden. SolarHistory-Tagesarchive werden automatisch separat gespeichert; ihre Spaltenzuordnung wird nicht verwendet.${nativeFile?'<br>Watt’n’Pit-Datei erkannt: Gerätetyp und Datenquelle bleiben erhalten.':''}</p><label class="form-field">Gerätetyp<select id="importKind"><option value="shunt">SmartShunt · Batterie</option><option value="solar">SmartSolar · Solar</option></select></label><div class="mapping-list">${basic.map(mapRow).join('')}<details><summary>Weitere Messwerte</summary>${fields.filter(f=>!basic.includes(f)).map(mapRow).join('')}</details></div><p class="muted" style="margin-top:16px">Zeitangaben ohne Zeitzone werden in der lokalen Telefonzeit gelesen. Werte mit kW/kWh/mA/mV in der Kopfzeile werden automatisch umgerechnet.</p><p id="importError" class="form-error" hidden></p><div class="form-actions"><button class="quiet" id="cancelCsv">Abbrechen</button><button class="primary" id="confirmCsv">Daten importieren</button></div>`);
  $('importKind').value=result.map.solar!=null || /solar|mppt/i.test(result.name)?'solar':'shunt';
  const dailyFile=result.headers.includes('Days ago') && result.headers.includes('Yield(Wh)');
  $('importKind').disabled=nativeFile||dailyFile;
  if(dailyFile){$('importKind').value='solar';document.querySelector('.mapping-list').hidden=true;}
  fields.forEach(field=>{const select=$('map-'+field);select.value=result.map[field]??-1;const update=()=>{$('example-'+field).textContent=select.value>=0?'Beispiel: '+(result.preview[0]?.[select.value] || 'leer'):'';};select.onchange=update;update();});
  $('cancelCsv').onclick=closeModal;
  $('confirmCsv').onclick=()=>{
    const map={...result.map};fields.forEach(f=>{const value=Number($('map-'+f).value);if(value>=0)map[f]=value;else delete map[f];});
    if(map.timestamp==null && map.date==null){toast('Bitte Datum und Zeit zuordnen.');return;}
    if(!['voltage','current','soc','power','solar','yieldWh','temperature'].some(f=>map[f]!=null)){toast('Bitte mindestens einen Messwert zuordnen.');return;}
    currentImport.busy=true;$('confirmCsv').disabled=true;$('cancelCsv').disabled=true;$('confirmCsv').textContent='Wird importiert …';
    if(native)native.importCsv(JSON.stringify(map),$('importKind').value);
  };
};
window.onImportDone = result => {
  if(currentImport)currentImport.busy=false;closeModal();
  toast(`${num(result.inserted)} Datensätze importiert · ${num(result.duplicates)} bereits vorhanden · ${num(result.rejected)} unlesbar.`);
  dashboard?.clearCache();poll();loadHistory();loadMini(true);
};
window.nativeError = result => {
  if(currentImport){currentImport.busy=false;if($('confirmCsv')){$('confirmCsv').disabled=false;$('cancelCsv').disabled=false;$('confirmCsv').textContent='Daten importieren';$('importError').hidden=false;$('importError').textContent=result.message;}}
  toast(result.message);
};

document.querySelectorAll('.bottom-nav button').forEach(b=>b.onclick=()=>showPage(b.dataset.page));
document.querySelectorAll('#rangeButtons button').forEach(b=>b.onclick=()=>selectRange(b.dataset.range));
document.querySelectorAll('#metricButtons button').forEach(b=>b.onclick=()=>{metric=b.dataset.metric;manualY=null;document.querySelectorAll('#metricButtons button').forEach(x=>x.classList.toggle('selected',x===b));loadHistory();});
document.querySelectorAll('#themeButtons button').forEach(b=>b.onclick=()=>theme(b.dataset.theme));
$('themeToggle').onclick=()=>theme(snapshot.settings.theme==='dark'?'light':'dark');
$('setupFirst').onclick=()=>showPage('devices');$('demoFirst').onclick=()=>setDemo(true);$('exitDemo').onclick=()=>setDemo(false);
$('settingsDemo').onclick=()=>{setDemo(!demo);showPage('overview');};
$('openHistory').onclick=()=>showPage('history');$('overviewRecord').onclick=toggleRecord;$('scanButton').onclick=toggleRecord;
$('reloadArchive').onclick=reloadMissingData;
$('addDevice').onclick=()=>deviceForm();$('closeModal').onclick=closeModal;
$('modal').onclick=event=>{if(event.target===$('modal'))closeModal();};
document.addEventListener('keydown',event=>{if(event.key==='Escape')window.handleBack();if(event.key==='Tab'&&!$('modal').hidden){const items=[...$('modal').querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),summary')].filter(el=>el.offsetParent);if(!items.length)return;const first=items[0],last=items[items.length-1];if(event.shiftKey&&document.activeElement===first){last.focus();event.preventDefault();}else if(!event.shiftKey&&document.activeElement===last){first.focus();event.preventDefault();}}});
$('applyRange').onclick=()=>setWindow(new Date($('rangeFrom').value).getTime(),new Date($('rangeTo').value).getTime());
$('historySource').onchange=()=>{source=$('historySource').value;loadHistory();};
$('energyBalance').onchange=()=>{if(metric!=='energy')return;points=energyPlotPoints(energyData);selectedPoint=null;$('chartTooltip').hidden=true;drawHistory();};
$('zoomIn').onclick=()=>zoom(.5);$('zoomOut').onclick=()=>zoom(2);$('openScale').onclick=scaleForm;
$('resetChart').onclick=()=>{viewFrom=baseFrom;viewTo=baseTo;manualY=null;loadHistory();};
['importCsv','historyImport','settingsImport'].forEach(id=>$(id).onclick=chooseImport);
['exportCsv','settingsExport'].forEach(id=>$(id).onclick=chooseExport);
$('exportPlanningDiagnostics').onclick=()=>{if(native?.choosePlanningDiagnostics)native.choosePlanningDiagnostics();else toast('Der Diagnoseexport ist in der Android-App verfügbar.');};
['capacitySetting','sampleSetting'].forEach(id=>$(id).oninput=()=>settingsDirty=true);
$('saveSettings').onclick=()=>{const settings={capacity:Number($('capacitySetting').value),sampleSeconds:Number($('sampleSetting').value)};if(!settings.capacity||settings.capacity<=0||settings.capacity>10000){toast('Bitte eine Kapazität zwischen 1 und 10.000 Ah eingeben.');return;}const error=native?native.settings(JSON.stringify(settings)):'';if(error){toast(error);return;}Object.assign(snapshot.settings,settings);settingsDirty=false;toast('Einstellungen gespeichert.');renderOverview();};
$('miniRange').onchange=()=>{const miniRange=$('miniRange').value,error=native?native.settings(JSON.stringify({miniRange})):'';if(error){toast(error);loadMini(true);return;}snapshot.settings.miniRange=miniRange;if(!native)localStorage.setItem('wattpit-mini-range',miniRange);loadMini(true);};
$('historyImport').innerHTML=icon('import');$('addDevice').innerHTML=icon('plus');
for(const [id,name] of [['navOverview','overview'],['navHistory','history'],['navDevices','devices'],['navSettings','settings']])$(id).innerHTML=icon(name);
lifecycle=new window.WattLifecycle({native,toast,poll,stateChanged:state=>reads?.state(state),completed:()=>{lastHistoryRevision=-1;lastMiniLoad=0;dashboard?.cache.clear();}});
lifecycle.poll();
dashboard = new window.WattDashboard({native,resizeMini:drawMini,isBlocked:()=>lifecycle.blocked,dataTime,getSnapshot:()=>snapshot,reading,isDemo:()=>demo,openModal,closeModal,toast});
equipment = new window.WattEquipment({native,getSnapshot:()=>snapshot,openModal,closeModal,toast,poll,repaint:()=>dashboard?.update()});
extras = new window.WattExtras({native,renderSolar:()=>solar?.render(),getSnapshot:()=>snapshot,isDemo:()=>demo,toast,poll,cancelArchive:()=>lifecycle.confirmCancellation(),repaint:()=>{dashboard?.update();drawHistory();drawMini();},imported:()=>{dashboard?.clearCache();if(page==='history')loadHistory();lastMiniLoad=0;}});
solar = new window.WattSolarPlan({native:deviceNative,equipment,getSnapshot:()=>snapshot,getReading:reading,openModal,closeModal,toast,poll,symbol:code=>extras.symbol(code)});
roller = new window.WattRoller({native:deviceNative,isDemo:()=>demo,getSolar:()=>solar,openModal,closeModal,toast,poll});
configuration = new window.WattSettingsBackup({native,getSnapshot:()=>snapshot,dashboard,equipment,openModal,closeModal,toast,applied:backup=>{
  settingsDirty=false;poll();
  dashboard.config=window.WattDashboardCore.normalize(backup.dashboard);dashboard.saved=JSON.parse(JSON.stringify(dashboard.config));dashboard.cache.clear();
  theme(snapshot.settings.theme,false);extras.palette(snapshot.settings.palette||'classic',false);equipment.key='';equipment.render();dashboard.render();renderSettings();loadMini(true);
}});
theme(!native?localStorage.getItem('wattpit-preview-theme')||'dark':'dark',false);
selectRange('today');poll();renderSettings();
if(new URLSearchParams(location.search).get('demo')==='1')setDemo(true);
window.pullReload=new window.WattPullReload({element:$('overview'),canStart:()=>page==='overview'&&!demo&&!dashboard.editing&&!lifecycle.blocked&&!lifecycle.busy&&!snapshot.archiveBusy&&snapshot.devices.length>0&&Date.now()>=archiveRequestedUntil&&$('modal').hidden,reload:reloadMissingData});
setInterval(poll,2000);setInterval(()=>{if(!lifecycle.blocked&&page==='history'&&snapshot.running&&!demo)loadHistory();},20000);
window.addEventListener('resize',()=>{drawHistory();drawMini();});
