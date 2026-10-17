'use strict';
window.WattExtras=class {
  constructor(a){
    this.a=a;this.key='';this.lastImport=0;
    this.palettes={classic:['Klassisch','#f5ac5c','#8ee0ca','#87b9ef'],blue:['Blau','#8ab8f5','#80d3cc','#d2b2e9'],green:['Grün','#9bcca6','#b7d58d','#9ebde1'],turquoise:['Türkis','#7ed6d0','#a9d6a0','#a4b9f0'],orange:['Orange','#f2b17b','#dcc193','#a0c9bd'],neutral:['Neutral','#bbc6d4','#a9c4bc','#b6bed3']};
    document.getElementById('paletteChoices').innerHTML=Object.entries(this.palettes).map(([id,p])=>`<button class="palette-choice" data-palette="${id}" aria-pressed="false"><span class="palette-dots">${p.slice(1).map(c=>`<i style="background:${c}"></i>`).join('')}</span>${p[0]}</button>`).join('');
    document.querySelectorAll('.palette-choice').forEach(b=>b.onclick=()=>this.palette(b.dataset.palette,true));
    document.getElementById('weatherSearchButton').onclick=()=>this.search();
    document.getElementById('weatherQuery').oninput=()=>{document.getElementById('weatherSearchButton').disabled=false;};
    document.getElementById('weatherQuery').onkeydown=e=>{if(e.key==='Enter')this.search();};
    document.getElementById('weatherOff').onclick=()=>this.place({off:true});
    document.getElementById('weatherRefresh').onclick=()=>{if(a.native)a.native.refreshWeather();else a.toast('Wetter ist in der Android-App verfügbar.');};
    document.getElementById('copyArchiveDiagnostics').onclick=()=>{if(!a.native?.copyArchiveDiagnostics){a.toast('Diagnose kopieren ist in der Android-App verfügbar.');return;}const error=a.native.copyArchiveDiagnostics();a.toast(error||'Diagnose ohne private Gerätedaten kopiert.');};
    document.getElementById('archiveCancel').onclick=()=>a.native?.cancelArchive();
    document.getElementById('deviceCards').addEventListener('click',e=>{const b=e.target.closest('.archive-import');if(!b)return;if(a.isDemo()){a.toast('Demo liest keine Geräte aus.');return;}if(a.native)a.native.importArchive(b.dataset.mac);else a.toast('Gerätearchive werden in der Android-App gelesen.');});
    window.onWeatherSearch=result=>{
      const query=document.getElementById('weatherQuery').value.trim();if(result.query!==query)return;
      const el=document.getElementById('weatherResults');document.getElementById('weatherSearchButton').disabled=false;
      if(result.error){el.textContent=result.error;return;}
      const places=result.places||[];el.innerHTML=places.length?places.map((p,i)=>`<button class="weather-place" data-index="${i}">${this.esc(p.name)}</button>`).join(''):'Keinen Ort gefunden. Bitte genauer suchen.';
      el.querySelectorAll('button').forEach(b=>b.onclick=()=>{this.place(places[+b.dataset.index]);el.innerHTML='';});
    };
    this.palette(a.getSnapshot().settings.palette||(!a.native?localStorage.getItem('wattpit-palette'):null)||'classic',false);
  }
  esc(s){return String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
  palette(id,save){
    if(!this.palettes[id])id='classic';const snapshot=this.a.getSnapshot();
    document.documentElement.dataset.palette=id;snapshot.settings.palette=id;
    document.querySelectorAll('.palette-choice').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.palette===id)));
    if(save){const error=this.a.native?this.a.native.settings(JSON.stringify({palette:id})):'';if(error)this.a.toast(error);else{if(!this.a.native)localStorage.setItem('wattpit-palette',id);this.a.repaint();}}
  }
  search(){const query=document.getElementById('weatherQuery').value.trim();if(query.length<2){this.a.toast('Bitte einen Ort eingeben.');return;}if(!this.a.native){this.a.toast('Die Ortssuche ist in der Android-App verfügbar.');return;}document.getElementById('weatherResults').textContent='Suche …';document.getElementById('weatherSearchButton').disabled=true;this.a.native.searchWeather(query);}
  place(place){if(!this.a.native)return;const error=this.a.native.weatherPlace(JSON.stringify(place));if(error)this.a.toast(error);else{document.getElementById('weatherLocation').textContent=place.off?'Wetter ist ausgeschaltet.':place.name;this.a.toast(place.off?'Wetter ausgeschaltet.':'Wetterort gespeichert. Vorschau wird geladen.');this.a.poll();}}
  symbol(code){
    const sun='<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.4 1.4m11.2 11.2L19 19M5 19l1.4-1.4M17.6 6.4 19 5"/>';
    const cloud='<path d="M6 17a4 4 0 1 1 1-7.9A6 6 0 0 1 19 11a3 3 0 0 1 0 6Z"/>';
    let label='Keine Vorhersage',path='<path d="M10 8a3 3 0 1 1 4 3c-2 1-2 2-2 3m0 3v.1"/>';
    if(code===0){label='Sonnig';path=sun;}
    else if(code===1||code===2){label='Heiter bis wolkig';path='<path d="M5 3v2M1 8h2m1-3 1 1m5-4v2"/><circle cx="7" cy="8" r="3.5"/>'+cloud;}
    else if(code===3){label='Bewölkt';path=cloud;}
    else if(code===45||code===48){label='Nebel';path=cloud+'<path d="M3 20h18M5 23h14"/>';}
    else if([51,53,55,56,57,61,63,65,66,67,80,81,82].includes(code)){label='Regen';path=cloud+'<path d="m7 20-1 2m6-2-1 2m6-2-1 2"/>';}
    else if([71,73,75,77,85,86].includes(code)){label='Schnee';path=cloud+'<path d="M7 20v2m-1-1h2m8-1v2m-1-1h2"/>';}
    else if([95,96,99].includes(code)){label='Gewitter';path=cloud+'<path d="m12 18-3 3h4l-2 3"/>';}
    return `<svg class="weather-symbol" viewBox="0 0 24 26" role="img" aria-label="${label}"><title>${label}</title><g fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">${path}</g></svg>`;
  }
  render(){
    const s=this.a.getSnapshot(),e=this.esc.bind(this);this.palette(s.settings.palette||'classic',false);
    const place=s.settings.weatherPlace||{},weather=s.weather||{},days=weather.days||[];
    document.getElementById('weatherLocation').textContent=place.name||'Noch kein Wetterort gewählt.';
    document.getElementById('weatherOff').hidden=!place.name;
    const strip=document.getElementById('weatherStrip');strip.hidden=!place.name;
    document.getElementById('weatherPlaceName').textContent=strip.dataset.title||place.name||'Wetter';
    document.getElementById('weatherPlaceName').title=place.name||'';
    const today=new Intl.DateTimeFormat('en-CA',{timeZone:weather.timezone||undefined,year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
    const valid=days.filter(d=>d.day>=today).slice(0,14);
    const weatherKey=JSON.stringify([valid,today]);
    if(this.weatherKey!==weatherKey){this.weatherKey=weatherKey;document.getElementById('weatherDays').innerHTML=valid.length?valid.map(d=>{const date=new Date(d.day+'T12:00:00');return `<div class="weather-day"><time datetime="${e(d.day)}" title="${e(date.toLocaleDateString('de-DE',{dateStyle:'full'}))}">${e(date.toLocaleDateString('de-DE',{weekday:'short'}))}<small>${e(date.toLocaleDateString('de-DE',{day:'2-digit'}))}</small></time>${this.symbol(d.code)}<span>${d.hours==null?'—':Number(d.hours).toLocaleString('de-DE',{maximumFractionDigits:1})} h</span></div>`;}).join(''):'<p class="muted">Vorschau lädt · Internet einschalten.</p>';}
    const note=s.weatherError|| (weather.updated?'Stand '+new Date(weather.updated).toLocaleTimeString('de-DE',{hour:'2-digit',minute:'2-digit'}):'Sonnenstunden');
    document.getElementById('weatherNote').textContent=note;document.getElementById('weatherNote').title=note;
    const reports=s.archiveDiagnostics||{};document.getElementById('archiveDetails').hidden=!Object.keys(reports).length;
    document.getElementById('archiveDiagnosticText').textContent=Object.entries(reports).map(([kind,r])=>(kind==='solar'?'Solarladeregler':'Batteriewächter')+'\n'+(r.result||'')+'\n'+(r.steps||[]).join('\n')).join('\n\n');
    const archive=s.archive||{};document.getElementById('archiveStatus').hidden=!archive.text;
    const historyStatus=document.getElementById('historyArchiveStatus');historyStatus.hidden=!archive.text||this.a.isDemo();historyStatus.textContent=archive.text?(archive.active?'Archivimport läuft · ':'Archivimport · ')+archive.text:'';
    document.getElementById('archiveText').textContent=archive.text||'';document.getElementById('archiveCancel').hidden=!archive.active;
    const progress=document.getElementById('archiveProgress');progress.hidden=!archive.active;if(archive.total>0){progress.max=archive.total;progress.value=Math.min(archive.done,archive.total);}else progress.removeAttribute('value');
    document.querySelectorAll('.archive-import').forEach(b=>b.disabled=!!archive.active||this.a.isDemo());
    if(archive.updated&&archive.updated!==this.lastImport&&!archive.active){this.lastImport=archive.updated;this.a.imported();}
    const histories=s.deviceHistory||[],card=document.getElementById('deviceLifetime');card.hidden=!histories.length;
    card.innerHTML=histories.map(h=>`<h2>Shunt-Gesamtwerte</h2><div class="lifetime-grid">${[['300','Tiefste Entladung','Ah'],['305','Gesamt entnommen','Ah'],['310','Entladene Energie','kWh'],['311','Geladene Energie','kWh'],['306','Min. Batteriespannung','V'],['307','Max. Batteriespannung','V']].map(([k,n,u])=>`<div><span class="muted">${n}</span><strong>${h[k]==null?'—':Number(h[k]).toLocaleString('de-DE',{maximumFractionDigits:2})} ${u}</strong></div>`).join('')}</div>`).join('');
  }
};
