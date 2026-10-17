"""Create upload volumes, torrent, hashes and the small Windows downloader from a repack."""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import time
import urllib.parse
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
TRACKERS = ['udp://tracker.opentrackr.org:1337/announce']


def bencode(value):
    if isinstance(value, str):
        value = value.encode('utf-8')
    if isinstance(value, bytes):
        return str(len(value)).encode()+b':'+value
    if isinstance(value, int):
        return b'i'+str(value).encode()+b'e'
    if isinstance(value, list):
        return b'l'+b''.join(bencode(v) for v in value)+b'e'
    if isinstance(value, dict):
        return b'd'+b''.join(bencode(k)+bencode(v) for k, v in sorted(value.items()))+b'e'
    raise TypeError(type(value))


def js(path, value):
    path.write_text(json.dumps(value, indent=2), encoding='utf-8')


def build(package, output, tools, sevenzip):
    release = json.loads((package/'REPACK.json').read_text(encoding='utf-8-sig'))
    version = release['productVersion']
    label = 'Aetherfall-'+version
    if output.exists():
        raise ValueError('Use a fresh output directory; existing volumes are never overwritten')
    if output.is_relative_to(package) or package.is_relative_to(output):
        raise ValueError('Distribution output must be separate from the repack')
    raw_index = (package/'FILES.json').read_bytes()
    index = json.loads(raw_index)
    names = [entry['path'] for entry in index['files']]+['FILES.json']
    names = sorted(set(names), key=str.casefold)
    if len({name.casefold() for name in names}) != len(names):
        raise ValueError('Case-colliding package paths')
    actual = set()
    for folder, directories, files in os.walk(package, followlinks=False):
        directories[:] = [name for name in directories if not (Path(folder)/name).is_symlink()]
        prefix = Path(folder).relative_to(package).as_posix()
        if prefix == '.':
            prefix = ''
        for name in files:
            actual.add((prefix+'/'+name if prefix else name).casefold())
    for name in names:
        path = Path(name)
        if path.is_absolute() or '..' in path.parts or '\n' in name or '\r' in name or '"' in name:
            raise ValueError('Unsafe package path: '+name)
        if name.casefold() not in actual:
            raise ValueError('Package file missing: '+name)
    total = sum(entry['bytes'] for entry in index['files'])+len(raw_index)
    if shutil.disk_usage(output.parent).free < total+2*1024**3:
        raise ValueError('Allow the package size plus 2 GiB free on the archive output drive')
    output.mkdir(parents=True)
    archive_file = output/(label+'.7z')
    listing = output/'archive-files.txt'
    listing.write_text('\n'.join('"'+name+'"' for name in names), encoding='utf-8')
    print('Packing', len(names), 'indexed package files into single archive', archive_file.name, flush=True)
    command = [str(sevenzip), 'a', '-t7z', '-mx=0', '-ms=off', '-scsUTF-8', '-spd',
               '-bso1', '-bsp1', str(archive_file), '@'+str(listing)]
    with (output/'packing.log').open('w', encoding='utf-8') as log:
        result = subprocess.run(command, cwd=package, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError('7-Zip failed; see '+str(output/'packing.log'))
    if (package/'FILES.json').read_bytes() != raw_index or json.loads((package/'REPACK.json').read_text()) != release:
        raise RuntimeError('Repack changed during packing; do not publish this archive')
    if not archive_file.is_file():
        raise RuntimeError('No archive file produced')
    piece_size = 4*1024**2
    pending = bytearray()
    pieces, files = [], []
    sha = hashlib.sha256()
    with archive_file.open('rb') as stream:
        while chunk := stream.read(piece_size):
            sha.update(chunk)
            pending.extend(chunk)
            while len(pending) >= piece_size:
                pieces.append(hashlib.sha1(pending[:piece_size]).digest())
                del pending[:piece_size]
    if pending:
        pieces.append(hashlib.sha1(pending).digest())
    files.append(dict(name=archive_file.name, bytes=archive_file.stat().st_size, sha256=sha.hexdigest()))
    print('Hashed archive:', archive_file.name, flush=True)
    info = {'name': archive_file.name, 'piece length': piece_size, 'pieces': b''.join(pieces),
            'length': files[0]['bytes']}
    torrent = {'announce': TRACKERS[0], 'announce-list': [[t] for t in TRACKERS],
               'created by': 'Aetherfall Repack Packer', 'creation date': int(time.time()), 'info': info}
    torrent_bytes = bencode(torrent)
    info_hash = hashlib.sha1(bencode(info)).hexdigest()
    manifest = dict(schema=1, product='Aion Aetherfall Repack', version=version, installFolder=label,
        torrentFolder='', torrentFile=label+'.torrent', torrentSha256=hashlib.sha256(torrent_bytes).hexdigest(),
        infoHash=info_hash, archiveBytes=files[0]['bytes'], installedBytes=total,
        packageFilesIndexSha256=hashlib.sha256(raw_index).hexdigest(), files=files)
    (output/(label+'.torrent')).write_bytes(torrent_bytes)
    js(output/'release-manifest.json', manifest)
    (output/'SHA256SUMS.txt').write_text(''.join(f['sha256']+'  '+f['name']+'\n' for f in files))
    magnet = 'magnet:?xt=urn:btih:'+info_hash+'&dn='+urllib.parse.quote(archive_file.name)
    magnet += ''.join('&tr='+urllib.parse.quote(t, safe='') for t in TRACKERS)
    (output/'magnet.txt').write_text(magnet+'\n')
    downloader = output/'Aetherfall-Downloader'
    (downloader/'bin').mkdir(parents=True)
    (downloader/'licenses/aria2').mkdir(parents=True)
    (downloader/'licenses/7zip').mkdir(parents=True)
    aria = tools/'aria2/aria2-1.37.0-win-64bit-build1'
    for name in ['aria2c.exe']:
        shutil.copy2(aria/name, downloader/'bin'/name)
    shutil.copy2(tools/'7zip/x64/7za.exe', downloader/'bin/7za.exe')
    for name in ['COPYING', 'LICENSE.OpenSSL', 'README.mingw', 'AUTHORS']:
        shutil.copy2(aria/name, downloader/'licenses/aria2'/name)
    js(downloader/'licenses/tool-sources.json', dict(aria2=dict(version='1.37.0', binaryUrl='https://github.com/aria2/aria2/releases/download/release-1.37.0/aria2-1.37.0-win-64bit-build1.zip', sourceUrl='https://github.com/aria2/aria2/archive/refs/tags/release-1.37.0.tar.gz'), sevenzip=dict(version='26.04', binaryUrl='https://github.com/ip7z/7zip/releases/download/26.04/7z2604-extra.7z', sourceUrl='https://github.com/ip7z/7zip/releases/tag/26.04')))
    for name in ['License.txt', 'readme.txt']:
        source = tools/'7zip'/name
        if source.exists():
            shutil.copy2(source, downloader/'licenses/7zip'/name)
    for name in ['Download.ps1', 'Start-Download.cmd', 'README.txt']:
        shutil.copy2(HERE/name, downloader/name)
    shutil.copy2(output/'release-manifest.json', downloader/'release-manifest.json')
    shutil.copy2(output/(label+'.torrent'), downloader/(label+'.torrent'))
    js(downloader/'mirrors.json', dict(schema=1, release=version, WebSeeds=[], Files={f['name']: [] for f in files}, SharePages=[]))
    js(downloader/'settings.json', dict(MirrorsUrl='', Mode='Auto', DownloadDirectory='Downloads', InstallDirectory='Games'))
    js(downloader/'bin/SHA256.json', {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (downloader/'bin').glob('*.exe')})
    shutil.copy2(HERE/'publish.py', output/'publish.py')
    shutil.copy2(HERE/'UPLOAD.txt', output/'UPLOAD.txt')
    seed = (
        '@echo off\r\npushd "%~dp0"\r\n'
        'if not exist "dht.dat" powershell -NoLogo -NoProfile -Command "$b=New-Object byte[] 56;$b[0]=0xA1;$b[1]=0xA2;$b[2]=2;$b[7]=3;$r=[Security.Cryptography.RandomNumberGenerator]::Create();$n=New-Object byte[] 20;$r.GetBytes($n);[Array]::Copy($n,0,$b,24,20);[IO.File]::WriteAllBytes(\'dht.dat\',$b)"\r\n'
        '"Aetherfall-Downloader\\bin\\aria2c.exe" --no-conf=true --check-integrity=true --seed-ratio=0 --enable-dht=true --dht-file-path=dht.dat --dht-entry-point=router.bittorrent.com:6881 --enable-peer-exchange=true --bt-enable-lpd=true --max-overall-upload-limit=2M --dir=. "'+label+'.torrent"\r\npopd\r\npause\r\n'
    )
    (output/'Start-Seeding.cmd').write_text(seed)
    with zipfile.ZipFile(output/'Aetherfall-Downloader.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(downloader.rglob('*')):
            if file.is_file():
                archive.write(file, 'Aetherfall-Downloader/'+file.relative_to(downloader).as_posix())
    js(output/'build-receipt.json', dict(status='ready locally', package=str(package), output=str(output),
        version=version, sourceFiles=len(names), volumes=len(files), archiveBytes=manifest['archiveBytes'],
        infoHash=info_hash, mirrorsConfigured=False, uploaded=False, seedingStarted=False,
        sourcePackageModified=False, metadata='SHA256 per volume and native cross-volume torrent piece hashes'))
    print('READY:', output, flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--tools', type=Path, required=True)
    parser.add_argument('--sevenzip', type=Path, default=Path(r'C:\Program Files\7-Zip\7z.exe'))
    args = parser.parse_args()
    build(args.package.resolve(strict=True), args.output.resolve(), args.tools.resolve(strict=True), args.sevenzip)
