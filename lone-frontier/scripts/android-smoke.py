"""Install and exercise the actual Android WebView application with ADB."""
import subprocess,time,xml.etree.ElementTree as ET,re,pathlib,json
OUT=pathlib.Path('lone-frontier/dist'); OUT.mkdir(exist_ok=True)
PKG='com.lonefrontier.game'
def adb(*args,check=True):return subprocess.run(['adb',*args],check=check,capture_output=True,text=True).stdout
def dump():
 adb('shell','uiautomator','dump','/sdcard/frontier-ui.xml')
 raw=adb('shell','cat','/sdcard/frontier-ui.xml')
 return ET.fromstring(raw),raw
def find_label(label):
 root,raw=dump()
 for node in root.iter('node'):
  if label in node.get('text','') or label in node.get('content-desc',''):
   xy=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
   if len(xy)==4 and xy[2]>xy[0] and xy[3]>xy[1]:return (xy[0]+xy[2])//2,(xy[1]+xy[3])//2
 return None
def click(label):
 for _ in range(8):
  pos=find_label(label)
  if pos:adb('shell','input','tap',str(pos[0]),str(pos[1]));time.sleep(.8);return
  time.sleep(.8)
 root,raw=dump();(OUT/'failed-ui.xml').write_text(raw)
 raise AssertionError('UI element not found: '+label)
def shot(name):
 data=subprocess.run(['adb','exec-out','screencap','-p'],check=True,capture_output=True).stdout
 (OUT/(name+'.png')).write_bytes(data)
adb('logcat','-c')
adb('install','-r',str(OUT/'LoneFrontier-v1.0.0.apk'))
adb('shell','am','start','-W','-n',PKG+'/.MainActivity')
time.sleep(4)
shot('01-briefing')
click('部署第一名步兵')
time.sleep(3)
click('升级制式步枪')
shot('02-battle')
click('暂停与设置')
root,raw=dump();assert '战术暂停' in raw
shot('03-settings')
click('备份 / 恢复存档')
root,raw=dump();assert '选中全部存档' in raw
click('返回作战')
adb('shell','input','keyevent','3');time.sleep(2)
adb('shell','am','start','-W','-n',PKG+'/.MainActivity');time.sleep(2)
assert adb('shell','pidof',PKG).strip()
root,raw=dump();(OUT/'verified-ui.xml').write_text(raw)
logs=adb('logcat','-d');(OUT/'android-logcat.txt').write_text(logs)
errors=[line for line in logs.splitlines() if ('Uncaught ' in line or 'FATAL EXCEPTION' in line) and ('chromium' in line or PKG in line)]
assert not errors,'\n'.join(errors)
(OUT/'android-smoke.json').write_text(json.dumps({'install':'passed','launch':'passed','deployment':'passed','upgrade':'passed','pause':'passed','backup':'passed','background_resume':'passed','javascript_errors':errors,'sdk':adb('shell','getprop','ro.build.version.sdk').strip()},indent=2))
print('ANDROID SMOKE PASSED: install, launch, deploy, upgrade, settings, backup, background resume; no JavaScript errors.')
