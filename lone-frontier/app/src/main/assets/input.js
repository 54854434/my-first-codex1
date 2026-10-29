/* Joystick vector response adapted from ricardo-foundry/canvas-vampire-survivors
 * src/input.js, MIT, Copyright (c) 2024 Survivor Game.
 * Reworked with pointer IDs/capture for simultaneous movement and skill touches.
 * See licenses/survivor-MIT.txt. */
(function(root){
class FieldInput{
 constructor(base,knob){this.keys={};this.touch={x:0,y:0};this.pointer=null;this.base=base;this.knob=knob;
 const reset=()=>{this.pointer=null;this.touch={x:0,y:0};knob.style.transform='translate(-50%,-50%)';};this.reset=()=>{this.keys={};reset();};
 base.addEventListener('pointerdown',e=>{if(this.pointer!==null)return;this.pointer=e.pointerId;base.setPointerCapture&&base.setPointerCapture(e.pointerId);this.update(e);e.preventDefault();});
 base.addEventListener('pointermove',e=>{if(e.pointerId===this.pointer){this.update(e);e.preventDefault();}});
 for(const name of ['pointerup','pointercancel','lostpointercapture'])base.addEventListener(name,e=>{if(e.pointerId===this.pointer)reset();});
 window.addEventListener('keydown',e=>{this.keys[e.key.toLowerCase()]=true;if(['ArrowUp','ArrowDown','ArrowLeft','ArrowRight',' '].includes(e.key))e.preventDefault();});
 window.addEventListener('keyup',e=>{this.keys[e.key.toLowerCase()]=false;});window.addEventListener('blur',this.reset);
 }
 update(e){const r=this.base.getBoundingClientRect(),cx=r.left+r.width/2,cy=r.top+r.height/2,maxR=r.width*.34,dx=e.clientX-cx,dy=e.clientY-cy,d=Math.hypot(dx,dy),length=Math.min(d,maxR),mag=length/maxR;
 this.knob.style.transform='translate(calc(-50% + '+(d?dx/d*length:0)+'px),calc(-50% + '+(d?dy/d*length:0)+'px))';
 if(mag<.15){this.touch={x:0,y:0};return;}const scaled=Math.pow((mag-.15)/.85,1.3);this.touch={x:dx/(d||1)*scaled,y:dy/(d||1)*scaled};}
 vector(){let x=0,y=0,k=this.keys;if(k.a||k.arrowleft)x--;if(k.d||k.arrowright)x++;if(k.w||k.arrowup)y--;if(k.s||k.arrowdown)y++;if(!x&&!y)return this.touch;const n=Math.hypot(x,y);return{x:x/n,y:y/n};}
}
root.FieldInput=FieldInput;
})(typeof globalThis!=='undefined'?globalThis:this);
