"""Runs inside one disposable network namespace (Docker container) per public endpoint.
Only tun0-bound, TLS-verified HTTP evidence counts. No default-route fallback.
"""
import ipaddress
import json
from pathlib import Path
import socket
import subprocess
import time


def curl(url, address, tunnel=False, maximum=8, output=None):
    command = ['curl', '-4', '--silent', '--show-error', '--fail', '--noproxy', '*',
               '--connect-timeout', '5', '--max-time', str(maximum),
               '--resolve', 'speed.cloudflare.com:443:' + address]
    if tunnel:
        command += ['--interface', 'tun0']
    if output:
        command += ['--output', str(output)]
    return subprocess.check_output(command + [url], stderr=subprocess.DEVNULL, timeout=maximum + 2)


def exit_ip(text):
    fields = dict(line.split('=', 1) for line in text.decode().splitlines() if '=' in line)
    return str(ipaddress.ip_address(fields['ip']))


def probe():
    address = socket.gethostbyname('speed.cloudflare.com')
    trace = 'https://speed.cloudflare.com/cdn-cgi/trace'
    before = exit_ip(curl(trace, address))
    logfile = Path('/tmp/openvpn.log')
    start = time.monotonic()
    with logfile.open('w') as log:
        process = subprocess.Popen(['openvpn', '--config', '/input/node.ovpn', '--dev', 'tun0',
            '--disable-dco', '--script-security', '0', '--auth-nocache', '--connect-timeout', '6',
            '--connect-retry-max', '1', '--hand-window', '15', '--verb', '3',
            '--pull-filter', 'ignore', 'dhcp-option', '--pull-filter', 'ignore', 'route-ipv6',
            '--pull-filter', 'ignore', 'ifconfig-ipv6'], stdout=log, stderr=subprocess.STDOUT,
            stdin=subprocess.DEVNULL)
        try:
            while time.monotonic() - start < 28:
                text = logfile.read_text(errors='replace')
                if 'Initialization Sequence Completed' in text:
                    break
                if process.poll() is not None:
                    raise RuntimeError('authentication_or_tunnel_failed')
                time.sleep(.2)
            else:
                raise RuntimeError('tunnel_timeout')
            connect_ms = round((time.monotonic() - start) * 1000)
            after = exit_ip(curl(trace, address, True))
            if after == before:
                raise RuntimeError('unchanged_egress')
            output = Path('/tmp/sample.bin')
            start = time.monotonic()
            curl('https://speed.cloudflare.com/__down?bytes=524288', address, True, 10, output)
            elapsed = time.monotonic() - start
            size = output.stat().st_size
            if size != 524288 or process.poll() is not None:
                raise RuntimeError('incomplete_sample_or_tunnel_dropped')
            # A second fresh HTTPS request also confirms the tunnel survived the sample.
            if exit_ip(curl(trace, address, True)) != after:
                raise RuntimeError('unstable_egress')
            return dict(ok=True, checked_at=int(time.time() * 1000), connect_ms=connect_ms,
                        mbps=round(size * 8 / elapsed / 1000000, 3), sample_bytes=size,
                        vpn_ip=after, scope='GitHub Actions / Linux container; not phone speed')
        finally:
            process.terminate()
            try:
                process.wait(3)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


if __name__ == '__main__':
    try:
        result = probe()
    except Exception as error:
        result = dict(ok=False, reason=type(error).__name__ + ': ' + str(error)[:160])
    print(json.dumps(result))
