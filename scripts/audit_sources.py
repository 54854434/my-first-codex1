"""Read official public source pages and inspect published download entry points.
No account creation, challenges, private feeds or arbitrary page-script execution.
"""
import concurrent.futures
import json
from html.parser import HTMLParser
from pathlib import Path
import re
import urllib.request
import urllib.parse

OUT = Path('.checks/source-audit')
OUT.mkdir(parents=True, exist_ok=True)
PAGES = {
    'vpnbook': 'https://www.vpnbook.com/freevpn/openvpn',
    'ipspeed': 'https://ipspeed.info/free-openvpn.php',
    'vpngate-mirrors': 'https://www.vpngate.net/en/sites.aspx',
    'gate-github-mirror': 'https://raw.githubusercontent.com/GeorgeXie2333/vpngate-list-mirror/main/data/vpngate.csv',
}

def get(url):
    req=urllib.request.Request(url,headers={'User-Agent':'AuroraFreeVPN/0.5 source compatibility audit'})
    with urllib.request.urlopen(req, timeout=15) as response:
        body=response.read(2*1024*1024+1)
        if len(body)>2*1024*1024: raise ValueError('Response exceeds bound')
        return response.geturl(),body.decode('utf-8','replace')

class Links(HTMLParser):
    def __init__(self): super().__init__();self.links=[];self.scripts=[]
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if 'href' in a:self.links.append(a['href'])
        if tag=='script' and 'src' in a:self.scripts.append(a['src'])

def inspect(item):
    name,url=item
    report={'source':name,'url':url}
    try:
        final,body=get(url);report.update(final_url=final,bytes=len(body))
        (OUT/(name+'.txt')).write_text(body)
        if name=='gate-github-mirror':
            report['csv_header']=next((x for x in body.splitlines() if x.startswith('#HostName,')),None)
        else:
            parser=Links();parser.feed(body)
            report['downloads']=[urllib.parse.urljoin(final,x) for x in parser.links if any(k in x.lower() for k in ('.ovpn','.zip','download','api/'))][:30]
            report['scripts']=[urllib.parse.urljoin(final,x) for x in parser.scripts if urllib.parse.urlparse(urllib.parse.urljoin(final,x)).netloc==urllib.parse.urlparse(final).netloc][:24]
            report['entry_lines']=[x.strip()[:1500] for x in body.splitlines() if re.search(r'(ovpn|download|password|fetch\(|data-server)',x,re.I)][:50]
            for index,script in enumerate(report['scripts']):
                try:
                    _,js=get(script);(OUT/f'{name}-script-{index}.txt').write_text(js)
                    report.setdefault('script_hints',[]).extend(re.findall(r'.{0,100}(?:download|\.ovpn|fetch\(|/api/).{0,180}',js)[:60])
                except Exception as error:report.setdefault('script_errors',[]).append(str(error))
    except Exception as error:report['error']=str(error)
    return report

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    reports=list(pool.map(inspect,PAGES.items()))
(OUT/'report.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2))
for r in reports:print(json.dumps({k:v for k,v in r.items() if k not in ('entry_lines','script_hints')},ensure_ascii=False))
