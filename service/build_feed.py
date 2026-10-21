"""Bounded parallel tunnel checks, then an inspectable feed and static status page."""
from concurrent.futures import ThreadPoolExecutor
import csv
import html
import io
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / '.checks/feed'
COLUMNS = ['HostName', 'IP', 'CountryLong', 'CountryShort', 'Speed', 'OpenVPN_ConfigData_Base64', 'AuroraSource', 'AuroraCheckedAt', 'AuroraMbps', 'AuroraConnectMs']


def rows(path):
    text = path.read_text()
    return list(csv.DictReader(io.StringIO(text[text.index('#HostName,') + 1:])))


def run_check(item):
    index, row = item
    name = 'aurora-probe-' + str(index)
    command = ['docker', 'run', '--rm', '--name', name, '--cap-add', 'NET_ADMIN',
               '--device', '/dev/net/tun', '--sysctl', 'net.ipv6.conf.all.disable_ipv6=1',
               '--mount', 'type=bind,src=' + str(WORK / ('node-' + str(index) + '.ovpn')) + ',dst=/input/node.ovpn,readonly',
               'aurora-feed-probe']
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=65)
        proof = json.loads(result.stdout.strip().splitlines()[-1])
    except Exception as error:
        proof = dict(ok=False, reason=type(error).__name__)
    finally:
        subprocess.run(['docker', 'rm', '-f', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    proof.update(ip=row['IP'], port=remote_port(row), source=row['AuroraSource'])
    return proof


def remote_port(row):
    import base64
    config = base64.b64decode(row['OpenVPN_ConfigData_Base64']).decode()
    return int(next(line.split()[2] for line in config.splitlines() if line.startswith('remote ')))


def publish(candidates, proofs, destination, now):
    by_endpoint = {(p['ip'], p['port']): p for p in proofs}
    successful = [p for p in proofs if p.get('ok')]
    if not successful:
        raise RuntimeError('No real tunnel/download passed; retain previous published feed without renewing its timestamp')
    accepted = []
    for row in candidates:
        proof = by_endpoint.get((row['IP'], remote_port(row)))
        if proof and not proof.get('ok'):
            continue
        row = dict(row)
        row.update(AuroraCheckedAt=proof['checked_at'] if proof else 0,
                   AuroraMbps=proof['mbps'] if proof else 0,
                   AuroraConnectMs=proof['connect_ms'] if proof else 0)
        accepted.append(row)
    accepted.sort(key=lambda r: (-float(r['AuroraMbps']), int(r['AuroraConnectMs']) or 999999))
    stream = io.StringIO()
    stream.write('#aurora_snapshot_timestamp=' + str(now) + '\n*vpn_servers\n')
    writer = csv.DictWriter(stream, fieldnames=COLUMNS, extrasaction='ignore')
    writer.writerow(dict(zip(COLUMNS, ['#HostName'] + COLUMNS[1:])))
    writer.writerows(accepted)
    destination.mkdir(parents=True, exist_ok=True)
    (destination / 'nodes.csv').write_text(stream.getvalue())
    report = dict(generated_at=now, scope='GitHub Actions / Linux container; not phone speed',
                  candidates=len(candidates), tested=len(proofs), passed=len(successful),
                  channels=['VPN Gate + mirror', 'VPNBook', 'AutoOVPN archive (same VPN Gate pool)'],
                  results=proofs)
    (destination / 'status.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    table = ''.join('<tr><td>' + html.escape(p['ip'] + ':' + str(p['port'])) + '</td><td>' +
                    html.escape(p['source']) + '</td><td>' + str(p['connect_ms']) + ' ms</td><td>' +
                    str(p['mbps']) + ' Mbps</td></tr>' for p in sorted(successful, key=lambda p: -p['mbps']))
    page = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>极光 · 公共线路检测</title>
<style>body{font:16px system-ui;background:#101a32;color:#eaf1ff;max-width:960px;margin:48px auto;padding:24px}h1{color:#66e3db}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:14px 8px;border-bottom:1px solid #334564}a{color:#66e3db}small{color:#b2c4df}</style><h1>极光 · 公共线路检测</h1>'''
    page += '<p>独立公开节点 ' + str(len(candidates)) + ' 条；本轮实连 ' + str(len(proofs)) + ' 条，通过 ' + str(len(successful)) + ' 条。</p>'
    page += '<p>更新时间：' + time.strftime('%Y-%m-%d %H:%M UTC', time.gmtime(now / 1000)) + '。每 6 小时计划更新，结果超过 6 小时应用降级为原始源。</p>'
    page += '<p>采集渠道：VPN Gate（含镜像）、VPNBook、AutoOVPN 历史快照。同一 IP 和端口去重；镜像不算独立节点运营商。</p>'
    page += '<p>下表为云端 Linux 容器内真实建隧道、出口变化、HTTPS 和 512 KiB 下载采样结果。不是手机速度，也不保证持续可用或匿名。</p><table><thead><tr><th>节点</th><th>来源</th><th>建隧道</th><th>云端下载采样</th></tr></thead><tbody>' + table + '</tbody></table><p><a href="nodes.csv">应用节点列表</a> · <a href="status.json">完整检测报告</a></p></html>'
    (destination / 'index.html').write_text(page)
    readme = '# 极光公共节点分发\n\n生成时间：' + time.strftime('%Y-%m-%d %H:%M UTC', time.gmtime(now / 1000))
    readme += '\n\n云端实连通过 ' + str(len(successful)) + '/' + str(len(proofs)) + '。这些速度不是手机实测。\n\n[完整报告](status.json) · [节点数据](nodes.csv) · [状态页源码](index.html)\n'
    readme += '\n|节点|来源|云端建隧道|云端下载采样（非手机）|\n|---|---|---|---|\n'
    for p in sorted(successful, key=lambda p: -p['mbps']):
        readme += '| ' + p['ip'] + ':' + str(p['port']) + ' | ' + p['source'] + ' | ' + str(p['connect_ms']) + ' ms | ' + str(p['mbps']) + ' Mbps |\n'
    (destination / 'README.md').write_text(readme)
    return report


if __name__ == '__main__':
    selected = rows(WORK / 'selected.csv')
    with ThreadPoolExecutor(max_workers=3) as pool:
        proofs = list(pool.map(run_check, enumerate(selected)))
    (WORK / 'probe-report.json').write_text(json.dumps(proofs, indent=2))
    report = publish(rows(WORK / 'candidates.csv'), proofs, ROOT / '.checks/published', int(time.time() * 1000))
    print(json.dumps({k: v for k, v in report.items() if k != 'results'}, ensure_ascii=False))
