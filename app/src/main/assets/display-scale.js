/* This preference belongs to this screen, including when it is a LAN viewer. */
(function(){
  'use strict';
  const key='wattpit-display-scale-v1',native=window.WattPit;
  const normalize=x=>Number.isFinite(Number(x))?Math.round(Math.max(50,Math.min(150,Number(x)))):100;
  let saved=100;
  try{saved=normalize(native?.displayScale?native.displayScale():localStorage.getItem(key)??100);}catch(_){}
  function apply(value){
    value=normalize(value);document.documentElement.style.zoom=String(value/100);
    document.documentElement.style.setProperty('--app-scale',String(value/100));
    const input=document.getElementById('displayScale'),output=document.getElementById('displayScaleValue');
    if(input)input.value=value;if(output)output.textContent=value+' %';
    window.dispatchEvent(new Event('resize'));return value;
  }
  window.WattDisplay={normalize,apply,get value(){return normalize(parseFloat(document.documentElement.style.zoom)*100);}};
  apply(saved);
  document.addEventListener('DOMContentLoaded',()=>{
    const input=document.getElementById('displayScale');
    const save=value=>{
      value=normalize(value);let error='';
      try{if(native?.saveDisplayScale)error=native.saveDisplayScale(value);else localStorage.setItem(key,String(value));}catch(_){error='Darstellungsgröße konnte nicht gespeichert werden.';}
      if(error){apply(saved);if(typeof window.toast==='function')window.toast(error);return;}saved=value;apply(saved);
    };
    input.oninput=()=>{document.getElementById('displayScaleValue').textContent=normalize(input.value)+' %';};input.onchange=()=>save(input.value);
    document.getElementById('resetDisplayScale').onclick=()=>save(100);apply(saved);
  });
})();
