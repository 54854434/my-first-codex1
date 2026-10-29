/* Lone Frontier: Rebirth Campaign — tactical survivor / RTS systems. MIT. */
(function(root){
'use strict';
const Spatial=typeof module!=='undefined'&&module.exports?require('./spatial-hash.js').SpatialHash:root.SpatialHash;
const M=typeof module!=='undefined'&&module.exports?require('./steering.js'):root.WarMath;
const W=2200,H=2200,clamp=M.clamp,dist=(a,b)=>Math.hypot(a.x-b.x,a.y-b.y);
const UNITS={
 infantry:{name:'突击步兵',cost:38,slots:1,hp:80,speed:116,range:255,damage:11,interval:.7,desc:'可靠的机动火力',requires:null},
 machine:{name:'机枪手',cost:95,slots:1,hp:110,speed:98,range:270,damage:7,interval:.19,desc:'高速压制 · 需要兵营',requires:'barracks'},
 medic:{name:'战地军医',cost:80,slots:1,hp:75,speed:112,range:220,damage:5,interval:1,desc:'持续治疗附近友军',requires:null},
 sniper:{name:'狙击手',cost:125,slots:1,hp:65,speed:106,range:400,damage:65,interval:1.7,desc:'优先攻击重甲 · 需要兵营',requires:'barracks'},
 mortar:{name:'迫击炮组',cost:175,slots:2,hp:100,speed:84,range:370,damage:64,interval:2,desc:'范围轰炸 · 需要军工厂',requires:'factory'},
 tank:{name:'主战坦克',cost:320,slots:3,hp:560,speed:87,range:320,damage:92,interval:1.3,desc:'重型装甲 · 需要军工厂',requires:'factory'}
};
const ENEMIES={rifle:{name:'敌军步兵',hp:38,speed:67,damage:8,range:155,interval:1.3,r:10,xp:7,pay:5},runner:{name:'突袭者',hp:26,speed:125,damage:12,range:20,interval:.85,r:9,xp:8,pay:6},heavy:{name:'重甲兵',hp:110,speed:43,damage:13,range:175,interval:1.7,r:13,xp:18,pay:13},apc:{name:'装甲车',hp:270,speed:55,damage:18,range:205,interval:1.5,r:22,xp:35,pay:24},boss:{name:'攻城战车',hp:1400,speed:40,damage:35,range:250,interval:1.5,r:31,xp:120,pay:120}};
const RESEARCH=[
 {id:'damage',name:'武器工程',desc:'下一次出击：全军伤害 +10% / 级',icon:'rifle'},
 {id:'health',name:'基因强化',desc:'下一次出击：指挥官生命 +12% / 级',icon:'heart'},
 {id:'command',name:'军团编制',desc:'下一次出击：军队容量 +1 / 级',icon:'army'},
 {id:'supply',name:'后勤网络',desc:'下一次出击：初始补给 +20，收入 +12% / 级',icon:'box'},
 {id:'regen',name:'再生医疗',desc:'下一次出击：每秒自动恢复 0.12 生命 / 级',icon:'cross'},
 {id:'intel',name:'战场学习',desc:'今后的结算研究点 +12% / 级',icon:'chip'}
];
const BOONS=[
 {id:'damage',name:'穿甲弹药',desc:'全军伤害 +22%',icon:'rifle',max:16},
 {id:'rate',name:'快速装填',desc:'全军射速 +15%',icon:'ammo',max:12},
 {id:'health',name:'战地强化',desc:'最大生命 +25%，立即恢复 35%',icon:'heart',max:12},
 {id:'speed',name:'战术步伐',desc:'移动速度 +10%，闪避冷却减少',icon:'boot',max:8},
 {id:'command',name:'扩充编制',desc:'军队容量 +2，立即获得 40 补给',icon:'army',max:8},
 {id:'magnet',name:'战场回收',desc:'经验吸取范围 +40，补给收益 +10%',icon:'magnet',max:8},
 {id:'grenade',name:'集束手雷',desc:'手雷伤害 +40%，范围 +15%',icon:'grenade',max:8},
 {id:'drone',name:'护卫无人机',desc:'新增 1 架环绕射击无人机',icon:'drone',max:4},
 {id:'mortar',name:'自动火炮',desc:'每 5 秒自动轰炸敌群，叠加提高伤害',icon:'mortar',max:6},
 {id:'shield',name:'电磁护盾',desc:'每 7 秒震退近敌并造成伤害',icon:'shield',max:6},
 {id:'regen',name:'战地急救',desc:'每秒恢复 0.6 生命，军医效果 +20%',icon:'cross',max:8},
 {id:'pierce',name:'贯穿射击',desc:'指挥官子弹多穿透 1 个敌人',icon:'target',max:4}
];
const SITE_LAYOUT=[
 ['supply','鸦巢哨站',650,1100,1,3],['barracks','废弃兵营',850,740,1,4],['medic','医疗营地',390,610,1,2],
 ['rescue','战俘营',390,1580,1,4],['factory','铸铁军工厂',1210,1070,2,5],['supply','南部补给库',850,1720,2,5],
 ['relay','雷达阵地',1300,450,2,5],['factory','装甲集结地',1670,1590,3,6],['oil','黑石油井',1840,1080,3,6],['hq','敌军指挥部',1830,450,4,8]
];
function profile(){return {version:2,intel:0,rebirths:0,sorties:0,totalKills:0,totalCaptured:0,bestSector:1,research:{damage:0,health:0,command:0,supply:0,regen:0,intel:0},settings:{sound:true,reduced:false},run:null,savedAt:Date.now(),welcomed:false};}
class Engine{
 constructor(saved){this.p=profile();this.events=[];this.grid=new Spatial(120);this.friends=new Spatial(150);this.obstacles=new Spatial(120);
 if(saved&&saved.version===2){this.p={...this.p,...saved,research:{...this.p.research,...saved.research},settings:{...this.p.settings,...saved.settings}};}
 else if(saved&&saved.version===1){this.p.intel=Math.max(0,saved.medals||0);this.p.legacy=saved;this.emit('migration');}
 this.r=this.p.run;if(this.r){this.obstacles.insertAll(this.r.obstacles);this.rebuild();}
 }
 emit(type,data={}){this.events.push({type,...data});if(this.events.length>300)this.events.shift();}
 drain(){return this.events.splice(0);}
 rand(){this.r.seed=(Math.imul(this.r.seed,1664525)+1013904223)>>>0;return this.r.seed/4294967296;}
 begin(seed){if(this.r&&!['dead','extracted'].includes(this.r.phase))return false;
 const p=this.p,a=p.research,heritage=1+p.rebirths*.02;p.sorties++;p.welcomed=true;
 const stats={damage:(1+a.damage*.1)*heritage,health:120*(1+a.health*.12)*heritage,slots:7+a.command,income:1+a.supply*.12,regen:a.regen*.12};
 this.r={phase:'active',sector:1,seed:Number.isFinite(seed)?seed>>>0:(Math.floor(Date.now()%4294967295)||54321),uid:1,time:0,sectorTime:0,supply:60+a.supply*20,level:1,xp:0,kills:0,captures:0,claimed:[],incomeClock:0,spawnClock:2.5,raidClock:40,bossClock:110,boons:{},cooldowns:{dash:0,grenade:0,air:0,rally:0,autoMortar:2,shield:3,drone:0},hero:{id:0,type:'commander',x:300,y:1100,hp:stats.health,max:stats.health,r:11,angle:0,shoot:0,invulnerable:1,dash:0,lastX:1,lastY:0},stats,allies:[],enemies:[],bullets:[],loot:[],sites:[],obstacles:[],visited:[],waypoint:null,order:{mode:'follow',x:300,y:1100},choices:[],pendingLevels:0,rewardPaid:false,reward:0};
 this.p.run=this.r;this.generate();this.emit('begin');return true;}
 generate(){const r=this.r;r.sites=[];r.enemies=[];r.bullets=[];r.loot=[];r.obstacles=[];r.visited=Array(18*18).fill(false);r.spawnClock=2.5;r.raidClock=40;r.bossClock=110;r.sectorTime=0;r.hero.x=300;r.hero.y=1100;r.waypoint=null;r.order={mode:'follow',x:300,y:1100};
 for(let i=0;i<SITE_LAYOUT.length;i++){const [type,name,x,y,tier,guards]=SITE_LAYOUT[i];const max=Math.round((type==='hq'?780:110+tier*100)*this.difficulty());const s={id:'S'+i,type,name,x:x+(this.rand()-.5)*38,y:y+(this.rand()-.5)*38,tier,owner:'enemy',hp:max,max,r:type==='hq'?48:36,progress:0,income:type==='oil'?8:type==='supply'?5:3,discovered:i===0,claimed:false};r.sites.push(s);for(let k=0;k<guards;k++){const angle=k/guards*Math.PI*2;this.spawnEnemy(tier>=3&&k===0?'apc':tier>=2&&k%3===0?'heavy':'rifle',s.x+Math.cos(angle)*(70+this.rand()*35),s.y+Math.sin(angle)*(70+this.rand()*35),s.id);}}
 for(let i=0;i<130;i++){let o={x:80+this.rand()*(W-160),y:80+this.rand()*(H-160),r:14+this.rand()*13,type:this.rand()>.5?'rock':'tree'};if(dist(o,r.hero)<120||r.sites.some(s=>dist(s,o)<155))continue;r.obstacles.push(o);}
 this.obstacles.insertAll(r.obstacles);for(let i=0;i<r.allies.length;i++){r.allies[i].x=280-(i%5)*20;r.allies[i].y=1060+Math.floor(i/5)*24;r.allies[i].hp=Math.max(r.allies[i].hp,r.allies[i].max*.65);}
 this.reveal();this.rebuild();}
 difficulty(){return Math.exp(Math.min(130,(this.r.sector-1)*Math.log(1.45)+this.r.time/60*Math.log(1.10)));}
 boon(id){return this.r.boons[id]||0;}
 damage(){return this.r.stats.damage*(1+.22*this.boon('damage'));}
 rate(){return 1+.15*this.boon('rate');}
 heroDamage(){return 19*this.damage();}
 heroSpeed(){return 145*(1+.10*this.boon('speed'));}
 slots(){return this.r.stats.slots+this.boon('command')*2;}
 usedSlots(){return this.r.allies.reduce((a,u)=>a+UNITS[u.type].slots,0);}
 income(){return this.r.sites.filter(s=>s.owner==='player').reduce((a,s)=>a+s.income,0)*this.r.stats.income*(1+this.boon('magnet')*.1);}
 owned(){return this.r.sites.filter(s=>s.owner==='player');}
 hasSite(type){return this.r.sites.some(s=>s.owner==='player'&&s.type===type);}
 researchCost(id){const n=this.p.research[id]||0;return Math.ceil((2+n*.7)*Math.pow(1.25,Math.floor(n/5)));}
 research(id){if(!RESEARCH.some(u=>u.id===id))return false;const cost=this.researchCost(id);if(this.p.intel<cost||this.p.research[id]>=99)return false;this.p.intel-=cost;this.p.research[id]++;this.emit('research',{id});return true;}
 recruit(type){const r=this.r,u=UNITS[type];if(!r||!['active','choice'].includes(r.phase)||!u)return false;if(r.supply<u.cost||this.usedSlots()+u.slots>this.slots()||(u.requires&&!this.hasSite(u.requires)))return false;
 r.supply-=u.cost;const max=u.hp*(1+.08*this.boon('health'));r.allies.push({id:r.uid++,type,x:r.hero.x-30+(this.rand()-.5)*40,y:r.hero.y+(this.rand()-.5)*70,hp:max,max,r:type==='tank'?22:10,angle:0,shoot:0,heal:0});this.emit('recruit',{type});return true;}
 setDestination(x,y){if(!this.r||this.r.phase!=='active')return false;this.r.waypoint={x:clamp(x,40,W-40),y:clamp(y,40,H-40)};this.emit('waypoint',this.r.waypoint);return true;}
 command(mode,siteId){const r=this.r;if(!r||r.phase!=='active')return false;if(mode==='follow'){r.order={mode:'follow',x:r.hero.x,y:r.hero.y};}
 else if(mode==='hold'){r.order={mode:'hold',x:r.hero.x,y:r.hero.y};}
 else if(mode==='assault'){const s=r.sites.find(s=>s.id===siteId);if(!s||!s.discovered)return false;r.order={mode:'assault',x:s.x,y:s.y,siteId};}
 else return false;this.emit('command',{mode});return true;}
 move(u,dx,dy,speed,dt){const len=Math.hypot(dx,dy);if(len<.0001)return;const step=Math.min(speed*dt,len),nx=dx/len,ny=dy/len;u.x=clamp(u.x+nx*step,24,W-24);u.y=clamp(u.y+ny*step,24,H-24);u.angle=Math.atan2(ny,nx);
 for(const o of this.obstacles.queryRect(u.x,u.y,65)){let x=u.x-o.x,y=u.y-o.y,d=Math.hypot(x,y),min=o.r+u.r;if(d<min){if(d<.001){x=1;y=0;d=1;}u.x=o.x+x/d*min;u.y=o.y+y/d*min;}}
 }
 spawnEnemy(type,x,y,home=null,raid=null){const r=this.r,t=ENEMIES[type];if(r.enemies.length>=185)return null;const site=home?r.sites.find(s=>s.id===home):null;const growth=this.difficulty()*(home?1+.2*((site?site.tier:1)-1):Math.exp(Math.min(30,r.time/60*Math.log(1.06))));const hp=t.hp*growth;const e={id:r.uid++,type,x:clamp(x,30,W-30),y:clamp(y,30,H-30),hp,max:hp,r:t.r,damage:t.damage*Math.pow(growth,.65),home,raid,shoot:this.rand(),angle:0,stun:0};r.enemies.push(e);return e;}
 rebuild(){if(!this.r)return;this.grid.insertAll(this.r.enemies.filter(e=>e.hp>0));for(const s of this.r.sites)if(s.owner==='enemy'&&s.hp>0&&(s.type!=='hq'||this.owned().length>=3))this.grid.insert(s);this.friends.insertAll([this.r.hero,...this.r.allies].filter(u=>u.hp>0));}
 nearest(u,range,heavy=false){let best=null,score=Infinity;const list=this.grid.queryRect(u.x,u.y,range);for(const t of list){if(t.hp<=0)continue;const d=dist(u,t);if(d>range+t.r)continue;const priority=t.owner?95:0;const n=d+priority-(heavy&&!t.owner?Math.min(t.max*.2,130):0);if(n<score){score=n;best=t;}}return best;}
 shoot(u,t,damage,kind,velocity=760){if(!t)return;const r=this.r,dx=t.x-u.x,dy=t.y-u.y,d=Math.hypot(dx,dy)||1;u.angle=Math.atan2(dy,dx);if(r.bullets.length<340)r.bullets.push({id:r.uid++,x:u.x,y:u.y,vx:dx/d*velocity,vy:dy/d*velocity,damage,kind,enemy:false,life:Math.max(.45,d/velocity+.3),r:kind==='tank'||kind==='mortar'?4:2,pierce:u.type==='commander'?this.boon('pierce'):0,hit:[]});this.emit('shot',{x:u.x,y:u.y,angle:u.angle,kind});}
 enemyShot(e,t){const r=this.r,dx=t.x-e.x,dy=t.y-e.y,d=Math.hypot(dx,dy)||1;if(r.bullets.length<340)r.bullets.push({id:r.uid++,x:e.x,y:e.y,vx:dx/d*350,vy:dy/d*350,damage:e.damage,kind:e.type,enemy:true,life:d/350+.4,r:e.type==='boss'||e.type==='apc'?5:3,pierce:0,hit:[]});e.angle=Math.atan2(dy,dx);}
 hitEnemy(t,damage){const r=this.r;if(!t||t.hp<=0)return;t.hp=Math.max(0,t.hp-damage);if(t.hp>0)return;
 if(t.owner){this.emit('structure',{x:t.x,y:t.y});return;}
 r.kills++;this.p.totalKills++;const def=ENEMIES[t.type],pay=Math.ceil(def.pay*r.stats.income*(1+this.boon('magnet')*.1));r.supply=Math.min(1e14,r.supply+pay);
 const gem={id:r.uid++,x:t.x,y:t.y,value:def.xp,kind:'xp'};if(r.loot.length<240)r.loot.push(gem);else{let close=r.loot.find(g=>dist(g,gem)<130&&g.kind==='xp');if(close)close.value+=def.xp;else r.loot[0].value+=def.xp;}
 if(this.rand()<.045&&r.loot.length<245)r.loot.push({id:r.uid++,x:t.x+12,y:t.y,kind:'heal',value:22});this.emit('kill',{x:t.x,y:t.y,type:t.type,pay});}
 damageFriendly(t,amount){const r=this.r;if(t===r.hero){if(t.invulnerable>0)return;t.hp=Math.max(0,t.hp-amount);t.invulnerable=.11;this.emit('hurt',{damage:amount});}
 else if(t.owner==='player'){t.hp=Math.max(0,t.hp-amount);if(t.hp===0){t.owner='enemy';t.hp=t.max*.45;t.progress=0;this.spawnEnemy('rifle',t.x+70,t.y,t.id);this.spawnEnemy('heavy',t.x,t.y+80,t.id);this.emit('lost',{name:t.name});}}
 else {t.hp=Math.max(0,t.hp-amount);if(t.hp<=0)this.emit('unitLost',{type:t.type,x:t.x,y:t.y});}}
 blast(x,y,radius,damage){for(const t of this.grid.queryRect(x,y,radius+50))if(t.hp>0&&dist(t,{x,y})<radius+t.r)this.hitEnemy(t,damage);this.emit('blast',{x,y,radius});}
 ability(id,input={x:0,y:0}){const r=this.r;if(!r||r.phase!=='active'||r.cooldowns[id]>0)return false;const h=r.hero;
 if(id==='dash'){const len=Math.hypot(input.x,input.y);h.lastX=len>.1?input.x/len:h.lastX;h.lastY=len>.1?input.y/len:h.lastY;h.dash=.27;h.invulnerable=.6;r.cooldowns.dash=Math.max(1.5,4.5-this.boon('speed')*.25);this.emit('dash',{x:h.x,y:h.y});}
 else if(id==='grenade'){const t=this.nearest(h,480)||h;this.blast(t.x,t.y,125*(1+.15*this.boon('grenade')),(95+this.heroDamage()*2)*(1+.4*this.boon('grenade')));r.cooldowns.grenade=13;}
 else if(id==='air'){if(r.level<5&&this.owned().length<2)return false;const site=r.sites.find(s=>s.id===r.order.siteId);const t=site&&dist(site,h)<650?site:this.nearest(h,600)||h;this.blast(t.x,t.y,300,210*this.damage());r.cooldowns.air=42;this.emit('air',{x:t.x,y:t.y});}
 else if(id==='rally'){r.order={mode:'follow',x:h.x,y:h.y};h.hp=Math.min(h.max,h.hp+h.max*.22);for(const a of r.allies)a.hp=Math.min(a.max,a.hp+a.max*.3);r.cooldowns.rally=28;this.emit('rally');}
 else return false;return true;}
 reveal(){const r=this.r;for(const u of [r.hero,...r.allies]){const gx=Math.floor(u.x/128),gy=Math.floor(u.y/128);for(let y=Math.max(0,gy-3);y<=Math.min(17,gy+3);y++)for(let x=Math.max(0,gx-3);x<=Math.min(17,gx+3);x++)if(Math.hypot(x-gx,y-gy)<3.5)r.visited[y*18+x]=true;for(const s of r.sites)if(!s.discovered&&dist(s,u)<420){s.discovered=true;this.emit('discover',{name:s.name});}}}
 stepHero(dt,input){const r=this.r,h=r.hero;h.invulnerable=Math.max(0,h.invulnerable-dt);h.dash=Math.max(0,h.dash-dt);const mag=Math.hypot(input.x||0,input.y||0);let dx=0,dy=0,speed=this.heroSpeed();
 if(h.dash>0){dx=h.lastX;dy=h.lastY;speed*=3.8;}
 else if(mag>.08){r.waypoint=null;dx=(input.x||0)/Math.max(1,mag);dy=(input.y||0)/Math.max(1,mag);h.lastX=dx/(Math.hypot(dx,dy)||1);h.lastY=dy/(Math.hypot(dx,dy)||1);}
 else if(r.waypoint){dx=r.waypoint.x-h.x;dy=r.waypoint.y-h.y;if(Math.hypot(dx,dy)<12){r.waypoint=null;dx=dy=0;}else {const l=Math.hypot(dx,dy);dx/=l;dy/=l;}}
 if(dx||dy)this.move(h,dx*1000,dy*1000,speed* Math.min(1,mag||1),dt);
 h.hp=Math.min(h.max,h.hp+(r.stats.regen+.6*this.boon('regen'))*dt);h.shoot-=dt;if(h.shoot<=0){const t=this.nearest(h,300);if(t){this.shoot(h,t,this.heroDamage(),'rifle');h.shoot=.48/this.rate();}else h.shoot=0;}
 }
 stepAllies(dt){const r=this.r,h=r.hero,order=r.order,alive=r.allies.filter(u=>u.hp>0);r.allies=alive;
 for(let i=0;i<alive.length;i++){const u=alive[i],def=UNITS[u.type];u.shoot-=dt;u.heal-=dt;let t=this.nearest(u,def.range,u.type==='sniper');const angle=i*2.39996,ring=35+Math.sqrt(i+1)*17;
 const anchor=order.mode==='follow'?h:order;let tx=anchor.x+Math.cos(angle)*ring,ty=anchor.y+Math.sin(angle)*ring;
 const enemyClose=t&&dist(u,t)<def.range*.85;
 if(!enemyClose||dist(u,anchor)>(order.mode==='follow'?280:190)){const dx=tx-u.x,dy=ty-u.y;if(Math.hypot(dx,dy)>13)this.move(u,dx,dy,def.speed*(dist(u,h)>360&&order.mode==='follow'?1.8:1),dt);}
 for(const other of this.friends.queryRect(u.x,u.y,45)){if(other===u||other.hp<=0)continue;const dx=u.x-other.x,dy=u.y-other.y,d=Math.hypot(dx,dy),min=u.r+(other.r||10)+4;if(d>0&&d<min){const p=(min-d)*Math.min(1,dt*6);u.x+=dx/d*p;u.y+=dy/d*p;}}
 if(t&&u.shoot<=0){this.shoot(u,t,def.damage*this.damage(),u.type);u.shoot=def.interval/this.rate();}
 if(u.type==='medic'&&u.heal<=0){let friend=[h,...alive].filter(a=>dist(a,u)<180&&a.hp<a.max).sort((a,b)=>a.hp/a.max-b.hp/b.max)[0];if(friend){friend.hp=Math.min(friend.max,friend.hp+10*(1+.2*this.boon('regen')));this.emit('heal',{x:friend.x,y:friend.y});}u.heal=1;}
 }
 }
 stepEnemies(dt){const r=this.r;for(const e of r.enemies){if(e.hp<=0)continue;e.shoot-=dt;e.stun=Math.max(0,e.stun-dt);if(e.stun>0)continue;const def=ENEMIES[e.type];let target=this.friends.findNearest(e.x,e.y,e.home?410:700);if(target&&target.hp<=0)target=null;
 if(e.raid){const site=r.sites.find(s=>s.id===e.raid&&s.owner==='player');if(site&&(!target||dist(e,target)>190))target=site;}
 if(!target&&!e.home)target=r.hero;
 if(target){const d=dist(e,target);if(d>def.range*.82)this.move(e,target.x-e.x,target.y-e.y,def.speed,dt);if(d<def.range+(target.r||10)&&e.shoot<=0){if(def.range<40)this.damageFriendly(target,e.damage);else this.enemyShot(e,target);e.shoot=def.interval;}}
 else if(e.home){const site=r.sites.find(s=>s.id===e.home);if(site&&dist(e,site)>110)this.move(e,site.x-e.x,site.y-e.y,def.speed*.65,dt);}
 for(const o of this.grid.queryRect(e.x,e.y,35)){if(o===e||o.owner||o.hp<=0)continue;const dx=e.x-o.x,dy=e.y-o.y,d=Math.hypot(dx,dy),min=e.r+o.r;if(d>.01&&d<min){e.x+=dx/d*(min-d)*dt*2.7;e.y+=dy/d*(min-d)*dt*2.7;}}
 }}
 stepBullets(dt){const r=this.r;for(const b of r.bullets){if(b.life<=0)continue;const oldX=b.x,oldY=b.y;b.x+=b.vx*dt;b.y+=b.vy*dt;b.life-=dt;const travel=Math.hypot(b.x-oldX,b.y-oldY);let targets=b.enemy?this.friends.queryRect((oldX+b.x)/2,(oldY+b.y)/2,60+travel):this.grid.queryRect((oldX+b.x)/2,(oldY+b.y)/2,65+travel);
 if(b.enemy)targets=targets.concat(r.sites.filter(s=>s.owner==='player'&&dist(s,b)<80));
 for(const t of targets){if(t.hp<=0||b.hit.includes(t.id))continue;const vx=b.x-oldX,vy=b.y-oldY,len=vx*vx+vy*vy,proj=len?clamp(((t.x-oldX)*vx+(t.y-oldY)*vy)/len,0,1):0;const d=Math.hypot(t.x-(oldX+vx*proj),t.y-(oldY+vy*proj));if(d>(t.r||10)+b.r)continue;b.hit.push(t.id);
 if(b.enemy)this.damageFriendly(t,b.damage);else if(b.kind==='mortar'||b.kind==='tank')this.blast(b.x,b.y,b.kind==='mortar'?83:58,b.damage);else this.hitEnemy(t,b.damage);
 b.pierce--;if(b.pierce<0){b.life=0;break;}}
 }
 r.bullets=r.bullets.filter(b=>b.life>0&&b.x>-20&&b.x<W+20&&b.y>-20&&b.y<H+20);r.enemies=r.enemies.filter(e=>e.hp>0);
 }
 stepSites(dt){const r=this.r;for(const s of r.sites){if(s.owner==='player'||s.hp>0)continue;const guards=r.enemies.some(e=>e.home===s.id&&e.hp>0);const nearby=[r.hero,...r.allies].some(u=>u.hp>0&&dist(s,u)<105);const contested=r.enemies.some(e=>e.hp>0&&dist(s,e)<135);if(!guards&&nearby&&!contested){s.progress=Math.min(1,s.progress+dt/4);if(s.progress>=1)this.capture(s);}else s.progress=Math.max(0,s.progress-dt*.08);}}
 capture(s){const r=this.r;if(s.owner==='player')return;s.owner='player';s.hp=s.max;s.progress=1;if(!s.claimed){s.claimed=true;r.captures++;this.p.totalCaptured++;r.supply+=70+s.tier*25;this.gainXP(20+s.tier*10);if(s.type==='rescue'){for(let i=0;i<3;i++){if(this.usedSlots()+1>this.slots())break;const def=UNITS.infantry;r.allies.push({id:r.uid++,type:'infantry',x:s.x+20*i,y:s.y+55,hp:def.hp,max:def.hp,r:10,angle:0,shoot:0,heal:0});}}}
 if(s.type==='medic'){r.hero.hp=r.hero.max;for(const u of r.allies)u.hp=u.max;}
 this.emit('capture',{name:s.name,type:s.type,income:s.income});if(r.order.siteId===s.id)r.order={mode:'hold',x:s.x,y:s.y};
 if(s.type==='hq'){r.phase='victory';this.p.bestSector=Math.max(this.p.bestSector,r.sector);this.emit('victory');}}
 gainXP(amount){const r=this.r;r.xp+=amount;while(r.xp>=this.xpNeed()&&r.level<999){r.xp-=this.xpNeed();r.level++;r.pendingLevels++;}if(r.pendingLevels>0&&r.phase==='active')this.offerChoice();}
 xpNeed(){return 18+this.r.level*12;}
 offerChoice(){const r=this.r,pool=BOONS.filter(b=>this.boon(b.id)<b.max),choices=[];while(choices.length<3&&pool.length){const i=Math.floor(this.rand()*pool.length);choices.push(pool.splice(i,1)[0].id);}if(!choices.length){r.pendingLevels=0;r.supply+=100;return;}r.choices=choices;r.phase='choice';this.emit('level',{level:r.level});}
 choose(id){const r=this.r;if(r.phase!=='choice'||!r.choices.includes(id))return false;r.boons[id]=(r.boons[id]||0)+1;if(id==='health'){const add=r.stats.health*.25;r.hero.max+=add;r.hero.hp=Math.min(r.hero.max,r.hero.hp+add+r.hero.max*.35);}if(id==='command')r.supply+=40;r.pendingLevels=Math.max(0,r.pendingLevels-1);r.phase='active';r.choices=[];if(r.pendingLevels)this.offerChoice();this.emit('boon',{id});return true;}
 collect(dt){const r=this.r,h=r.hero,magnet=85+this.boon('magnet')*40;const rest=[];for(const g of r.loot){const d=dist(g,h);if(d<magnet){const move=Math.min(d,dt*(190+magnet*1.7));if(d>1){g.x+=(h.x-g.x)/d*move;g.y+=(h.y-g.y)/d*move;}}if(d<16){if(g.kind==='heal'){h.hp=Math.min(h.max,h.hp+g.value);this.emit('heal',{x:h.x,y:h.y});}else this.gainXP(g.value);}else rest.push(g);}r.loot=rest;}
 periodic(dt){const r=this.r,h=r.hero;r.incomeClock+=dt;if(r.incomeClock>=1){r.incomeClock-=1;r.supply=Math.min(1e14,r.supply+this.income());for(const s of this.owned())s.hp=Math.min(s.max,s.hp+s.max*.005);this.reveal();}
 r.spawnClock-=dt;if(r.spawnClock<=0){const count=Math.min(13,2+Math.floor(r.time/35)+r.sector);const angle=this.rand()*Math.PI*2;for(let i=0;i<count;i++){const a=angle+(this.rand()-.5)*1.3,d=480+this.rand()*150;const pick=this.rand();this.spawnEnemy(r.time>80&&pick<.16?'apc':r.time>30&&pick<.30?'heavy':pick>.6?'runner':'rifle',h.x+Math.cos(a)*d,h.y+Math.sin(a)*d);}r.spawnClock=Math.max(1.8,5.2-r.time/100);this.emit('encounter',{count});}
 r.raidClock-=dt;if(r.raidClock<=0){const sites=this.owned();if(sites.length){const s=sites[Math.floor(this.rand()*sites.length)],a=this.rand()*Math.PI*2;for(let i=0;i<5+r.sector;i++)this.spawnEnemy(i%3===0?'heavy':'rifle',s.x+Math.cos(a)*400+i*13,s.y+Math.sin(a)*400,null,s.id);this.emit('raid',{name:s.name});}r.raidClock=50;}
 r.bossClock-=dt;if(r.bossClock<=0){this.spawnEnemy('boss',clamp(h.x+500,50,W-50),clamp(h.y-200,50,H-50));r.bossClock=Math.max(55,110-r.time/30);this.emit('boss');}
 if(this.boon('drone')&&r.cooldowns.drone<=0){for(let i=0;i<this.boon('drone');i++){const u={x:h.x+Math.cos(r.time*2+i*3)*45,y:h.y+Math.sin(r.time*2+i*3)*45,angle:0},t=this.nearest(u,330);if(t)this.shoot(u,t,12*this.damage(),'drone');}r.cooldowns.drone=.45/this.rate();}
 if(this.boon('mortar')&&r.cooldowns.autoMortar<=0){const t=this.nearest(h,450);if(t)this.blast(t.x,t.y,95,(48+this.boon('mortar')*38)*this.damage());r.cooldowns.autoMortar=5;}
 if(this.boon('shield')&&r.cooldowns.shield<=0){this.blast(h.x,h.y,105,(20+this.boon('shield')*24)*this.damage());for(const e of this.grid.queryRect(h.x,h.y,130))if(!e.owner&&dist(e,h)<130){const d=dist(e,h)||1;e.x=clamp(e.x+(e.x-h.x)/d*70,25,W-25);e.y=clamp(e.y+(e.y-h.y)/d*70,25,H-25);e.stun=.8;}r.cooldowns.shield=7;}
 }
 update(dt,input={x:0,y:0}){const r=this.r;if(!r||r.phase!=='active')return;dt=clamp(dt,0,.05);r.time+=dt;r.sectorTime+=dt;for(const id in r.cooldowns)r.cooldowns[id]=Math.max(0,r.cooldowns[id]-dt);this.rebuild();this.stepHero(dt,input);this.stepAllies(dt);this.stepEnemies(dt);this.rebuild();this.stepBullets(dt);if(r.hero.hp<=0){this.finish('dead');return;}this.stepSites(dt);this.collect(dt);this.periodic(dt);}
 rewardPreview(){const r=this.r;if(!r||r.time<15&&r.kills<3&&!r.captures)return 0;return Math.max(1,Math.floor((r.kills/9+r.captures*3+r.time/60+(r.sector-1)*8)*(1+.12*this.p.research.intel)));}
 finish(reason='extracted'){const r=this.r;if(!r||r.rewardPaid)return 0;const reward=this.rewardPreview();r.reward=reward;r.rewardPaid=true;r.phase=reason;r.waypoint=null;this.p.intel+=reward;if(reward>0)this.p.rebirths++;this.p.bestSector=Math.max(this.p.bestSector,r.sector);this.emit('end',{reason,reward});return reward;}
 nextSector(){const r=this.r;if(!r||r.phase!=='victory')return false;r.sector++;r.phase='active';r.hero.hp=Math.min(r.hero.max,r.hero.hp+r.hero.max*.5);this.generate();if(r.pendingLevels)this.offerChoice();this.emit('sector',{sector:r.sector});return true;}
 snapshot(){this.p.run=this.r;return JSON.parse(JSON.stringify({...this.p,savedAt:Date.now()}));}
}
function validSave(d){if(!d||d.version!==2||!Number.isFinite(d.intel)||d.intel<0||d.intel>1e14||!Number.isInteger(d.rebirths)||d.rebirths<0||!d.research)return false;for(const u of RESEARCH)if(!Number.isInteger(d.research[u.id])||d.research[u.id]<0||d.research[u.id]>99)return false;const r=d.run;if(!r)return true;if(!['active','choice','dead','extracted','victory'].includes(r.phase)||!r.hero||!r.stats||!r.boons||!r.cooldowns||!r.order)return false;for(const k of ['supply','time','level','sector','uid','xp','kills','captures','seed'])if(!Number.isFinite(r[k])||r[k]<0||r[k]>1e14)return false;if(r.level<1||r.sector<1||r.hero.hp<0||r.hero.max<=0)return false;for(const k of ['x','y','hp','max'])if(!Number.isFinite(r.hero[k]))return false;for(const [key,max] of [['allies',60],['enemies',200],['bullets',400],['loot',250],['sites',12],['obstacles',140],['visited',324]])if(!Array.isArray(r[key])||r[key].length>max)return false;for(const a of [r.allies,r.enemies,r.sites,r.obstacles,r.loot,r.bullets])for(const e of a)if(!e||!Number.isFinite(e.x)||!Number.isFinite(e.y)||e.x< -100||e.y< -100||e.x>W+100||e.y>H+100)return false;return r.allies.every(u=>UNITS[u.type]&&Number.isFinite(u.hp)&&u.max>0)&&r.enemies.every(e=>ENEMIES[e.type]&&e.hp>=0&&e.max>0);}
const api={Engine,UNITS,ENEMIES,RESEARCH,BOONS,W,H,profile,validSave,dist};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.Campaign=api;
})(typeof globalThis!=='undefined'?globalThis:this);
