"""Exercise both chained Game gates and the passive probe in an isolated browser."""
import argparse
import ctypes as c
import http.server
import json
import os
from pathlib import Path
import struct
import threading
import time
from stage import PE,SITES

def gates(root):
    data=(root/'bin64/Game.dll').read_bytes();pe=PE(data);manifest=json.loads((root/'manifest.json').read_text())
    k=c.WinDLL('kernel32',use_last_error=True)
    k.VirtualAlloc.argtypes=[c.c_void_p,c.c_size_t,c.c_ulong,c.c_ulong];k.VirtualAlloc.restype=c.c_void_p
    k.VirtualFree.argtypes=[c.c_void_p,c.c_size_t,c.c_ulong]
    base=k.VirtualAlloc(None,pe.image_size,0x3000,0x40);assert base
    def put(rva,value):c.memmove(base+rva,value,len(value))
    try:
        for _,_,rva,length,raw,*_ in pe.sections:put(rva,data[raw:raw+length])
        seen=[]
        @c.CFUNCTYPE(c.c_int)
        def initialize():seen.append('initialize');return 1
        @c.CFUNCTYPE(None)
        def tick():seen.append('tick')
        for i,fn in enumerate([initialize,tick]):put(manifest['iat']+i*8,struct.pack('<Q',c.cast(fn,c.c_void_p).value))
        captured=(c.c_uint64*4)()
        stub=b'\x48\xb8'+struct.pack('<Q',c.addressof(captured))+bytes.fromhex('488908488950084c8940104c894818b8efbe0000c3')
        for old_gate in manifest['priorGates']:put(old_gate,stub)
        for site in SITES:
            fn=c.CFUNCTYPE(c.c_int,c.c_uint64,c.c_uint64,c.c_uint64,c.c_uint64)(base+site)
            for args in [(1,2,3,4),(0xffffffffffffffff,0x123456789abcdef0,0,0xabcdef),(0,0,0,0)]:
                assert fn(*args)==0xbeef
                assert tuple(captured)==args
        assert seen==['initialize']*3+['tick']*3
        print('PASS: both diagnostic gates preserve arguments, stack, imports and chain to the installed original gates')
    finally:k.VirtualFree(base,0,0x8000)

def browser(root,binpath):
    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self,*unused):pass
        def do_GET(self):
            body=b'<html><body style="margin:0;background:#173956;color:white"><h1>Browser fixture</h1><p>Opaque surface</p></body></html>'
            self.send_response(200);self.send_header('Content-Type','text/html');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
    server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
    directory=os.add_dll_directory(str(binpath));dll=c.CDLL(str(binpath/'Awesomium.dll'));ptr=c.c_void_p
    def api(name,result,*params):
        fn=getattr(dll,name);fn.restype=result;fn.argtypes=list(params);return fn
    initialize=api('awe_webcore_initialize_default',None);update=api('awe_webcore_update',None);shutdown=api('awe_webcore_shutdown',None)
    create=api('awe_webcore_create_webview',ptr,c.c_int,c.c_int,c.c_bool);destroy=api('awe_webview_destroy',None,ptr)
    make=api('awe_string_create_from_wide',ptr,c.c_wchar_p,c.c_size_t);free=api('awe_string_destroy',None,ptr)
    load=api('awe_webview_load_url',None,ptr,ptr,ptr,ptr,ptr);render=api('awe_webview_render',ptr,ptr)
    pixels=api('awe_renderbuffer_get_buffer',ptr,ptr);width=api('awe_renderbuffer_get_width',c.c_int,ptr);height=api('awe_renderbuffer_get_height',c.c_int,ptr);stride=api('awe_renderbuffer_get_rowspan',c.c_int,ptr)
    copy=api('awe_renderbuffer_copy_to',None,ptr,ptr,c.c_int,c.c_int,c.c_bool,c.c_bool)
    resize=api('awe_webview_resize',None,ptr,c.c_int,c.c_int,c.c_bool,c.c_int)
    transparent=api('awe_webview_set_transparent',None,ptr,c.c_bool)
    initialize();view=None
    def pump(seconds):
        end=time.monotonic()+seconds
        while time.monotonic()<end:update();time.sleep(.01)
    def image(w,h):
        buf=render(view);assert buf and (width(buf),height(buf))==(w,h)
        pitch=stride(buf);expected=c.string_at(pixels(buf),pitch*h);out=c.create_string_buffer(pitch*h)
        copy(buf,out,pitch,4,False,False);assert out.raw==expected,'Copy changed pixel contents'
        flipped=c.create_string_buffer(pitch*h);copy(buf,flipped,pitch,4,False,True)
        assert flipped.raw==b''.join(expected[y*pitch:(y+1)*pitch] for y in range(h-1,-1,-1))
        rgba=c.create_string_buffer(pitch*h);copy(buf,rgba,pitch,4,True,False)
        reference=bytearray(expected);reference[0::4],reference[2::4]=reference[2::4],reference[0::4]
        assert rgba.raw==reference
        return expected
    try:
        view=create(320,200,False);transparent(view,True)
        url=f'http://127.0.0.1:{server.server_port}/';value=make(url,len(url));empty=make('',0)
        load(view,value,empty,empty,empty);free(value);free(empty);pump(2)
        before=image(320,200)
        probe=c.CDLL(str(root/'bin64/AionBrowserProbe.dll'));probe.AionBrowserProbeInitialize.restype=c.c_int
        assert probe.AionBrowserProbeInitialize()==1
        assert image(320,200)==before,'Render changed pixel contents'
        for _ in range(30):assert image(320,200)==before
        resize(view,400,240,True,1000);pump(1);image(400,240)
        # Test natural initial transparent frames too: these are diagnostics,
        # not justification to modify legitimate navigation surfaces.
        second=create(128,128,False);transparent(second,True);render(second);destroy(second)
        pump(9.2);probe.AionBrowserProbeTick();logs=list((root/'Logs').glob('BrowserFrames.*.log'));log=next(p for p in logs if p.name==f'BrowserFrames.{os.getpid()}.log').read_text()
        assert 'status=sample' in log and 'resizes=1' in log and 'copies=96' in log,log
        print('PASS: original BGRA pixels, RGBA conversion, vertical flip, transparent buffers, repeated render and resize survive the probe')
    finally:
        if view:destroy(view)
        shutdown();server.shutdown();directory.close()

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--staged',type=Path,required=True);parser.add_argument('--browser-bin',type=Path,required=True);args=parser.parse_args()
    gates(args.staged.resolve());browser(args.staged.resolve(),args.browser_bin.resolve())
