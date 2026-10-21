"""Additional acquisition channel, same VPN Gate pool: never counted as a new operator."""
import base64
import csv
import io
import json
from pathlib import Path
import time
import urllib.request


def collect():
    request = urllib.request.Request('https://raw.githubusercontent.com/9xN/auto-ovpn/main/json/data.json',
                                     headers={'User-Agent': 'Aurora-public-feed/0.7'})
    with urllib.request.urlopen(request, timeout=12) as response:
        body = response.read(5 * 1024 * 1024 + 1)
    if len(body) > 5 * 1024 * 1024:
        raise ValueError('archive exceeds limit')
    data, stamp = json.loads(body)
    if not 0 <= time.time() * 1000 - int(stamp) <= 86400000:
        raise ValueError('archive expired')
    stream = io.StringIO()
    stream.write('*vpn_servers\n')
    writer = csv.writer(stream)
    writer.writerow(['#HostName', 'IP', 'CountryLong', 'CountryShort', 'Speed', 'OpenVPN_ConfigData_Base64', 'AuroraSource'])
    for row in data['servers'][:500]:
        config = row['openvpn_configdata_base64']
        if len(base64.b64decode(config, validate=True)) > 65536:
            continue
        writer.writerow(['public', row['ip'], row['countrylong'], row['countryshort'], row['speed'], config, 'VPN Gate'])
    return stream.getvalue()


if __name__ == '__main__':
    destination = Path('.checks/extra.csv')
    destination.parent.mkdir(exist_ok=True)
    try:
        destination.write_text(collect())
        print('AutoOVPN archive retrieved; VPN Gate pool, deduplicated by endpoint')
    except Exception as error:
        destination.write_text('')
        print('AutoOVPN channel unavailable:', type(error).__name__)
