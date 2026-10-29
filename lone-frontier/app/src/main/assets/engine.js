/* Lone Frontier — original game systems. MIT. */
(function(root){
'use strict';
const MAX=1e140, cap=n=>Math.min(MAX,Math.max(0,Number.isFinite(n)?n:MAX));
const UPGRADES=[
 {id:'damage',name:'制式步枪',sub:'全队伤害 ×1.23',tab:'arms',base:24,growth:1.24,unlock:1,icon:'rifle'},
 {id:'rate',name:'快速装填',sub:'全队射速 +7%',tab:'arms',base:42,growth:1.3,unlock:1,icon:'ammo'},
 {id:'armor',name:'复合防线',sub:'防线耐久 ×1.20 · 补充耐久',tab:'arms',base:36,growth:1.25,unlock:1,icon:'shield'},
 {id:'income',name:'战地后勤',sub:'击杀与通关补给 +18%',tab:'arms',base:55,growth:1.32,unlock:2,icon:'box'},
 {id:'infantry',name:'增援步兵',sub:'增加 1 名自动射击步兵',tab:'squad',base:85,growth:1.68,unlock:2,icon:'soldier',max:8},
 {id:'mg',name:'机枪小组',sub:'压制火力 · 单位秒伤 24',tab:'squad',base:260,growth:1.85,unlock:4,icon:'rifle',max:4},
 {id:'sniper',name:'狙击小组',sub:'优先猎杀重甲 · 单位秒伤 38',tab:'squad',base:620,growth:1.9,unlock:7,icon:'cross',max:3},
 {id:'mortar',name:'迫击炮组',sub:'范围轰炸 · 单位秒伤 56',tab:'squad',base:1400,growth:2,unlock:10,icon:'mortar',max:3},
 {id:'tank',name:'主战坦克',sub:'重型范围火力 · 单位秒伤 110',tab:'squad',base:3600,growth:2.2,unlock:15,icon:'tank',max:2},
 {id:'drone',name:'武装无人机',sub:'快速精确打击 · 单位秒伤 170',tab:'squad',base:8800,growth:2.3,unlock:22,icon:'drone',max:2},
 {id:'veteran',name:'精锐训练',sub:'永久：全队伤害 +20%',tab:'tech',base:1,growth:1.35,unlock:0,permanent:true,max:50},
 {id:'fortress',name:'工程学',sub:'永久：防线耐久 +25%',tab:'tech',base:1,growth:1.35,unlock:0,permanent:true,max:50},
 {id:'logistics',name:'补给网络',sub:'永久：补给收益 +20%',tab:'tech',base:1,growth:1.35,unlock:0,permanent:true,max:50}
];
const ENEMIES={
 rifle:{name:'突击步兵',hp:30,speed:20,damage:6,reward:7,radius:9},
 runner:{name:'突击尖兵',hp:21,speed:35,damage:5,reward:8,radius:8},
 heavy:{name:'重甲步兵',hp:90,speed:13,damage:12,reward:15,radius:11},
 apc:{name:'装甲车',hp:210,speed:17,damage:21,reward:32,radius:17},
 tank:{name:'敌方坦克',hp:480,speed:10,damage:35,reward:55,radius:21},
 boss:{name:'指挥战车',hp:700,speed:7,damage:45,reward:125,radius:26}
};
function blank(){return {version:1,credits:35,medals:0,meta:{veteran:0,fortress:0,logistics:0},levels:UPGRADES.filter(u=>!u.permanent).reduce((o,u)=>(o[u.id]=0,o),{}),wave:1,runBest:0,best:0,kills:0,totalKills:0,totalRuns:0,health:160,phase:'ready',timer:0,spawnTimer:0,spawned:0,total:0,enemies:[],cooldowns:{grenade:0,air:0,repair:0,focus:0},autonext:true,autoBuy:false,speed:1,seed:75321,elapsed:0,welcomed:false,sound:true,reduced:false,savedAt:Date.now()};}
class Engine{
 constructor(saved){this.s=blank(); this.events=[]; this.shots=[]; this.shootTimer={};this.uid=1;
 if(saved&&saved.version===1){
   const d=this.s; this.s={...d,...saved,meta:{...d.meta,...saved.meta},levels:{...d.levels,...saved.levels},cooldowns:{...d.cooldowns,...saved.cooldowns}};
   this.s.enemies=Array.isArray(saved.enemies)?saved.enemies.filter(e=>e.hp>0):[];
   this.uid=1+Math.max(0,...this.s.enemies.map(e=>e.id||0));
   this.s.health=Math.min(this.s.health,this.maxHealth());
 }
 this.buyClock=0;
 }
 rand(){this.s.seed=(Math.imul(1664525,this.s.seed)+1013904223)>>>0;return this.s.seed/4294967296;}
 emit(type,data={}){this.events.push({type,...data});if(this.events.length>160)this.events.shift();}
 drain(){return this.events.splice(0);}
 damageFactor(){return cap(Math.pow(1.23,this.s.levels.damage)*(1+.2*this.s.meta.veteran));}
 rateFactor(){return 1+.07*this.s.levels.rate;}
 incomeFactor(){return cap((1+.18*this.s.levels.income)*(1+.2*this.s.meta.logistics));}
 maxHealth(){return cap(160*Math.pow(1.2,this.s.levels.armor)*(1+.25*this.s.meta.fortress));}
 roster(){const l=this.s.levels;return [{kind:'infantry',count:1+l.infantry,dps:10,interval:.8},{kind:'mg',count:l.mg,dps:24,interval:.16},{kind:'sniper',count:l.sniper,dps:38,interval:1.7},{kind:'mortar',count:l.mortar,dps:56,interval:2.2},{kind:'tank',count:l.tank,dps:110,interval:1.4},{kind:'drone',count:l.drone,dps:170,interval:.38}];}
 dps(){return cap(this.roster().reduce((a,u)=>a+u.count*u.dps,0)*this.damageFactor()*this.rateFactor());}
 cost(id){const u=UPGRADES.find(u=>u.id===id);if(!u)return MAX;const l=u.permanent?this.s.meta[id]:this.s.levels[id];return Math.ceil(cap(u.base*Math.pow(u.growth,l)));}
 unlocked(u){return u.permanent||this.s.runBest>=u.unlock-1;}
 buy(id){const u=UPGRADES.find(u=>u.id===id);if(!u||!this.unlocked(u))return false;let bag=u.permanent?this.s.meta:this.s.levels; if(bag[id]>=(u.max||1000))return false;
 const c=this.cost(id),currency=u.permanent?'medals':'credits';if(this.s[currency]<c)return false;
 const before=this.maxHealth();this.s[currency]-=c;bag[id]++;if(id==='armor'||id==='fortress')this.s.health=cap(this.s.health+this.maxHealth()-before);
 this.emit('buy',{id});return true;}
 waveConfig(w=this.s.wave){return {total:Math.min(100,5+Math.floor(w*1.35))+(w%5===0?1:0),factor:Math.pow(1.145,Math.min(w-1,1800)),interval:Math.max(.25,1.15-w*.012),boss:w%5===0};}
 startWave(){if(!['ready','break'].includes(this.s.phase))return false;this.s.phase='battle';this.s.timer=0;this.s.spawned=0;this.s.spawnTimer=.25;this.s.enemies=[];this.s.total=this.waveConfig().total;this.emit('wave',{wave:this.s.wave,boss:this.s.wave%5===0});return true;}
 spawn(){let s=this.s,w=s.wave,c=this.waveConfig(),i=s.spawned,type='rifle',r=this.rand();
 if(c.boss&&i===c.total-1)type='boss';else if(w>=18&&r<.11)type='tank';else if(w>=9&&r<.19)type='apc';else if(w>=4&&r<.35)type='heavy';else if(w>=2&&r>.72)type='runner';
 const t=ENEMIES[type],hp=cap(t.hp*c.factor*(type==='boss'?1+w*.025:1));
 s.enemies.push({id:this.uid++,type,x:102+this.rand()*9,y:23+this.rand()*62,hp,max:hp,damage:cap(t.damage*Math.pow(1.095,Math.min(w-1,1800))),speed:t.speed*.055,reward:cap(t.reward*Math.pow(1.095,Math.min(w-1,1800))),attack:1,phase:this.rand()*6.28});s.spawned++;}
 hit(e,damage){if(!e||e.hp<=0)return;e.hp-=damage;if(e.hp<=0){const pay=Math.max(1,Math.floor(cap(e.reward*this.incomeFactor())));this.s.credits=cap(this.s.credits+pay);this.s.kills++;this.s.totalKills++;this.emit('kill',{x:e.x,y:e.y,type:e.type,pay});}}
 shoot(dt){const s=this.s;for(const unit of this.roster()){if(!unit.count)continue;const interval=unit.interval/this.rateFactor();for(let i=0;i<unit.count;i++){const key=unit.kind+i;this.shootTimer[key]=(this.shootTimer[key]||0)-dt;if(this.shootTimer[key]>0)continue;
 let targets=s.enemies.filter(e=>e.hp>0);if(!targets.length){this.shootTimer[key]=0;continue;}
 const rounds=Math.max(1,1+Math.floor(-this.shootTimer[key]/interval));
 targets.sort(unit.kind==='sniper'?(a,b)=>b.max-a.max:(a,b)=>a.x-b.x);let target=targets[0];const damage=cap(unit.dps*unit.interval*this.damageFactor()*rounds);
 const splash=['mortar','tank'].includes(unit.kind);this.emit('shot',{kind:unit.kind,index:i,x:target.x,y:target.y,id:target.id});
 this.hit(target,damage);if(splash)for(const e of targets.slice(1)){if(Math.hypot(e.x-target.x,(e.y-target.y)*.7)<(unit.kind==='mortar'?19:12))this.hit(e,damage*.65);}
 this.shootTimer[key]+=rounds*interval;
 }}}
 ability(id,x=65,y=50){let s=this.s;if(s.phase!=='battle'||s.cooldowns[id]>0)return false;
 if(id==='focus'){s.cooldowns.focus=.22;let e=s.enemies.filter(e=>e.hp>0).sort((a,b)=>Math.hypot(a.x-x,a.y-y)-Math.hypot(b.x-x,b.y-y))[0];if(!e)return false;this.hit(e,cap(this.dps()*.16+6*this.damageFactor()));this.emit('focus',{x:e.x,y:e.y});}
 else if(id==='grenade'){s.cooldowns.grenade=13;const es=s.enemies.filter(e=>e.hp>0).sort((a,b)=>a.x-b.x);if(es.length){let t=es[0];for(const e of es)if(Math.hypot(e.x-t.x,(e.y-t.y)*.7)<25)this.hit(e,cap(this.dps()*3+50*this.damageFactor()));this.emit('blast',{x:t.x,y:t.y,size:25});}}
 else if(id==='air'){if(s.runBest<4)return false;s.cooldowns.air=38;for(const e of s.enemies)this.hit(e,cap(this.dps()*5+100*this.damageFactor()));this.emit('air');}
 else if(id==='repair'){s.cooldowns.repair=32;s.health=Math.min(this.maxHealth(),s.health+this.maxHealth()*.35);this.emit('repair');}
 else return false;return true;}
 update(dt){dt=Math.max(0,Math.min(.1,dt));const s=this.s;s.elapsed+=dt;for(const k in s.cooldowns)s.cooldowns[k]=Math.max(0,s.cooldowns[k]-dt);
 if(s.autoBuy&&s.runBest>=7){this.buyClock+=dt;if(this.buyClock>.75){this.buyClock=0;const choices=UPGRADES.filter(u=>!u.permanent&&this.unlocked(u)&&this.s.levels[u.id]<(u.max||1000)).sort((a,b)=>this.cost(a.id)-this.cost(b.id));if(choices[0])this.buy(choices[0].id);}}
 if(s.phase==='break'){s.timer=Math.max(0,s.timer-dt);if(s.timer===0&&s.autonext)this.startWave();return;}
 if(s.phase!=='battle')return;
 s.spawnTimer-=dt;if(s.spawned<s.total&&s.spawnTimer<=0){this.spawn();s.spawnTimer=this.waveConfig().interval;}
 this.shoot(dt);
 for(const e of s.enemies){if(e.hp<=0)continue;if(e.x>22)e.x=Math.max(22,e.x-e.speed*dt);else{e.attack-=dt;if(e.attack<=0){e.attack=1.3;s.health=Math.max(0,s.health-e.damage);this.emit('hurt',{damage:e.damage});}}}
 s.enemies=s.enemies.filter(e=>e.hp>0);
 if(s.health<=0){s.phase='defeat';this.emit('defeat');return;}
 if(s.spawned>=s.total&&!s.enemies.length){let w=s.wave;s.runBest=Math.max(s.runBest,w);s.best=Math.max(s.best,w);const reward=Math.floor(cap((18+w*7)*Math.pow(1.08,Math.min(w,1800))*this.incomeFactor()));s.credits=cap(s.credits+reward);s.health=Math.min(this.maxHealth(),s.health+this.maxHealth()*.08);s.wave++;s.phase='break';s.timer=6;this.emit('clear',{wave:w,reward});}
 }
 regroup(){if(this.s.phase!=='defeat')return false;this.s.wave=Math.max(1,this.s.wave-2);this.s.health=this.maxHealth();this.s.enemies=[];this.s.phase='ready';this.s.cooldowns={grenade:0,air:0,repair:0,focus:0};this.emit('regroup');return true;}
 prestigeGain(){return this.s.runBest>=10?Math.floor(this.s.runBest/5)+Math.floor(this.s.kills/120):0;}
 prestige(){let gain=this.prestigeGain();if(!gain)return 0;const old=this.s;this.s=blank();Object.assign(this.s,{medals:old.medals+gain,meta:{...old.meta},best:old.best,totalKills:old.totalKills,totalRuns:old.totalRuns+1,welcomed:true,sound:old.sound,reduced:old.reduced,autonext:old.autonext,speed:old.speed});this.s.health=this.maxHealth();this.shootTimer={};this.emit('prestige',{gain});return gain;}
 offline(now=Date.now()){const seconds=Math.min(8*3600,Math.max(0,(now-this.s.savedAt)/1000));this.s.savedAt=now;if(seconds<60||!this.s.welcomed)return 0;const reward=Math.floor(cap(seconds/60*(6+this.s.best*2)*this.incomeFactor()));this.s.credits=cap(this.s.credits+reward);return reward;}
 snapshot(){return JSON.parse(JSON.stringify({...this.s,savedAt:Date.now()}));}
}
const api={Engine,UPGRADES,ENEMIES,blank,cap};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.Frontier=api;
})(typeof globalThis!=='undefined'?globalThis:this);
