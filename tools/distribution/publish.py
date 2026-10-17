"""Update mirror metadata and the small downloader ZIP without repacking game files."""
import argparse
import hashlib
import json
import urllib.parse
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


def decode(data):
    def read(pos):
        tag = data[pos:pos+1]
        if tag == b'i':
            end = data.index(b'e', pos)
            return int(data[pos+1:end]), end+1
        if tag in (b'l', b'd'):
            result = [] if tag == b'l' else {}
            pos += 1
            while data[pos:pos+1] != b'e':
                value, pos = read(pos)
                if tag == b'l':
                    result.append(value)
                else:
                    other, pos = read(pos)
                    result[value] = other
            return result, pos+1
        colon = data.index(b':', pos)
        size = int(data[pos:colon])
        return data[colon+1:colon+1+size], colon+1+size
    result, end = read(0)
    if end != len(data):
        raise ValueError('Extra torrent data')
    return result


def encode(value):
    if isinstance(value, str):
        value = value.encode()
    if isinstance(value, bytes):
        return str(len(value)).encode()+b':'+value
    if isinstance(value, int):
        return b'i'+str(value).encode()+b'e'
    if isinstance(value, list):
        return b'l'+b''.join(encode(v) for v in value)+b'e'
    return b'd'+b''.join(encode(k)+encode(v) for k, v in sorted(value.items()))+b'e'


def https(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != 'https' or not parsed.netloc or parsed.username or any(c.isspace() for c in url):
        raise ValueError('Expected a public direct HTTPS URL: '+url)
    return url


def publish(root, mirrors_url=None, trackers=None):
    downloader = root/'Aetherfall-Downloader'
    manifest = json.loads((downloader/'release-manifest.json').read_text())
    mirrors = json.loads((downloader/'mirrors.json').read_text())
    if mirrors['schema'] != 1 or mirrors['release'] != manifest['version']:
        raise ValueError('Mirror release does not match')
    bases = [https(u) for u in mirrors['WebSeeds']]
    if any(not u.endswith('/') for u in bases):
        raise ValueError('Multi-file webseed bases must end in /')
    allowed = {f['name'] for f in manifest['files']}
    if set(mirrors['Files'])-allowed:
        raise ValueError('Unknown archive name in mirror list')
    for urls in mirrors['Files'].values():
        if not isinstance(urls, list):
            raise ValueError('Each archive maps to an array of direct URLs')
        for url in urls:
            https(url)
    torrent = root/manifest['torrentFile']
    meta = decode(torrent.read_bytes())
    if hashlib.sha1(encode(meta[b'info'])).hexdigest() != manifest['infoHash']:
        raise ValueError('Torrent content changed')
    if trackers:
        for url in trackers:
            parsed = urllib.parse.urlsplit(url)
            if parsed.scheme not in {'udp', 'https'} or not parsed.netloc or any(c.isspace() for c in url):
                raise ValueError('Invalid tracker URL')
        meta[b'announce'] = trackers[0].encode()
        meta[b'announce-list'] = [[url.encode()] for url in trackers]
    if bases:
        meta[b'url-list'] = [u.encode() for u in bases]
    else:
        meta.pop(b'url-list', None)
    content = encode(meta)
    torrent.write_bytes(content)
    (downloader/torrent.name).write_bytes(content)
    manifest['torrentSha256'] = hashlib.sha256(content).hexdigest()
    magnet_name = manifest.get('torrentFolder') or manifest['files'][0]['name']
    magnet = 'magnet:?xt=urn:btih:'+manifest['infoHash']+'&dn='+urllib.parse.quote(magnet_name)
    for tier in meta.get(b'announce-list', [[meta[b'announce']]]):
        for tracker in tier:
            magnet += '&tr='+urllib.parse.quote(tracker.decode(), safe='')
    (root/'magnet.txt').write_text(magnet+'\n')
    for path in [root/'release-manifest.json', downloader/'release-manifest.json']:
        path.write_text(json.dumps(manifest, indent=2))
    if mirrors_url:
        path = downloader/'settings.json'
        settings = json.loads(path.read_text())
        settings['MirrorsUrl'] = https(mirrors_url)
        path.write_text(json.dumps(settings, indent=2))
    namespace = 'urn:ietf:params:xml:ns:metalink'
    ET.register_namespace('', namespace)
    document = ET.Element('{'+namespace+'}metalink')
    complete = True
    prefix = (manifest['torrentFolder']+'/') if manifest.get('torrentFolder') else ''
    for file in manifest['files']:
        element = ET.SubElement(document, '{'+namespace+'}file', name=prefix+file['name'])
        ET.SubElement(element, '{'+namespace+'}size').text = str(file['bytes'])
        ET.SubElement(element, '{'+namespace+'}hash', type='sha-256').text = file['sha256']
        urls = list(mirrors['Files'].get(file['name'], []))
        urls += [base+urllib.parse.quote(prefix+file['name'], safe='/') for base in bases]
        if not urls:
            complete = False
        for priority, url in enumerate(urls, 1):
            ET.SubElement(element, '{'+namespace+'}url', priority=str(priority)).text = url
    metalink = root/'repack.meta4'
    if complete:
        ET.ElementTree(document).write(metalink, encoding='utf-8', xml_declaration=True)
    elif metalink.exists():
        metalink.unlink()
    with zipfile.ZipFile(root/'Aetherfall-Downloader.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(downloader.rglob('*')):
            if file.is_file() and file.suffix not in {'.lock', '.log'} and file.name not in {'mirrors-live.json', 'http-downloads.txt', 'dht.dat'} and not any(v in {'Downloads','Games'} for v in file.relative_to(downloader).parts):
                archive.write(file, 'Aetherfall-Downloader/'+file.relative_to(downloader).as_posix())
    print('Downloader ZIP refreshed; torrent swarm unchanged:', manifest['infoHash'])
    print('HTTP Metalink ready:', complete)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('distribution', type=Path)
    parser.add_argument('--mirrors-url')
    parser.add_argument('--tracker', action='append')
    args = parser.parse_args()
    publish(args.distribution.resolve(strict=True), args.mirrors_url, args.tracker)
