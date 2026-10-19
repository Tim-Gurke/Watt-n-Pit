(function(root,factory){
  const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.WattReadBridge=api;
})(typeof globalThis==='object'?globalThis:this,function(){
  'use strict';
  const methods=new Set(['history','dashboardHistory','energyHistory','energyHistoryWindow']);
  const missing=()=>({points:[],summary:{},pending:true});
  const pending=method=>method.startsWith('energy')?{solar:missing(),consumption:missing(),balance:missing(),pending:true}:missing();
  return class {
    constructor(raw,ready){
      this.raw=raw;this.ready=ready;this.cache=new Map();this.queue=new Map();this.nextId=0;this.generation=0;this.busy=false;
      this.bridge=new Proxy(raw,{get:(target,name)=>{const value=target[name];if(typeof value!=='function')return value;return methods.has(name)?(...args)=>this.read(name,args):(...args)=>target[name](...args);}});
    }
    state(state){
      const changed=this.busy!==!!state.busy||this.runId!==state.runId;
      if(this.busy&&(!state.busy||this.runId!==state.runId))this.cache.clear();
      if(changed){this.generation++;this.queue.clear();this.runId=state.runId;}
      this.busy=!!state.busy;
    }
    key(method,args){return JSON.stringify([method,args.map((v,i)=>i<2?Math.floor(v/10000):v)]);}
    remember(key,value){if(this.cache.size>=128&&!this.cache.has(key))this.cache.delete(this.cache.keys().next().value);this.cache.set(key,value);}
    read(method,args){
      if(!this.busy||!this.raw.requestHistory){const value=this.raw[method](...args);
        if(JSON.parse(value).syncing){try{this.state(JSON.parse(this.raw.syncState()));}catch(_){}return this.busy?this.read(method,args):JSON.stringify(pending(method));}
        this.remember(this.key(method,args),{method,args,value});return value;}
      const key=this.key(method,args),cached=this.cache.get(key);if(cached)return cached.value;
      if(!this.queue.has(key)&&!(this.inflight?.key===key&&this.inflight?.generation===this.generation)){
        if(this.queue.size>=24)this.queue.delete(this.queue.keys().next().value);
        this.queue.set(key,{key,method,args,generation:this.generation});this.pump();
      }
      // Keep a very recent response for the same window visible while refreshing it.
      const previous=[...this.cache.values()].reverse().find(x=>x.method===method&&x.args.slice(2).every((v,i)=>v===args[i+2])&&Math.abs(x.args[0]-args[0])<30000&&Math.abs(x.args[1]-args[1])<30000);
      if(previous)return previous.value;
      return JSON.stringify(pending(method));
    }
    pump(){
      if(this.inflight||!this.queue.size)return;
      const [key,query]=this.queue.entries().next().value;this.queue.delete(key);
      this.inflight={...query,id:++this.nextId};
      try{this.raw.requestHistory(JSON.stringify(this.inflight));}
      catch(_){this.result({id:this.inflight.id,data:{error:'Zeitraum konnte nicht geladen werden.'}});}
    }
    result(response){
      const query=this.inflight;if(!query||query.id!==response.id)return;
      this.inflight=null;
      if(query.generation===this.generation){this.remember(query.key,{method:query.method,args:query.args,value:JSON.stringify(response.data||{error:'Zeitraum konnte nicht geladen werden.'})});this.ready();}
      this.pump();
    }
  };
});
