(function(root){
  'use strict';
  const actions=new Set(['savePresence','saveAbsence','resolveAbsence','saveSolarSetup','saveSolarSite','weatherPlace','refreshWeather','saveEquipment','saveRoller','rollerReconnect','saveDevice','removeDevice','importSettings','start','stop','importArchive','importMissingArchive','cancelArchive','backgroundArchive']);
  root.WattControlBridge=class{
    constructor(native,notify,changed,revision){
      this.revision=revision;this.native=native;this.notify=notify;this.changed=changed;this.next=0;this.pending=null;this.formRevision=null;
      this.bridge=new Proxy(native,{get:(target,key)=>{
        const fn=target[key];if(typeof fn!=='function')return fn;
        if(actions.has(key)||key==='settings')return (...args)=>{
          const shared=key!=='settings'||['capacity','sampleSeconds'].some(k=>Object.hasOwn(JSON.parse(args[0]),k));
          return target.isLanViewer?.()&&shared?this.send(key,args):fn.apply(target,args);
        };
        return fn.bind(target);
      }});
    }
    capture(){this.formRevision=Number(this.revision?.()??this.native.controlRevision?.());}
    send(action,args,explicitRevision){
      if(this.pending)return Promise.resolve('Bitte die Bestätigung der laufenden Änderung abwarten.');
      if(!this.native.requestPlantCommand)return Promise.resolve('Bitte beide Handys auf Version 1.6.3 oder neuer aktualisieren.');
      const revision=explicitRevision??(typeof document!=='undefined'&&!document.getElementById('modal')?.hidden&&this.formRevision!==null?this.formRevision:Number(this.revision?.()??this.native.controlRevision()));
      const id=++this.next;
      return new Promise(resolve=>{
        this.pending={id,resolve,timer:setTimeout(()=>this.result({id,error:'Keine Bestätigung vom Hauptgerät. Bitte Verbindung und aktuellen Stand prüfen.'}),30000)};
        this.status('Bestätigung vom Hauptgerät wird erwartet …',true);
        try{this.native.requestPlantCommand(JSON.stringify({id,action,args,revision}));}catch(_){this.result({id,error:'Die Änderung konnte nicht gesendet werden.'});}
      });
    }
    result(result){
      const pending=this.pending;if(!pending||pending.id!==result.id)return;
      clearTimeout(pending.timer);this.pending=null;
      const error=result.error||(!result.ok?'Keine Bestätigung vom Hauptgerät.':'');
      this.status(error||'Vom Hauptgerät bestätigt.',false);this.changed?.();this.native.refreshLan?.();pending.resolve(error);
    }
    status(text,busy){
      const el=typeof document!=='undefined'?document.getElementById('plantActionStatus'):null;
      if(el){el.hidden=false;el.textContent=text;el.setAttribute('aria-busy',String(busy));}
      if(typeof document!=='undefined'){
        document.body.classList.toggle('plant-command-busy',busy);
        if(busy){this.inert=[...document.querySelectorAll('main,.topbar,.bottom-nav,#modal')].map(el=>[el,el.inert]);this.inert.forEach(([el])=>el.inert=true);}
        else{this.inert?.forEach(([el,prior])=>el.inert=prior);this.inert=null;}
      }
      clearTimeout(this.statusTimer);if(el&&!busy)this.statusTimer=setTimeout(()=>el.hidden=true,10000);
      if(!el)this.notify?.(text);
    }
  };
  if(typeof module==='object'&&module.exports)module.exports=root.WattControlBridge;
})(typeof globalThis==='object'?globalThis:this);
