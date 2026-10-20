import importlib.util
from pathlib import Path
import tempfile
import shutil
import unittest
import xml.etree.ElementTree as ET
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("prepare", ROOT / "scripts/prepare.py")
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)

class OverlayTests(unittest.TestCase):
    def test_manifest_and_identity_idempotent(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            up = root / "upstream"
            for variant in ("main", "ui"):
                folder = up / f"main/src/{variant}"
                folder.mkdir(parents=True)
                (folder / "AndroidManifest.xml").write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:name=".core.ICSOpenVPNApplication"><activity android:name=".activities.MainActivity"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity><service android:name=".core.OpenVPNService"/></application></manifest>''')
            for folder in ("java", "res"):
                (root / "overlay" / folder).mkdir(parents=True)
            (root / "overlay/assets").mkdir()
            (root / "overlay/assets/gate-seed.csv").write_text("dated public snapshot fixture")
            (root / "config").mkdir()
            shutil.copyfile(ROOT / "config/lint-baseline.xml", root / "config/lint-baseline.xml")
            (up / "main/build.gradle.kts").write_text('defaultConfig {\n minSdk = 21\n versionCode = 219\n versionName = "0.7.64"\n}\nlint {\n}\n')
            native = up / "main/src/main/cpp/openvpn-config/config.h"
            native.parent.mkdir(parents=True)
            native.write_text("/* upstream API-21 configuration */\n")
            with patch.object(prepare, "ROOT", root), patch.object(prepare, "UPSTREAM", up), patch.object(prepare.subprocess, "check_output", return_value=prepare.PIN):
                prepare.prepare()
                prepare.prepare()
            a = "{http://schemas.android.com/apk/res/android}"
            app = ET.parse(up / "main/src/ui/AndroidManifest.xml").getroot().find("application")
            self.assertEqual(len([x for x in app.findall("activity") if x.get(a+"name") == "com.aurora.vpn.MainActivity"]), 1)
            self.assertEqual(len(app.findall(".//category")), 1)
            self.assertIsNotNone(app.find("service"))
            build = (up / "main/build.gradle.kts").read_text()
            self.assertEqual(build.count('applicationId = "com.aurora.freevpn"'), 1)
            self.assertIn("minSdk = 26", build)
            self.assertEqual(native.read_text().count("#define HAVE_BASENAME 1"), 1)
            properties = (up / "gradle.properties").read_text()
            self.assertEqual(properties.count("org.gradle.jvmargs="), 1)
            self.assertIn("-Xmx4g", properties)
            self.assertEqual((up / "main/src/main/assets/gate-seed.csv").read_text(), "dated public snapshot fixture")
            self.assertIn('versionName = "0.4.0-alpha"', build)
            self.assertIn('versionCode = 222', build)
            self.assertIn('baseline = file("lint-baseline.xml")', build)
            main_app = ET.parse(up / "main/src/main/AndroidManifest.xml").getroot().find("application")
            self.assertEqual(main_app.get(a+"allowBackup"), "false")

    def test_baseline_contains_only_reviewed_upstream_plural_findings(self):
        issues = ET.parse(ROOT / "config/lint-baseline.xml").getroot().findall("issue")
        self.assertEqual(len(issues), 36)
        for issue in issues:
            self.assertEqual(issue.get("id"), "ImpliedQuantity")
            for location in issue.findall("location"):
                self.assertTrue(location.get("file").startswith("src/ui/res/values-"))
                self.assertTrue(location.get("file").endswith("/plurals.xml"))

if __name__ == "__main__":
    unittest.main()
