"""Read exact public box pages and the content-table URLs those pages expose.

Raw downloaded evidence stays under output. 4.8 metadata is kept separate from
the US contents dataset; the latter must not be called official 4.8 drop odds.
"""
import concurrent.futures
import hashlib
import html
import json
import re
import urllib.request
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'output/season-pass/box-research'
IDS=[188052187,188052555,188053068,188053975,188053976]+list(range(188053979,188053990))

def get(url,path):
    if path.exists():return path.read_text(encoding='utf-8')
    req=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'})
    with urllib.request.urlopen(req,timeout=30) as r:data=r.read().decode('utf-8')
    path.write_text(data,encoding='utf-8');return data

def page(item):
    result=dict(id=item,pages={},groups=[])
    for region in ['48','us']:
        url=f'https://aioncodex.com/{region}/item/{item}/?sl=1'
        text=get(url,OUT/f'{item}-{region}.html')
        assert f'ID: {item}' in text,f'Wrong item page: {url}'
        tail=text[text.find(f'ID: {item}'):]
        visible=html.unescape(re.sub('<[^>]+>',' ',tail[:6500]))
        result['pages'][region]=dict(url=url,sha256=hashlib.sha256(text.encode()).hexdigest(),summary=' '.join(visible.split())[:1800])
        for endpoint in re.findall(r'var source\s*=\s*"([^"]+)"',text):
            if 'a=contents' not in endpoint:continue
            assert endpoint.startswith('/query.php?')
            gid=re.search(r'&gid=(\d+)',endpoint).group(1)
            raw=get('https://aioncodex.com'+endpoint,OUT/f'{item}-{region}-{gid}.json')
            rows=json.loads(raw)['aaData']
            clean=[]
            for row in rows:
                clean.append(dict(id=int(row[0]),name=html.unescape(re.sub('<[^>]+>','',str(row[2]))),level=row[3],quantity=row[4],chance=row[5],grade=row[6],race=row[7]))
            result['groups'].append(dict(region=region,gid=int(gid),url='https://aioncodex.com'+endpoint,sha256=hashlib.sha256(raw.encode()).hexdigest(),items=clean))
    return result

def main():
    OUT.mkdir(parents=True,exist_ok=True);results=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        futures={pool.submit(page,i):i for i in IDS}
        for future in concurrent.futures.as_completed(futures):
            try:
                result=future.result();results.append(result)
                print('FOUND',result['id'],'groups',len(result['groups']),'rows',sum(len(g['items']) for g in result['groups']),flush=True)
            except Exception as e:print('UNRESOLVED',futures[future],str(e),flush=True)
    (OUT/'sources.json').write_text(json.dumps(sorted(results,key=lambda r:r['id']),indent=2),encoding='utf-8')
    print('Saved exact-ID research:',OUT/'sources.json')

if __name__=='__main__':main()
