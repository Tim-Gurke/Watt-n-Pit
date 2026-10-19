'use strict';
/** Lightweight native status only; never queries history while an archive is being imported. */
window.WattLifecycle=class {
  constructor(a){
    this.a=a;this.blocked=false;this.mode='';this.wasBusy=false;this.cancelled=false;
    this.root=document.getElementById('activityOverlay');
    document.getElementById('cockpitUnderstood').onclick=()=>{
      const error=a.native?.acknowledgeCockpit?.();if(error){const note=document.getElementById('cockpitNoticeError');note.textContent=error;note.hidden=false;return;}
      this.accepted=true;this.release();a.poll();
    };
    document.getElementById('syncCancel').onclick=()=>{
      if(this.cancelled)return;this.cancelled=true;
      document.getElementById('syncCancel').disabled=true;
      document.getElementById('syncCancel').textContent='Wird abgebrochen …';
      a.native?.cancelArchive?.();
    };
    this.root.addEventListener('keydown',e=>{
      if(e.key==='Escape'){e.preventDefault();e.stopPropagation();return;}
      if(e.key==='Tab'){e.stopPropagation();const button=this.root.querySelector('section:not([hidden]) button');button?.focus();e.preventDefault();}
    });
  }
  lock(mode){
    if(!this.blocked){
      this.focus=document.activeElement;this.overflow=document.body.style.overflow;
      this.siblings=[...document.body.children].filter(el=>el!==this.root&&el.tagName!=='SCRIPT').map(el=>[el,el.inert]);
      this.siblings.forEach(([el])=>{el.inert=true;});document.body.style.overflow='hidden';
    }
    const changed=this.mode!==mode;this.blocked=true;this.mode=mode;this.root.hidden=false;
    document.getElementById('cockpitNotice').hidden=mode!=='notice';
    document.getElementById('syncWaiting').hidden=mode!=='sync';
    if(changed)this.root.querySelector('section:not([hidden]) button')?.focus();
  }
  release(){
    if(!this.blocked)return;
    this.root.hidden=true;this.blocked=false;this.mode='';
    this.siblings.forEach(([el,inert])=>{el.inert=inert;});document.body.style.overflow=this.overflow;
    if(this.focus?.isConnected)this.focus.focus({preventScroll:true});
  }
  poll(){
    if(!this.a.native?.syncState)return false;
    let state;try{state=JSON.parse(this.a.native.syncState());}catch(_){return this.blocked;}
    if(state.theme)document.documentElement.dataset.theme=state.theme;
    if(state.palette)document.documentElement.dataset.palette=state.palette;
    if(state.cockpitNoticeAccepted===false&&!this.accepted){this.lock('notice');return true;}
    if(state.busy){
      if(!this.wasBusy){this.cancelled=false;const cancel=document.getElementById('syncCancel');cancel.disabled=false;cancel.textContent='Synchronisation abbrechen';}
      this.wasBusy=true;this.lock('sync');const archive=state.archive||{},active=archive.active;
      document.getElementById('syncDevice').textContent=active?archive.device||'Gerät wird verbunden':'Nächstes Gerät wird vorbereitet';
      document.getElementById('syncPhase').textContent=this.cancelled?'Abbruch läuft. Bereits gespeicherte Werte bleiben erhalten.':active?archive.text||'Verbindung wird aufgebaut …':'Gerätedaten werden vorbereitet …';
      const progress=document.getElementById('syncWaitingProgress');
      if(active&&archive.total>0){progress.max=archive.total;progress.value=Math.min(archive.total,Math.max(0,archive.done||0));}
      else progress.removeAttribute('value');
      document.getElementById('syncProgressLabel').textContent=active&&archive.total>0?`${archive.done||0} von ${archive.total} · aktuelles Gerät`:'';
      return true;
    }
    const finished=this.wasBusy;this.wasBusy=false;this.release();
    if(finished){this.a.completed();this.a.toast(state.archive?.text||'Synchronisation beendet.');}
    return false;
  }
};
