(function () {
  'use strict';
  const C=window.WattDashboardCore, $=id=>document.getElementById(id);
  const esc=s=>String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const copy=x=>JSON.parse(JSON.stringify(x));
  const option=(v,label)=>`<option value="${esc(v)}">${esc(label)}</option>`;
  const field=(label,content)=>`<label class="form-field">${label}${content}</label>`;
  const select=(id,items)=>`<select id="${id}">${items.map(x=>option(x[0],x[1])).join('')}</select>`;
  const input=(id,value,extra='')=>`<input id="${id}" value="${esc(value)}" ${extra}>`;
  const dateInput=t=>{const d=new Date(t);return new Date(t-d.getTimezoneOffset()*60000).toISOString().slice(0,16);};
  const stamp=t=>new Date(t).toLocaleString('de-DE',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'});
  const aggregateNames={avg:'Durchschnitt',min:'Minimum',max:'Maximum',last:'Letzter Wert'};
  // 620 px board plus its normal 22 px frame; display scaling does not select another saved layout.
  const screenProfile=()=>window.matchMedia('(min-width:642px)').matches?'wide':'compact';
  class WattDashboard {
    constructor(adapter) {
      this.a=adapter;this.board=$('dashboardBoard');this.weatherContent=$('weatherStrip');this.widgets={weather:{content:this.weatherContent,dock:$('weatherDock')},lan:{content:$('lanBanner'),dock:$('lanDock')}};this.editing=false;this.cache=new Map();this.elements=new Map();this.profile='';
      this.chargeEstimator=new C.BatteryTimeEstimator();this.chargeDemo=null;this.chargeEstimate=null;
      let stored;
      try { stored=JSON.parse(adapter.native?.dashboard?adapter.native.dashboard():localStorage.getItem('wattpit-dashboard-v1') || '{}'); } catch (_) { stored={}; }
      this.config=C.normalize(stored);this.saved=copy(this.config);this.history=$('miniHistory');
      const handle=$('miniHistoryResize');
      handle.onpointerdown=event=>this.historyPointerDown(event);
      handle.onpointermove=event=>this.historyPointerMove(event);
      handle.onpointerup=event=>this.historyPointerEnd(event,false);
      handle.onpointercancel=event=>this.historyPointerEnd(event,true);
      handle.onlostpointercapture=event=>this.historyPointerEnd(event,true);
      handle.onkeydown=event=>{
        if(!this.editing||!['ArrowUp','ArrowDown','Home','End'].includes(event.key))return;
        event.preventDefault();this.config.historyHeight[this.profile]=event.key==='Home'?300:event.key==='End'?1000:C.clamp(this.config.historyHeight[this.profile]+(event.key==='ArrowUp'?-20:20),300,1000);this.commit();handle.focus({preventScroll:true});
      };
      $('addTile').onclick=()=>this.picker();
      $('editDashboard').onclick=()=>{if(this.historyDrag)this.historyPointerEnd({pointerId:this.historyDrag.pointer},true);this.editing=!this.editing;this.render();};
      $('resetDashboard').onclick=()=>this.reset();
      this.observer=new ResizeObserver(()=>{if(!this.drag)this.layout();});this.observer.observe(this.board);
      this.render();
    }
    commit() {
      const scrollX=window.scrollX,scrollY=window.scrollY;const restore=()=>window.scrollTo(scrollX,scrollY);
      this.config=C.normalize(this.config);
      try {
        const json=JSON.stringify(this.config);
        if(this.a.native?.saveDashboard) {const error=this.a.native.saveDashboard(json);if(error)throw new Error(error);}
        else localStorage.setItem('wattpit-dashboard-v1',json);
        this.saved=copy(this.config);this.render();restore();requestAnimationFrame(restore);return true;
      } catch(error) {this.config=copy(this.saved);this.render();restore();this.a.toast(error.message || 'Die Übersicht konnte nicht gespeichert werden.');return false;}
    }
    clearCache() {this.cache.clear();this.update();}
    render() {
      this.profile=screenProfile();
      this.board.classList.toggle('is-editing',this.editing);
      this.history.classList.toggle('is-editing',this.editing);$('miniHistoryResize').hidden=!this.editing;$('miniHistorySize').hidden=!this.editing;
      $('editDashboard').textContent=this.editing?'Fertig':'Anordnen';
      $('editDashboard').setAttribute('aria-pressed',String(this.editing));
      $('layoutHelp').hidden=!this.editing;
      this.renderBlockOrder();
      $('layoutProfile').textContent=this.profile==='wide'?'Breite Ansicht':'Schmale Ansicht';
      for(const widget of Object.values(this.widgets))widget.dock.append(widget.content);
      this.board.innerHTML='';this.elements.clear();
      for (const t of this.config.tiles) {
        const m=C.CATALOG[t.metric],el=document.createElement('article');
        el.className='cockpit-tile type-'+t.type;el.dataset.tile=t.id;el.tabIndex=0;el.setAttribute('role','button');el.setAttribute('aria-label',(t.title || m.label)+' anpassen');
        el.innerHTML=`<div class="tile-head"><span class="tile-label">${esc(t.title || m.label)}</span><button class="tile-menu" aria-label="${esc(t.title || m.label)} bearbeiten">···</button></div><div class="tile-visual"></div><div class="tile-hint"></div><span class="tile-resize" aria-hidden="true"></span>`;
        if(m.widget)el.querySelector('.tile-visual').append(this.widgets[m.widget].content);
        if(t.metric==='weather'){this.weatherContent.dataset.title=t.title;this.weatherContent.querySelector('#weatherPlaceName').textContent=t.title||this.a.getSnapshot().settings.weatherPlace?.name||'Wetter';el.querySelector('.tile-visual').insertAdjacentHTML('beforeend','<span class="weather-disabled">Wetterort in den Einstellungen wählen.</span>');}
        el.style.setProperty('--tile-color',t.color);el.style.setProperty('--number-size',t.fontSize+'px');
        el.style.setProperty('--tile-bg',t.background==='auto'?`color-mix(in srgb,var(--panel) ${t.opacity}%,transparent)`:`${t.background}${Math.round(t.opacity*2.55).toString(16).padStart(2,'0')}`);
        el.querySelector('.tile-menu').onclick=e=>{e.stopPropagation();this.editor(t.id);};
        el.onclick=e=>{if(m.widget&&e.target.closest('.weather-days,button'))return;if(Date.now()-(this.lastDrag || 0)>300)this.editing?this.editor(t.id):this.choose(t.id);};
        el.onkeydown=e=>{
          if(e.target!==el)return;
          if(e.key==='Enter' || e.key===' '){e.preventDefault();this.editing?this.editor(t.id):this.choose(t.id);}
          if(this.editing && ['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.key)){
            e.preventDefault();const p=t.layouts[this.profile];p.x=C.clamp(p.x+(e.key==='ArrowLeft'?-.5:e.key==='ArrowRight'?.5:0),0,12-p.w);p.y=Math.max(0,p.y+(e.key==='ArrowUp'?-.5:e.key==='ArrowDown'?.5:0));this.commit();this.elements.get(t.id)?.focus({preventScroll:true});
          }
        };
        el.addEventListener('pointerdown',e=>this.pointerDown(e,t));
        el.addEventListener('pointermove',e=>this.pointerMove(e));
        el.addEventListener('pointerup',e=>this.pointerEnd(e,false));
        el.addEventListener('pointercancel',e=>this.pointerEnd(e,true));
        this.board.append(el);this.elements.set(t.id,el);
      }
      $('dashboardEmpty').hidden=!!this.config.tiles.length;
      this.layout();this.update();
    }
    renderBlockOrder() {
      const container=$('overviewBlocks'),controls=$('overviewOrder'),labels={tiles:'Kacheln',history:'Verlauf',record:'Start / Pause'};
      for(const key of this.config.blockOrder){const block=container.querySelector(`[data-overview-block="${key}"]`);if(block)container.append(block);}
      controls.hidden=!this.editing;
      const order=this.config.blockOrder.filter(key=>labels[key]);
      controls.innerHTML='<p>Reihenfolge der Blöcke</p>'+order.map((key,i)=>`<div class="overview-order-row"><span>${labels[key]}</span><button class="quiet" data-block="${key}" data-move="-1" aria-label="${labels[key]} nach oben" ${i===0?'disabled':''}>↑</button><button class="quiet" data-block="${key}" data-move="1" aria-label="${labels[key]} nach unten" ${i===order.length-1?'disabled':''}>↓</button></div>`).join('');
      controls.querySelectorAll('button').forEach(button=>button.onclick=()=>{const i=order.indexOf(button.dataset.block),next=i+Number(button.dataset.move);if(next<0||next>=order.length)return;const a=this.config.blockOrder.indexOf(order[i]),b=this.config.blockOrder.indexOf(order[next]);[this.config.blockOrder[a],this.config.blockOrder[b]]=[this.config.blockOrder[b],this.config.blockOrder[a]];this.commit();controls.querySelector(`[data-block="${button.dataset.block}"][data-move="${button.dataset.move}"]`)?.focus({preventScroll:true});});
    }
    layout() {
      const profile=screenProfile();
      if(this.profile!==profile){if(this.historyDrag)this.historyPointerEnd({pointerId:this.historyDrag.pointer},true);this.profile=profile;this.render();return;}
      this.layoutHistory();
      this.row=this.profile==='wide'?30:26;
      const width=this.board.clientWidth;
      for(const t of this.config.tiles){const p=t.layouts[this.profile],el=this.elements.get(t.id);if(!el)continue;
        el.classList.toggle('short-tile',p.h<=3);
        el.classList.toggle('narrow-tile',p.w/12*width-10<95);
        el.classList.toggle('narrow-weather',t.metric==='weather'&&p.w<8);
        Object.assign(el.style,{left:(p.x/12*width+5)+'px',top:(p.y*this.row+5)+'px',width:(p.w/12*width-10)+'px',height:(p.h*this.row-10)+'px'});
      }
      const visible=this.config.tiles.filter(t=>!this.elements.get(t.id)?.hidden);
      this.board.style.height=(visible.length?Math.max(...visible.map(t=>{const p=t.layouts[this.profile];return p.y+p.h;}))+(this.editing?2:0):0)*this.row+'px';
      this.fitNumbers();
    }
    layoutHistory() {
      const height=this.config.historyHeight[this.profile];
      this.history.style.height=height+'px';$('miniHistoryResize').setAttribute('aria-valuenow',String(height));$('miniHistoryResize').setAttribute('aria-valuetext',height+' Pixel');$('miniHistorySize').textContent=height+' px · Höhe ziehen';
      this.a.resizeMini?.();
    }
    historyPointerDown(event) {
      if(!this.editing||event.button!==0||this.historyDrag)return;
      event.preventDefault();this.historyDrag={pointer:event.pointerId,startY:event.clientY,profile:this.profile,origin:this.config.historyHeight[this.profile],moved:false};event.currentTarget.setPointerCapture(event.pointerId);
    }
    historyPointerMove(event) {
      const d=this.historyDrag;if(!d||event.pointerId!==d.pointer)return;
      event.preventDefault();const zoom=this.history.getBoundingClientRect().width/this.history.offsetWidth||1,dy=(event.clientY-d.startY)/zoom;if(Math.abs(dy)>3)d.moved=true;
      if(d.moved){this.config.historyHeight[d.profile]=Math.round(C.clamp(d.origin+dy,300,1000));this.layoutHistory();}
    }
    historyPointerEnd(event,cancelled) {
      const d=this.historyDrag;if(!d||event.pointerId!==d.pointer)return;
      this.historyDrag=null;
      if(cancelled)this.config.historyHeight[d.profile]=d.origin;
      const handle=$('miniHistoryResize');if(handle.hasPointerCapture(d.pointer))handle.releasePointerCapture(d.pointer);
      if(!cancelled&&d.moved)this.commit();else this.layoutHistory();
    }
    pointerDown(event,t) {
      if(!this.editing || event.button!==0 || event.target.closest('button'))return;
      const el=this.elements.get(t.id),p=t.layouts[this.profile];
      this.drag={id:t.id,element:el,pointer:event.pointerId,startX:event.clientX,startY:event.clientY,origin:{...p},resize:!!event.target.closest('.tile-resize'),moved:false};
      el.setPointerCapture(event.pointerId);el.classList.add('is-dragging');
    }
    pointerMove(event) {
      const d=this.drag;if(!d || d.pointer!==event.pointerId)return;
      const zoom=this.board.getBoundingClientRect().width/this.board.clientWidth||1,dx=(event.clientX-d.startX)/zoom,dy=(event.clientY-d.startY)/zoom;
      if(Math.abs(dx)+Math.abs(dy)>5)d.moved=true;
      if(!d.moved)return;
      event.preventDefault();const t=this.config.tiles.find(t=>t.id===d.id),p=t.layouts[this.profile],cols=Math.round(dx/(this.board.clientWidth/24))/2,rows=Math.round(dy/(this.row/2))/2;
      if(d.resize){const min=C.CATALOG[t.metric].widget?4:2;p.w=C.clamp(d.origin.w+cols,min,12-d.origin.x);p.h=C.clamp(d.origin.h+rows,min,18);}
      else{p.x=C.clamp(d.origin.x+cols,0,12-p.w);p.y=C.clamp(d.origin.y+rows,0,1000);}
      this.layout();
    }
    pointerEnd(event,cancelled) {
      const d=this.drag;if(!d || d.pointer!==event.pointerId)return;
      this.drag=null;d.element.classList.remove('is-dragging');
      if(cancelled){this.config.tiles.find(t=>t.id===d.id).layouts[this.profile]=d.origin;this.layout();return;}
      if(d.moved){this.lastDrag=Date.now();this.commit();}
    }
    data(t,window,solarFrom=window.from,solarTo=window.to,retainScope) {
      if(this.a.isBlocked?.()||window.to<=window.from)return {points:[],summary:{}};
      const m=C.CATALOG[t.metric],backend=m.backend || m.field;
      const period=m.flow?this.config.energyPeriod:t.period,day=['today','sunrise'].includes(period.mode)?new Date(window.from).toLocaleDateString('sv-SE'):'';
      const scope=retainScope||JSON.stringify(['dashboard',m.flow?'flow':backend,period,m.flow?this.config.tiles.find(t=>t.metric==='solarEnergy')?.period:null,day,this.a.isDemo()]);
      const read=(method,args)=>this.a.read?this.a.read(method,args,scope):this.a.native[method](...args);
      const key=[m.flow?'energy-flow':backend,Math.floor(window.from/10000),Math.floor(window.to/10000),m.flow?Math.floor(solarFrom/10000):'',m.flow?Math.floor(solarTo/10000):'',this.a.isDemo()].join(':');
      if(this.cache.has(key))return m.flow?this.cache.get(key)[m.flow]:this.cache.get(key);
      let data={points:[],summary:{}};
      try {
        if(m.flow){
          if(this.a.isDemo())data=this.demoFlow(t,window,solarFrom,solarTo);
          else if(this.a.native?.energyHistoryWindow)data=JSON.parse(read('energyHistoryWindow',[Math.floor(window.from),Math.ceil(window.to),'all',Math.floor(solarFrom),Math.ceil(solarTo)]));
          else if(this.a.native?.energyHistory)data=JSON.parse(read('energyHistory',[Math.floor(window.from),Math.ceil(window.to),'all',solarFrom]));
          else throw new Error('Energieberechnung nicht verfügbar.');
          if(data.error||!data.solar||!data.consumption||!data.balance)throw new Error('Energiedaten nicht verfügbar.');
          if(!this.a.isDemo()&&!data.pending&&data.accountingVersion!==2){const missing={points:[],summary:{},error:true,updateRequired:true};data.consumption=missing;data.mobile=missing;}
        }
        else if(this.a.isDemo())data=this.demoData(t,window);
        else if(this.a.native?.dashboardHistory)data=JSON.parse(read('dashboardHistory',[Math.floor(window.from),Math.ceil(window.to),backend]));
        else if(this.a.native?.history){data=JSON.parse(read('history',[Math.floor(window.from),Math.ceil(window.to),backend,'all']));data.summary=this.summary(data.points || []);}
      } catch(_){const missing={points:[],summary:{},error:true};data=m.flow?{solar:missing,consumption:missing,balance:missing}:missing;}
      if(this.cache.size>128)this.cache.clear();this.cache.set(key,data);return m.flow?data[m.flow]:data;
    }
    summary(points) {
      const valid=points.filter(p=>C.finite(p.v));if(!valid.length)return {};
      const n=valid.reduce((n,p)=>n+(p.n || 1),0),last=valid[valid.length-1];
      return {n,avg:valid.reduce((n,p)=>n+p.v*(p.n || 1),0)/n,min:Math.min(...valid.map(p=>p.min??p.v)),max:Math.max(...valid.map(p=>p.max??p.v)),last:last.v,lastTime:last.last || last.t};
    }
    demoData(t,window) {
      const m=C.CATALOG[t.metric],now=Date.now(),step=Math.max(60000,(window.to-window.from)/240),out=[];
      let energy=0,previous=null;
      for(let time=window.from;time<=Math.min(now,window.to);time+=step){
        const hour=new Date(time).getHours()+new Date(time).getMinutes()/60,solar=Math.max(0,Math.sin((hour-7)/12*Math.PI))*170;
        const shunt={soc:70+6*Math.sin((hour-9)/24*Math.PI*2),ttg:480,voltage:13+.15*Math.sin(hour/12*Math.PI),current:(solar*.9-32)/13,power:solar*.9-32,consumed:-23.6,temperature:null,aux:null,alarm:0};
        const reg={solar,voltage:13.08,current:solar/13.08,yieldWh:Math.max(0,Math.min(900,(hour-7)*80)),state:solar>1?3:0,error:0,load:0};
        let value=t.metric==='remainingWh'?shunt.soc*shunt.voltage/100:t.metric==='remainingAh'?shunt.soc:t.metric==='consumed'?Math.abs(shunt.consumed):(m.kind==='shunt'?shunt:reg)[m.field];
        if(m.energy){const power=t.metric==='solarEnergy'?solar:t.metric==='consumptionEnergy'?solar-shunt.power:['energyBalance','batteryEnergy'].includes(t.metric)?shunt.power:Math.max(0,-shunt.power);if(previous!=null)energy+=(previous+power)/2*step/3600000;previous=power;value=energy;}
        if(m.modelPower)value=0;
        if(C.finite(value))out.push({t:time,last:time,kind:m.kind,device:'demo-'+m.kind,origin:'live',v:value,min:value,max:value,n:1});
      }
      return {points:out,summary:{...this.summary(out),...(m.energy?{coverage:1}:{}),...(m.modelPower?{live:0,liveAt:now,estimated:true}:{})}};
    }
    demoFlow(t,window,solarFrom,solarTo=window.to){
      const result={solar:{points:[]},consumption:{points:[]},balance:{points:[]},mobile:{points:[]}},totals={solar:0,consumption:0,balance:0,mobile:0};
      const times=Array.from({length:241},(_,i)=>window.from+(window.to-window.from)*i/240);
      const productionStart=new Date(window.from);productionStart.setHours(7,0,0,0);const productionEnd=productionStart.getTime()+12*3600000;
      solarFrom=Math.max(window.from,Math.min(solarFrom,productionStart.getTime(),window.to));solarTo=Math.min(window.to,Math.max(solarTo,productionEnd));
      if(solarFrom>window.from&&solarFrom<window.to)times.push(solarFrom);if(solarTo>window.from&&solarTo<window.to)times.push(solarTo);times.sort((a,b)=>a-b);
      let last=null;
      for(const time of times){
        const d=new Date(time),hour=d.getHours()+d.getMinutes()/60+d.getSeconds()/3600,solar=hour>=7&&hour<=19?Math.max(0,Math.sin((hour-7)/12*Math.PI))*170:0,net=solar*.9-32;
        if(last){
          const hours=(time-last.time)/3600000;
          totals.solar+=(last.solar+solar)/2*hours;
          totals.balance+=(last.net+net)/2*hours;
          totals.consumption=totals.solar-totals.balance;
        }
        for(const kind of Object.keys(result))if(kind!=='solar'||time>=solarFrom&&time<=solarTo){const v=totals[kind];result[kind].points.push({t:time,last:time,v,min:v,max:v,n:1,gap:false,kind,device:'demo-'+kind,origin:'calculated'});}
        last={time,solar,net};
      }
      for(const kind of Object.keys(result))result[kind].summary={...this.summary(result[kind].points),last:totals[kind],coverage:1,from:kind==='solar'?solarFrom:window.from,to:kind==='solar'?solarTo:window.to};
      return result;
    }
    updateChargeEstimate(shunt=this.a.reading('shunt'),snapshot=this.a.getSnapshot()) {
      const demo=this.a.isDemo();
      if(demo!==this.chargeDemo){this.chargeEstimator.reset();this.chargeDemo=demo;}
      this.chargeEstimate=this.chargeEstimator.update(shunt,snapshot.settings.capacity,Date.now());
      return this.chargeEstimate;
    }
    update() {
      if(this.a.isBlocked?.()||!this.a.getSnapshot)return;
      const snapshot=this.a.getSnapshot(),shunt=this.a.reading('shunt'),solar=this.a.reading('solar'),now=this.a.dataTime?.()??Date.now();
      this.updateChargeEstimate(shunt,snapshot);
      const flowWindow=C.windowFor({metric:'energyBalance',type:'chart',period:this.config.energyPeriod},now,snapshot.weather);
      const solarTile=this.config.tiles.find(t=>t.metric==='solarEnergy'),solarWindow=solarTile?C.windowFor(solarTile,now,snapshot.weather):flowWindow;
      const solarFrom=solarTile?.period.mode==='sunrise'?solarWindow.solarFrom??solarWindow.from:flowWindow.from;
      const solarTo=solarTile?.period.mode==='sunrise'?solarWindow.solarTo??flowWindow.to:flowWindow.to;
      let visibilityChanged=false;
      for(const t of this.config.tiles){
        const el=this.elements.get(t.id);if(!el)continue;
        if(t.metric==='weather'){
          const configured=!!snapshot.settings.weatherPlace?.name,hidden=!configured&&!this.editing;
          if(el.hidden!==hidden){el.hidden=hidden;visibilityChanged=true;}
          el.querySelector('.weather-disabled').hidden=configured;continue;
        }
        if(C.CATALOG[t.metric].widget)continue;
        el.style.setProperty('--tile-color',this.paint(t.color,t,true));
        el.style.setProperty('--tile-muted',this.paint(getComputedStyle(document.documentElement).getPropertyValue('--muted').trim(),t,true));
        const m=C.CATALOG[t.metric];let window=C.windowFor(t,now,snapshot.weather);
        if(t.metric==='chargeTime'){
          const label=C.tileLabel(t,this.chargeEstimate);el.querySelector('.tile-label').textContent=label;
          el.setAttribute('aria-label',label+' anpassen');el.querySelector('.tile-menu').setAttribute('aria-label',label+' bearbeiten');
        }
        const query=window&&t.metric==='solar'&&t.period.mode==='sunrise'?C.solarQueryWindow(window):window|| (m.modelPower?{from:Math.max(0,now-60000),to:now}:null);
        let data=query?this.data(t,m.flow?flowWindow:query,m.flow?solarFrom:query.from,m.flow?solarTo:query.to):null;
        if(window&&t.period.mode==='sunrise'&&(t.metric==='solar'||m.flow==='solar'))window=C.solarPlotWindow(window,data?.points||[],m.flow==='solar'?data?.summary:null);
        if(window?.solarFrom!=null&&t.metric==='solar'&&!m.flow&&data){const points=(data.points||[]).filter(p=>p.t>=window.from&&p.t<=window.to);data={...data,points,summary:this.summary(points)};}
        let value=C.displayValue(t,data?.summary,shunt,solar,snapshot,this.chargeEstimate);
        value=C.convert(value,t.unit,t.metric);
        const estimated=m.modelPower||m.estimated||data?.summary?.modelledMillis>0;
        const reading=`<strong class="tile-number">${esc(C.format(value,t))}</strong><span class="tile-unit">${!m.status?esc(t.unit):''}${estimated?'<small class="tile-estimate-tag"> · geschätzt</small>':''}</span>`;
        const visual=el.querySelector('.tile-visual');
        if(t.type==='gauge')visual.innerHTML=this.gauge(t,value,reading);
        else if(t.type==='chart')visual.innerHTML=`<div class="tile-reading chart-reading">${reading}</div>${this.sparkline(t,data?.points || [],window,snapshot.settings.capacity,data?.summary,data)}`;
        else visual.innerHTML=`<div class="tile-reading">${reading}</div>`;
        let hint=window?`${aggregateNames[t.period.aggregate]} · ${this.periodLabel(t,window)}`:m.kind==='shunt'?'SmartShunt':'SmartSolar';
        if(!window && t.metric==='remaining' && !C.finite(value))hint=shunt?.current>.03?'Akku wird geladen':'Wert vom SmartShunt';
        if(t.metric==='chargeTime')hint=C.chargeHint(this.chargeEstimate);
        if(!window && t.metric==='solar' && solar?.state!=null)hint=C.PHASES[solar.state] || 'Ladephase '+solar.state;
        if(!window && t.metric==='power' && C.finite(shunt?.power))hint=shunt.power>.4?'Akku wird geladen':shunt.power<-.4?'Akku wird entladen':'Bilanz ausgeglichen';
        if(m.energy&&!data?.error)hint=t.period.mode==='sunrise'?`${this.periodLabel(t,window)} · ${Math.floor((data?.summary?.coverage||0)*100)} %`:`Berechnet · ${this.periodLabel(t,window)} · ${Math.floor((data?.summary?.coverage||0)*100)} % erfasst`;
        if(m.energy&&data?.summary?.interpolatedMillis>0)hint=this.periodLabel(t,window)+' · '+Math.floor((data.summary.measuredCoverage??data.summary.coverage??0)*100)+' % gemessen · kurz interpoliert';
        if(t.type==='chart'&&C.usesLiveValue(t))hint=`${C.finite(value)?'Aktuell':'Kein Livewert'} · ${data?.error?'Verlauf nicht verfügbar':(t.period.mode==='sunrise'?'':'Verlauf: ')+this.periodLabel(t,window)}`;
        else if(data?.error)hint='Zeitraum nicht verfügbar';
        if(data?.pending)hint=data.stale?'Letzter Stand · neue Daten werden geladen …':'Daten werden im Hintergrund geladen …';
        else if(data?.refreshError)hint='Letzter Stand · Aktualisierung fehlgeschlagen';
        if(data?.summary?.modelledMillis>0)hint=this.periodLabel(t,window)+' · '+Math.floor((data.summary.coverage||0)*100)+' % abgedeckt · mit mobilem Modellanteil';
        if(m.modelPower||m.estimated||data?.summary?.modelledMillis>0)hint='Geschätzt · '+hint;
        if(data?.summary?.inconsistentMillis>0)hint+=' · Schätzung und Messbilanz passen teilweise nicht zusammen';
        if(data?.summary?.adjustedMillis>0)hint+=' · bilanzgestützt';
        if(data?.summary?.limitedMillis>0)hint+=' · Ladebegrenzung berücksichtigt';
        if(data?.summary?.missingPriorMillis>0)hint+=' · Ladebegrenzung, Grundbedarf noch unbekannt';
        if(data?.summary?.abnormalMillis>0)hint+=' · auffällige Messwerte';
        if(m.energy&&C.finite(value)&&data?.summary?.coverage<.995)hint='Teilsumme · '+hint;
        if(data?.updateRequired)hint='Bitte auch das Hauptgerät auf 1.6.10 oder neuer aktualisieren';
        const coverage=m.energy?Math.floor((data?.summary?.coverage||0)*100)+' % des Zeitraums abgedeckt'+(data?.summary?.interpolatedMillis>0?' · davon '+(data.summary.interpolatedMillis/60000).toLocaleString('de-DE',{maximumFractionDigits:1})+' min kurz interpoliert':''):'';
        const hintElement=el.querySelector('.tile-hint');hintElement.textContent=t.metric==='consumptionEnergy'&&C.finite(value)&&value<0?'Zusätzliche Einspeisung · '+hint:hint;hintElement.title=[hint,window?.note||'',coverage,m.hint||''].filter(Boolean).join(' ');hintElement.classList.toggle('period-sunrise',t.period.mode==='sunrise');
        el.classList.toggle('no-value',!C.finite(value));
      }
      if(visibilityChanged)this.layout();else this.fitNumbers();
      this.detail?.themeChanged();
    }
    fitNumbers() {
      for(const el of this.elements.values()){
        const number=el.querySelector('.tile-number');if(!number)continue;
        number.style.fontSize='';const parent=number.closest('.tile-reading') || number.parentElement;parent.classList.remove('stack-unit');
        let available=parent.clientWidth-(parent.querySelector('.tile-unit')?.offsetWidth || 0)-8;
        const font=parseFloat(getComputedStyle(number).fontSize),natural=number.scrollWidth;
        // Keep a readable value on narrow tiles; put the unit below before shrinking further.
        if((available<=10||font*available/Math.max(1,natural)<14)&&parent.classList.contains('tile-reading')){parent.classList.add('stack-unit');available=parent.clientWidth;}
        if(natural>available&&available>10)number.style.fontSize=Math.max(14,Math.floor(font*available/natural))+'px';
      }
    }
    periodLabel(t,window) {
      const p=t.period;
      if(p.mode==='today')return 'heute';
      if(p.mode==='sunrise')return window?.label==='Seit Sonnenaufgang'?'seit Sonnenaufgang':'heute (Ersatz)';
      if(p.mode==='reset')return 'seit '+stamp(p.resetAt);
      if(p.mode==='custom')return stamp(p.from)+' – '+stamp(p.to);
      if(p.mode==='live')return 'letzte 6 h';
      return 'letzte '+p.amount+' '+({min:'min',h:'h',d:'Tage'}[p.unit]);
    }
    gauge(t,value,reading) {
      const {min,max}=C.fixedRange(t),percent=C.finite(value)?C.clamp((value-min)/(max-min),0,1):0;
      const point=angle=>{const r=angle*Math.PI/180;return [80+61*Math.cos(r),80+61*Math.sin(r)];};
      let paths='';
      for(let i=0;i<60;i++){
        const a=point(135+i/60*270),b=point(135+(i+.82)/60*270),f=t.invert?1-i/60:i/60;
        const tone=t.multicolor?this.blend(f<.5?t.color:t.colorMid,f<.5?t.colorMid:t.colorEnd,f<.5?f*2:(f-.5)*2):t.color;
        paths+=`<path d="M${a} A61 61 0 0 1 ${b}" stroke="${this.paint(tone,t)}" opacity="${C.finite(value) && i/60<percent?1:.13}"/>`;
      }
      const p=point(135+percent*270),arrow=C.finite(value)?`<circle cx="${p[0]}" cy="${p[1]}" r="3.7" fill="var(--text)" stroke="var(--panel)" stroke-width="1.5"/>`:'';
      return `<div class="tile-gauge"><svg viewBox="0 0 160 150" aria-hidden="true"><g fill="none" stroke-width="6" stroke-linecap="round">${paths}</g>${arrow}</svg><div class="gauge-reading">${reading}</div><span class="gauge-min">${esc(this.short(min))}</span><span class="gauge-max">${esc(this.short(max))}</span></div>`;
    }
    short(n){return Number(n.toFixed(Math.abs(n)<10?1:0)).toLocaleString('de-DE');}
    blend(a,b,p){const ca=parseInt(a.slice(1),16),cb=parseInt(b.slice(1),16);return '#'+[16,8,0].map(s=>Math.round(((ca>>s)&255)*(1-p)+((cb>>s)&255)*p).toString(16).padStart(2,'0')).join('');}
    paint(color,t,text=false) {
      if(t.adaptiveColor===false&&!text)return color;
      const css=getComputedStyle(document.documentElement);
      if(t.adaptiveColor!==false&&color===C.CATALOG[t.metric]?.color){const tone=['solar','mobileSolarPower','mobileSolarEnergy','yieldWh','solarEnergy','solarVoltage','solarCurrent','pvVoltage','state','error'].includes(t.metric)?'--orange':['energyBalance','batteryEnergy','temperature','aux','load'].includes(t.metric)?'--blue':'--mint';color=css.getPropertyValue(tone).trim()||color;}
      const background=t.background==='auto'?css.getPropertyValue('--panel').trim():t.background;
      const page=css.getPropertyValue('--bg').trim(),target=text?7:4.5,key=[color,background,page,t.opacity,target].join(':');this.paintCache=this.paintCache || new Map();
      if(this.paintCache.has(key))return this.paintCache.get(key);
      const result=C.readableColor(color,background,page,t.opacity,target);
      this.paintCache.set(key,result);return result;
    }
    sparkline(t,points,window,capacity,summary,state) {
      if(!window)return '';
      const series=points.map(p=>({...p,v:C.convert(C.signedValue(t,C.historicalValue(t.metric,p.v,capacity)),t.unit,t.metric)})).filter(p=>C.finite(p.v));
      if(!series.length)return `<div class="tile-chart-empty">${state?.pending?'Werte werden geladen …':state?.error?'Werte konnten nicht geladen werden':'Keine gespeicherten Werte'}</div>`;
      const bounds=C.chartRange(t,points,capacity,summary),low=bounds.min,high=bounds.max,x=p=>4+(p.t-window.from)/(window.to-window.from)*292,y=p=>58-C.clamp((p.v-low)/(high-low),0,1)*52;
      const zero=low<0&&high>=0?`<path class="tile-zero-line" d="M4 ${y({v:0}).toFixed(2)}H296" stroke="var(--border)" stroke-dasharray="3 3" vector-effect="non-scaling-stroke"/>`:'';
      let path='',last=null;
      for(const p of series){const gap=C.seriesGap(last,p,Math.max(this.a.isDemo()?600001:45000,(p.period||0)*1500,(window.to-window.from)/600*3));path+=(last&&!gap?'L':'M')+x(p).toFixed(2)+','+y(p).toFixed(2);last=p;}
      return `<svg class="tile-sparkline" data-scale-min="${low}" data-scale-max="${high}" viewBox="0 0 300 64" preserveAspectRatio="none" aria-hidden="true"><path d="M4 60H296" stroke="var(--border)"/>${zero}<path d="${path}" fill="none" stroke="${this.paint(t.color,t)}" stroke-width="2.5" vector-effect="non-scaling-stroke"/></svg><div class="tile-chart-range"><span>${esc(C.axisLabel(low,high-low))}</span>${bounds.auto?'<span title="Skala nach bisherigen Messwerten im gewählten Zeitraum">Auto</span>':''}<span>${esc(C.axisLabel(high,high-low))} ${esc(t.unit)}</span></div>`;
    }
    choose(id) {
      const t=this.config.tiles.find(t=>t.id===id);if(!t)return;
      if(t.metric==='lan'){$('lanOpen').click();return;}
      if(t.metric==='weather'){this.editor(id);return;}
      this.a.openModal(C.tileLabel(t,this.chargeEstimate),`<div class="tile-actions"><button class="primary" id="tileActionDetail">Großansicht öffnen</button><button class="secondary" id="tileActionSettings">Kachel-Einstellungen</button></div>`);
      $('tileActionSettings').onclick=()=>this.editor(id);
      $('tileActionDetail').onclick=()=>{this.a.closeModal();if(!this.detail)this.detail=new window.WattTileDetail(this);this.detail.open(id);};
    }
    picker() {
      if(this.config.tiles.length>=64){this.a.toast('Bis zu 64 Kacheln sind möglich.');return;}
      const groups=[...new Set(Object.values(C.CATALOG).map(m=>m.group))];
      this.a.openModal('Kachel hinzufügen',`${field('Messwert suchen','<input id="tileSearch" type="search" placeholder="Zum Beispiel Restlaufzeit">')}<div class="tile-catalog">${groups.map(group=>`<div class="catalog-group"><h3>${group}</h3>${Object.entries(C.CATALOG).filter(([,m])=>m.group===group).map(([key,m])=>`<button class="metric-option" data-metric="${key}"><i style="background:${m.color}"></i><span>${esc(m.label)}</span><b>+</b></button>`).join('')}</div>`).join('')}</div>`);
      $('tileSearch').oninput=()=>{const query=$('tileSearch').value.toLocaleLowerCase('de-DE');document.querySelectorAll('.metric-option').forEach(el=>el.hidden=!el.textContent.toLocaleLowerCase('de-DE').includes(query));document.querySelectorAll('.catalog-group').forEach(el=>el.hidden=![...el.querySelectorAll('.metric-option')].some(el=>!el.hidden));};
      document.querySelectorAll('.metric-option').forEach(el=>el.onclick=()=>this.add(el.dataset.metric));
    }
    add(metric,prototype) {
      if(C.CATALOG[metric]?.widget&&this.config.tiles.some(t=>t.metric===metric)){this.a.closeModal();this.a.toast('Diese Kachel ist bereits vorhanden. Über „Anordnen“ verschieben oder anpassen.');return;}
      const t=prototype?{...copy(prototype),id:C.tile(metric).id}:C.tile(metric);
      for(const profile of ['compact','wide']){const y=this.config.tiles.length?Math.max(...this.config.tiles.map(t=>t.layouts[profile].y+t.layouts[profile].h)):0;t.layouts[profile]={...t.layouts[profile],x:0,y};}
      this.config.tiles.push(t);
      if(this.commit()){this.a.closeModal();requestAnimationFrame(()=>this.elements.get(t.id)?.scrollIntoView({behavior:'smooth',block:'center'}));this.a.toast('Kachel hinzugefügt. Antippen zum Anpassen.');}
    }
    editor(id,unsaved) {
      const original=this.config.tiles.find(t=>t.id===id);if(!original)return;
      const t=copy(unsaved || original),m=C.CATALOG[t.metric],p=t.layouts[this.profile];let layer='keep';
      if(m.widget){this.widgetEditor(t);return;}
      const types=m.status?[['number','Zahlenwert']]:[['number','Zahlenwert'],['gauge','Rundinstrument'],...(!m.liveOnly?[['chart','Verlauf']]:[])];
      const fields=`<div class="form-grid">${field('Messwert',select('tileMetric',Object.entries(C.CATALOG).map(([key,m])=>[key,m.label])))}${field('Eigene Beschriftung',input('tileTitle',t.title,'maxlength="60" placeholder="'+esc(m.label)+'"'))}${field('Darstellung',select('tileType',types))}${field('Einheit',select('tileUnit',m.units.map(u=>[u,u || 'Ohne Einheit'])))}</div><p class="muted tile-explanation">${esc(m.hint || (m.kind==='shunt'?'Messwert vom SmartShunt.':'Messwert vom SmartSolar.'))}</p>`;
      const period=`<details open><summary>Wert & Zeitraum</summary><div class="form-grid">${field('<span id="tilePeriodLabel">Zeitraum</span>',select('tilePeriod',m.liveOnly?[['live','Aktuell']]:[['live','Aktuell'],['last','Letzte …'],['today','Heute'],['sunrise','Seit Sonnenaufgang'],['reset','Seit Zurücksetzen'],['custom','Freier Zeitraum']]))}${field('<span id="tileAggregateLabel">Auswertung</span>',select('tileAggregate',m.status?[['last','Letzter Wert']]:Object.entries(aggregateNames)))}</div><p class="muted" id="tileLiveHint" hidden>Aktuell zeigt den Liveempfang. Ohne frischen Wert erscheint —; die gespeicherte Kurve bleibt sichtbar.</p><p class="muted" id="tileSunriseHint" hidden></p>${m.energy&&!m.flow?'<label class="check-field"><input type="checkbox" id="tileReverseSign"> Vorzeichen umkehren (+/−)</label>':''}${m.flow?'<p class="muted">Geräteverbrauch und Akkubilanz verwenden denselben Zeitraum. Bei „Heute“ kann der Solarertrag ab Sonnenaufgang zählen; der nächtliche Verbrauch bleibt enthalten. Geräteverbrauch = festes Solar + geschätzte zusätzliche Solarerzeugung − Akkubilanz. Mobile Anteile sind geschätzt; bei Mobil aus zählt ab dem Umschaltzeitpunkt 0 W. Frühere mobile Erträge bleiben erhalten.</p>':''}<div id="tileLastFields" class="form-grid">${field('Anzahl',input('tileAmount',t.period.amount,'type="number" min="1" max="10000"'))}${field('Zeiteinheit',select('tileTimeUnit',[['min','Minuten'],['h','Stunden'],['d','Tage']]))}</div><div id="tileCustomFields" class="form-grid">${field('Von',input('tileFrom',dateInput(t.period.from || Date.now()-86400000),'type="datetime-local"'))}${field('Bis',input('tileTo',dateInput(t.period.to || Date.now()),'type="datetime-local"'))}</div><div id="tileResetFields"><p class="muted" id="tileResetLabel"></p><button class="quiet" id="tileReset">Jetzt zurücksetzen</button></div></details>`;
      const appearance=`<details><summary>Farben & Schrift</summary><div class="form-grid">${field('Wertfarbe',input('tileColor',t.color,'type="color"'))}${field('Schriftgröße',input('tileFont',t.fontSize,'type="number" min="18" max="20"'))}${field('Nachkommastellen',select('tileDigits',[[0,'Keine'],[1,'Eine'],[2,'Zwei'],[3,'Drei']]))}${field('Deckkraft der Fläche',input('tileOpacity',t.opacity,'type="range" min="0" max="100"'))}${field('Hintergrund',select('tileBackgroundMode',[['auto','Passend zum Farbschema'],['custom','Eigene Farbe']]))}${field('Eigene Hintergrundfarbe',input('tileBackground',t.background==='auto'?'#18303b':t.background,'type="color"'))}</div><label class="check-field"><input type="checkbox" id="tileAdaptiveColor"> Kurven- und Instrumentfarben an Hell/Dunkel anpassen</label></details>`;
      const scale=`<details><summary>Skala</summary><div id="tileChartScale">${field('Verlaufsskala',select('tileScaleMode',[['fixed','Feste Skala'],['auto','Automatisch nach bisherigen Werten']]))}<p class="muted" id="tileScaleHint" hidden>Passt sich den Messwerten im gewählten Zeitraum an, mit etwas Platz über dem Maximum. Spannung, Temperatur und Akkustand passen auch die Untergrenze an. Für die bisherigen Tageswerte den Zeitraum „Heute“ wählen.</p></div><div class="form-grid">${field('Minimum',input('tileMin',this.shortInput(C.fixedRange(t).min),'type="number" step="any"'))}${field('Maximum',input('tileMax',this.shortInput(C.fixedRange(t).max),'type="number" step="any"'))}</div><label class="check-field"><input type="checkbox" id="tileMulticolor"> Mehrfarbiges Rundinstrument</label><div class="form-grid">${field('Mittlere Farbe',input('tileColorMid',t.colorMid,'type="color"'))}${field('Endfarbe',input('tileColorEnd',t.colorEnd,'type="color"'))}</div><label class="check-field"><input type="checkbox" id="tileInvert"> Farbverlauf umkehren</label></details>`;
      const arrangement=`<details><summary>Größe & Ebene</summary><div class="form-grid">${field('Breite',input('tileWidth',p.w,'type="range" min="2" max="12" step="0.5"'))}${field('Höhe',input('tileHeight',p.h,'type="range" min="2" max="18" step="0.5"'))}</div><div class="row"><button class="quiet" id="tileBack">Nach hinten</button><button class="quiet" id="tileFront">Nach vorne</button><button class="quiet" id="duplicateTile">Duplizieren</button></div><p class="muted">Gilt für die ${this.profile==='wide'?'breite':'schmale'} Ansicht. Andere Kacheln behalten ihre Position.</p></details>`;
      this.a.openModal('Kachel anpassen',`<div class="tile-editor">${fields}${period}${appearance}${m.status?'':scale}${arrangement}<p class="form-error" id="tileError" hidden></p><div class="form-actions"><button class="quiet danger-text" id="removeTile">Entfernen</button><button class="primary" id="saveTile">Übernehmen</button></div></div>`);
      const values={tileMetric:t.metric,tileType:t.type,tileUnit:t.unit,tilePeriod:t.period.mode,tileAggregate:m.status?'last':t.period.aggregate,tileTimeUnit:t.period.unit,tileDigits:t.digits,tileBackgroundMode:t.background==='auto'?'auto':'custom'};
      for(const [id,value] of Object.entries(values))$(id).value=value;
      $('tileAdaptiveColor').checked=t.adaptiveColor!==false;
      if($('tileReverseSign')){$('tileReverseSign').checked=t.reverseSign;$('tileReverseSign').onchange=()=>{const min=$('tileMin').value,max=$('tileMax').value;$('tileMin').value=this.shortInput(-Number(max));$('tileMax').value=this.shortInput(-Number(min));};}
      if($('tileMulticolor')){$('tileMulticolor').checked=t.multicolor;$('tileInvert').checked=t.invert;}
      const scaleChanged=()=>{if(!$('tileScaleMode'))return;const chart=$('tileType').value==='chart',auto=chart&&$('tileScaleMode').value==='auto';$('tileChartScale').hidden=!chart;$('tileScaleHint').hidden=!auto;$('tileMin').disabled=auto;$('tileMax').disabled=auto;};
      if($('tileScaleMode')){$('tileScaleMode').value=t.scaleMode||'fixed';$('tileScaleMode').onchange=scaleChanged;}scaleChanged();
      const periodChanged=()=>{
        const chart=$('tileType').value==='chart',mode=$('tilePeriod').value;
        $('tilePeriodLabel').textContent=chart?'Kurvenzeitraum':'Zeitraum';$('tileAggregateLabel').textContent=chart?'Große Zahl':'Auswertung';
        const liveOption=$('tilePeriod').querySelector('option[value="live"]');liveOption.hidden=chart||!!m.energy;liveOption.disabled=chart||!!m.energy;
        $('tileLastFields').hidden=mode!=='last';$('tileCustomFields').hidden=mode!=='custom';$('tileResetFields').hidden=mode!=='reset';
        $('tileAggregate').disabled=!!m.energy||!chart&&mode==='live';$('tileLiveHint').hidden=!chart||$('tileAggregate').value!=='live';
        $('tileResetLabel').textContent='Ab '+stamp(t.period.resetAt);
        $('tileSunriseHint').hidden=mode!=='sunrise';
        if(mode==='sunrise'){
          const weather=this.a.getSnapshot().weather||{},window=C.windowFor({...t,period:{...t.period,mode:'sunrise'}},Date.now(),weather);
          $('tileSunriseHint').textContent=window.label==='Seit Sonnenaufgang'?'Ab '+new Date(window.from).toLocaleTimeString('de-DE',{hour:'2-digit',minute:'2-digit',timeZone:weather.timezone||undefined})+' Uhr am gewählten Wetterort. '+window.note:window.note+' Es werden ersatzweise die heutigen Daten gezeigt.';
        }
      };
      const valueOptions=value=>{const chart=$('tileType').value==='chart';$('tileAggregate').innerHTML=(m.energy?[['last','Berechnete Energie']]:m.status?[['last','Letzter Wert']]:[...(chart?[['live','Aktuell (Live)']]:[]),...Object.entries(aggregateNames).map(([key,name])=>[key,key==='last'?'Letzter gespeicherter Wert':name])]).map(x=>option(...x)).join('');$('tileAggregate').value=value;};
      valueOptions(t.type==='chart'&&t.valueMode==='live'?'live':t.period.aggregate);
      if(t.type==='chart'&&$('tilePeriod').value==='live'){$('tilePeriod').value='last';$('tileAmount').value=6;$('tileTimeUnit').value='h';}
      $('tilePeriod').onchange=periodChanged;$('tileAggregate').onchange=periodChanged;periodChanged();
      $('tileReset').onclick=()=>{t.period.resetAt=Date.now();periodChanged();};
      $('tileType').onchange=()=>{
        const chart=$('tileType').value==='chart',value=$('tileAggregate').value;
        if($('tileType').value!=='number')$('tileHeight').value=Math.max(Number($('tileHeight').value),6);
        const wasLive=$('tilePeriod').value==='live';
        if(chart&&wasLive)$('tilePeriod').value='last';
        if(!chart&&value==='live')$('tilePeriod').value='live';
        valueOptions(chart&&wasLive?'live':value==='live'?t.period.aggregate:value);periodChanged();scaleChanged();
      };
      let previousUnit=t.unit;
      $('tileUnit').onchange=()=>{if($('tileMin'))for(const id of ['tileMin','tileMax'])$(id).value=this.shortInput(C.convert(C.unconvert(Number($(id).value),previousUnit,t.metric),$('tileUnit').value,t.metric));previousUnit=$('tileUnit').value;};
      $('tileMetric').onchange=()=>{const changed=C.tile($('tileMetric').value,id);changed.layouts=copy(t.layouts);changed.title=$('tileTitle').value;this.editor(id,changed);};
      $('tileBackgroundMode').onchange=()=>{$('tileBackground').disabled=$('tileBackgroundMode').value==='auto';};$('tileBackgroundMode').onchange();
      $('tileBack').onclick=()=>{layer='back';this.a.toast('Wird hinter die anderen Kacheln gelegt.');};
      $('tileFront').onclick=()=>{layer='front';this.a.toast('Wird vor die anderen Kacheln gelegt.');};
      $('duplicateTile').onclick=()=>{if(this.config.tiles.length<64)this.add(t.metric,t);else this.a.toast('Bis zu 64 Kacheln sind möglich.');};
      $('removeTile').onclick=()=>{this.config.tiles=this.config.tiles.filter(t=>t.id!==id);if(this.commit())this.a.closeModal();};
      $('saveTile').onclick=()=>{
        try {
          t.title=$('tileTitle').value.trim();t.unit=$('tileUnit').value;t.type=$('tileType').value;t.reverseSign=!!$('tileReverseSign')?.checked;
          const selectedValue=$('tileAggregate').value;t.valueMode=t.type==='chart'&&selectedValue==='live'?'live':'history';
          t.period={...t.period,mode:$('tilePeriod').value,aggregate:selectedValue==='live'?t.period.aggregate:selectedValue,amount:Number($('tileAmount').value),unit:$('tileTimeUnit').value,from:new Date($('tileFrom').value).getTime(),to:new Date($('tileTo').value).getTime()};
          if(t.type==='chart' && t.period.mode==='live')t.period.mode='last';
          if(t.period.mode==='last' && (!C.finite(t.period.amount)||t.period.amount<1||t.period.amount>10000))throw new Error('Bitte einen Zeitraum zwischen 1 und 10.000 wählen.');
          if(t.period.mode==='custom' && (!C.finite(t.period.from)||!C.finite(t.period.to)||t.period.from>=t.period.to))throw new Error('Bitte einen gültigen Zeitraum wählen.');
          if($('tileMin')){t.scaleMode=$('tileScaleMode').value;if(t.type!=='chart'||t.scaleMode!=='auto'){const min=C.unconvert(Number($('tileMin').value),t.unit,t.metric),max=C.unconvert(Number($('tileMax').value),t.unit,t.metric);t.min=t.reverseSign?-max:min;t.max=t.reverseSign?-min:max;if(!C.finite(t.min)||!C.finite(t.max)||t.min>=t.max)throw new Error('Das Skalenmaximum muss größer als das Minimum sein.');}t.multicolor=$('tileMulticolor').checked;t.invert=$('tileInvert').checked;t.colorMid=$('tileColorMid').value;t.colorEnd=$('tileColorEnd').value;}
          t.adaptiveColor=$('tileAdaptiveColor').checked;t.color=$('tileColor').value;t.digits=Number($('tileDigits').value);t.fontSize=Number($('tileFont').value);t.opacity=Number($('tileOpacity').value);t.background=$('tileBackgroundMode').value==='auto'?'auto':$('tileBackground').value;
          t.layouts[this.profile]={...t.layouts[this.profile],w:Number($('tileWidth').value),h:Number($('tileHeight').value)};
          t.layouts[this.profile].x=Math.min(t.layouts[this.profile].x,12-t.layouts[this.profile].w);
          if(m.flow){
            this.config.energyPeriod={...t.period,mode:t.metric==='solarEnergy'&&t.period.mode==='sunrise'?'today':t.period.mode};
            if(t.metric!=='solarEnergy'&&t.period.mode==='today')for(const other of this.config.tiles)if(other.metric==='solarEnergy')other.period.mode='sunrise';
          }
          this.config.tiles=this.config.tiles.map(old=>old.id===id?t:old);
          if(layer!=='keep'){this.config.tiles=this.config.tiles.filter(old=>old.id!==id);if(layer==='front')this.config.tiles.push(t);else this.config.tiles.unshift(t);}
          if(this.commit()){this.a.closeModal();this.a.toast('Kachel gespeichert.');}
        } catch(error){$('tileError').hidden=false;$('tileError').textContent=error.message;}
      };
    }
    widgetEditor(t){
      const p=t.layouts[this.profile],weather=t.metric==='weather',label=weather?'Wetterkachel':'Verbundkachel';
      this.a.openModal(label+' anpassen',`<div class="tile-editor"><p class="muted">${weather?'14 Tage mit Wettersymbol und Sonnenstunden, seitlich wischbar.':'Name, Rolle und Verbindungsstand deines Anlagenverbunds.'} Unter „Anordnen“ frei zwischen deine Messkacheln verschieben.</p>${field('Eigene Beschriftung',input('tileTitle',t.title,'maxlength="60"'))}<div class="form-grid">${field('Breite',input('tileWidth',p.w,'type="range" min="4" max="12" step="0.5"'))}${field('Höhe',input('tileHeight',p.h,'type="range" min="4" max="18" step="0.5"'))}${field('Deckkraft der Fläche',input('tileOpacity',t.opacity,'type="range" min="0" max="100"'))}</div><button id="tileWidgetSettings" class="quiet">${weather?'Wetterort ändern':'Verbund öffnen'}</button><div class="form-actions"><button class="quiet" id="removeTile">Entfernen</button><button class="primary" id="saveTile">Übernehmen</button></div></div>`);
      $('tileWidgetSettings').onclick=()=>{if(!weather){$('lanOpen').click();return;}this.a.closeModal();document.querySelector('[data-page="settings"]').click();$('weatherQuery').focus();};
      $('removeTile').onclick=()=>{this.config.tiles=this.config.tiles.filter(old=>old.id!==t.id);if(this.commit())this.a.closeModal();};
      $('saveTile').onclick=()=>{t.title=$('tileTitle').value.trim();t.opacity=Number($('tileOpacity').value);t.layouts[this.profile]={...p,w:Number($('tileWidth').value),h:Number($('tileHeight').value)};t.layouts[this.profile].x=Math.min(p.x,12-t.layouts[this.profile].w);this.config.tiles=this.config.tiles.map(old=>old.id===t.id?t:old);if(this.commit()){this.a.closeModal();this.a.toast(label+' gespeichert.');}};
    }
    shortInput(n){return Math.round(n*1e6)/1e6;}
    reset() {
      this.a.openModal('Standardübersicht wiederherstellen?',`<p class="muted">Alle Kacheln, Größen, Positionen, Kachelfarben und Blockreihenfolgen werden für Hoch- und Queransicht auf die Standardübersicht zurückgesetzt. Auch die Verlaufshöhe wird zurückgesetzt. Geräte, Rollerprofil und Messdaten bleiben erhalten.</p><div class="form-actions"><button class="quiet" id="keepDashboard">Behalten</button><button class="primary" id="confirmDashboardReset">Wiederherstellen</button></div>`);
      $('keepDashboard').onclick=()=>this.a.closeModal();$('confirmDashboardReset').onclick=()=>{this.config=C.defaults();if(this.commit())this.a.closeModal();};
    }
  }
  window.WattDashboard=WattDashboard;
})();
