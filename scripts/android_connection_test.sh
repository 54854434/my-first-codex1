#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .checks/android-proof
python3 - <<'PY'
from pathlib import Path
import subprocess
app=list(Path('upstream/main/build/outputs/apk/uiOvpn2/debug').glob('*.apk'))
test=list(Path('upstream/main/build/outputs/apk/androidTest').rglob('*.apk'))
assert len(app)==1,app
assert len(test)==1,test
for apk in app+test:
    subprocess.run(['adb','install','-r',str(apk)],check=True)
PY
adb shell appops set com.aurora.freevpn ACTIVATE_VPN allow
adb shell pm grant com.aurora.freevpn android.permission.POST_NOTIFICATIONS
adb shell input keyevent 82
set +e
timeout 570 adb shell am instrument -w -r com.aurora.freevpn.test/com.aurora.vpn.RealConnectionInstrumentation > .checks/android-proof/instrumentation.txt 2>&1
instrument_status=$?
adb exec-out run-as com.aurora.freevpn cat files/connection-proof.json > .checks/android-proof/connection-proof.json
adb exec-out run-as com.aurora.freevpn cat files/connection-proof.png > .checks/android-proof/connection-proof.png
set -e
python3 - <<'PY'
import json
from pathlib import Path
path=Path('.checks/android-proof/connection-proof.json')
try:
    report=json.loads(path.read_text())
except Exception:
    print(Path('.checks/android-proof/instrumentation.txt').read_text())
    raise
print(json.dumps(report,ensure_ascii=False,indent=2))
assert report['success'], 'Android actual connection failed; see proof for native errors'
assert report['native_connected'] and report['android_vpn_network'] and report['egress_changed']
assert report['probe_bound_to_physical_network'] and report['probe_while_vpn_active_ms']>0
assert report['https_bytes']>0
assert report['persisted_priority'] and report['reconnect_attempts']==1 and report['reconnect_https_bytes']>0
PY

