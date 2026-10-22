'use strict';
/** Queue-scoped background choice and lightweight status; history reads remain asynchronous. */
window.WattLifecycle=class {
  constructor(a){
    this.a=a;this.blocked=false;this.mode='';this.wasBusy=false;this.busy=false;this.cancelled=false;
    this.root=document.getElementById('activityOverlay');
    this.dockObserver=new ResizeObserver(()=>{const dock=document.getElementById('syncCompact');if(!dock.hidden)document.documentElement.style.setProperty('--sync-dock-height',(dock.getBoundingClientRect().height+12)+'px');});this.dockObserver.observe(document.getElementById('syncCompact'));
    document.getElementById('cockpitUnderstood').onclick=()=>{
      const error=a.native?.acknowledgeCockpit?.();if(error){const note=document.getElementById('cockpitNoticeError');note.textContent=error;note.hidden=false;return;}
      this.accepted=true;this.release();a.poll();
    };
    document.getElementById('syncCancel').onclick=()=>this.confirmCancellation();
    document.getElementById('syncContinue').onclick=()=>{this.confirmCancel=false;this.paint(this.state);document.getElementById('syncBackground').focus();};
    document.getElementById('syncCancelConfirm').onclick=()=>{
      if(this.cancelled||!this.confirmCancel)return;this.cancelled=true;this.confirmCancel=false;this.a.native?.cancelArchive?.();this.paint(this.state);
    };
    document.getElementById('syncBackground').onclick=()=>{
      const error=a.native?.backgroundArchive?.(this.runId);
      if(error){document.getElementById('syncChoiceError').textContent=error;document.getElementById('syncChoiceError').hidden=false;return;}
      this.review=false;this.confirmCancel=false;this.poll();a.poll();
    };
    document.getElementById('syncOpen').onclick=()=>{this.review=true;this.lock('sync');this.paint(this.state);};
    this.root.addEventListener('keydown',e=>{
      if(e.key==='Escape'){e.preventDefault();e.stopPropagation();this.back();return;}
      if(e.key==='Tab'){
        e.stopPropagation();const buttons=[...this.root.querySelectorAll('section:not([hidden]) button')].filter(el=>!el.disabled&&!el.hidden&&el.getClientRects().length);
        const i=buttons.indexOf(document.activeElement),next=(i+(e.shiftKey?-1:1)+buttons.length)%buttons.length;buttons[next]?.focus();e.preventDefault();
      }
    });
  }
  lock(mode){
    if(!this.blocked){
      this.focus=document.activeElement;this.overflow=document.body.style.overflow;
      this.siblings=[...document.body.children].filter(el=>el!==this.root&&el.tagName!=='SCRIPT').map(el=>[el,el.inert]);
      this.siblings.forEach(([el])=>{el.inert=true;});document.body.style.overflow='hidden';
    }
    const changed=this.mode!==mode;this.blocked=true;this.mode=mode;this.root.hidden=false;
    document.getElementById('cockpitNotice').hidden=mode!=='notice';document.getElementById('syncWaiting').hidden=mode!=='sync';
    if(changed)this.root.querySelector('section:not([hidden]) button:not([hidden])')?.focus();
  }
  release(){
    if(!this.blocked)return;this.root.hidden=true;this.blocked=false;this.mode='';
    this.siblings.forEach(([el,inert])=>{el.inert=inert;});document.body.style.overflow=this.overflow;
    if(this.focus?.isConnected)this.focus.focus({preventScroll:true});
  }
  confirmCancellation(){
    if(!this.busy||this.cancelled)return;
    this.confirmCancel=true;this.review=true;this.lock('sync');this.paint(this.state);document.getElementById('syncContinue').focus();
  }
  back(){if(this.blocked&&this.confirmCancel){this.confirmCancel=false;this.paint(this.state);document.getElementById('syncCancel').focus();return true;}if(this.blocked&&this.review&&this.state?.background){this.review=false;this.release();this.a.poll();return true;}return this.blocked;}
  paint(state){
    if(!state)return;const archive=state.archive||{},active=archive.active,total=Number(archive.total)||0,done=Math.max(0,Math.min(total,Number(archive.done)||0));
    const device=active?archive.device||'Gerät wird verbunden':'Nächstes Gerät wird vorbereitet';
    document.getElementById('syncDevice').textContent=device;
    document.getElementById('syncPhase').textContent=this.cancelled?'Abbruch läuft. Bereits gespeicherte Werte bleiben erhalten.':active?archive.text||'Verbindung wird aufgebaut …':'Gerätedaten werden vorbereitet …';
    const progress=document.getElementById('syncWaitingProgress');
    if(active&&total>0){progress.max=total;progress.value=done;}else progress.removeAttribute('value');
    document.getElementById('syncProgressLabel').textContent=active&&total>0?`${done} von ${total} · aktuelles Gerät`:'';
    document.getElementById('syncCancel').disabled=this.cancelled;document.getElementById('syncCancel').textContent=this.cancelled?'Wird abgebrochen …':'Synchronisation abbrechen';
    document.getElementById('syncCancel').hidden=!!this.confirmCancel;document.getElementById('syncCancelChoice').hidden=!this.confirmCancel;
    document.getElementById('syncBackground').hidden=!this.a.native?.backgroundArchive||!!this.confirmCancel;
    document.getElementById('syncBackground').textContent=state.background?'Weiter im Hintergrund':'Im Hintergrund synchronisieren';
    document.getElementById('syncCompactTitle').textContent=this.cancelled?'Synchronisation wird abgebrochen':'Synchronisation läuft · Werte vorläufig';
    document.getElementById('syncCompactDetail').textContent=device+(active&&total>0?` · ${done}/${total}`:' · Bitte warten');
  }
  poll(){
    if(!this.a.native?.syncState)return false;
    let state;try{state=JSON.parse(this.a.native.syncState());}catch(_){return this.blocked;}
    this.state=state;this.a.stateChanged?.(state);
    if(state.theme)document.documentElement.dataset.theme=state.theme;if(state.palette)document.documentElement.dataset.palette=state.palette;
    if(state.cockpitNoticeAccepted===false&&!this.accepted){this.lock('notice');return true;}
    this.busy=!!state.busy;document.body.classList.toggle('sync-background',this.busy&&!!state.background);
    if(this.busy){
      if(!this.wasBusy||this.runId!==state.runId){this.cancelled=false;this.confirmCancel=false;this.review=false;document.getElementById('syncChoiceError').hidden=true;}
      this.runId=state.runId;this.startedAt=state.startedAt||this.startedAt||Date.now();this.wasBusy=true;this.paint(state);
      document.getElementById('syncCompact').hidden=!state.background;
      if(state.background&&!this.review){this.release();return false;}
      this.lock('sync');return true;
    }
    const finished=this.wasBusy;this.wasBusy=false;this.review=false;this.startedAt=0;document.getElementById('syncCompact').hidden=true;this.release();
    if(finished){this.a.completed();this.a.toast(state.archive?.text||'Synchronisation beendet.');}
    return false;
  }
};
