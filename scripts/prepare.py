#!/usr/bin/env python3
"""Apply the small Aurora overlay to an exact, unmodified upstream checkout."""
from pathlib import Path
import shutil
import subprocess
import re
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
    if (ROOT / "overlay/assets").exists():
        shutil.copytree(ROOT / "overlay/assets", UPSTREAM / "main/src/main/assets", dirs_exist_ok=True)

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
    content = re.sub(r'versionCode = \d+', 'versionCode = 220', content, count=1)
    content = re.sub(r'versionName = "[^"]+"', 'versionName = "0.2.0-alpha"', content, count=1)
    # One installable APK covering the same four ABIs, without simultaneous split packagers.
    content = content.replace("isEnable = true", "isEnable = false")
    if 'baseline = file("lint-baseline.xml")' not in content:
        content = content.replace("lint {", 'lint {\n        baseline = file("lint-baseline.xml")', 1)
    gradle.write_text(content)
    # This reviewed baseline contains only 36 pre-existing ImpliedQuantity
    # findings in unmodified upstream plural resources. New issues still fail lint.
    shutil.copyfile(ROOT / "config/lint-baseline.xml", UPSTREAM / "main/lint-baseline.xml")
    properties = UPSTREAM / "gradle.properties"
    props = properties.read_text() if properties.exists() else ""
    for key, value in {"org.gradle.jvmargs": "-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8", "org.gradle.workers.max": "2"}.items():
        props = re.sub(r"(?m)^" + re.escape(key) + r"=.*$", "", props)
        props += f"\n{key}={value}\n"
    properties.write_text(props)
    # Android API 23+ provides GNU basename in libc. The upstream API-21 config
    # otherwise declares a conflicting fallback when our minimum is API 26.
    native_config = UPSTREAM / "main/src/main/cpp/openvpn-config/config.h"
    native_text = native_config.read_text()
    if "#define HAVE_BASENAME 1" not in native_text:
        native_config.write_text("#define HAVE_BASENAME 1\n" + native_text)
    # Notification channels and the native VPN service retain upstream lifecycle behavior.
    print("Aurora overlay applied to", revision)

if __name__ == "__main__":
    prepare()
