(function(root,factory){
  const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.WattReadBridge=api;
})(typeof globalThis==='object'?globalThis:this,function(){
  'use strict';
  const methods=new Set(['history','dashboardHistory','energyHistory','energyHistoryWindow','dailyEnergy']);
  const missing=()=>({points:[],summary:{},pending:true});
  const pending=method=>method==='dailyEnergy'?{days:[],pending:true}:method.startsWith('energy')?{solar:missing(),consumption:missing(),balance:missing(),pending:true}:missing();
  return class {
    constructor(raw,ready){
      this.raw=raw;this.ready=ready;this.cache=new Map();this.queue=new Map();this.nextId=0;this.generation=0;this.busy=false;
      this.bridge=new Proxy(raw,{get:(target,name)=>{const value=target[name];if(typeof value!=='function')return value;return methods.has(name)?(...args)=>this.read(name,args):(...args)=>target[name](...args);}});
    }
    source(){
      let source=this.raw.isLanViewer?.()?'viewer':'local';
      if(source==='viewer'&&this.raw.lanState)try{const lan=JSON.parse(this.raw.lanState());source+=':'+(lan.id||lan.name||'');}catch(_){}
      if(this.owner!==undefined&&source!==this.owner)this.invalidate(true);
      this.owner=source;
    }
    state(state){
      this.source();const changed=this.busy!==!!state.busy||this.runId!==state.runId;
      if(changed){this.invalidate();this.runId=state.runId;}
      this.busy=!!state.busy;
    }
    key(method,args,scope){return JSON.stringify([scope??null,method,args.map((v,i)=>i<2?Math.floor(v/10000):v)]);}
    remember(key,value){
      if(value.scope!=null)for(const [oldKey,old] of this.cache)if(old.method===value.method&&old.scope===value.scope)this.cache.delete(oldKey);
      this.cache.delete(key);if(this.cache.size>=128)this.cache.delete(this.cache.keys().next().value);this.cache.set(key,value);
    }
    // A new revision invalidates freshness, not the last successfully displayed values.
    invalidate(drop=false){this.generation++;if(drop)this.cache.clear();this.queue.clear();}
    previous(method,args,scope){
      return [...this.cache.values()].reverse().find(x=>{
        if(!x.data||x.method!==method||x.scope!==scope)return false;
        const semantic=method==='dailyEnergy'?[]:method.startsWith('energy')?args.slice(2,3):args.slice(2);
        if(!semantic.every((v,i)=>v===x.args[i+2]))return false;
        // A caller-supplied scope describes the same tile/period as its live window moves.
        // Unscoped callers retain only a nearby window, with all query parameters intact.
        return scope!=null||x.args.slice(2).every((v,i)=>method==='dailyEnergy'||v===args[i+2])&&Math.abs(x.args[0]-args[0])<30000&&Math.abs(x.args[1]-args[1])<30000;
      });
    }
    value(entry,method,refreshing=false){
      if(!entry?.data)return JSON.stringify(entry?.error?{error:entry.error}:pending(method));
      if(!refreshing&&!entry.error&&entry.generation===this.generation)return JSON.stringify(entry.data);
      const data=JSON.parse(JSON.stringify(entry.data)),mark=part=>{
        part.pending=refreshing;part.stale=true;part.dataAt=entry.at;
        if(entry.error)part.refreshError=entry.error;else delete part.refreshError;
      };
      mark(data);if(method.startsWith('energy'))for(const key of ['solar','consumption','balance'])if(data[key])mark(data[key]);
      return JSON.stringify(data);
    }
    accept(query,data){
      const error=data?.error||(!data||typeof data!=='object'?'Zeitraum konnte nicht geladen werden.':'');
      const old=error?this.previous(query.method,query.args,query.scope):null;
      this.remember(query.key,{...query,data:error?old?.data:data,at:error?old?.at:Date.now(),error,generation:this.generation,retryAt:error?Date.now()+5000:0});
    }
    read(method,args,scope){
      this.source();const key=this.key(method,args,scope),query={key,method,args,scope,generation:this.generation};
      if((!this.busy&&method!=='dailyEnergy'&&!this.raw.isLanViewer?.())||!this.raw.requestHistory){
        let data;try{data=JSON.parse(this.raw[method](...args));}catch(_){data={error:'Zeitraum konnte nicht geladen werden.'};}
        if(data.syncing){try{this.state(JSON.parse(this.raw.syncState()));}catch(_){}return this.busy?this.read(method,args,scope):this.value(this.previous(method,args,scope),method,true);}
        this.accept(query,data);return this.value(this.cache.get(key),method);
      }
      const cached=this.cache.get(key);
      if(cached?.generation===this.generation&&(!cached.error||cached.retryAt>Date.now()))return this.value(cached,method);
      if(!this.queue.has(key)&&!(this.inflight?.key===key&&this.inflight?.generation===this.generation)){
        if(scope!=null)for(const [oldKey,old] of this.queue)if(old.method===method&&old.scope===scope)this.queue.delete(oldKey);
        if(this.queue.size>=24)this.queue.delete(this.queue.keys().next().value);
        this.queue.set(key,query);this.pump();
      }
      return this.value(this.previous(method,args,scope),method,true);
    }
    pump(){
      if(this.inflight||!this.queue.size)return;
      const [key,query]=this.queue.entries().next().value;this.queue.delete(key);
      const request={...query,id:++this.nextId};this.inflight=request;
      this.timer=setTimeout(()=>this.result({id:request.id,data:{error:'Der Abruf dauert zu lange. Die letzten Werte bleiben erhalten.'}}),20000);this.timer?.unref?.();
      try{this.raw.requestHistory(JSON.stringify(request));}
      catch(_){this.result({id:request.id,data:{error:'Zeitraum konnte nicht geladen werden.'}});}
    }
    result(response){
      const query=this.inflight;if(!query||query.id!==response.id)return;
      clearTimeout(this.timer);this.inflight=null;
      if(query.generation===this.generation){this.accept(query,response.data||{error:response.error||'Zeitraum konnte nicht geladen werden.'});this.ready(query.method);}
      this.pump();
    }
  };
});
