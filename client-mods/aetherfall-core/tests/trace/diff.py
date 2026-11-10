"""Readable first exact ordered trace difference, with saved-file byte details."""
import argparse
import base64
from collections import deque
import difflib
import gzip
import json
from pathlib import Path


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('reference',type=Path)
    p.add_argument('current',type=Path)
    p.add_argument('--context',type=int,default=3)
    a=p.parse_args()
    before=deque(maxlen=a.context)
    with gzip.open(a.reference,'rt',encoding='utf-8') as left,gzip.open(a.current,'rt',encoding='utf-8') as right:
        line=0
        while True:
            line+=1
            old,new=left.readline(),right.readline()
            if old!=new:
                print(f'First exact difference: record {line}')
                for row in before:print('  '+row.rstrip())
                print('- '+old.rstrip());print('+ '+new.rstrip())
                try:
                    x,y=json.loads(old),json.loads(new)
                    if x[1]=='saved' and y[1]=='saved':
                        oldbytes,newbytes=base64.b64decode(x[3][0]),base64.b64decode(y[3][0])
                        print(f'Saved bytes: reference {len(oldbytes)}, current {len(newbytes)}')
                        for part in difflib.unified_diff(oldbytes.decode().splitlines(keepends=True),newbytes.decode().splitlines(keepends=True),fromfile='reference/'+x[2],tofile='current/'+y[2]):print(part,end='')
                except (ValueError,IndexError):pass
                for _ in range(a.context):
                    print('- '+left.readline().rstrip());print('+ '+right.readline().rstrip())
                raise SystemExit(1)
            if not old:break
            before.append(old)
    print(f'PASS: {line-1} records identical, including order and exact saved bytes')


if __name__=='__main__':main()
