(function(){
  'use strict';
  const $=id=>document.getElementById(id),C=window.WattRollerCore;
  const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const num=(v,d=1)=>typeof v==='number'&&Number.isFinite(v)?v.toLocaleString('de-DE',{maximumFractionDigits:d}):'—';
  const energy=v=>num(v)+' Wh';
  const dateInput=v=>{if(!v)return '';const d=new Date(v);return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16);};
  window.WattRoller=class{
    constructor(a){this.a=a;this.state={profile:C.defaults,live:{},session:{}};$('rollerConfigure').onclick=$('rollerSetup').onclick=()=>this.configure();$('rollerSettings').onclick=()=>this.configure();$('rollerJournal').onclick=()=>this.journal();$('rollerSession').onclick=()=>this.session();window.onRollerResult=result=>this.result(result);}
    render(){
      try{if(this.a.native?.rollerSnapshot)this.state=JSON.parse(this.a.native.rollerSnapshot());else this.state.profile={...C.defaults,...JSON.parse(localStorage.getItem('wattpit-roller')||'{}')};}catch(_){}
      const s=this.state,p={...C.defaults,...s.profile},l=s.live||{},r=C.plan(p,l),session=s.session||{};
      const configured=!!p.mac||!!session.started,viewer=!!this.a.native?.isLanViewer?.();
      $('rollerPanel').hidden=this.a.isDemo()||!configured;$('rollerWelcome').hidden=configured&&!this.a.isDemo();$('rollerViewerNote').hidden=!viewer;$('rollerSetup').disabled=viewer;
      $('rollerActionStatus').hidden=!this.feedback;$('rollerActionStatus').textContent=this.feedback||'';
      $('rollerName').textContent=p.name;
      $('rollerState').textContent=this.a.isDemo()?'Demo · Rollerwerte werden nicht angezeigt':(session.active?(session.automatic?'Automatisch erkannt · Sondernutzung läuft · ':'Sondernutzung · Rollerladung läuft · '):'')+(s.status||'Direkte BMS-Verbindung in der Android-App');
      const live=this.a.isDemo()?{}:l;
      $('rollerSoc').textContent=num(live.soc)+' %';$('rollerPower').textContent=num(live.power)+' W';
      $('rollerInput').textContent=energy(session.inputWh);$('rollerCharged').textContent=energy(session.batteryWh);
      $('rollerLiveNote').textContent=live.at?`${num(live.voltage,2)} V · ${num(live.current,2)} A · Wohnwagenanteil ≈ ${num(s.estimatedInputWatts)} W`:'Keine aktuellen BMS-Werte · alte Werte werden nicht fortgeschrieben.';
      $('rollerSession').textContent=session.active?'Sondernutzung beenden':'Ladung als Sondernutzung starten';$('rollerSession').disabled=!!this.pending||this.a.isDemo()||!this.a.native?.rollerSession&&!this.a.native?.requestRollerSession||!session.active&&!s.fresh||viewer&&s.status==='Hauptgerät nicht erreichbar';
      if(this.pending)$('rollerSession').textContent='Bestätigung wird erwartet …';
      if($('rollerConfirmSession')){$('rollerConfirmSession').disabled=!!this.pending;$('rollerConfirmSession').textContent=this.pending?'Bestätigung wird erwartet …':'Aufzeichnung starten';}
      $('rollerGaps').textContent=session.started?`${num((session.observedMillis||0)/60000)} min erfasst · ${num((session.gapMillis||0)/1000,0)} s ohne Messung${session.active?' · Aufzeichnung läuft':''}`:(p.autoSpecialUse?(s.autoSuppressed?'Automatik pausiert bis zum erkannten Ladeende. Die nächste Ladung wird wieder automatisch zugeordnet.':'Automatische Sondernutzung aktiv: Eine Ladung am Wohnwagen wird anhand des BMS-Ladestroms erkannt.'):'Rollerladung als Sondernutzung starten: Die Ladezeit bleibt vom üblichen Tagesbedarf getrennt.');
      let text=r.away?'Geplante Fahrt läuft: Roller als Zusatzspeicher nicht verfügbar.':r.impossible?'Die geplante Fahrt samt Reserve überschreitet die hinterlegte Akkukapazität.':r.missing===null?'Für die Ladeplanung aktuelle BMS-Werte empfangen.':`Bis zum Ladeziel fehlen ≈ ${energy(r.missing)} im Roller. Wohnwagenbedarf ≈ ${energy(r.draw)}, Ladedauer ≈ ${num(r.hours)} h.`;
      if(r.late)text+=' Bis zur geplanten Abfahrt reicht die angesetzte Ladeleistung voraussichtlich nicht.';
      if(p.rideKm>0)text+=` Fahrt: ${num(p.rideKm)} km · Bedarf inkl. Fahrpuffer ${energy(r.rideWh)} plus ${energy(r.capacity*p.reservePercent/100)} Mindestreserve.`;
      const today=this.a.getSolar()?.days?.find(d=>d.day===new Date().toLocaleDateString('sv-SE'));
      if(!r.away&&!r.impossible&&r.draw>0&&today){
        const now=Date.now(),solar=(today.timeline||[]).reduce((sum,h)=>sum+h.wh*Math.max(0,Math.min(1,(h.to-now)/(h.to-h.from))),0),remainingHours=Math.max(0,(new Date(today.day+'T23:59:59').getTime()-now)/3600000),usage=typeof today.usage?.wh==='number'?today.usage.wh*remainingHours/24:null;
        if(usage!==null)text+=` Grober Solarüberschuss bis Tagesende nach üblichem Bedarf: ${energy(Math.max(0,solar-usage))}. ${solar-usage>=r.draw?'Die Vorschau deckt die geplante Ladung rechnerisch.':'Die geplante Ladung benötigt voraussichtlich zusätzlich Batteriereserve.'}`;
      }
      if(p.planCharge||session.active)text+=' Die Akkuprognose berücksichtigt den verbleibenden Ladebedarf ab jetzt, sofern machbar und aktuelle BMS-Werte vorliegen.';
      $('rollerPlan').textContent=text;
    }
    journal(){const rows=this.state.journal||[];this.a.openModal('Lokales Ladejournal',rows.length?rows.slice().reverse().map(s=>`<article class="card" style="padding:14px;margin:12px 0"><strong>${esc(s.profile?.name||'Roller')} · ${esc(new Date(s.started).toLocaleString('de-DE'))}</strong><p class="muted">Sondernutzung · Rollerladung</p><p>BMS: ${energy(s.batteryWh)} · Wohnwagenanteil geschätzt: ${energy(s.inputWh)}</p><p class="muted">${num(s.observedMillis/60000)} min erfasst · ${num(s.gapMillis/1000,0)} s ohne Messung</p></article>`).join(''):'<p>Noch keine abgeschlossene Ladeaufzeichnung.</p>');}
    session(){if(this.a.isDemo()||this.pending)return;const active=!!this.state.session?.active;
      if(!active){this.a.openModal('Rollerladung zuordnen',`<p>Das Ladekabel muss über Wechselrichter und Ladegerät am Wohnwagen angeschlossen sein. Die App schaltet keine Ladegeräte.</p><p><strong>Sondernutzung:</strong> Dieser Ladezeitraum wird aus dem Lernen des üblichen Tagesbedarfs herausgenommen. Die tatsächliche Energie bleibt im Gesamtverbrauch enthalten.</p><p class="muted">BMS-Ladeenergie wird gemessen. Der Wohnwagenanteil wird mit dem vorläufigen Verlustprofil geschätzt und nicht zum Shuntverbrauch addiert. Die letzte Aufzeichnung bleibt im lokalen Ladejournal erhalten.</p><button class="primary" id="rollerConfirmSession">Aufzeichnung starten</button>`);$('rollerConfirmSession').onclick=()=>this.toggle(true);}
      else this.toggle(false);
    }
    toggle(start){
      if(this.pending)return;
      if(this.a.native?.requestRollerSession){
        this.pending=true;this.feedback='Änderung wird bestätigt …';this.render();
        try{this.a.native.requestRollerSession(start,Number(this.state.sessionRevision)||0);}catch(e){this.result({error:'Die Ladeanfrage konnte nicht gesendet werden.'});}
      }else{const error=this.a.native.rollerSession(start);this.result(error?{error}:{ok:true});}
    }
    result(result){
      this.pending=false;if(result.roller)this.state=result.roller;this.a.poll();this.render();
      if(result.error){this.feedback=result.error;this.a.toast(result.error);}
      else{this.feedback=this.state.session?.active?'Bestätigt: Sondernutzung läuft.':'Bestätigt: Sondernutzung beendet.';if($('rollerConfirmSession'))this.a.closeModal();this.a.toast(this.feedback);}
      this.render();
    }
    configure(){if(this.a.native?.isLanViewer?.()){this.a.toast('Das Rollerprofil am Hauptgerät einrichten.');return;}this.render();const p={...C.defaults,...this.state.profile};
      const field=(key,label,step='any')=>`<label class="form-field">${label}<input id="roller-${key}" type="number" step="${step}" value="${p[key]}"></label>`;
      this.a.openModal('Roller · Speicher und Ladeweg',`<p class="muted">Direktverbindung zu JBD/Overkill-BMS. Aktuelles ScootPit kann seine BMS-Werte lokal teilen; die Verbindung wird automatisch übergeben. Overkill muss bei belegter Verbindung weiterhin getrennt werden. Watt’n’Pit liest Messwerte und ändert keine BMS-Einstellungen.</p><label class="form-field">Name<input id="roller-name" maxlength="60" value="${esc(p.name)}"></label><label class="form-field">Bluetooth-Adresse des BMS<input id="roller-mac" placeholder="AA:BB:CC:DD:EE:FF" value="${esc(p.mac)}" autocapitalize="characters"></label><div id="rollerBonded"></div><p class="muted">Adresse wie in ScootPit/Overkill oder aus den gekoppelten Geräten wählen.</p><label class="form-field">Batterietechnologie<select id="roller-technology"><option value="Li-Ion">Lithium-Ionen</option><option value="LiFePO4">LiFePO₄</option></select></label><div class="roller-fields">${field('capacityAh','Nennkapazität · Ah')}${field('nominalVoltage','Nennspannung · V')}${field('reservePercent','Mindestreserve · %')}${field('targetPercent','Ladeziel · %')}</div><h3>Ladeprofil · vorläufig</h3><p class="muted">Messpunkt: 96 W im Roller bei 138 W am Wohnwagen. Modell: 21 W Grundbedarf plus leistungsabhängige Verluste. Andere Lasten beeinflussen diese Zuordnung nicht.</p><div class="roller-fields">${field('idleWatts','Wechselrichter-Grundbedarf · W')}${field('conversionEfficiency','Wirkungsgrad ohne Grundbedarf · 0–1')}${field('chargeWatts','Angesetzte BMS-Ladeleistung · W')}</div><label class="form-field"><span><input id="roller-planCharge" type="checkbox"> Ladung ab jetzt in der Akkuprognose einplanen</span></label><p class="muted">Die Prognose zieht nur den noch benötigten Ladebedarf ab. Während einer Ladeaufzeichnung gilt dies automatisch. Empfangslücken unterbrechen diese Zusatzprognose.</p><h3>Automatische Sondernutzung</h3><label class="form-field"><span><input id="roller-autoSpecialUse" type="checkbox"> Laden am Wohnwagen automatisch zuordnen</span></label><p class="muted">Einmal einschalten, wenn der Roller über den Wohnwagen geladen wird. Frischer positiver BMS-Ladestrom startet die Sondernutzung; bestätigtes Ladeende beendet sie. Empfangslücken erzeugen keine Ladeenergie. Das BMS erkennt die Stromquelle nicht: Vor dem Laden an einer externen Steckdose diese Option ausschalten. Manuelles Beenden pausiert die Automatik bis zum erkannten Ladeende.</p><h3>Geplante Fahrt</h3><div class="roller-fields">${field('rideKm','Gesamtstrecke · km')}${field('whPerKm','Verbrauch · Wh/km')}${field('rideReservePercent','Zusätzlicher Fahrpuffer · %')}</div><label class="form-field">Abfahrt<input id="roller-departure" type="datetime-local" value="${dateInput(p.departure)}"></label><label class="form-field">Rückkehr<input id="roller-returnAt" type="datetime-local" value="${dateInput(p.returnAt)}"></label><p class="muted">Fahrverbrauch vorerst manuell. BMS-Livewerte aus ScootPit werden automatisch übernommen. Der Austausch abgeschlossener Fahrten ist noch nicht enthalten. Ohne Rückspeisemessung wird keine nutzbare Energie für den Rückweg zum Wohnwagen zugesagt.</p><p id="rollerError" class="form-error" role="alert" hidden></p><button class="primary" id="rollerSave">Profil speichern</button>`);
      $('roller-technology').value=p.technology;$('roller-planCharge').checked=!!p.planCharge;$('roller-autoSpecialUse').checked=!!p.autoSpecialUse;
      try{const devices=JSON.parse(this.a.native?.rollerDevices?.()||'[]');for(const d of devices){const b=document.createElement('button');b.className='quiet';b.textContent=(d.name||'Bluetooth-Gerät')+' · '+d.mac;b.onclick=()=>$('roller-mac').value=d.mac;$('rollerBonded').append(b);}}catch(_){}
      $('rollerSave').disabled=!!this.state.session?.active;
      if(this.state.session?.active){$('rollerError').hidden=false;$('rollerError').textContent='Vor Profiländerungen die Ladeaufzeichnung beenden.';}
      $('rollerSave').onclick=()=>{try{
        const next={...p,autoSpecialUse:$('roller-autoSpecialUse').checked,planCharge:$('roller-planCharge').checked,name:$('roller-name').value.trim(),mac:$('roller-mac').value.trim().toUpperCase(),technology:$('roller-technology').value};
        for(const k of Object.keys(C.defaults))if(typeof C.defaults[k]==='number'){const el=$('roller-'+k);if(k==='departure'||k==='returnAt'){next[k]=el.value?new Date(el.value).getTime():0;}else{if(!el.value.trim())throw Error('Bitte alle Zahlenfelder ausfüllen.');next[k]=Number(el.value);if(!Number.isFinite(next[k]))throw Error('Bitte gültige Zahlen eingeben.');}}
        const error=this.a.native?.saveRoller?.(JSON.stringify(next));if(error)throw Error(error);if(!this.a.native)localStorage.setItem('wattpit-roller',JSON.stringify(next));this.a.closeModal();this.a.poll();this.render();this.a.toast('Rollerprofil gespeichert. Aufzeichnung für Bluetooth-Empfang starten.');
      }catch(e){$('rollerError').hidden=false;$('rollerError').textContent=e.message;}};
    }
  };
})();
