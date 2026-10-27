(function(){
  'use strict';
  const $=id=>document.getElementById(id),esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const stamp=t=>t?new Date(t).toLocaleString('de-DE',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'}):'noch kein Empfang';
  window.WattLan=class {
    constructor(a){
      this.a=a;this.state={role:'off'};
      document.addEventListener('watt-modal-close',()=>document.body.classList.remove('showing-pairing-qr'));
      $('lanManage').onclick=$('lanOpen').onclick=()=>this.open();
      window.onLanResult=result=>{this.finishJoin();if(result.error)this.message(result.error);else{try{sessionStorage.setItem('wattpit-lan-joined','1');}catch(_){}location.reload();}};
      requestAnimationFrame(()=>{try{const joined=this.a.native?.consumeLanJoined?.()||sessionStorage.getItem('wattpit-lan-joined');sessionStorage.removeItem('wattpit-lan-joined');if(joined){this.render();if(this.state.role==='viewer')this.confirmJoined();}}catch(_){}});
      window.onLanScanned=result=>{
        if(result.cancelled)return;
        if(!$('lanInvitation'))this.open();
        if(result.error){this.message(result.error);return;}
        if(!$('lanInvitation')){this.a.toast('Bitte den bestehenden Verbund zuerst verlassen.');return;}
        $('lanInvitation').value=result.code||'';this.join();
      };
    }
    render(){
      document.body.classList.toggle('showing-pairing-qr',!!$('lanQr')&&!$('lanQr').hidden&&!$('modal').hidden);
      try{this.state=JSON.parse(this.a.native?.lanState?.()||'{"role":"off"}');}catch(_){this.state={role:'off'};}
      const s=this.state,viewer=s.role==='viewer',host=s.role==='host',active=viewer||host;
      $('lanBanner').hidden=false;
      $('lanOpen').textContent=active?'Verbund öffnen':'Geräte verknüpfen';
      $('lanTitle').textContent=active?s.name:'Anlagenverbund';
      const status=viewer?(s.connected?'Anzeige · vom Hauptgerät':`Hauptgerät nicht erreichbar · Stand ${stamp(s.receivedAt)}`):host?(s.hosting?`Hauptgerät · ${s.peers?.length||0} Anzeige${s.peers?.length===1?'':'n'} verbunden`:s.error||'Hauptgerät wird gestartet …'):'Messwerte direkt auf diesem Gerät';
      $('lanStatus').textContent=status;$('lanSummary').textContent=active?`${s.name} · ${status}`:'Ein Hauptgerät zeichnet auf. Weitere Handys zeigen denselben Datenstand im WLAN.';
      $('lanBanner').classList.toggle('lan-offline',viewer&&!s.connected);
      $('lanBanner').closest('.cockpit-tile')?.classList.toggle('lan-offline',viewer&&!s.connected);
      const ids=['settingsImport','historyImport','importCsv','settingsExport','exportCsv','exportPlanningDiagnostics'];
      for(const id of ids){const el=$(id);if(el){el.disabled=viewer;el.title=viewer?'Verlaufsdateien am Hauptgerät verwalten':'';}}
      if($('lanHostStatus'))$('lanHostStatus').textContent=status;
    }
    confirmJoined(){
      const s=this.state;
      this.a.openModal('Verbindung hergestellt',`<div class="lan-success" role="status"><span aria-hidden="true">✓</span><h3>${esc(s.name)}</h3><p>Dieses Handy ist jetzt als Anzeige verbunden.</p><p class="muted">Datenstand vom Hauptgerät empfangen: ${esc(stamp(s.receivedAt))}. Die Aufzeichnung läuft am Hauptgerät. Du kannst die gemeinsamen Einstellungen und die Aufzeichnung auch von diesem Handy aus steuern. Änderungen bestätigt das Hauptgerät.</p><button id="lanJoinedDone" class="primary">Zur Übersicht</button></div>`);
      $('lanJoinedDone').onclick=()=>{this.a.closeModal();document.querySelector('[data-page="overview"]').click();$('lanBanner').closest('.cockpit-tile')?.scrollIntoView({block:'center'});};
    }
    message(text){const el=$('lanError');if(el){el.hidden=false;el.textContent=text;}else this.a.toast(text);}
    finishJoin(){if($('lanJoin')){$('lanJoin').disabled=false;$('lanJoin').textContent='Als Anzeige beitreten';}}
    clearError(){if($('lanError')){$('lanError').hidden=true;$('lanError').textContent='';}}
    join(){
      this.clearError();
      if(!this.a.native?.joinLan){this.message('Der Anlagenverbund ist in der Android-App verfügbar.');return;}
      const code=$('lanInvitation')?.value.trim();if(!code){this.message('Bitte einen Verbindungscode einfügen oder den QR-Code scannen.');return;}
      $('lanJoin').disabled=true;$('lanJoin').textContent='Verbindung wird geprüft …';
      try{this.a.native.joinLan(code);}catch(e){this.finishJoin();this.message('Verbindungscode konnte nicht gelesen werden. Bitte das gesamte Codefeld ersetzen oder den QR-Code scannen.');}
    }
    open(){
      document.body.classList.remove('showing-pairing-qr');
      this.render();const s=this.state;
      if(s.role==='host'){
        this.a.openModal('Anlagenverbund',`<h3>${esc(s.name)}</h3><p role="status">Dieses Handy ist als Hauptgerät eingerichtet.</p><p id="lanHostStatus" class="muted"></p><p>Dieses Hauptgerät liefert Messwerte, Verläufe, Wetter, Lerndaten und Rollerwerte. Weitere Handys brauchen denselben WLAN-Zugang. Auch das Hotspot-Handy kann Hauptgerät oder Anzeige sein.</p><p>Am anderen Handy <strong>Einstellungen → Anlagenverbund → Beitreten</strong> öffnen und den QR-Code scannen oder den vollständigen Textcode einfügen.</p><div class="row"><button class="secondary" id="lanCopy">Verbindungscode kopieren</button><button class="quiet" id="lanShowCode">Code anzeigen</button></div><textarea class="lan-code" id="lanCode" readonly rows="5" aria-label="Verbindungscode" hidden></textarea><p class="muted">Der Code erlaubt Zugriff und Bedienung deiner Anlage. Nur auf die eigenen Geräte übertragen. Beim Verlassen wird der Zugang ungültig.</p><p id="lanError" class="form-error" role="alert" hidden></p><button class="quiet" id="lanLeave">Verbund beenden</button>`);
        const qrButton=document.createElement('button');qrButton.id='lanShowQr';qrButton.className='primary';qrButton.textContent='QR-Code anzeigen';$('lanCopy').parentElement.prepend(qrButton);
        const qr=document.createElement('div');qr.id='lanQr';qr.className='lan-qr';qr.hidden=true;qr.innerHTML='<img id="lanQrImage" alt="Verbindungscode zum Scannen" width="640" height="640"><p class="muted">Am anderen Handy „QR-Code scannen“ wählen.</p>';$('lanCopy').parentElement.after(qr);
        qrButton.onclick=()=>{this.clearError();try{const result=JSON.parse(this.a.native?.lanQrCode?.()||'{}');if(result.error)throw Error(result.error);if(!/^data:image\/png;base64,/.test(result.image||''))throw Error('QR-Code ist in der Android-App ab 1.6.1 verfügbar.');$('lanQrImage').src=result.image;qr.hidden=false;document.body.classList.add('showing-pairing-qr');qrButton.textContent='QR-Code aktualisieren';qr.scrollIntoView({block:'nearest'});}catch(e){this.message(e.message);}};
        $('lanCopy').onclick=()=>{const error=this.a.native?.copyLanCode?.();if(error)this.message(error);else this.a.toast('Verbindungscode kopiert.');};
        $('lanShowCode').onclick=()=>{try{const result=JSON.parse(this.a.native.lanPairingCode());if(result.error)throw Error(result.error);$('lanCode').value=result.code;$('lanCode').hidden=false;}catch(e){this.message(e.message);}};
        this.leaveButton(true);this.render();return;
      }
      if(s.role==='viewer'){
        this.a.openModal('Anlagenverbund',`<h3>${esc(s.name)}</h3><p id="lanHostStatus" class="muted"></p><p>Diese Anzeige übernimmt die Daten des Hauptgeräts. Kacheln, Anordnung und Farben bleiben auf jedem Handy frei wählbar. Solaranlage, Wetter, Zusatzspeicher, Geräte und Aufzeichnung kannst du von hier aus einstellen. Änderungen werden am Hauptgerät gespeichert und bestätigt.</p><p class="muted">Ohne Verbindung bleibt der letzte empfangene Stand erkennbar. Neue Verläufe benötigen das erreichbare Hauptgerät. Die bisherigen eigenen Messdaten bleiben erhalten.</p><button class="secondary" id="lanRefresh">Jetzt aktualisieren</button><p class="muted">Nach einem WLAN-Wechsel wird das Hauptgerät automatisch gesucht. Falls es nicht gefunden wird: Verbund hier verlassen und einen frischen Code vom Hauptgerät übernehmen.</p><p id="lanError" class="form-error" role="alert" hidden></p><button class="quiet" id="lanLeave">Verbund verlassen</button>`);
        $('lanRefresh').onclick=()=>{this.a.native?.refreshLan?.();this.a.toast('Hauptgerät wird gesucht.');};this.leaveButton(false);this.render();return;
      }
      this.a.openModal('Anlagenverbund',`<p>Ein gemeinsamer Datenstand für deine Handys. Ein Gerät bleibt bei der Anlage und zeichnet auf; weitere Geräte lesen seine Daten im selben WLAN. Der Hotspot muss dafür kein Internet haben.</p><label class="form-field">Name deiner Anlage<input id="lanName" maxlength="60" value="Mein Wohnwagen"></label><button class="secondary" id="lanCreate">Als Hauptgerät erstellen</button><h3>Bestehendem Verbund beitreten</h3><label class="form-field">Verbindungscode vom Hauptgerät<textarea id="lanInvitation" rows="4" maxlength="3000" placeholder="WNP1.…" spellcheck="false" autocapitalize="off" autocomplete="off"></textarea></label><button class="primary" id="lanJoin">Als Anzeige beitreten</button><p class="muted">Der Austausch ist verschlüsselt und bleibt im lokalen Netz. Kein Konto und keine Cloud. Ein Verbund ist unabhängig von den Solarkreisen deiner Anlage.</p><p id="lanError" class="form-error" role="alert" hidden></p>`);
      $('lanCreate').onclick=()=>{if(!this.a.native?.createLan){this.message('Der Anlagenverbund ist in der Android-App verfügbar.');return;}const error=this.a.native.createLan($('lanName').value);if(error){this.message(error);return;}this.a.closeModal();setTimeout(()=>this.open(),400);};
      const scan=document.createElement('button');scan.id='lanScan';scan.className='secondary';scan.textContent='QR-Code scannen';$('lanInvitation').closest('label').before(scan);
      scan.onclick=()=>{this.clearError();if(this.a.native?.scanLanCode)this.a.native.scanLanCode();else this.message('Der QR-Scanner ist in der Android-App ab 1.6.1 verfügbar.');};
      $('lanInvitation').setAttribute('autocorrect','off');$('lanInvitation').maxLength=4096;
      $('lanJoin').onclick=()=>this.join();
    }
    leaveButton(host){$('lanLeave').onclick=()=>{
      this.a.openModal('Anlagenverbund verlassen',`<p>${host?'Der Zugang für alle verbundenen Anzeigen wird beendet.':'Dieses Handy verwendet anschließend wieder seine eigenen Geräte und Daten.'} Messdaten und Anordnungen bleiben erhalten.</p><div class="row"><button class="quiet" id="lanKeep">Zurück</button><button class="secondary" id="lanConfirmLeave">${host?'Verbund beenden':'Verbund verlassen'}</button></div><p id="lanError" class="form-error" role="alert" hidden></p>`);
      $('lanKeep').onclick=()=>this.open();$('lanConfirmLeave').onclick=()=>{const error=this.a.native?.leaveLan?.();if(error)this.message(error);else location.reload();};
    };}
  };
})();
