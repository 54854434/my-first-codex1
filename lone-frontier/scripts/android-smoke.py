"""Install and operate the real v2 Android survivor/RTS app, preserving device evidence."""
import subprocess,time,xml.etree.ElementTree as ET,re,pathlib,json,atexit,struct
OUT=pathlib.Path('lone-frontier/dist');OUT.mkdir(exist_ok=True)
PKG='com.lonefrontier.game'
def adb(*args,check=True):return subprocess.run(['adb',*args],check=check,capture_output=True,text=True).stdout
def shot(name):
 data=subprocess.run(['adb','exec-out','screencap','-p'],check=True,capture_output=True).stdout
 (OUT/(name+'.png')).write_bytes(data)
 return struct.unpack('>II',data[16:24])
def preserve():
 try:(OUT/'android-logcat.txt').write_text(adb('logcat','-d'));shot('last-screen')
 except Exception:pass
atexit.register(preserve)
def dump():
 for _ in range(5):
  adb('shell','uiautomator','dump','--compressed','/sdcard/frontier-ui.xml',check=False)
  raw=adb('shell','cat','/sdcard/frontier-ui.xml',check=False)
  if raw.lstrip().startswith('<?xml'):
   try:return ET.fromstring(raw),raw
   except ET.ParseError:pass
  time.sleep(2)
 raise AssertionError('UI hierarchy unavailable')
def locate(label):
 root,raw=dump()
 for n in root.iter('node'):
  if label in n.get('text','') or label in n.get('content-desc',''):
   v=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
   if len(v)==4 and v[2]>v[0] and v[3]>v[1]:return (v[0]+v[2])//2,(v[1]+v[3])//2
 return None
def click(label):
 for attempt in range(5):
  pos=locate(label)
  if pos:adb('shell','input','tap',str(pos[0]),str(pos[1]));time.sleep(.7);return
  time.sleep(1)
 root,raw=dump();(OUT/'failed-ui.xml').write_text(raw);preserve();raise AssertionError('Missing: '+label)
def save_from_ui():
 root,raw=dump();(OUT/'verified-ui.xml').write_text(raw)
 summary='';prefix=''
 for n in root.iter('node'):
  if n.get('resource-id')=='saveSummary':summary=n.get('text','')
  text=n.get('text','')
  if text.startswith('{"version":2'):prefix=text
 # Android accessibility truncates long text to 10,000 characters. The save
 # itself remains complete; inspect its early fields and the visible order summary.
 assert prefix,'V2 save must be visible'
 hero=json.loads(re.search(r'"hero":(\{[^}]+\})',prefix).group(1))
 allies=json.loads(re.search(r'"allies":(\[[^\]]*\])',prefix).group(1))
 supply=float(re.search(r'"supply":([0-9.eE+\-]+)',prefix.split('"run":',1)[1]).group(1))
 sorties=int(re.search(r'"sorties":([0-9]+)',prefix).group(1))
 mode='assault' if '独立进攻' in summary else 'hold' if '驻守' in summary else 'follow'
 return {'version':2,'sorties':sorties,'run':{'hero':hero,'allies':allies,'supply':supply,'order':{'mode':mode}}}
adb('logcat','-c')
adb('install','-r',str(OUT/'LoneFrontier-Rebirth-v2.0.0.apk'))
adb('shell','am','start','-W','-n',PKG+'/.MainActivity')
time.sleep(18)
width,height=shot('01-rebirth-camp');assert width>height,'App must launch in landscape'
click('出击')
click('招募部队')
click('招募 · 38')
shot('02-army-recruitment')
click('返回战场')
# Set a waypoint on empty central terrain to exercise actual movement.
adb('shell','input','tap',str(int(width*.52)),str(int(height*.53)));time.sleep(1.5)
click('打开战术地图')
shot('03-tactical-map')
click('派兵攻打')
shot('04-free-battle')
adb('shell','input','keyevent','4');time.sleep(.35)
click('备份存档')
saved=save_from_ui();assert saved['run']['allies'],'Recruitment must persist'
assert saved['run']['order']['mode']=='assault','Army must accept independent orders'
assert abs(saved['run']['hero']['x']-300)>20 or abs(saved['run']['hero']['y']-1100)>20,'Waypoint movement must occur'
assert saved['run']['supply']>=0
adb('shell','input','keyevent','3');time.sleep(2)
adb('shell','am','start','-W','-n',PKG+'/.MainActivity');time.sleep(2)
assert adb('shell','pidof',PKG).strip()
# Keep the backup panel paused while backgrounding and restarting.
# Force a real process restart to verify SharedPreferences restoration.
adb('shell','am','force-stop',PKG)
adb('shell','am','start','-W','-n',PKG+'/.MainActivity');time.sleep(8)
click('继续作战')
adb('shell','input','keyevent','4');time.sleep(.35)
click('备份存档')
restored=save_from_ui();assert restored['version']==2;assert restored['sorties']==saved['sorties'];assert restored['run']['allies'];assert restored['run']['order']['mode']=='assault'
logs=adb('logcat','-d');(OUT/'android-logcat.txt').write_text(logs)
errors=[l for l in logs.splitlines() if 'Uncaught ' in l and 'chromium' in l or 'FATAL EXCEPTION' in l]
assert not errors,'\n'.join(errors)
report={'sdk':adb('shell','getprop','ro.build.version.sdk').strip(),'install':'passed','landscape':'passed','deployment':'passed','recruitment':'passed','movement':'passed','tactical_map':'passed','independent_army_orders':'passed','backup':'passed','background_resume':'passed','process_restart_restore':'passed','javascript_errors':errors,'saved_army':len(restored['run']['allies'])}
(OUT/'android-smoke.json').write_text(json.dumps(report,indent=2))
print('ANDROID V2 SMOKE PASSED',json.dumps(report))
