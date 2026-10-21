import base64
import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('feed_builder', Path(__file__).resolve().parents[1] / 'service/build_feed.py')
feed = importlib.util.module_from_spec(spec)
spec.loader.exec_module(feed)


class FeedServiceTest(unittest.TestCase):
    def row(self, ip):
        return dict(HostName='public', IP=ip, CountryLong='<script>', CountryShort='JP', Speed=1,
                    OpenVPN_ConfigData_Base64=base64.b64encode(('remote ' + ip + ' 443\n').encode()).decode(), AuroraSource='VPN Gate')

    def test_failed_checks_removed_and_evidence_preserved(self):
        proof = dict(ip='8.8.8.8', port=443, source='VPN Gate', ok=True, checked_at=999, mbps=3.5, connect_ms=3000)
        failed = dict(ip='9.9.9.9', port=443, source='VPN Gate', ok=False)
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            result = feed.publish([self.row('8.8.8.8'), self.row('9.9.9.9')], [proof, failed], out, 1000)
            self.assertEqual(result['passed'], 1)
            self.assertNotIn('9.9.9.9', (out / 'nodes.csv').read_text())
            self.assertIn('999,3.5,3000', (out / 'nodes.csv').read_text())
            self.assertIn('不是手机速度', (out / 'index.html').read_text())

    def test_zero_success_never_refreshes_old_feed(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            (out / 'nodes.csv').write_text('old snapshot')
            with self.assertRaises(RuntimeError):
                feed.publish([], [], out, 1000)
            self.assertEqual((out / 'nodes.csv').read_text(), 'old snapshot')

    def test_http_proofs_cannot_fall_back_to_default_route(self):
        from unittest.mock import patch
        spec2 = importlib.util.spec_from_file_location('worker', Path(__file__).resolve().parents[1] / 'service/worker.py')
        worker = importlib.util.module_from_spec(spec2)
        spec2.loader.exec_module(worker)
        with patch.object(worker.subprocess, 'check_output', return_value=b'ok') as call:
            worker.curl('https://speed.cloudflare.com/cdn-cgi/trace', '1.1.1.1', True)
            args = call.call_args.args[0]
            self.assertEqual(args[args.index('--interface') + 1], 'tun0')
            self.assertNotIn('--insecure', args)
