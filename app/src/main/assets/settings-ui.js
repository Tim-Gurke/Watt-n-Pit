(function(){
 'use strict';
 const $=id=>document.getElementById(id),esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 window.WattSettingsBackup=class {
  constructor(a){
   this.a=a;
   $('exportConfiguration').onclick=()=>this.export();
   $('importConfiguration').onclick=()=>{if(a.native?.chooseSettingsImport)a.native.chooseSettingsImport();else a.toast('Einstellungen lassen sich in der Android-App aus einer Datei laden.');};
   window.onSettingsImportPreview=result=>this.preview(result);
  }
  export(){
   try{
    if(!this.a.native?.chooseSettingsExport){this.a.toast('Die Dateisicherung ist in der Android-App verfügbar.');return;}
    const backup=window.WattSettingsCore.create(this.a.getSnapshot(),this.a.dashboard.config,this.a.equipment.config()),error=this.a.native.chooseSettingsExport(JSON.stringify(backup));
    if(error)throw new Error(error);
   }catch(error){this.a.toast(error.message);}
  }
  preview(result){
   try{
    const backup=window.WattSettingsCore.normalize(result.backup),s=backup.settings;
    this.a.openModal('Einstellungen übernehmen?',`<p class="muted">${esc(result.name||'Einstellungssicherung')} · ${backup.dashboard.tiles.length} Kacheln · ${backup.equipment.groups.length} Solarkreise</p><p class="muted">${s.theme==='light'?'Helles':'Dunkles'} Design · ${esc(backup.weatherPlace.name||'Wetter ausgeschaltet')}</p><p class="muted">Die gesicherten Kacheln, Farben, Zeiträume und Anlageneinstellungen ersetzen deine jetzigen Einstellungen. Messdaten und eingerichtete Geräte bleiben erhalten. Mobile Kreise sind nach dem Übernehmen ausgeschaltet; ihre Aufstellung meldest du auf diesem Handy.</p><p id="configurationError" class="form-error" role="alert" hidden></p><div class="form-actions"><button id="cancelConfigurationImport" class="quiet">Abbrechen</button><button id="applyConfigurationImport" class="primary">Einstellungen übernehmen</button></div>`);
    $('cancelConfigurationImport').onclick=()=>this.a.closeModal();
    $('applyConfigurationImport').onclick=async()=>{
     try{
      const error=await this.a.native?.importSettings(JSON.stringify(backup));if(error)throw new Error(error);
      this.a.applied(backup);this.a.closeModal();this.a.toast('Einstellungen übernommen.');
     }catch(error){$('configurationError').hidden=false;$('configurationError').textContent=error.message;}
    };
   }catch(error){this.a.toast(error.message);}
  }
 };
})();
