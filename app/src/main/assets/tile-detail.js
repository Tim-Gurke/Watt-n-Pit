(function(root,factory){
  const api=factory(typeof module==='object'&&module.exports?require('./dashboard-core.js'):root.WattDashboardCore);
  if(typeof module==='object'&&module.exports)module.exports=api;
  else{root.WattDetailCore=api;root.WattTileDetail=api.WattTileDetail;}
})(typeof globalThis==='object'?globalThis:this,function(C){
  'use strict';
  const copy=x=>JSON.parse(JSON.stringify(x));
  const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const $=id=>document.getElementById(id);
  const label=t=>t.title||C.CATALOG[t.metric].label;
  const local=t=>{const d=new Date(t);return new Date(t-d.getTimezoneOffset()*60000).toISOString().slice(0,16);};
  const stamp=t=>new Date(t).toLocaleString('de-DE',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'});
  const numericMetrics=()=>Object.entries(C.CATALOG).filter(([,m])=>!m.widget&&!m.status&&!m.liveOnly);
  function periodWindow(period,weather,now=Date.now()){
    const midnight=new Date(now);midnight.setHours(0,0,0,0);
    let from=midnight.getTime(),to=now,note='';
    if(period.mode==='sunrise')return C.miniWindow('sunrise',weather,now);
    if(period.mode==='yesterday'){to=from;const d=new Date(from);d.setDate(d.getDate()-1);from=d.getTime();}
    else if(period.mode==='week'||period.mode==='month'){midnight.setDate(midnight.getDate()-(period.mode==='week'?6:29));from=midnight.getTime();}
    else if(period.mode==='last'||period.mode==='live')from=now-C.clamp(Number(period.amount)||6,1,10000)*({min:60000,h:3600000,d:86400000}[period.unit]||3600000);
    else if(period.mode==='reset')from=Math.min(Number(period.resetAt)||now,now);
    else if(period.mode==='custom'){from=Number(period.from);to=Math.min(Number(period.to),now);}
    if(!Number.isFinite(from)||!Number.isFinite(to)||from<0||to<from)return null;
    return {from,to,note};
  }
  function clampView(view,base){
    const full=Math.max(1,base.to-base.from),span=C.clamp(view.to-view.from,Math.min(60000,full),full);
    const from=C.clamp(view.from,base.from,Math.max(base.from,base.to-span));
    return {from,to:Math.min(base.to,from+span)};
  }
  function zoomView(view,base,factor,anchor=.5){
    const center=view.from+(view.to-view.from)*C.clamp(anchor,0,1),span=(view.to-view.from)*factor;
    return clampView({from:center-span*anchor,to:center+span*(1-anchor)},base);
  }
  function panView(view,base,delta){return clampView({from:view.from+delta,to:view.to+delta},base);}
  function seriesPoints(t,points,capacity){
    const convert=v=>C.convert(C.signedValue(t,C.historicalValue(t.metric,v,capacity)),t.unit,t.metric);
    return points.filter(p=>C.finite(p.t)).map(p=>{
      const v=convert(p.v),a=convert(p.min??p.v),b=convert(p.max??p.v);
      return {...p,v,min:C.finite(a)&&C.finite(b)?Math.min(a,b):v,max:C.finite(a)&&C.finite(b)?Math.max(a,b):v};
    }).sort((a,b)=>a.t-b.t);
  }
  class WattTileDetail{
    constructor(dashboard){
      this.d=dashboard;this.a=dashboard.a;this.active=false;this.pointers=new Map();
      this.root=document.createElement('section');this.root.id='tileDetail';this.root.className='tile-detail';this.root.hidden=true;
      this.root.setAttribute('role','dialog');this.root.setAttribute('aria-modal','true');this.root.setAttribute('aria-labelledby','tileDetailTitle');document.body.append(this.root);
      this.observer=new ResizeObserver(()=>{if(this.active)this.draw();});this.observer.observe(this.root);
      document.addEventListener('keydown',e=>{
        if(!this.active||this.a.isBlocked?.())return;
        if(e.key==='Escape'){e.preventDefault();this.close();}
        if(e.key==='Tab'){
          const nodes=[...this.root.querySelectorAll('button,input,select,summary,[tabindex="0"]')].filter(n=>!n.disabled&&n.getClientRects().length);
          const first=nodes[0],last=nodes.at(-1);
          if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}
          else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}
        }
      });
    }
    open(id){
      const source=this.d.config.tiles.find(t=>t.id===id);if(!source)return;
      this.close();this.tile=copy(source);this.tiles=new Map([[source.metric,this.tile]]);this.selected=new Set([source.metric]);
      this.period=copy(source.period);if(this.period.mode==='live')this.period.mode='last';
      this.selectedTime=null;this.scaleMode='auto';this.manual=C.fixedRange(this.tile);this.valueMode=C.usesLiveValue(source)?'live':source.period.aggregate;
      this.active=true;this.previousOverflow=document.body.style.overflow;document.body.style.overflow='hidden';this.root.hidden=false;this.pointers.clear();
      this.root.innerHTML=`<div class="detail-top"><div><h2 id="tileDetailTitle">${esc(label(source))}</h2><span>Änderungen gelten nur in dieser Großansicht.</span></div><button id="detailClose" class="iconbutton" aria-label="Großansicht schließen">×</button></div><div class="detail-content"><div class="detail-toolbar"><label>Zeitraum<select id="detailPeriod">${[['last','Letzte …'],['today','Heute'],['yesterday','Gestern'],['week','Woche'],['month','Monat'],['sunrise','Seit Sonnenaufgang'],['reset','Seit Zurücksetzen'],['custom','Von / bis']].map(([v,n])=>`<option value="${v}">${n}</option>`).join('')}</select></label><div class="detail-zoom"><button id="detailZoomIn" aria-label="Vergrößern">+</button><button id="detailZoomOut" aria-label="Verkleinern">−</button><button id="detailReset">Gesamter Zeitraum</button></div></div><div id="detailLast" class="detail-period-fields"><label>Anzahl<input id="detailAmount" type="number" min="1" max="10000"></label><label>Einheit<select id="detailTimeUnit"><option value="min">Minuten</option><option value="h">Stunden</option><option value="d">Tage</option></select></label></div><div id="detailCustom" class="detail-period-fields"><label>Von<input id="detailFrom" type="datetime-local"></label><label>Bis<input id="detailTo" type="datetime-local"></label><button id="detailApplyPeriod" class="secondary">Anzeigen</button></div><p id="detailError" class="form-error" hidden></p><details id="detailSettings" class="detail-options"><summary>Ansicht einstellen</summary><div class="form-grid"><label>Darstellung<select id="detailType"><option value="number">Zahlenwert</option><option value="gauge">Rundinstrument</option><option value="chart">Verlauf</option></select></label><label>Einheit<select id="detailUnit">${C.CATALOG[source.metric].units.map(u=>`<option>${esc(u)}</option>`).join('')}</select></label><label>Große Zahl<select id="detailValue">${[['live','Aktuell'],['last','Letzter Wert'],['avg','Durchschnitt'],['min','Minimum'],['max','Maximum']].filter(([v])=>!C.CATALOG[source.metric].energy||v==='last').map(([v,n])=>`<option value="${v}">${n}</option>`).join('')}</select></label><label>Skala<select id="detailScale"><option value="auto">Automatisch</option><option value="fixed">Feste Grenzen</option></select></label><label>Minimum<input id="detailMin" type="number" step="any"></label><label>Maximum<input id="detailMax" type="number" step="any"></label><label>Nachkommastellen<select id="detailDigits"><option value="0">Keine</option><option value="1">Eine</option><option value="2">Zwei</option><option value="3">Drei</option></select></label><label>Farbe<input id="detailColor" type="color"></label></div>${C.CATALOG[source.metric].energy&&!C.CATALOG[source.metric].flow?'<label class="detail-check"><input id="detailReverse" type="checkbox"> Vorzeichen umkehren (+/−)</label>':''}</details><details id="detailComparisons" class="detail-options"><summary>Vergleichswerte <span id="detailCompareCount"></span></summary><div id="detailCompareOptions" class="detail-compare-options"></div><p>Werte mit gleicher Einheit liegen übereinander. Andere Einheiten erhalten eigene Skalen mit gemeinsamer Zeitachse.</p></details><div class="detail-main"><div id="detailReading" class="detail-reading"></div><div class="detail-plot"><p id="detailWindow" class="detail-window"></p><div id="detailLegend" class="detail-legend"></div><canvas id="detailCanvas" aria-label="Verlauf; antippen oder gedrückt halten und Messpunktauswahl ziehen; mit zwei Fingern zoomen" tabindex="0"></canvas><button id="detailClearSelection" class="textbutton" hidden>Auswahl aufheben</button><div id="detailTooltip" class="detail-tooltip" hidden></div><p class="detail-note">Antippen oder gedrückt halten wählt einen Messpunkt. Die Auswahl lässt sich ziehen. Nach „Auswahl aufheben“ verschiebt ein Finger den Zeitraum; zwei Finger zoomen.</p><p id="detailNote" class="detail-note"></p></div></div><div id="detailWeather" hidden></div></div>`;
      $('detailClose').onclick=()=>this.close();$('detailPeriod').value=this.period.mode;
      $('detailAmount').value=this.period.amount;$('detailTimeUnit').value=this.period.unit;
      $('detailFrom').value=local(this.period.from||Date.now()-86400000);$('detailTo').value=local(this.period.to||Date.now());
      $('detailType').value=source.type==='weather'?'number':source.type;$('detailUnit').value=source.unit;$('detailValue').value=this.valueMode;
      $('detailDigits').value=source.digits;$('detailColor').value=source.color;$('detailMin').value=this.manual.min;$('detailMax').value=this.manual.max;
      if($('detailReverse')){$('detailReverse').checked=source.reverseSign;$('detailReverse').onchange=()=>{this.tile.reverseSign=$('detailReverse').checked;this.refresh(false);};}
      $('detailPeriod').onchange=()=>{this.period.mode=$('detailPeriod').value;this.changePeriod();};
      for(const name of ['detailAmount','detailTimeUnit'])$(name).onchange=()=>{this.period.amount=Number($('detailAmount').value);this.period.unit=$('detailTimeUnit').value;this.changePeriod();};
      $('detailApplyPeriod').onclick=()=>{this.period.from=new Date($('detailFrom').value).getTime();this.period.to=new Date($('detailTo').value).getTime();this.changePeriod();};
      for(const name of ['detailType','detailValue','detailDigits','detailColor'])$(name).onchange=()=>{
        this.tile.type=$('detailType').value;this.tile.digits=Number($('detailDigits').value);this.tile.color=$('detailColor').value;this.valueMode=$('detailValue').value;this.refresh(false);
      };
      $('detailUnit').onchange=()=>{
        const before=this.tile.unit,next=$('detailUnit').value;
        this.manual={min:C.convert(C.unconvert(this.manual.min,before,this.tile.metric),next,this.tile.metric),max:C.convert(C.unconvert(this.manual.max,before,this.tile.metric),next,this.tile.metric)};
        this.tile.unit=next;$('detailMin').value=this.manual.min;$('detailMax').value=this.manual.max;this.renderComparisons();this.refresh(false);
      };
      for(const name of ['detailScale','detailMin','detailMax'])$(name).onchange=()=>{
        const min=Number($('detailMin').value),max=Number($('detailMax').value);
        if(!Number.isFinite(min)||!Number.isFinite(max)||min>=max){this.error('Das Maximum muss größer als das Minimum sein.');return;}
        this.error('');this.scaleMode=$('detailScale').value;this.manual={min,max};this.scaleControls();this.draw();
      };
      $('detailZoomIn').onclick=()=>this.zoom(.5);$('detailZoomOut').onclick=()=>this.zoom(2);$('detailReset').onclick=()=>{this.view={from:this.base.from,to:this.base.to};this.draw();};
      $('detailClearSelection').onclick=()=>{this.selectedTime=null;this.draw();};
      this.renderComparisons();this.scaleControls();this.bindGestures();this.changePeriod();
      if(C.CATALOG[source.metric].widget)this.weather();
      this.timer=setInterval(()=>this.refresh(true),5000);$('detailClose').focus();
    }
    close(){
      if(!this.active)return;this.active=false;clearTimeout(this.pickTimer);clearInterval(this.timer);this.root.hidden=true;this.root.innerHTML='';document.body.style.overflow=this.previousOverflow||'';this.pointers.clear();
      this.d.elements.get(this.tile.id)?.focus({preventScroll:true});
    }
    error(message){$('detailError').textContent=message;$('detailError').hidden=!message;}
    scaleControls(){const auto=this.scaleMode==='auto';$('detailMin').disabled=auto;$('detailMax').disabled=auto;}
    changePeriod(){
      $('detailLast').hidden=this.period.mode!=='last';$('detailCustom').hidden=this.period.mode!=='custom';
      const window=periodWindow(this.period,this.a.getSnapshot().weather||{});
      if(!window||this.period.mode==='custom'&&window.to<=window.from){this.error('Bitte einen gültigen Zeitraum wählen.');return;}
      if(this.period.mode==='last'&&(!Number.isFinite(this.period.amount)||this.period.amount<1||this.period.amount>10000)){this.error('Bitte eine Anzahl zwischen 1 und 10.000 wählen.');return;}
      this.selectedTime=null;this.error('');this.base=window;this.view={from:window.from,to:window.to};this.refresh(false);
    }
    renderComparisons(){
      const options=$('detailCompareOptions');
      options.innerHTML=numericMetrics().filter(([metric])=>metric!==this.tile.metric).map(([metric,m])=>{
        const t=this.tiles.get(metric)||C.tile(metric);this.tiles.set(metric,t);
        return `<div><label class="detail-check"><input type="checkbox" data-compare="${metric}" ${this.selected.has(metric)?'checked':''}>${esc(m.label)}</label><select data-compare-unit="${metric}" aria-label="Einheit für ${esc(m.label)}">${m.units.map(u=>`<option ${t.unit===u?'selected':''}>${esc(u)}</option>`).join('')}</select></div>`;
      }).join('');
      options.querySelectorAll('[data-compare]').forEach(input=>input.onchange=()=>{if(input.checked)this.selected.add(input.dataset.compare);else this.selected.delete(input.dataset.compare);this.refresh(false);});
      options.querySelectorAll('[data-compare-unit]').forEach(input=>input.onchange=()=>{this.tiles.get(input.dataset.compareUnit).unit=input.value;this.refresh(false);});
    }
    refresh(follow=false){
      if(!this.active||this.a.isBlocked?.())return;
      if(C.CATALOG[this.tile.metric].widget){this.weather();return;}
      if(follow&&this.period.mode!=='custom'&&this.period.mode!=='yesterday'){
        const next=periodWindow(this.period,this.a.getSnapshot().weather||{});
        if(next){const all=this.view.from===this.base.from&&this.view.to===this.base.to;this.base=next;this.view=all?{from:next.from,to:next.to}:clampView(this.view,next);}
      }
      const snapshot=this.a.getSnapshot();this.series=[];
      for(const metric of this.selected){
        const t=this.tiles.get(metric),m=C.CATALOG[metric];
        const sun=C.miniWindow('sunrise',snapshot.weather||{}),daily=['today','sunrise'].includes(this.period.mode);
        const query=this.period.mode==='sunrise'&&(t.metric==='solar'||m.flow)?C.solarQueryWindow(this.base):this.base;
        const data=m.status||m.liveOnly?{points:[],summary:{}}:this.d.data(t,query,daily?Math.max(query.from,sun.solarFrom??query.from):query.from,daily?sun.solarTo??query.to:query.to);
        this.series.push({tile:t,data,points:seriesPoints(t,data.points||[],snapshot.settings.capacity||100)});
      }
      const primary=this.series[0],m=C.CATALOG[this.tile.metric];
      if(this.period.mode==='sunrise'&&(this.tile.metric==='solar'||m.flow==='solar')){const full=this.view.from===this.base.from&&this.view.to===this.base.to;this.base=C.solarPlotWindow(this.base,primary.data.points||[],m.flow==='solar'?primary.data.summary:null);if(full)this.view={from:this.base.from,to:this.base.to};}
      let value=this.valueMode==='live'?C.signedValue(this.tile,C.liveValue(this.tile.metric,this.a.reading('shunt'),this.a.reading('solar'),snapshot)):C.signedValue(this.tile,C.historicalValue(this.tile.metric,primary.data.summary?.[this.valueMode],snapshot.settings.capacity||100));
      value=C.convert(value,this.tile.unit,this.tile.metric);
      const color=this.d.paint(this.tile.color,this.tile,true),reading=$('detailReading');reading.style.setProperty('--tile-color',color);reading.style.setProperty('--number-size','42px');
      const number=`<strong class="detail-number">${esc(C.format(value,this.tile))}</strong><span>${esc(m.status?'':this.tile.unit)}</span>`;
      this.value=value;reading.innerHTML=this.tile.type==='gauge'?this.gauge():`<div class="detail-number-row">${number}</div>`;
      reading.insertAdjacentHTML('beforeend',`<p>${esc({live:'Aktuell',last:'Letzter Wert',avg:'Durchschnitt',min:'Minimum',max:'Maximum'}[this.valueMode])}</p>`);
      $('detailCompareCount').textContent=this.selected.size>1?'('+String(this.selected.size-1)+')':'';
      $('detailLegend').innerHTML=this.series.map((s,i)=>`<span><i style="background:${this.color(s,i)}"></i>${esc(label(s.tile))} · ${esc(s.tile.unit)}</span>`).join('');
      const notes=[this.base.note||'',m.hint||''];
      if(m.energy)notes.push(Math.floor((primary.data.summary?.coverage||0)*100)+' % des Zeitraums erfasst.');
      if(m.liveOnly||m.status)notes.push('Für diesen Wert steht kein gespeicherter Verlauf zur Verfügung.');
      $('detailNote').textContent=notes.filter(Boolean).join(' ');this.draw();
    }
    gauge(){
      const points=this.series[0].data.points?.filter(p=>p.t>=this.view.from&&p.t<=this.view.to)||[];
      const range=this.scaleMode==='auto'?C.chartRange({...this.tile,scaleMode:'auto'},points,this.a.getSnapshot().settings.capacity||100):this.manual;
      const tile={...this.tile,reverseSign:false,min:C.unconvert(range.min,this.tile.unit,this.tile.metric),max:C.unconvert(range.max,this.tile.unit,this.tile.metric)};
      return this.d.gauge(tile,this.value,'');
    }
    weather(){
      const holder=$('detailWeather');if(!holder)return;holder.hidden=false;holder.innerHTML='';
      const clone=this.d.weatherContent.cloneNode(true);clone.removeAttribute('id');clone.hidden=false;
      clone.querySelectorAll('[id]').forEach(n=>n.removeAttribute('id'));clone.querySelector('button')?.addEventListener('click',()=>this.d.weatherContent.querySelector('button')?.click());holder.append(clone);
      this.root.querySelector('.detail-main').hidden=true;this.root.querySelector('.detail-toolbar').hidden=true;
      $('detailLast').hidden=true;$('detailCustom').hidden=true;$('detailSettings').hidden=true;$('detailComparisons').hidden=true;
    }
    color(s,i){
      const palette=['#65cbb0','#efb468','#81baf4','#bc92e8','#ed86b0','#6ccfd9','#d5c46d'];
      return i===0?this.d.paint(s.tile.color,s.tile):this.d.paint(palette[i%palette.length],s.tile);
    }
    themeChanged(){if(!this.active)return;const key=document.documentElement.dataset.theme+':'+document.documentElement.dataset.palette;if(this.themeKey!==key){this.themeKey=key;this.refresh(false);}}
    draw(){
      if(!this.active||!this.series||C.CATALOG[this.tile.metric].widget)return;
      const canvas=$('detailCanvas'),groups=new Map();
      this.series.forEach((s,i)=>{const unit=s.tile.unit;if(!groups.has(unit))groups.set(unit,[]);groups.get(unit).push({...s,color:this.color(s,i)});});
      canvas.style.height=groups.size>1?(groups.size*145+35)+'px':'clamp(210px,52vh,440px)';
      const rect=canvas.getBoundingClientRect(),w=Math.max(1,rect.width),h=Math.max(1,rect.height),ratio=window.devicePixelRatio||1;
      canvas.width=Math.round(w*ratio);canvas.height=Math.round(h*ratio);const ctx=canvas.getContext('2d');ctx.scale(ratio,ratio);
      const css=getComputedStyle(document.documentElement),muted=css.getPropertyValue('--muted').trim(),border=css.getPropertyValue('--border').trim();
      const left=48,right=13,bottom=28,pane=(h-bottom)/Math.max(1,groups.size),span=Math.max(1,this.view.to-this.view.from),plot=Math.max(1,w-left-right),x=t=>left+(t-this.view.from)/span*plot;
      this.plot={left,right,plot,w};canvas.dataset.from=this.view.from;canvas.dataset.to=this.view.to;canvas.dataset.units=[...groups.keys()].join(',');
      if(this.selectedTime!=null&&(this.selectedTime<this.view.from||this.selectedTime>this.view.to))this.selectedTime=null;
      const selection=this.selection();
      canvas.dataset.selectedTime=this.selectedTime??'';canvas.dataset.selectedPoints=selection.filter(s=>s.point).length;
      ctx.font='10px sans-serif';let groupIndex=0,hasValues=false;
      for(const [unit,series] of groups){
        const top=groupIndex*pane+20,height=Math.max(30,pane-29);
        const values=series.flatMap(s=>s.points.filter(p=>p.t>=this.view.from&&p.t<=this.view.to&&C.finite(p.v)).flatMap(p=>[p.min,p.max,p.v])).filter(C.finite);
        let low=values.length?Math.min(...values):0,high=values.length?Math.max(...values):1;
        const zero=series.some(s=>['power','current','solar','solarCurrent','solarEnergy','consumptionEnergy','dischargeEnergy','energyBalance','batteryEnergy','remainingAh','remainingWh','yieldWh','consumed'].includes(s.tile.metric));
        if(zero){low=Math.min(0,low);high=Math.max(0,high);}const padding=(high-low||Math.max(Math.abs(high)*.02,.1))*.1;low-=padding;high+=padding;
        if(low>=-padding&&zero)low=0;
        if(this.scaleMode==='fixed'&&unit===this.tile.unit){low=this.manual.min;high=this.manual.max;}
        if(high<=low)high=low+1;
        const y=v=>top+(high-v)/(high-low)*height;
        ctx.fillStyle=muted;ctx.textAlign='left';ctx.fillText(unit,left,top-7);ctx.lineWidth=1;
        for(let i=0;i<4;i++){const value=high-(high-low)*i/3,py=top+height*i/3;ctx.strokeStyle=border;ctx.beginPath();ctx.moveTo(left,py);ctx.lineTo(w-right,py);ctx.stroke();ctx.fillStyle=muted;ctx.textAlign='right';ctx.fillText(C.axisLabel(value,high-low),left-7,py+3);}
        ctx.save();ctx.beginPath();ctx.rect(left,top,plot,height);ctx.clip();
        for(const s of series){
          const points=s.points.filter(p=>p.t>=this.view.from&&p.t<=this.view.to);let last=null;
          ctx.strokeStyle=s.color;ctx.lineWidth=2.1;ctx.setLineDash(s.tile.metric==='energyBalance'?[6,3]:[]);ctx.beginPath();
          for(const p of points){
            if(!C.finite(p.v)){last=null;continue;}hasValues=true;
            const limit=Math.max(this.a.isDemo()?600001:45000,(p.period||0)*1500,(this.base.to-this.base.from)/600*3);
            const gap=!last||p.gap||!C.CATALOG[s.tile.metric].energy&&(last.device!==p.device||last.origin!==p.origin||p.t-(last.last||last.t)>limit);
            if(gap)ctx.moveTo(x(p.t),y(p.v));else ctx.lineTo(x(p.t),y(p.v));last=p;
          }
          ctx.stroke();ctx.setLineDash([]);
          for(const p of points.filter(p=>C.finite(p.v))){if(points.length===1){ctx.fillStyle=s.color;ctx.beginPath();ctx.arc(x(p.t),y(p.v),3,0,Math.PI*2);ctx.fill();}}
        }
        if(this.selectedTime!=null){
          const px=x(this.selectedTime);ctx.strokeStyle=css.getPropertyValue('--text').trim();ctx.globalAlpha=.65;ctx.lineWidth=1.25;ctx.setLineDash([]);ctx.beginPath();ctx.moveTo(px,top);ctx.lineTo(px,top+height);ctx.stroke();ctx.globalAlpha=1;
          for(const selected of selection){
            if(!selected.point||!series.some(s=>s.tile.metric===selected.series.tile.metric))continue;
            const p=selected.point,color=this.color(selected.series,selected.index);ctx.beginPath();ctx.arc(x(p.t),y(p.v),5,0,Math.PI*2);ctx.fillStyle=color;ctx.fill();ctx.strokeStyle=css.getPropertyValue('--panel').trim();ctx.lineWidth=2;ctx.stroke();
          }
        }
        ctx.restore();groupIndex++;
      }
      ctx.fillStyle=muted;
      for(let i=0;i<4;i++){const t=this.view.from+span*i/3;ctx.textAlign=i===0?'left':i===3?'right':'center';ctx.fillText(span>172800000?new Date(t).toLocaleDateString('de-DE',{day:'2-digit',month:'2-digit'}):new Date(t).toLocaleTimeString('de-DE',{hour:'2-digit',minute:'2-digit'}),x(t),h-7);}
      if(!hasValues){ctx.textAlign='center';ctx.fillText('Keine gespeicherten Werte im sichtbaren Zeitraum.',left+plot/2,h/2);}
      $('detailWindow').textContent=stamp(this.view.from)+' – '+stamp(this.view.to);
      $('detailTooltip').hidden=this.selectedTime==null;$('detailClearSelection').hidden=this.selectedTime==null;
      if(this.selectedTime!=null)$('detailTooltip').innerHTML=selection.map(({series:s,point:p})=>esc(label(s.tile))+': '+(p?esc(C.format(p.v,s.tile))+' '+esc(s.tile.unit)+' · '+esc(stamp(p.t)):'—')).join('<br>');
      if(this.tile.type==='gauge')$('detailReading').querySelector('.tile-gauge').outerHTML=this.gauge();
    }
    zoom(factor,anchor=.5){if(!this.base)return;this.view=zoomView(this.view,this.base,factor,anchor);this.draw();}
    selection(){
      if(this.selectedTime==null)return [];
      const tolerance=Math.max((this.view.to-this.view.from)/30,10000);
      return this.series.map((series,index)=>{
        const point=series.points.filter(p=>C.finite(p.v)&&p.t>=this.view.from&&p.t<=this.view.to).reduce((a,p)=>!a||Math.abs(p.t-this.selectedTime)<Math.abs(a.t-this.selectedTime)?p:a,null);
        return {series,index,point:point&&Math.abs(point.t-this.selectedTime)<=tolerance?point:null};
      });
    }
    pick(clientX){
      if(!this.plot)return;
      const rect=$('detailCanvas').getBoundingClientRect(),fraction=C.clamp((clientX-rect.left-this.plot.left)/this.plot.plot,0,1),time=this.view.from+fraction*(this.view.to-this.view.from);
      this.selectedTime=time;
      const nearest=this.selection().find(s=>s.point)?.point;if(nearest)this.selectedTime=nearest.t;
      this.draw();
    }
    bindGestures(){
      const canvas=$('detailCanvas');
      const start=()=>{const points=[...this.pointers.values()];this.gesture={view:{...this.view},points:copy(points),distance:points.length>1?Math.hypot(points[1].x-points[0].x,points[1].y-points[0].y):0,moved:false};};
      canvas.onpointerdown=e=>{
        clearTimeout(this.pickTimer);this.pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});canvas.setPointerCapture(e.pointerId);start();
        this.gesture.selecting=this.pointers.size===1&&this.selectedTime!=null;
        if(this.pointers.size>1){this.selectedTime=null;this.draw();}
        else if(this.gesture.selecting)this.pick(e.clientX);
        else this.pickTimer=setTimeout(()=>{if(this.gesture&&!this.gesture.moved&&this.pointers.size===1){this.gesture.selecting=true;this.pick(e.clientX);}},350);
      };
      canvas.onpointermove=e=>{
        if(!this.pointers.has(e.pointerId)||!this.gesture||!this.plot)return;e.preventDefault();this.pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});const points=[...this.pointers.values()],g=this.gesture;
        if(points.length===1&&g.selecting){this.pick(points[0].x);return;}
        if(points.length===1){const dx=points[0].x-g.points[0].x;if(Math.abs(dx)>3){g.moved=true;clearTimeout(this.pickTimer);}this.view=panView(g.view,this.base,-dx/this.plot.plot*(g.view.to-g.view.from));}
        else if(g.points.length>1){
          const distance=Math.max(1,Math.hypot(points[1].x-points[0].x,points[1].y-points[0].y)),center=(g.points[0].x+g.points[1].x)/2,current=(points[0].x+points[1].x)/2;
          const anchor=C.clamp((center-canvas.getBoundingClientRect().left-this.plot.left)/this.plot.plot,0,1),zoomed=zoomView(g.view,this.base,g.distance/distance,anchor);
          this.view=panView(zoomed,this.base,-(current-center)/this.plot.plot*(zoomed.to-zoomed.from));g.moved=true;
        }this.draw();
      };
      const end=e=>{clearTimeout(this.pickTimer);if(!this.pointers.has(e.pointerId))return;const tap=this.pointers.size===1&&!this.gesture?.moved;this.pointers.delete(e.pointerId);if((tap||this.gesture?.selecting)&&e.type!=='pointercancel')this.pick(e.clientX);if(this.pointers.size)start();else this.gesture=null;};
      canvas.onpointerup=end;canvas.onpointercancel=end;
      canvas.addEventListener('wheel',e=>{e.preventDefault();const rect=canvas.getBoundingClientRect();this.zoom(Math.exp(C.clamp(e.deltaY,-400,400)*.003),C.clamp((e.clientX-rect.left-this.plot.left)/this.plot.plot,0,1));},{passive:false});
    }
  }
  return {periodWindow,clampView,zoomView,panView,seriesPoints,WattTileDetail};
});
