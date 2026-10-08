"""Isolated linkage-gate regressions, with no native world or database execution."""
import argparse,subprocess,zipfile
from pathlib import Path
from verify_runtime_linkage import verify
import stage_companion_update as shared

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    out=a.output.resolve();shared.validate_output(out);out.mkdir(parents=True,exist_ok=False)
    source=out/'src/com/aionemu/linkagefixture';source.mkdir(parents=True)
    (source/'Dep.java').write_text('''package com.aionemu.linkagefixture;
public class Dep { public static int value=1; public static int number(){return value;} }
''')
    (source/'Caller.java').write_text('''package com.aionemu.linkagefixture;
public class Caller {
 static { throwIfInitialized(); }
 static void throwIfInitialized(){throw new AssertionError("Game-style initializer executed");}
 public int run(){return Dep.number()+Dep.value;}
}''')
    (source/'Lambda.java').write_text('''package com.aionemu.linkagefixture;
public class Lambda { public java.util.function.IntSupplier make(){return Dep::number;} }''')
    subprocess.run(['javac','-d',str(out/'classes')]+list(map(str,source.glob('*.java'))),check=True)
    prefix='com/aionemu/linkagefixture/'
    def jar(label,names,dep=None):
        path=out/(label+'.jar')
        with zipfile.ZipFile(path,'w') as z:
            for name in names:z.write((dep if name=='Dep' and dep else out/'classes')/(prefix+name+'.class'),prefix+name+'.class')
        return path
    assert 'OK:' in verify(jar('complete',['Caller','Dep','Lambda']),out/'complete-audit')
    count=1
    def reject(label,names,expected,dep=None):
        nonlocal count
        try:verify(jar(label,names,dep),out/(label+'-audit'))
        except RuntimeError as e:assert expected in str(e),str(e)
        else:raise AssertionError(label+' was accepted')
        count+=1
    reject('missing-class',['Caller'],'missing class '+prefix+'Dep')
    reject('lambda-missing-class',['Lambda'],'missing class '+prefix+'Dep')
    for label,method,error in [('missing-method','', 'missing member '+prefix+'Dep.number()I'),
                               ('wrong-kind','public int number(){return 1;}', 'static/instance mismatch '+prefix+'Dep.number')]:
        alternate=out/label;alternate.mkdir()
        (source/'Dep.java').write_text('package com.aionemu.linkagefixture; public class Dep {public static int value=1;'+method+'}')
        subprocess.run(['javac','-d',str(alternate),str(source/'Dep.java')],check=True)
        reject(label,['Caller','Dep'],error,alternate)
    print('OK: '+str(count)+' linkage regressions; missing types/lambda targets/members/static kinds detected; initializers never executed')

if __name__=='__main__':main()
