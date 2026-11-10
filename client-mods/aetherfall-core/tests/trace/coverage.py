"""Lua 5.1 instruction line tables: report executed and unreached source lines."""
from collections import Counter
import re
import struct
from lupa.lua51 import LuaRuntime


def executable_lines(source):
    lua=LuaRuntime(encoding=None)
    raw=lua.eval(b'function(s) return string.dump(assert(loadstring(s))) end')(source)
    assert raw[:6]==b'\x1bLua\x51\x00'
    endian='<' if raw[6] else '>'
    integer,size_t,instruction,number=raw[7:11]
    at=12
    lines=set()
    def take(n):
        nonlocal at
        value=raw[at:at+n];at+=n;return value
    def uint(n=integer):return int.from_bytes(take(n),'little' if endian=='<' else 'big')
    def string():take(uint(size_t))
    def function():
        string();uint();uint();take(4)
        take(uint()*instruction)
        for _ in range(uint()):
            kind=uint(1)
            if kind==1:take(1)
            elif kind==3:take(number)
            elif kind==4:string()
            else:assert kind==0,kind
        for _ in range(uint()):function()
        for _ in range(uint()):lines.add(uint())
        for _ in range(uint()):string();uint();uint()
        for _ in range(uint()):string()
    function()
    assert at==len(raw)
    return lines-{0}


def report(sources,coverage):
    result={}
    for name,raw in sources.items():
        executable=executable_lines(raw)
        reached={int(n) for n in coverage.get(name,{})}
        missing=sorted(executable-reached)
        text=raw.decode('utf-8-sig').splitlines()
        sections=[]
        starts=[(1,'preamble')]+[(i,line.strip('- ')) for i,line in enumerate(text,1) if line.startswith('-- ----------')]
        for pos,(first,label) in enumerate(starts):
            last=starts[pos+1][0]-1 if pos+1<len(starts) else len(text)
            active=executable&set(range(first,last+1))
            hits=active&reached
            sections.append({'section':label,'first':first,'last':last,'executableLines':len(active),'reachedLines':len(hits),'unreachedLines':sorted(active-reached)})
        result[name]={'executableLines':len(executable),'reachedLines':len(executable&reached),'percent':round(100*len(executable&reached)/len(executable),2),'unreached':[{'line':n,'text':text[n-1]} for n in missing],'sections':sections}
    return result
