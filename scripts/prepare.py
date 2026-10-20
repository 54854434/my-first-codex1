#!/usr/bin/env python3
"""Apply the small Aurora overlay to an exact, unmodified upstream checkout."""
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = ROOT / "upstream"
PIN = "73ce3c99dde39207990d66c21be8201228cd1f09"  # OpenVPN for Android v0.7.64

def prepare():
    if not (UPSTREAM / "main/build.gradle.kts").exists():
        raise SystemExit("Run git submodule update --init --recursive first")
    revision = subprocess.check_output(["git", "-C", str(UPSTREAM), "rev-parse", "HEAD"], text=True).strip()
    if revision != PIN:
        raise SystemExit(f"Unexpected upstream revision: {revision}")
    for folder in ("java", "res"):
        shutil.copytree(ROOT / "overlay" / folder, UPSTREAM / "main/src/ui" / folder, dirs_exist_ok=True)

    android = "http://schemas.android.com/apk/res/android"
    tools = "http://schemas.android.com/tools"
    ET.register_namespace("android", android)
    ET.register_namespace("tools", tools)
    attr = lambda n: "{" + android + "}" + n
    for variant in ("main", "ui"):
        manifest = UPSTREAM / f"main/src/{variant}/AndroidManifest.xml"
        tree = ET.parse(manifest)
        app = tree.getroot().find("application")
        if variant == "main":
            app.set(attr("label"), "极光免费 VPN")
            app.set(attr("allowBackup"), "false")
            app.set(attr("usesCleartextTraffic"), "false")
            app.set(attr("icon"), "@drawable/aurora_icon")
            app.set(attr("roundIcon"), "@drawable/aurora_icon")
        else:
            for activity in app.findall("activity"):
                for intent in list(activity.findall("intent-filter")):
                    if any(c.get(attr("name"), "").endswith("LAUNCHER") for c in intent.findall("category")):
                        activity.remove(intent)
            for activity in list(app.findall("activity")):
                if activity.get(attr("name")) == "com.aurora.vpn.MainActivity":
                    app.remove(activity)
            activity = ET.SubElement(app, "activity", {
                attr("name"): "com.aurora.vpn.MainActivity",
                attr("exported"): "true",
                attr("configChanges"): "orientation|screenSize|uiMode",
                attr("theme"): "@style/AuroraTheme",
            })
            intent = ET.SubElement(activity, "intent-filter")
            ET.SubElement(intent, "action", {attr("name"): "android.intent.action.MAIN"})
            ET.SubElement(intent, "category", {attr("name"): "android.intent.category.LAUNCHER"})
        tree.write(manifest, encoding="utf-8", xml_declaration=True)

    gradle = UPSTREAM / "main/build.gradle.kts"
    content = gradle.read_text()
    if 'applicationId = "com.aurora.freevpn"' not in content:
        content = content.replace("defaultConfig {", 'defaultConfig {\n        applicationId = "com.aurora.freevpn"', 1)
    content = content.replace("minSdk = 21", "minSdk = 26")
    gradle.write_text(content)
    # Notification channels and the native VPN service retain upstream lifecycle behavior.
    print("Aurora overlay applied to", revision)

if __name__ == "__main__":
    prepare()
