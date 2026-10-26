(function(){
 'use strict';
 window.WattPullReload=class {
  constructor(a){this.a=a;this.hint=document.getElementById('pullReloadHint');
   a.element.addEventListener('touchstart',e=>{this.start=null;if(e.touches.length!==1||window.scrollY>1||!a.canStart()||e.target.closest('button,input,select,textarea,canvas,#rollerPanel'))return;this.start={x:e.touches[0].clientX,y:e.touches[0].clientY};this.distance=0;},{passive:true});
   a.element.addEventListener('touchmove',e=>{if(!this.start)return;if(e.touches.length!==1||!a.canStart()||window.scrollY>1){this.clear();return;}const dy=e.touches[0].clientY-this.start.y,dx=e.touches[0].clientX-this.start.x;if(Math.abs(dx)>Math.abs(dy)||dy<0){this.clear();return;}if(dy>12){e.preventDefault();this.distance=dy;this.hint.hidden=false;this.hint.textContent=dy>=80?'Loslassen zum Nachladen':'Zum Nachladen weiter herunterziehen';}},{passive:false});
   a.element.addEventListener('touchend',()=>{const ready=this.start&&this.distance>=80&&a.canStart();this.clear();if(ready)a.reload();},{passive:true});
   a.element.addEventListener('touchcancel',()=>this.clear(),{passive:true});
  }
  clear(){this.start=null;this.distance=0;this.hint.hidden=true;}
 };
})();
