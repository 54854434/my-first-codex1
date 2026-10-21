"""CI connectivity evidence only: no TUN device, no routes, no hooks, bounded runtime.
This test's egress network differs from the Android device. Do not label these as phone-verified.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import json
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / ".checks/handshakes"
OUT.mkdir(parents=True, exist_ok=True)
subprocess.run(["java", "-cp", str(ROOT / ".checks"), "com.aurora.vpn.GateHandshakeProfiles",
                str(ROOT / "overlay/assets/gate-seed.csv"), str(OUT)], check=True)

def check(profile):
    command = ["openvpn", "--config", str(profile), "--dev", "null", "--disable-dco",
               "--route-noexec", "--ifconfig-noexec", "--script-security", "0",
               "--connect-timeout", "6", "--connect-retry-max", "1", "--hand-window", "12",
               "--verb", "3", "--auth-nocache"]
    output = ""
    try:
        run = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                             timeout=24, stdin=subprocess.DEVNULL)
        output = run.stdout
    except subprocess.TimeoutExpired as error:
        output = error.stdout or b""
        if isinstance(output, bytes):
            output = output.decode("utf-8", "replace")
    # Retain redacted milestones, never profile keys or the complete VPN configuration.
    milestones = [line for line in output.splitlines() if any(marker in line for marker in (
        "VERIFY ERROR", "TLS Error", "Connection refused", "Network is unreachable",
        "Peer Connection Initiated", "Initialization Sequence Completed", "Options error", "AUTH_FAILED"))]
    ok = "Peer Connection Initiated" in output or "Initialization Sequence Completed" in output
    return {"ip": profile.stem.split("-",1)[-1], "source": "VPNBook" if profile.stem.startswith("book-") else "VPN Gate", "handshake": ok, "milestones": milestones[-6:],
            "scope": "CI egress only; not tested on user phone"}

with ThreadPoolExecutor(max_workers=3) as pool:
    results = list(pool.map(check, sorted(OUT.glob("*.ovpn"))))
report = OUT / "report.json"
report.write_text(json.dumps(results, ensure_ascii=False, indent=2))
print(report.read_text())
print(f"CI OpenVPN handshakes: {sum(x['handshake'] for x in results)}/{len(results)}")
for profile in OUT.glob("*.ovpn"):
    profile.unlink()

