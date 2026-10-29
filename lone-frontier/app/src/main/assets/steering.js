/* Adapted from trydionel/uRTS util/math.js (MIT, Jeff Tucker 2014). See licenses/uRTS-MIT.txt. */
(function(root){const math={lerp:function(a,b,t){return (1-t)*a+t*b;},clamp:function(x,l,u){return Math.min(u,Math.max(x,l));}};if(typeof module!=='undefined'&&module.exports)module.exports=math;else root.WarMath=math;})(typeof globalThis!=='undefined'?globalThis:this);

