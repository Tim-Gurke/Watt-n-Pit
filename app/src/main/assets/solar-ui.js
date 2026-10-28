(function(){
  'use strict';
  const C=window.WattSolarCore,$=id=>document.getElementById(id),copy=x=>JSON.parse(JSON.stringify(x));
  const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const number=(x,d=0)=>typeof x==='number'&&Number.isFinite(x)?x.toLocaleString('de-DE',{maximumFractionDigits:d}):'—';
  const energy=x=>x==null?'—':number(x)+' Wh';
  const known=x=>typeof x==='number'&&Number.isFinite(x);
  const percent=x=>known(x)?number(x)+' %':'—';
  const field=(label,html)=>`<label class="form-field">${label}${html}</label>`;
  const shades=['Unbekannt','Frei','Leicht','Mittel','Stark'];
  window.WattSolarPlan=class {
    constructor(a){
      this.a=a;this.knowledge={source:'',observations:[],forecasts:[],updated:0};this.renderKey='';this.lastRequest=0;this.contextKey=null;this.lastRemembered='';
      $('solarSetupSettings').onclick=()=>this.openSetup();$('solarSetupButton').onclick=()=>this.openSetup();$('solarConsumptionSettings').onclick=()=>this.openUsage();
      $('solarMobileToggle').onclick=()=>this.mobile();$('solarGpsSettings').onclick=()=>{this.openSetup();this.locate();};
      window.onSolarPlanData=data=>{if(data.error){this.knowledge.error=data.error;}else{this.knowledge=data;this.knowledge.observations??=[];this.knowledge.forecasts??=[];}this.renderKey='';this.render();};
      window.onSolarSunsetSoc=result=>{
        if(result.id!==this.sunsetRead?.id)return;
        Object.assign(this.sunsetRead,{done:true,point:result.point,error:result.error});this.renderKey='';this.render();
        const panel=document.querySelector('[data-solar-reserve]'),day=this.days?.find(d=>d.day===panel?.dataset.solarReserve);
        if(panel&&day)panel.outerHTML=this.reservePanel(day);
      };
      window.onSolarLocation=result=>{
        if(!this.draft||!$('solarSetupPane'))return;
        if(result.error){this.status(result.error);return;}
        if(!Number.isFinite(result.latitude)||!Number.isFinite(result.longitude)){this.status('Standort konnte nicht übernommen werden.');return;}
        this.selectPlace({name:'GPS-Standort',latitude:result.latitude,longitude:result.longitude},`Standort ermittelt · ungefähr ±${Math.round(result.accuracy||0)} m`);
      };
      window.onSolarPlaces=result=>{
        if(!$('solarPlaceResults')||result.query!==this.lastQuery)return;
        $('solarPlaceResults').textContent=result.error||'';
        for(const p of result.places||[]){const b=document.createElement('button');b.className='weather-place';b.textContent=p.name;b.onclick=()=>this.selectPlace(p,'Ort gewählt. Bitte Aufstellung speichern.');$('solarPlaceResults').append(b);}
      };
      window.onSolarCompass=result=>this.compassResult(result);
      document.addEventListener('watt-modal-close',()=>this.stopSensors());
      document.addEventListener('visibilitychange',()=>{if(document.hidden)this.stopSensors();else if($('solarCompass'))this.startCompass();});
      $('weatherNote').setAttribute('role','button');$('weatherNote').tabIndex=0;
      $('weatherNote').onclick=e=>{e.stopPropagation();this.openDay(this.noteDay||0);};$('weatherNote').onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();e.stopPropagation();this.openDay(this.noteDay||0);}};
      if(window.ResizeObserver){const observer=new ResizeObserver(()=>this.layoutPreview());observer.observe($('weatherStrip'));observer.observe($('weatherDays'));}
    }
    equipment(){return this.a.equipment.config();}
    plan(){
      try{return C.normalize(JSON.parse(this.a.native?.solarSetup?this.a.native.solarSetup():localStorage.getItem('wattpit-solar-setup')||'{}'),this.equipment());}
      catch(_){return C.normalize({},this.equipment());}
    }
    async save(plan,place){
      const clean=C.normalize(plan,this.equipment());let error='';
      if(this.a.native){error=await (place&&this.a.native.saveSolarSite?this.a.native.saveSolarSite(JSON.stringify(clean),JSON.stringify(place)):this.a.native.saveSolarSetup?.(JSON.stringify(clean),false)||'');if(!this.a.native.saveSolarSetup&&!this.a.native.saveSolarSite)throw Error('Bitte die Android-App aktualisieren.');}
      else{localStorage.setItem('wattpit-solar-setup',JSON.stringify(clean));this.a.getSnapshot().settings.solarSetup=copy(clean);if(place&&Object.keys(place).length){this.a.getSnapshot().settings.weatherPlace=copy(place);this.a.getSnapshot().weather={};}}
      if(error)throw Error(error);this.renderKey='';this.contextKey=null;this.a.poll();this.render();
    }
    render(){
      let rollerState={};try{rollerState=JSON.parse(this.a.native?.rollerSnapshot?.()||'{}');}catch(_){}
      const rollerLoads=window.WattRollerCore?.loads(rollerState)||[];
      const snapshot=this.a.getSnapshot(),weather=snapshot.weather||{},eq=this.equipment(),plan=this.plan(),now=Date.now(),shunt=this.a.getReading?.('shunt'),key=JSON.stringify([weather,eq,plan,this.knowledge.updated,this.knowledge.error,Math.floor(now/60000),shunt?[Math.round(shunt.soc),Math.round(shunt.voltage*10)/10]:null,snapshot.settings.capacity,snapshot.deviceSettings,rollerLoads]);
      if(this.a.native?.requestSolarData&&!snapshot.archiveBusy&&(Date.now()-this.lastRequest>300000||this.revision!==snapshot.historyRevision&&Date.now()-this.lastRequest>30000)){
        this.lastRequest=Date.now();this.revision=snapshot.historyRevision;this.a.native.requestSolarData();
      }
      if(key===this.renderKey){this.renderNote(snapshot,weather,now);return;}this.renderKey=key;
      this.days=C.forecast(weather,eq,plan,this.knowledge,now);
      const reserves=C.batteryForecast(this.days,weather,shunt?{...shunt,capacity:snapshot.settings.capacity}:null,now,rollerLoads);this.days.forEach((d,i)=>d.reserve=reserves[i]);
      this.savedSunset(snapshot,now);
      this.renderNote(snapshot,weather,now);
      const place=snapshot.settings.weatherPlace||weather.place,context=C.setupKey(eq,plan,place,this.knowledge.source),solarKey=C.calibrationKey(eq,plan,place,this.knowledge.source);
      if(context&&this.knowledge.source&&context!==this.contextKey){this.contextKey=context;this.a.native?.rememberSolarContext?.(JSON.stringify({key:context,solarKey}));this.a.native?.requestSolarData?.();}
      const remembered=solarKey+':'+weather.updated;
      if(remembered!==this.lastRemembered&&this.knowledge.source){this.lastRemembered=remembered;const rows=C.trainingRows(weather,eq,plan,this.days,this.knowledge.source);if(rows.length)this.a.native?.rememberSolarForecasts?.(JSON.stringify(rows));}
      const radiation=this.days.map(d=>d.radiation).filter(x=>Number.isFinite(x)&&x>=0),maximum=Math.max(1,...radiation),days=$('weatherDays');
      days.innerHTML=this.days.length?this.days.map((d,i)=>{
        const date=new Date(d.day+'T12:00:00'),reserve=d.reserve.evening,label=`${date.toLocaleDateString('de-DE',{weekday:'long',day:'numeric',month:'long'})}, ${number(d.hours,1)} Sonnenstunden, Gesamtpotenzial aller aktiven Kreise: ${d.total==null?'noch nicht bestimmbar':energy(d.low)+' bis '+energy(d.high)}, Akku bei Sonnenuntergang: ${known(reserve.soc)?reserve.mode==='measured'?'gespeichert '+percent(reserve.soc):percent(reserve.low)+' bis '+percent(reserve.high):reserve.reason}`;
        const bar=Number.isFinite(d.radiation)?Math.round(100*d.radiation/maximum):null;
        return `<button class="weather-day solar-day" data-solar-day="${i}" aria-label="${esc(label)}" title="${esc(label)}"><time datetime="${d.day}">${date.toLocaleDateString('de-DE',{weekday:'short'})}<small>${date.toLocaleDateString('de-DE',{day:'2-digit'})}</small></time>${this.a.symbol(d.code)}<span class="solar-sun-hours">${number(d.hours,1)} h</span>${this.scale('sun','Sonne',bar,known(bar)?number(bar)+' %':'—','Sonneneinstrahlung relativ zum besten Vorschautag')}${this.scale('battery','Akku',reserve.soc,known(reserve.soc)?(reserve.mode==='measured'?'':'≈ ')+percent(reserve.soc):'—','Akkustand bei Sonnenuntergang · '+(reserve.mode==='measured'?'gespeicherter SmartShunt-Wert':reserve.reason||percent(reserve.low)+' bis '+percent(reserve.high)))}<span class="solar-wh" title="Geschätztes Gesamtpotenzial aller aktiven Kreise, einschließlich mobiler Entlastung">${d.total==null?'— Wh':'≈ '+number(d.total)+' Wh'}</span></button>`;
      }).join(''):'<p class="muted">Solarvorschau lädt · Standort wählen und Internet einschalten.</p>';
      days.querySelectorAll('[data-solar-day]').forEach(b=>b.onclick=()=>this.openDay(+b.dataset.solarDay));
      $('weatherPeriod').textContent='14 Tage';
      const mobile=eq.groups.filter(g=>g.placement==='mobile'),active=mobile.filter(g=>plan.groups[g.id]?.active);
      $('solarMobileToggle').hidden=!mobile.length;$('solarMobileToggle').textContent=mobile.length===1?'Mobil '+(active.length?'an':'aus'):`Mobil ${active.length}/${mobile.length}`;$('solarMobileToggle').setAttribute('aria-pressed',String(active.length>0));
      const usage=this.days[0]?.usage||C.consumption(plan,this.knowledge.observations,new Date().toISOString().slice(0,10));
      $('solarLearningNote').textContent=this.knowledge.error||(usage.wh==null?usage.pendingSlots?.length?`Tagesbedarf noch offen: ${usage.pendingSlots.map(i=>C.PARTS[i]).join(', ')} braucht mehr vergleichbare Tage oder Messzeit. Die vorhandenen Schätzungen bleiben unter Datengrundlage sichtbar.`:'Verbrauch wird automatisch aus geeigneten Messabschnitten ermittelt, auch bei mobiler Aufstellung.':`${usage.manual?'Eigener Standard':usage.provisional?'Vorläufiger Bedarf':'Erlernter Bedarf'}: ≈ ${energy(usage.wh)} pro Tag${usage.days?' · Daten aus '+usage.days+' Tagen':''}. ${usage.extrapolated?'Fehlende Tagesabschnitte werden vorläufig hochgerechnet. ':''}${usage.estimatedHours?'Mobiler Beitrag teilweise geschätzt. ':''}Jahreszeit und Tagesabschnitte werden berücksichtigt. Rollerladungen sind als Sondernutzung ausgenommen.`);
    }
    savedSunset(snapshot,now){
      for(const day of this.days){
        const reserve=day.reserve.evening;if(!known(reserve.at)||reserve.at>=now)continue;
        const key=reserve.at+':'+(snapshot.devices||[]).filter(d=>d.kind==='shunt').map(d=>d.mac).join('|'),revision=snapshot.historyRevision;
        let read=this.sunsetRead;
        if(this.a.native?.requestSunsetSoc&&!snapshot.archiveBusy&&(!read||read.key!==key||read.done&&(read.revision!==revision||read.error)&&now-read.requested>=30000)){
          read=this.sunsetRead={key,revision,requested:now,id:this.sunsetRequestId=(this.sunsetRequestId||0)+1,done:false};
          this.a.native.requestSunsetSoc(reserve.at,read.id);
        }
        const point=read?.key===key&&read.done?read.point:null;
        if(known(point?.soc)&&point.soc>=0&&point.soc<=100&&known(point.t)&&point.t<=now&&Math.abs(point.t-reserve.at)<=15*60000){
          Object.assign(reserve,{soc:point.soc,sampledAt:point.t,origin:point.origin,mode:'measured',reasonCode:'measured',reason:''});
        }else{
          reserve.reasonCode='history';reserve.reason=read?.key===key&&!read.done?'Gespeicherter Akkustand wird geladen.':read?.error||'Kein gespeicherter SmartShunt-Ladezustand innerhalb von 15 Minuten vor oder nach Sonnenuntergang vorhanden.';
        }
      }
    }
    layoutPreview(){
      const strip=$('weatherStrip'),days=$('weatherDays'),width=days.clientWidth;if(!width)return;
      const gap=parseFloat(getComputedStyle(days).columnGap)||4,columns=width>=620?Math.max(3,Math.floor((width+gap)/(116+gap))):width>=260?3:width>=175?2:1;
      strip.style.setProperty('--solar-day-width',((width-gap*(columns-1))/columns)+'px');strip.classList.toggle('solar-strip-small',strip.clientHeight<112);
    }
    renderNote(snapshot,weather,now){
      const index=this.days?.findIndex(d=>known(d.reserve?.evening.at)&&d.reserve.evening.at>=now);this.noteDay=index>=0?index:0;
      const reserve=this.days?.[this.noteDay]?.reserve?.evening,labels={soc:'Akku: aktueller Shuntwert fehlt',capacity:'Akku: Kapazität fehlt',voltage:'Akku: Batteriespannung fehlt',consumption:'Akku: Verbrauch wird ermittelt',weather:'Akku: Ertragsdaten unvollständig',sun:'Akku: Sonnenzeit fehlt',past:'Akku: Zeitpunkt bereits vergangen'};
      const text=(labels[reserve?.reasonCode]||'Akku bei Sonnenuntergang')+(snapshot.weatherError?' · Wetter nicht aktuell':weather.partial?' · Wetterdaten teilweise unvollständig':'');
      $('weatherNote').textContent=text;$('weatherNote').title=(reserve?.reason?reserve.reason+' ':'')+'Akku bei Sonnenuntergang · feste Skala von 0 bis 100 %. Sonne: Einstrahlung relativ zum besten Vorschautag. Antippen öffnet die Erklärung und die Reserve beim nächsten Sonnenaufgang.'+(snapshot.weatherError?' '+snapshot.weatherError:weather.updated?' Stand '+new Date(weather.updated).toLocaleTimeString('de-DE',{hour:'2-digit',minute:'2-digit'}):'');
    }
    scale(kind,label,value,text,title){return `<span class="solar-scale-row solar-scale-${kind}${known(value)?'':' solar-no-data'}" title="${esc(title)}"><b>${label}</b><span class="solar-track"><i style="width:${known(value)?Math.max(0,Math.min(100,value)):0}%"></i></span><em>${esc(text)}</em></span>`;}
    learningPanel(usage){
      if(usage.manual||!usage.slotStats)return '';
      const rows=usage.slotStats.map((s,i)=>`<tr><th scope="row">${C.PARTS[i]}<small>${['00–11 Uhr','11–16 Uhr','16–24 Uhr'][i]}</small></th><td>${s.extrapolated?'Fehlt · hochgerechnet':number(s.hours,1)+' h · '+s.days+' '+(s.days===1?'Tag':'Tage')}<small>${s.extrapolated?'Noch keine eigene Datengrundlage':s.provisional?'Vorläufig · wenig Datengrundlage':'Saisonal gelernt'}</small>${s.recentDays?`<small>Letzte 14 Tage: ${s.recentDays} Vergleichstage</small>`:''}${s.estimatedHours?`<small>${number(s.estimatedHours,1)} h mobil geschätzt${s.reconstructedHours?`, davon ${number(s.reconstructedHours,1)} h Archivschätzung`:''}</small>`:''}</td><td>${s.pending?'Noch offen':known(s.candidateWh)?'≈ '+energy(s.candidateWh):'—'}${s.pending?`<small>Einzelschätzung: ≈ ${energy(s.candidateWh)} · weitere Vergleichstage nötig</small>`:''}</td></tr>`).join('');
      const labels={roller:'Rollerladung · Sondernutzung',unknownSetup:'Frühere Aufstellung unbekannt',solarGap:'Solarwerte fehlen',measurementGap:'Gemeinsame Messabdeckung fehlt',missingValue:'Verbrauch nicht bestimmbar',controllerProfile:'Frühere Reglereinstellungen fehlen',chargeLimit:'Ladephase begrenzt oder unklar',weather:'Passende historische Wetterdaten fehlen',extraCharging:'Weitere ungemessene Ladung',invalidValue:'Unplausibler Energiewert',inconsistentEnergy:'Energie und geschätzter Beitrag widersprechen sich',outlier:'Abweichender Tag · Ausreißer'};
      const exclusions=usage.slotStats.flatMap((s,i)=>Object.entries(s.rejected||{}).map(([reason,hours])=>`<div><dt>${C.PARTS[i]} · ${esc(labels[reason]||reason)}</dt><dd>${number(hours,2)} h</dd></div>`)).join('');
      const confirmations=(this.knowledge.confirmedSetups||[]).map(c=>`<p class="muted">Frühere Aufstellung nachträglich bestätigt: ${esc(c.fromDay?.split('-').reverse().join('.'))} bis ${esc(c.throughDay?.split('-').reverse().join('.'))}.${c.weatherComplete?'':' Historische Wetterdaten werden ergänzt; bereits verfügbare Abschnitte bleiben nutzbar.'}</p>`).join('');
      return `<details class="solar-explanation" id="solarLearningCoverage"><summary>Datengrundlage: ${number(usage.usedHours,1)} ausgewertete Stunden</summary><div class="solar-table-scroll"><table class="solar-table solar-learning-table"><thead><tr><th>Abschnitt</th><th>Ausgewertet</th><th>Bedarf im Abschnitt</th></tr></thead><tbody>${rows}</tbody></table></div>${confirmations}<p class="muted">Jeder Tagesabschnitt wird einzeln bewertet. Die letzten 14 Tage erhalten zusätzliches Gewicht, sobald mindestens drei Vergleichstage und genügend wirksame Messzeit vorliegen. Wiederholte neue Gewohnheiten können dadurch ein älteres saisonales Muster ablösen. Bis zu 400 Tage alte Lerndaten bleiben als saisonale Erfahrung verfügbar; sie zählen mit zunehmendem Alter weniger. Ältere Lerndaten werden aus der Prognose genommen, während die Messwerte im Verlauf erhalten bleiben.</p><p class="muted">Ein nur schwach belegter, geschätzter Abschnitt braucht mindestens drei verschiedene Tage und genügend wirksame Messzeit, bevor daraus ein Tagesbedarf berechnet wird. Geschätzte mobile Beiträge zählen schwächer. Bei einer bestätigten früheren Aufstellung mit unbekannter Ladebegrenzung wird der mobile Beitrag innerhalb seines Modellpotenzials geschätzt; diese Archivschätzungen zählen nochmals schwächer und behalten eine große Unsicherheit. Die Einzelschätzung bleibt sichtbar, bis zusätzliche geeignete Messzeit vorliegt. Vollständig fehlende Abschnitte bleiben als Hochrechnung gekennzeichnet.</p>${exclusions?`<h3>Nicht ausgewertete Zeiten</h3><dl class="solar-learning-exclusions">${exclusions}</dl>`:''}</details>`;
    }
    settingsPanel(){
      const settings=this.a.getSnapshot().deviceSettings||{},solar=settings.solar,shunt=settings.shunt;
      const fields=(profile,list)=>list.map(([key,label,unit])=>`<tr><th scope="row">${label}</th><td>${known(profile?.values?.[key])?number(profile.values[key],2)+' '+unit:'—'}</td></tr>`).join('');
      const stamp=profile=>known(profile?.at)?'Zuletzt gelesen: '+new Date(profile.at).toLocaleString('de-DE')+(Date.now()-profile.at>C.HOUR*6?' · Stand veraltet':'')+'.':'Noch nicht gelesen. Beim nächsten Geräteabruf werden die verfügbaren Werte ergänzt.';
      const limit={voltage:'Spannungsgeregelte Ladephase: ungenutzter Solarertrag ist möglich.',current:'Der Regler liegt an seiner eingestellten Stromgrenze; ungenutzter Solarertrag ist möglich.',clear:'Aktuell ist anhand der gelesenen Grenzen und Messwerte keine Begrenzung erkennbar.',unknown:'Eine aktuelle Begrenzung ist mit den verfügbaren Daten noch nicht beurteilbar.'};
      return `<details class="solar-explanation" id="solarDeviceSettings"><summary>Aktive Ladeeinstellungen und Begrenzung</summary><p>${esc(limit[solar?.limit]||limit.unknown)}</p><h3>SmartSolar</h3><p class="muted">${esc(stamp(solar))}</p><table class="solar-table"><tbody>${fields(solar,[['absorptionVoltage','Absorption','V'],['floatVoltage','Erhaltung','V'],['maxChargeCurrent','Ladestromgrenze','A'],['absorptionHours','Maximale Absorptionsdauer','h']])}</tbody></table><h3>SmartShunt</h3><p class="muted">${esc(stamp(shunt))}</p><table class="solar-table"><tbody>${fields(shunt,[['capacity','Akkukapazität','Ah'],['chargedVoltage','Vollerkennung ab','V'],['tailCurrent','Schweifstrom','%'],['chargedMinutes','Erkennungsdauer','min'],['chargeEfficiency','Ladewirkungsgrad','%']])}</tbody></table><p class="muted">Die App liest die verfügbaren Einstellungen beim Geräteabruf. Ein Wechsel wird beim nächsten erfolgreichen Lesen erkannt. Angezeigte 100 % allein belegen keine Begrenzung. Entgangene Wh sind nicht direkt messbar. Das Spannungsziel lässt sich nicht zuverlässig in ein Akkuprozent-Ladeziel umrechnen. Geräteeinstellungen werden weiterhin in VictronConnect geändert.</p></details>`;
    }
    reservePanel(d){
      const zone=this.a.getSnapshot().weather?.timezone,clock=t=>known(t)?new Date(t).toLocaleTimeString('de-DE',{timeZone:zone||undefined,hour:'2-digit',minute:'2-digit'}):'—';
      const point=(label,r)=>{
        const recorded=r.mode==='measured',description=recorded?'Gespeichert · '+clock(r.sampledAt)+' · '+({archive:'Gerätearchiv',live:'Aufzeichnung',csv:'CSV-Sicherung'}[r.origin]||'SmartShunt'):'Schätzbereich '+percent(r.low)+'–'+percent(r.high);
        return `<div class="solar-reserve-point"><h3>${label} · ${clock(r.at)}</h3>${known(r.soc)?`<p><strong>${recorded?'':'≈ '}${percent(r.soc)}</strong><span>${esc(description)}</span></p><div class="solar-reserve-track" role="img" aria-label="${esc(label+': '+percent(r.soc)+', '+description)}"><i style="width:${r.soc}%"></i>${recorded?'':`<span style="left:${r.low}%;width:${r.high-r.low}%"></span>`}</div><div class="solar-reserve-ticks"><span>0 %</span><span>50 %</span><span>100 %</span></div>${r.possibleShortfall?'<p class="solar-reserve-warning">Die Reserve könnte vorher bereits aufgebraucht sein.</p>':''}`:`<p class="muted">${esc(r.reason||'Noch nicht bestimmbar.')}</p>`}</div>`;
      };
      return `<section class="solar-reserve" data-solar-reserve="${esc(d.day)}" aria-label="Akkustand und erwartete Reserve"><h2>Deine Akkureserve</h2>${point('Bei Sonnenuntergang',d.reserve.evening)}${point('Bei Sonnenaufgang am Folgetag',d.reserve.morning)}<p class="solar-reserve-note">${known(d.reserve.evening.at)&&d.reserve.evening.at<Date.now()?'Der vergangene Abendpunkt verwendet einen gespeicherten SmartShunt-Messwert mit höchstens 15 Minuten Abstand zum Sonnenuntergang. Die tatsächliche Messzeit steht dabei; der aktuelle Akkustand wird nicht rückwirkend übernommen. ':''}Zukünftige Werte werden ab dem aktuellen SmartShunt-Ladezustand stundenweise geschätzt. Solar versorgt zuerst den Verbrauch, der Rest lädt den Akku bis höchstens 100 %. Überschüsse werden nicht als Reserve für später gespeichert. Die Umrechnung von Wh in Akkuprozent verwendet Kapazität und aktuelle Batteriespannung. Der Schätzbereich berücksichtigt unsicheren Ertrag und Verbrauch; er ist keine garantierte Grenze. Die Tagesansicht gilt für dieselbe Aufstellung, mobile Kreise zählen nur bei gemeldeter Aufstellung.</p></section>`;
    }

    openDay(index){
      const d=this.days?.[index];if(!d)return;
      const date=new Date(d.day+'T12:00:00').toLocaleDateString('de-DE',{weekday:'long',day:'numeric',month:'long'}),range=d.total==null?'Noch nicht bestimmbar':`${energy(d.low)} – ${energy(d.high)}`;
      const explanation=(d.issues||[]).map(i=>({equipment:'Bitte deine Solarkreise in der Anlagenkonfiguration hinterlegen.',tilt:`${i.group}: Der Montagewinkel fehlt in der Anlagenkonfiguration.`,direction:`${i.group}: Die Himmelsrichtung fehlt unter Aufstellung.`,weather:'Für diesen Tag fehlen Strahlungsdaten. Bitte die Vorschau aktualisieren.',time:'Die Zeitzuordnung der gespeicherten Wetterdaten ist fehlerhaft. Bitte die Vorschau aktualisieren.'}[i.kind]||'Die Ertragsprognose ist gerade nicht verfügbar.')).join(' ');
      const refresh=(d.issues||[]).some(i=>i.kind==='weather'||i.kind==='time');
      const rows=d.parts.map((p,i)=>`<tr><th scope="row">${C.PARTS[i]}<small>${['bis 11 Uhr','11–16 Uhr','ab 16 Uhr'][i]}</small></th><td>${d.complete?'≈ '+energy(p.fixed):'—'}</td><td>${d.complete?'≈ '+energy(p.mobile):'—'}</td></tr>`).join('');
      this.a.openModal('Solarvorschau',`<p class="eyebrow">${esc(date)}</p><div class="solar-result"><strong>${range}</strong><span>geschätztes Gesamtpotenzial aller aktiven Kreise · ${number(d.hours,1)} Sonnenstunden</span><span>Feste Kreise: ${d.fixed==null?'—':'≈ '+energy(d.fixed)} · Mobile Entlastung: ${d.mobile==null?'—':'≈ '+energy(d.mobile)}</span></div><p class="muted">Das Gesamtpotenzial enthält den mobilen Beitrag. Die gemessene Solarertrag-Kachel erfasst dagegen nur die Solarregler; ungemessene mobile Entlastung ist dort nicht enthalten. Ladebegrenzung kann den tatsächlich abgerufenen Ertrag verringern. Der Schätzbereich ist kein garantierter Mindestertrag.</p><div class="solar-table-scroll"><table class="solar-table"><thead><tr><th>Zeitraum</th><th>Feste Kreise</th><th>Mobile Entlastung</th></tr></thead><tbody>${rows}</tbody></table></div><p class="muted">${d.index>3?'Grobe Tendenz für die aktuelle Aufstellung.':'Schätzung für die aktuelle Aufstellung.'} ${d.unknownShade?'Verschattung ist teilweise unbekannt; die Spanne ist deshalb breiter.':''} ${d.complete?'Montagewinkel, Ausrichtung und geschätzter Schatten sind berücksichtigt. Die Tageswerte gelten für dieselbe Aufstellung über den ganzen Tag.':esc(explanation||'Die Ertragsprognose ist gerade nicht verfügbar.')}</p><div class="solar-budget"><p>${d.usage.provisional?'Vorläufiger Bedarf':'Üblicher Verbrauch'}: <strong>${d.usage.wh==null?'Wird ermittelt':'≈ '+energy(d.usage.wh)}</strong></p><p>Mobile Entlastung: <strong>${d.mobile==null?'—':'≈ '+energy(d.mobile)}</strong></p><p>Verbleibender Bedarf: <strong>${d.usage.wh==null||d.mobile==null?'—':'≈ '+energy(d.usage.wh-d.mobile)}</strong></p><p>Erwartete Tagesbilanz: <strong>${d.balance==null?'—':'≈ '+energy(d.balance)}</strong></p></div><p class="muted">${d.usage.manual?'Eigener saisonaler Verbrauchsstandard.':d.usage.wh==null?'Der Bedarf wird aus geeigneten Messabschnitten gelernt, auch bei mobiler Aufstellung.':`${d.usage.provisional?'Vorläufige Schätzung':'Saisonale Schätzung'} aus ${d.usage.days} Tagen mit geeigneten Messabschnitten. ${d.usage.extrapolated?'Fehlende Tagesabschnitte werden vorläufig hochgerechnet. ':''}${d.usage.estimatedHours?'Der mobile Beitrag ist dabei teilweise geschätzt. ':''}`} Ein negativer verbleibender Bedarf bedeutet einen möglichen Überschuss aus dem mobilen Modul. Der mobile Beitrag ist eine Schätzung und kein gemessener Solarertrag.</p><details class="solar-explanation"><summary>Wie die Vorschau lernt</summary><p>Frühere Vorhersagen werden mit geeigneten vollständigen Messabschnitten derselben Aufstellung verglichen. Die Ertragskorrektur greift je Tagesabschnitt ab mindestens drei geeigneten Vergleichstagen mit vorher gespeicherter Prognose; bis dahin gilt das Wetter- und Anlagenmodell. Die tatsächliche Solarquelle wird gemeinsam bewertet; Kreise an einem Regler sind nicht einzeln messbar. Nachtabschnitte können auch bei gemeldeter mobiler Aufstellung den Verbrauch ergänzen. In geeigneten, nicht erkennbar begrenzten Tagesabschnitten wird ungemessene mobile Erzeugung anhand von Leistung, Ausrichtung, Schatten und Wetter geschätzt; solche Verbrauchsbeiträge werden schwächer gewichtet. Begrenzte oder unklare Solarphasen verbessern nicht die Ertragskorrektur. Bei vollständig gemessener Erzeugung bleibt der Verbrauch auch in einer begrenzten Ladephase nutzbar. Tageszeit und Jahreszeit fließen in den üblichen Verbrauch ein. Aufgezeichnete Rollerladungen sind Sondernutzungen: Die betreffenden Ladezeiten bleiben beim Verbrauchslernen unberücksichtigt; geeignete Messzeiten davor und danach bleiben nutzbar. Die tatsächliche Energie bleibt im gemessenen Gesamtverbrauch enthalten. Ausdrücklich bestätigte frühere Aufstellungen können historische Messabschnitte ergänzen. Bei unbekannter früherer Ladebegrenzung bleiben mobile Beiträge besonders unsichere Archivschätzungen; sie trainieren nicht die Solar-Ertragskorrektur. Solarertrag und Verbrauch werden weiterhin mit der bisherigen Messformel ausgewertet. Wetterfehler und Veränderungen vor Ort bleiben möglich.</p></details>${this.settingsPanel()}${refresh?'<button class="quiet" id="solarDayRefresh">Vorschau aktualisieren</button>':''}<div class="form-actions"><button class="quiet" id="solarDayUsage">Verbrauch</button><button class="primary" id="solarDaySetup">Aufstellung ändern</button></div>`);
      const result=document.querySelector('#modalBody .solar-result');if(result)result.insertAdjacentHTML('afterend',this.reservePanel(d));
      const budget=document.querySelector('#modalBody .solar-budget');if(budget)budget.insertAdjacentHTML('afterend',this.learningPanel(d.usage));
      if($('solarDayRefresh'))$('solarDayRefresh').onclick=async()=>{const error=await this.a.native?.refreshWeather?.();if(error){this.a.toast(error);return;}this.a.closeModal();this.a.toast('Solarvorschau wird aktualisiert.');};
      $('solarDaySetup').onclick=()=>this.openSetup();$('solarDayUsage').onclick=()=>this.openUsage();
    }
    async mobile(){
      const eq=this.equipment(),groups=eq.groups.filter(g=>g.placement==='mobile');
      if(groups.length!==1){this.openSetup();return;}
      try{const plan=this.plan(),p=plan.groups[groups[0].id];p.active=!p.active;await this.save(plan);this.a.toast(p.active?'Mobiler Kreis wird als geschätzte Entlastung berücksichtigt.':'Mobiler Kreis abgebaut.');}catch(e){this.a.toast(e.message);}
    }
    openSetup(resume=false){
      const eq=this.equipment();if(!resume||!this.draft)this.draft={plan:copy(this.plan()),place:copy(this.a.getSnapshot().settings.weatherPlace||{}),newSite:false};
      this.a.openModal('Aufstellung ändern',`<div id="solarSetupPane"><p class="muted">Der Standort gilt für alle Kreise. Winkel und Leistung kommen aus deiner Anlagenkonfiguration.</p><div class="solar-site"><strong id="solarSelectedPlace">${esc(this.draft.place.name||'Noch kein Standort')}</strong><button class="secondary" id="solarLocate">GPS-Standort übernehmen</button><div class="weather-search"><input id="solarPlaceQuery" type="search" placeholder="Ort oder Postleitzahl" maxlength="120" aria-label="Standort suchen"><button class="quiet" id="solarPlaceSearch">Suchen</button></div><div id="solarPlaceResults" class="weather-results" role="status"></div></div><div id="solarSiteGroups">${eq.groups.length?eq.groups.map(g=>this.groupForm(g)).join(''):'<p class="muted">Bitte zuerst deine Solarkreise in der Anlagenkonfiguration hinterlegen.</p>'}</div><p id="solarSetupStatus" class="muted" role="status"></p><div class="solar-setup-tools"><button class="quiet" id="solarNewSite">Neuer Standplatz</button><button class="quiet" id="solarEditEquipment">Anlagenkonfiguration</button></div><div class="form-actions"><button class="quiet" id="solarSetupCancel">Abbrechen</button><button class="primary" id="solarSetupSave">Aufstellung speichern</button></div></div>`);
      $('solarLocate').onclick=()=>this.locate();$('solarPlaceSearch').onclick=()=>this.searchPlace();$('solarPlaceQuery').onkeydown=e=>{if(e.key==='Enter')this.searchPlace();};
      $('solarNewSite').onclick=()=>{this.gather();this.draft.plan=C.newSite(this.draft.plan,eq);this.draft.newSite=true;this.openSetup(true);this.status('Neue Aufstellung: mobile Kreise sind aus; Richtung und Schatten bitte neu bestimmen.');};
      $('solarEditEquipment').onclick=()=>{this.gather();this.a.equipment.open();};$('solarSetupCancel').onclick=()=>{this.draft=null;this.a.closeModal();};
      $('solarSetupSave').onclick=async()=>{try{this.gather();await this.save(this.draft.plan,this.draft.place);this.draft=null;this.a.closeModal();this.a.toast('Aufstellung gespeichert. Solarvorschau wird aktualisiert.');}catch(e){this.status(e.message);}};
      for(const element of $('solarSiteGroups').querySelectorAll('[data-solar-group]'))this.bindGroup(element);
    }
    groupForm(g){
      const p=this.draft.plan.groups[g.id],same=p.shade.every(x=>x===p.shade[0]),shadeSelect=(slot,value)=>`<select data-shade="${slot}">${shades.map((label,i)=>`<option value="${i-1}" ${value===(i===0?null:i-1)?'selected':''}>${label}</option>`).join('')}</select>`;
      return `<fieldset class="solar-group solar-position" data-solar-group="${g.id}"><legend>${esc(g.label)} · ${number(g.count*g.watts)} Wp</legend><label class="solar-check"><input type="checkbox" data-solar-active ${p.active?'checked':''}>${g.placement==='mobile'?'Aufgestellt · als geschätzte Entlastung berücksichtigen':'Diesen Kreis berücksichtigen'}</label><p class="muted">Montagewinkel: ${g.tilt==null?'noch unbekannt':number(g.tilt,1)+'°'} · ${g.placement==='mobile'?'mobiler Kreis':'fest installiert'}</p><div class="solar-bearing"><div class="solar-dial" role="img" aria-label="Himmelsrichtung"><span class="north">N</span><span class="east">O</span><span class="south">S</span><span class="west">W</span><i style="transform:rotate(${p.azimuth??0}deg)"></i></div><div>${field('Himmelsrichtung',`<select data-solar-direction><option value="">Noch bestimmen</option>${C.DIRECTIONS.map((d,i)=>`<option value="${i*22.5}" ${p.azimuth===i*22.5?'selected':''}>${d}</option>`).join('')}</select>`)}<input data-solar-angle type="range" min="0" max="337.5" step="22.5" value="${p.azimuth??0}" aria-label="Himmelsrichtung drehen"><button class="quiet" data-solar-compass>Mit Handy bestimmen</button></div></div>${field('Verschattung für den ganzen Tag',`<select data-shade-all><option value="mixed" ${!same?'selected':''}>Nach Tageszeit unterschiedlich</option>${shades.map((label,i)=>`<option value="${i-1}" ${same&&p.shade[0]===(i===0?null:i-1)?'selected':''}>${label}</option>`).join('')}</select>`)}<details ${same?'':'open'}><summary>Nach Tageszeit unterscheiden</summary><div class="solar-shade-times">${C.PARTS.map((label,i)=>field(label,shadeSelect(i,p.shade[i]))).join('')}</div></details></fieldset>`;
    }
    bindGroup(el){
      const select=el.querySelector('[data-solar-direction]'),range=el.querySelector('[data-solar-angle]'),needle=el.querySelector('.solar-dial i');
      range.oninput=()=>{select.value=range.value;needle.style.transform=`rotate(${range.value}deg)`;};
      select.onchange=()=>{if(select.value!=='')range.value=select.value;needle.style.transform=`rotate(${range.value}deg)`;};
      const dial=el.querySelector('.solar-dial');dial.onpointerdown=e=>{const b=dial.getBoundingClientRect(),value=C.bearing(Math.atan2(e.clientX-b.left-b.width/2,-(e.clientY-b.top-b.height/2))*180/Math.PI);range.value=(Math.round(value/22.5)%16)*22.5;range.oninput();};
      el.querySelector('[data-shade-all]').onchange=e=>{if(e.target.value==='mixed')return;el.querySelectorAll('[data-shade]').forEach(s=>s.value=e.target.value);};
      el.querySelectorAll('[data-shade]').forEach(s=>s.onchange=()=>{const values=[...el.querySelectorAll('[data-shade]')].map(x=>x.value);el.querySelector('[data-shade-all]').value=values.every(x=>x===values[0])?values[0]:'mixed';});
      el.querySelector('[data-solar-compass]').onclick=()=>{this.gather();this.openCompass(el.dataset.solarGroup);};
    }
    gather(){
      if(!this.draft||!$('solarSiteGroups'))return;
      for(const el of $('solarSiteGroups').querySelectorAll('[data-solar-group]')){const value=el.querySelector('[data-solar-direction]').value;this.draft.plan.groups[el.dataset.solarGroup]={active:el.querySelector('[data-solar-active]').checked,azimuth:value===''?null:Number(value),shade:[...el.querySelectorAll('[data-shade]')].map(s=>Number(s.value)<0?null:Number(s.value))};}
    }
    status(text){if($('solarSetupStatus'))$('solarSetupStatus').textContent=text;}
    locate(){this.gather();if(this.a.native?.locateSolarSite){this.status('Standort wird ermittelt …');this.a.native.locateSolarSite();}else this.status('GPS ist in der Android-App verfügbar. Du kannst den Ort manuell suchen.');}
    searchPlace(){this.gather();const query=$('solarPlaceQuery').value.trim();if(query.length<2){this.status('Bitte einen Ort oder eine Postleitzahl eingeben.');return;}if(!this.a.native?.searchSolarPlaces){this.status('Die Ortssuche ist in der Android-App verfügbar.');return;}this.lastQuery=query;$('solarPlaceResults').textContent='Suche …';this.a.native.searchSolarPlaces(query);}
    selectPlace(place,message){
      this.gather();const old=this.draft.place,changed=!Number.isFinite(old.latitude)||Math.abs(old.latitude-place.latitude)>.001||Math.abs(old.longitude-place.longitude)>.001;
      if(changed){this.draft.plan=C.newSite(this.draft.plan,this.equipment());this.draft.newSite=true;}
      this.draft.place={name:place.name,latitude:place.latitude,longitude:place.longitude};this.openSetup(true);this.status(message+(changed?' Richtung und Schatten am neuen Standort bitte prüfen.':''));
    }
    openCompass(group){
      this.compassGroup=group;this.compassSamples=[];this.compassOutside=true;this.capturedHeading=null;
      const g=this.equipment().groups.find(g=>g.id===group);
      this.a.openModal('Himmelsrichtung bestimmen',`<div id="solarCompass"><p class="eyebrow">${esc(g?.label||'Solarkreis')}</p><div class="segments solar-compass-modes"><button id="solarCompassOutside" class="selected">Draußen</button><button id="solarCompassInside">Drinnen</button></div><p id="solarCompassInstruction" class="solar-instruction"></p><div class="solar-compass-result"><strong id="solarCompassValue">—</strong><span id="solarCompassDegrees"></span></div><p id="solarCompassQuality" class="muted" role="status">Handy waagerecht halten und kurz ruhig halten.</p><p class="muted">Metall, Magnete und elektrische Geräte können die Richtung verfälschen. Drinnen ist eine Kontrollmessung draußen mit Abstand zum Fahrzeug sinnvoll. Auch ein ruhiger Zeiger garantiert keine richtige Richtung.</p><div class="form-actions"><button class="quiet" id="solarCompassBack">Manuell wählen</button><button class="primary" id="solarCompassCapture" disabled>Richtung übernehmen</button></div></div>`);
      const mode=outside=>{this.compassOutside=outside;this.compassSamples=[];this.capturedHeading=null;$('solarCompassCapture').disabled=true;$('solarCompassOutside').classList.toggle('selected',outside);$('solarCompassInside').classList.toggle('selected',!outside);$('solarCompassInstruction').textContent=outside?'Frontal vor die Modulvorderseite stellen. Handy waagerecht halten, die Oberkante gerade auf das Modul richten. Die App übernimmt die Gegenrichtung.':'Drinnen vor die Montagewand stellen. Handy waagerecht halten und die Oberkante gerade auf die Wand richten, an deren Außenseite das Modul sitzt.';};
      $('solarCompassOutside').onclick=()=>mode(true);$('solarCompassInside').onclick=()=>mode(false);mode(true);
      $('solarCompassBack').onclick=()=>this.openSetup(true);$('solarCompassCapture').onclick=()=>{if(this.capturedHeading==null)return;this.draft.plan.groups[group].azimuth=(Math.round(this.capturedHeading/22.5)%16)*22.5;this.openSetup(true);this.status('Himmelsrichtung übernommen. Bitte Aufstellung speichern.');};
      if(this.a.native?.startSolarCompass)this.startCompass();else $('solarCompassQuality').textContent='Der Handykompass ist in der Android-App verfügbar. Die manuelle Richtungsauswahl bleibt möglich.';
    }
    compassResult(data){
      if(!$('solarCompass'))return;if(data.error){$('solarCompassQuality').textContent=data.error;return;}
      const heading=C.panelHeading(data.heading,this.compassOutside);if(heading==null)return;
      $('solarCompassValue').textContent=C.direction(heading);$('solarCompassDegrees').textContent=number(heading)+'° · Modulvorderseite';
      if(!data.usable){this.compassSamples=[];this.capturedHeading=null;$('solarCompassCapture').disabled=true;$('solarCompassQuality').textContent='Messung noch unsicher. Handy waagerecht halten, Abstand zu Metall und Magneten vergrößern; gegebenenfalls den Kompass mit einer Achtbewegung kalibrieren.';return;}
      const now=Date.now();this.compassSamples.push({t:now,h:heading});this.compassSamples=this.compassSamples.filter(x=>now-x.t<4000).slice(-16);
      const sine=this.compassSamples.reduce((sum,x)=>sum+Math.sin(x.h*Math.PI/180),0),cosine=this.compassSamples.reduce((sum,x)=>sum+Math.cos(x.h*Math.PI/180),0),average=C.bearing(Math.atan2(sine,cosine)*180/Math.PI),spread=Math.max(...this.compassSamples.map(x=>Math.abs(((x.h-average+540)%360)-180)));
      const stable=this.compassSamples.length>=8&&now-this.compassSamples[0].t>=1200&&spread<6;
      this.capturedHeading=stable?average:null;$('solarCompassCapture').disabled=!stable;$('solarCompassQuality').textContent=stable?'Sensorwerte sind ruhig. Richtung vor dem Übernehmen prüfen.'+(data.trueNorth?'':' Für eine genauere Richtung zuerst den Standort wählen.'):'Messung beruhigt sich …';
    }
    startCompass(){this.a.native?.startSolarCompass?.(JSON.stringify(this.draft?.place||{}));}
    stopSensors(){this.a.native?.stopSolarCompass?.();this.a.native?.cancelSolarLocation?.();this.compassSamples=[];this.capturedHeading=null;if($('solarCompassCapture'))$('solarCompassCapture').disabled=true;}
    openUsage(){
      const plan=this.plan();this.a.openModal('Verbrauch für die Planung',`<p class="muted">Ohne Vorgaben nutzbar: Die App lernt den üblichen Bedarf aus geeigneten Messabschnitten. Geschätzte mobile Beiträge brauchen mehrere Vergleichstage und werden schwächer gewichtet. Noch unbekannte Jahreszeiten bleiben eine vorläufige Schätzung.</p>${field('Verbrauch ermitteln','<select id="solarUsageMode"><option value="auto">Automatisch lernen</option><option value="manual">Eigene saisonale Werte verwenden</option></select>')}<div id="solarUsageFields" class="solar-manual-grid">${['Frühling','Sommer','Herbst','Winter'].map((name,i)=>field(name,`<input data-solar-manual="${i}" type="number" min="0" max="100000" step="1" placeholder="Automatisch" value="${plan.manual[i]??''}"><small>Wh pro Tag · freiwillig</small>`)).join('')}</div><p id="solarUsageStatus" class="muted" role="status"></p><div class="form-actions"><button class="quiet" id="solarUsageCancel">Abbrechen</button><button class="primary" id="solarUsageSave">Speichern</button></div>`);
      $('solarUsageMode').value=plan.usageMode;const update=()=>{$('solarUsageFields').hidden=$('solarUsageMode').value==='auto';};$('solarUsageMode').onchange=update;update();$('solarUsageCancel').onclick=()=>this.a.closeModal();
      $('solarUsageSave').onclick=async()=>{try{plan.usageMode=$('solarUsageMode').value;plan.manual=[...document.querySelectorAll('[data-solar-manual]')].map(input=>input.value===''?null:Number(input.value));await this.save(plan);this.a.closeModal();this.a.toast('Verbrauchseinstellung gespeichert.');}catch(e){$('solarUsageStatus').textContent=e.message;}};
    }
  };
})();
