import importlib.util
from pathlib import Path
import tempfile
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
            (up / "main/build.gradle.kts").write_text("defaultConfig {\n minSdk = 21\n}")
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
            main_app = ET.parse(up / "main/src/main/AndroidManifest.xml").getroot().find("application")
            self.assertEqual(main_app.get(a+"allowBackup"), "false")

if __name__ == "__main__":
    unittest.main()
