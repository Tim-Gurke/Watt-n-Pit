(function(){
  'use strict';
  const $=id=>document.getElementById(id),esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const stamp=t=>new Date(t).toLocaleString('de-DE',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'});
  const local=t=>{const d=new Date(t);return new Date(t-d.getTimezoneOffset()*60000).toISOString().slice(0,16);};
  const names={home:'Bewohnt',away:'Abwesend',unknown:'Anwesenheit unklar'};
  window.WattPresence=class{
    constructor(a){this.a=a;this.state={};this.prompted='';$('presenceSettings').onclick=()=>this.open();}
    render(){
      try{this.state=JSON.parse(this.a.native?.presenceSnapshot?.()||'{}');}catch(_){this.state={state:'unknown'};}
      const s=this.state,pending=s.pending||[];
      $('presenceSummary').textContent=`${names[s.state]||'Anwesenheit wird ermittelt'} · ${s.mode&&s.mode!=='auto'?'manuelle Korrektur':'automatisch über WLAN am Hauptgerät'}${s.stale?' · letzter empfangener Stand':''}${pending.length?' · Abfahrt bitte prüfen':''}`;
      if($('presenceLive'))$('presenceLive').textContent=$('presenceSummary').textContent;
      if($('presenceMode')&&document.activeElement!==$('presenceMode'))$('presenceMode').value=s.mode||'auto';
      const plan=pending[0],key=plan?.id+':'+s.revision;
      if(plan&&!s.stale&&s.state==='home'&&key!==this.prompted&&!document.hidden&&$('modal').hidden&&!document.body.classList.contains('plant-command-busy')){this.prompted=key;this.ask(plan);}
    }
    async command(method,...args){
      if(!this.a.native?.[method])throw Error('Bitte Watt’n’Pit auf allen Geräten aktualisieren.');
      const error=await this.a.native[method](...args);if(error)throw Error(error);
      this.a.poll();this.a.refresh();this.render();
    }
    error(e){const el=$('presenceError');if(el){el.hidden=false;el.textContent=e.message;}else this.a.toast(e.message);}
    open(){
      const s=this.state,plans=(s.plans||[]).filter(p=>!['cancelled','returned'].includes(p.status)).sort((a,b)=>a.from-b.from),pending=s.pending||[];
      this.a.openModal('Anwesenheit und Planung',`<p id="presenceLive" role="status">${esc($('presenceSummary').textContent)}</p><p class="muted">WLAN am Hauptgerät verbunden: nach 30 Sekunden bewohnt. Ohne Verbindung: nach fünf Minuten abwesend. Internet ist dafür nicht nötig; S20 oder S10 als Hotspot ist gleichwertig. Bei ausgeschaltetem WLAN oder fehlender Beobachtung bleibt die Anwesenheit unklar. Ein ausgeschalteter Hotspot kann wie Abwesenheit wirken.</p><label class="form-field">Anwesenheit<select id="presenceMode"><option value="auto">Automatisch über WLAN</option><option value="home">Korrektur: bewohnt</option><option value="away">Korrektur: abwesend</option></select></label><p class="muted">Bewohnter Bedarf und Abwesenheits-Grundlast werden getrennt gelernt. Tatsächliche Messabschnitte zählen für das Lernen; geplante Termine gelten für die Vorschau. Unbekannte historische Zeiten bleiben als bisherige Vergleichsbasis erhalten. Neue unklare Zeiten werden keinem Profil zugerechnet. Lernarchiv: drei Jahre, mit stärkerem Gewicht neuer und jahreszeitlich passender Daten.</p>${pending.length?'<button id="presenceReview" class="primary">Du bist noch da? Abfahrt prüfen</button>':''}<h3>Geplante Abwesenheiten</h3><div class="presence-plans">${plans.length?plans.map(p=>`<article><p><strong>${esc(stamp(p.from))}</strong><br>Rückkehr: ${esc(stamp(p.to))}</p><p class="muted">${esc({pending:'Abfahrt ausgeblieben · Klärung offen',snoozed:'Fahre gleich · erneute Prüfung folgt',active:'Abwesenheit erkannt',planned:'Geplant'}[p.status]||'Geplant')}</p><div class="row"><button class="secondary" data-presence-edit="${esc(p.id)}">Ändern</button><button class="quiet" data-presence-cancel="${esc(p.id)}">Absagen</button></div></article>`).join(''):'<p class="muted">Noch keine Abwesenheit geplant.</p>'}</div><button id="presenceAdd" class="primary">Abwesenheit planen</button><p class="muted">Abfahrt und Rückkehr sind einzeln bearbeitbar. Bei ausgebliebener Abfahrt fragt die App nach fünf Minuten nach. Bis zur Klärung berücksichtigt die Vorschau die erkannte Anwesenheit. „Fahre gleich“ erinnert nach 15 Minuten erneut.</p><button id="presenceNotify" class="quiet">Benachrichtigungen erlauben</button><p id="presenceError" class="form-error" hidden role="alert"></p>`);
      $('presenceMode').value=s.mode||'auto';$('presenceMode').onchange=async()=>{try{await this.command('savePresence',$('presenceMode').value);this.open();}catch(e){this.error(e);}};
      $('presenceAdd').onclick=()=>this.edit();if($('presenceReview'))$('presenceReview').onclick=()=>this.ask(pending[0]);
      document.querySelectorAll('[data-presence-edit]').forEach(b=>b.onclick=()=>this.edit(plans.find(p=>p.id===b.dataset.presenceEdit)));
      document.querySelectorAll('[data-presence-cancel]').forEach(b=>b.onclick=async()=>{try{await this.command('resolveAbsence',b.dataset.presenceCancel,'cancel');this.open();}catch(e){this.error(e);}});
      $('presenceNotify').onclick=()=>this.a.native?.presenceNotifications?.();
    }
    edit(plan){
      const from=plan?.from||Math.ceil((Date.now()+3600000)/900000)*900000,to=plan?.to||from+86400000;
      this.a.openModal('Abwesenheit ändern',`<label class="form-field">Abfahrt<input id="presenceFrom" type="datetime-local" value="${local(from)}" required></label><label class="form-field">Rückkehr<input id="presenceTo" type="datetime-local" value="${local(to)}" required></label><p class="muted">Beide Zeiten lassen sich unabhängig ändern. Die eingetragene Rückkehr bleibt bestehen, wenn du nur die Abfahrt anpasst. Zeitangaben gelten in der Zeitzone dieses Handys.</p><div class="form-actions"><button id="presenceBack" class="quiet">Zurück</button><button id="presenceSave" class="primary">Speichern</button></div><p id="presenceError" class="form-error" hidden role="alert"></p>`);
      $('presenceBack').onclick=()=>this.open();$('presenceSave').onclick=async()=>{try{const f=Date.parse($('presenceFrom').value),t=Date.parse($('presenceTo').value);if(!Number.isFinite(f)||!Number.isFinite(t)||t<=f)throw Error('Bitte Abfahrt und eine spätere Rückkehr eingeben.');await this.command('saveAbsence',JSON.stringify({id:plan?.id||'',from:f,to:t}));this.open();}catch(e){this.error(e);}};
    }
    ask(plan){
      this.a.openModal('Du bist offenbar noch da',`<p>Deine Abfahrt war für <strong>${esc(stamp(plan.from))}</strong> geplant. Möchtest du die geplante Abwesenheit ändern oder absagen?</p><p class="muted">Geplante Rückkehr: ${esc(stamp(plan.to))}. Abfahrt und Rückkehr bleiben unabhängig bearbeitbar.</p><div class="presence-choices"><button class="primary" id="presenceChange">Ändern</button><button class="secondary" id="presenceCancel">Absagen</button><button class="quiet" id="presenceSoon">Fahre gleich</button></div><p id="presenceError" class="form-error" hidden role="alert"></p>`);
      $('presenceChange').onclick=()=>this.edit(plan);
      for(const [id,action] of [['presenceCancel','cancel'],['presenceSoon','soon']])$(id).onclick=async()=>{try{await this.command('resolveAbsence',plan.id,action);this.a.closeModal();}catch(e){this.error(e);}};
    }
  };
})();
