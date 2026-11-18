"""Bind actual native-browser companion checks to unchanged normal builder media."""
import argparse,hashlib,json,os,subprocess,sys,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
NAMES=('bots.html','bots.css','bots.js')
# The page loads the shared translation helper, a config resource of the distribution, not the JAR.
SHARED={'af-i18n.js':('game-server/game-server.zip','game-server/config/central-market/media/af-i18n.js','game-server/config/central-market/media/af-i18n.js')}
WEB_TEXT='client-mods/launcher/chs/web-text.json'
def web_text():return ROOT/WEB_TEXT
RENDERER=('Awesomium.dll','AwesomiumProcess.exe','icudt42.dll')
def sha(path):
 with Path(path).open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def jar(build):return Path(build)/'game-server/game-server-4.8-SNAPSHOT.jar'
def media_bytes(build):
 with zipfile.ZipFile(jar(build)) as archive:
  data={name:archive.read('playerbots/media/'+name) for name in NAMES}
 for name,(artifact,member,source) in SHARED.items():
  with zipfile.ZipFile(Path(build)/artifact) as archive:data[name]=archive.read(member)
 return data
def media_hashes(build):
 return {name:hashlib.sha256(data).hexdigest() for name,data in media_bytes(build).items()}
def artifacts(build):return {artifact:sha(Path(build)/artifact) for artifact,member,shared in SHARED.values()}
def source(name):return ROOT/(SHARED[name][2] if name in SHARED else 'game-server/config/playerbots/media/'+name)
def validate(build,out,evidence=None):
 out=Path(out);e=json.loads((out/'evidence.json').read_text()) if evidence is None else evidence
 assert e['version']==1 and e['exitCode']==0,'Native browser verification failed'
 assert e['gameSha256']==sha(jar(build)) and e['buildManifestSha256']==sha(Path(build)/'build-manifest.json'),'Browser evidence belongs to another normal build'
 assert e['gateSha256']==sha(__file__) and e['checkerSha256']==sha(ROOT/'tools/check-playerbot-ui-behavior.py'),'Browser verification tools changed'
 assert e['artifacts']==artifacts(build),'Browser evidence belongs to another normal distribution'
 assert e['media']==media_hashes(build),'Builder media changed after native-browser verification'
 for name,digest in e['media'].items():
  assert sha(out/'media'/name)==sha(source(name))==digest,'Source/builder/tested media differ: '+name
 assert e['reportSha256']==sha(out/'report/report.json') and e['stdoutSha256']==sha(out/'browser.txt'),'Browser reports changed'
 report=json.loads((out/'report/report.json').read_text())
 assert report['version']==1 and report['passed'] is True and report['authenticatedActions']==43,'Incomplete native companion controls verification'
 expected={str((ROOT/'tools/check-playerbot-ui-behavior.py').resolve()):e['checkerSha256']}
 expected.update({str(Path(e['testedMedia'])/name):digest for name,digest in e['media'].items()})
 assert e['webTextSha256']==sha(web_text()),'Simplified Chinese web text changed after native-browser verification'
 expected[str(web_text().resolve())]=e['webTextSha256']
 expected.update({str(Path(e['browserBin'])/name):sha(Path(e['browserBin'])/name) for name in RENDERER})
 assert report['inputs']==expected,'Native renderer/tested media binding changed'
 assert report['screenshots'] and set(report['screenshots'])=={p.name for p in (out/'report').glob('*.png')},'Missing/extra native visual evidence'
 for name,digest in report['screenshots'].items():assert sha(out/'report'/name)==digest,'Native screenshot changed: '+name
 assert 'OK: native Aion WebKit' in (out/'browser.txt').read_text(encoding='utf-8'),'Missing browser success result'
 return e
def run(build,out,browser):
 out=Path(out).resolve()
 if out==ROOT or ROOT in out.parents or out==Path(browser).resolve().parent or Path(browser).resolve().parent in out.parents:raise ValueError('Native browser evidence must stay outside source and installations')
 out.mkdir(parents=True,exist_ok=True)
 (out/'evidence.json').unlink(missing_ok=True)
 media=out/'media';media.mkdir(exist_ok=True)
 for name,data in media_bytes(build).items():(media/name).write_bytes(data)
 before=dict(version=1,webTextSha256=sha(web_text()),artifacts=artifacts(build),gameSha256=sha(jar(build)),buildManifestSha256=sha(Path(build)/'build-manifest.json'),gateSha256=sha(__file__),checkerSha256=sha(ROOT/'tools/check-playerbot-ui-behavior.py'),media=media_hashes(build),testedMedia=str(media),browserBin=str(Path(browser).resolve()))
 result=subprocess.run([sys.executable,str(ROOT/'tools/check-playerbot-ui-behavior.py'),'--media',str(media),'--output',str(out/'report'),'--browser-bin',before['browserBin']],cwd=ROOT,capture_output=True,text=True,encoding='utf-8',env=os.environ|{'PYTHONIOENCODING':'utf-8','PYTHONOPTIMIZE':'0'})
 (out/'browser.txt').write_text(result.stdout+result.stderr,encoding='utf-8')
 if result.returncode:raise RuntimeError('Native companion browser verification failed; see '+str(out/'browser.txt'))
 before.update(exitCode=0,reportSha256=sha(out/'report/report.json'),stdoutSha256=sha(out/'browser.txt'))
 validate(build,out,before)
 (out/'evidence.json').write_text(json.dumps(before,indent=2),encoding='utf-8')
 print('OK: exact normal builder companion media / 43 native browser actions / bound screenshot and renderer evidence')
def main():
 if not __debug__:raise RuntimeError('Browser validation requires assertions')
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--browser-bin',type=Path,required=True);a=p.parse_args();run(a.build,a.output,a.browser_bin)
if __name__=='__main__':main()
