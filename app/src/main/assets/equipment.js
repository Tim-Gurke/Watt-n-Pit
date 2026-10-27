(function(root){
  'use strict';
  const clean=(s,max)=>String(s??'').trim().slice(0,max);
  function normalize(input){
    if(input?.version!==1||!Array.isArray(input.groups)||input.groups.length>32)throw new Error('Bitte höchstens 32 Solargruppen anlegen.');
    const ids=new Set();
    return {version:1,groups:input.groups.map(g=>{
      const id=String(g.id||''),label=clean(g.label,60),controller=clean(g.controller,80),count=Number(g.count),watts=Number(g.watts);
      if(!/^[a-zA-Z0-9_-]{1,48}$/.test(id)||ids.has(id))throw new Error('Ungültige Solargruppe.');ids.add(id);
      if(!label)throw new Error('Bitte jede Solargruppe benennen.');
      if(!Number.isInteger(count)||count<1||count>99||!Number.isFinite(watts)||watts<1||watts>5000)throw new Error('Bitte 1–99 Module mit jeweils 1–5.000 Wp angeben.');
      if(!['fixed','mobile','other'].includes(g.placement)||!['solar','shunt','none'].includes(g.measurement))throw new Error('Bitte Montage und Messwertquelle wählen.');
      const tilt=g.tilt==null||g.tilt===''?null:Number(g.tilt);
      if(tilt!==null&&(!Number.isFinite(tilt)||tilt<0||tilt>90))throw new Error('Der Montagewinkel muss zwischen 0° und 90° liegen.');
      return {id,label,controller,count,watts,placement:g.placement,measurement:g.measurement,tilt,defaultActive:g.placement==='mobile'?false:g.defaultActive!==false&&g.defaultActive!=='false'};
    })};
  }
  function totals(input){return (input?.groups||[]).reduce((t,g)=>({watts:t.watts+g.count*g.watts,modules:t.modules+g.count}),{watts:0,modules:0});}
  const core={normalize,totals};root.WattEquipmentCore=core;if(typeof module==='object'&&module.exports)module.exports=core;
  if(typeof window==='undefined')return;
  const $=id=>document.getElementById(id),esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const num=n=>Number(n).toLocaleString('de-DE',{maximumFractionDigits:1});
  root.WattEquipment=class {
    constructor(a){this.a=a;this.key='';$('editEquipment').onclick=()=>this.open();}
    config(){const saved=this.a.getSnapshot().equipment;
      if(saved)return saved;
      if(!this.a.native){try{return normalize(JSON.parse(localStorage.getItem('wattpit-equipment')||'{"version":1,"groups":[]}'));}catch(_){}}
      return {version:1,groups:[]};
    }
    source(measurement){return measurement==='solar'?'Solarwert des Bluetooth-Reglers':measurement==='shunt'?'In der Batteriebilanz':'Ohne Messwert';}
    render(){
      const config=this.config(),key=JSON.stringify(config);if(key===this.key)return;this.key=key;
      const t=totals(config);$('equipmentTotal').textContent=config.groups.length?`${num(t.watts)} Wp · ${num(t.modules)} ${t.modules===1?'Modul':'Module'}`:'Noch keine Solaranlage hinterlegt.';
      $('equipmentGroups').innerHTML=config.groups.map(g=>`<div class="equipment-summary"><div><strong>${esc(g.label)}</strong><p>${num(g.count)} × ${num(g.watts)} Wp · ${{fixed:'Fest installiert',mobile:'Mobil',other:'Sonstige Montage'}[g.placement]}</p><p>${esc(g.controller||'Regler nicht benannt')} · ${this.source(g.measurement)}</p></div><span>${num(g.count*g.watts)} Wp</span></div>`).join('');
      const indirect=config.groups.filter(g=>g.measurement==='shunt'),unmeasured=config.groups.filter(g=>g.measurement==='none');
      $('equipmentNote').textContent=indirect.length?`${indirect.map(g=>g.label).join(', ')}: In der Batteriebilanz enthalten, wenn der Ladekreis über den Shunt läuft. Die einzelne Solarleistung ist ohne eigenen Messwert nicht sichtbar.`:unmeasured.length?`${unmeasured.map(g=>g.label).join(', ')}: kein eigener Messwert zugeordnet. Die Nennleistung deiner Anlage ersetzt keine Messung.`:'Die Solarleistung kommt vom verbundenen Solarladeregler. Module und weitere Regler kannst du unter Einstellungen → Deine Solaranlage anpassen.';
    }
    blank(){return {id:'group-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,7),label:'Solarmodule',count:1,watts:100,placement:'fixed',controller:'',measurement:'solar',tilt:null,defaultActive:true};}
    open(){
      this.draft=JSON.parse(JSON.stringify(this.config()));if(!this.draft.groups.length)this.draft.groups.push(this.blank());
      this.a.openModal('Solaranlage anpassen','<p class="muted">Lege pro Regler oder Modulgruppe einen Solarkreis an. Im Anlagenverbund werden die Angaben am Hauptgerät gespeichert.</p><div id="solarGroupRows"></div><button id="addSolarGroup" class="quiet">+ Solarkreis</button><p id="solarEquipmentError" class="form-error" role="alert" hidden></p><div class="form-actions"><button id="cancelSolarEquipment" class="quiet">Abbrechen</button><button id="saveSolarEquipment" class="primary">Anlage speichern</button></div>');
      this.rows();$('addSolarGroup').onclick=()=>{this.gather();if(this.draft.groups.length>=32){this.a.toast('Bis zu 32 Solarkreise sind möglich.');return;}this.draft.groups.push(this.blank());this.rows();};
      $('cancelSolarEquipment').onclick=()=>this.a.closeModal();$('saveSolarEquipment').onclick=()=>this.save();
      $('solarGroupRows').addEventListener('click',event=>{const b=event.target.closest('.remove-solar-group');if(!b)return;this.gather();this.draft.groups.splice(+b.dataset.index,1);this.rows();});
    }
    rows(){
      const select=(name,options,value)=>`<select data-field="${name}">${options.map(([v,l])=>`<option value="${v}" ${v===value?'selected':''}>${esc(l)}</option>`).join('')}</select>`;
      $('solarGroupRows').innerHTML=this.draft.groups.map((g,i)=>`<fieldset class="solar-group" data-group="${esc(g.id)}"><legend>Solarkreis ${i+1}</legend><label class="form-field">Bezeichnung<input data-field="label" maxlength="60" value="${esc(g.label)}"></label><div class="solar-module-row"><label class="form-field">Modulanzahl<input data-field="count" type="number" min="1" max="99" step="1" value="${esc(g.count)}"></label><label class="form-field">Wp pro Modul<input data-field="watts" type="number" min="1" max="5000" step="0.1" value="${esc(g.watts)}"></label></div><label class="form-field">Montage${select('placement',[['fixed','Fest installiert'],['mobile','Mobil / aufstellbar'],['other','Sonstige Montage']],g.placement)}</label><div class="solar-module-row"><label class="form-field">Montagewinkel<input data-field="tilt" type="number" min="0" max="90" step="1" placeholder="Noch unbekannt" value="${esc(g.tilt??'')}"><small>0° waagerecht · 90° senkrecht</small></label><label class="form-field">Standardmäßig berücksichtigen${select('defaultActive',[['true','Ja'],['false','Nein']],String(g.defaultActive!==false))}<small>Mobile Kreise werden vor Ort zugeschaltet.</small></label></div><label class="form-field">Regler / Ladekreis<input data-field="controller" maxlength="80" placeholder="z. B. MPPT oder PWM" value="${esc(g.controller)}"></label><label class="form-field">Messwertquelle${select('measurement',[['solar','Bluetooth-Solarladeregler'],['shunt','Batteriebilanz des Shunts'],['none','Ohne Messwert']],g.measurement)}</label><button class="textbutton remove-solar-group" data-index="${i}">Solarkreis entfernen</button></fieldset>`).join('');
    }
    gather(){this.draft.groups=[...$('solarGroupRows').querySelectorAll('[data-group]')].map(el=>{const g={id:el.dataset.group};el.querySelectorAll('[data-field]').forEach(input=>g[input.dataset.field]=input.type==='checkbox'?input.checked:input.value);return g;});}
    async save(){
      try{this.gather();const config=normalize(this.draft);const error=this.a.native?await this.a.native.saveEquipment(JSON.stringify(config)):'';if(error)throw new Error(error);
        if(!this.a.native)localStorage.setItem('wattpit-equipment',JSON.stringify(config));this.a.getSnapshot().equipment=config;this.a.closeModal();this.key='';this.render();this.a.repaint();this.a.toast('Solaranlage gespeichert.');
      }catch(error){$('solarEquipmentError').hidden=false;$('solarEquipmentError').textContent=error.message;}
    }
  };
})(typeof globalThis==='object'?globalThis:this);
