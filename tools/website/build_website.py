"""Normal Composer/Vite build and portable website packaging; all output is external."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import zipfile


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--php', type=Path, required=True)
    parser.add_argument('--composer', type=Path, required=True)
    parser.add_argument('--php-archive', type=Path, required=True)
    parser.add_argument('--php-sha256', required=True)
    parser.add_argument('--resume', action='store_true', help='Resume a failed external build using the unchanged installed dependency lock')
    parser.add_argument('--reuse-built-assets', action='store_true', help='Repackage interpreted PHP changes when the frontend inputs and CSS classes are unchanged')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    out = args.output.resolve()
    if (out.exists() and not args.resume) or out.is_relative_to(repo):
        raise ValueError('Choose a fresh output directory outside the source repository')
    if digest(args.php_archive) != args.php_sha256:
        raise ValueError('PHP archive does not match the verified official checksum')
    app = out / 'build/app'
    reuse_assets = args.reuse_built_assets
    if reuse_assets:
        if not args.resume or not (app/'public/build/manifest.json').is_file():
            raise ValueError('Asset reuse requires a completed previous frontend build')
        for source in (repo/'website/resources').rglob('*'):
            if not source.is_file():
                continue
            relative = source.relative_to(repo/'website')
            old = (app/relative).read_text(encoding='utf-8')
            new = source.read_text(encoding='utf-8')
            if relative.suffix in {'.css','.js'} and old != new:
                raise ValueError('Frontend source changed: '+str(relative))
            if relative.name.endswith('.blade.php'):
                pattern = r'<style\b[^>]*>.*?</style>|\bclass\s*=\s*[\"\'][^\"\']*[\"\']'
                if re.findall(pattern,old,re.S) != re.findall(pattern,new,re.S):
                    raise ValueError('Frontend styles/classes changed: '+str(relative))
    out.mkdir(parents=True, exist_ok=args.resume)
    shutil.copytree(repo/'website', app, ignore=shutil.ignore_patterns(
        '.git', '.env', 'vendor', 'node_modules', 'build', 'hot', '*.sqlite*'), dirs_exist_ok=args.resume)
    env = os.environ.copy()
    env.update(COMPOSER_HOME=str(out/'build/composer-home'), COMPOSER_CACHE_DIR=str(out/'build/composer-cache'))
    php = [str(args.php.resolve()), '-d', 'extension_dir='+str(args.php.resolve().parent/'ext')]
    commands = [
        php+[str(args.composer.resolve()), 'install', '--no-dev', '--prefer-dist', '--no-interaction', '--no-scripts', '--no-autoloader'],
        php+[str(args.composer.resolve()), 'config', 'optimize-autoloader', 'false'],
        php+[str(args.composer.resolve()), 'dump-autoload', '--no-dev', '--no-scripts'],
        php+['artisan', 'package:discover', '--ansi'],
        php+['artisan', 'filament:upgrade', '--ansi'],
        ['npm.cmd', 'ci', '--no-audit', '--no-fund'],
        ['npm.cmd', 'run', 'build'],
    ]
    if args.resume:
        lock = json.loads((app/'composer.lock').read_text(encoding='utf-8'))
        installed = json.loads((app/'vendor/composer/installed.json').read_text(encoding='utf-8'))
        if {p['name']:p['version'] for p in lock['packages']} != {p['name']:p['version'] for p in installed['packages']}:
            raise ValueError('Cannot resume: installed production dependencies do not match composer.lock')
        commands[0] = php+[str(args.composer.resolve()), 'check-platform-reqs', '--no-dev']
    for index, command in enumerate([] if reuse_assets else commands):
        with (out/f'build-{index}.log').open('w', encoding='utf-8') as log:
            result = subprocess.run(command, cwd=app, env=env, stdout=log, stderr=subprocess.STDOUT)
        if result.returncode:
            raise RuntimeError('Build failed: '+str(out/f'build-{index}.log'))
    # Cache manifests can contain build-machine paths. Discover at first local boot.
    for file in (app/'bootstrap/cache').glob('*.php'):
        file.unlink()
    shutil.copyfile(Path(__file__).with_name('native-website.sql'), app/'native-website.sql')
    payload = out/'standalone-payload'
    target = payload
    target.mkdir(parents=True, exist_ok=args.resume)
    with zipfile.ZipFile(target/'app.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(app.rglob('*')):
            rel = path.relative_to(app)
            if any(p in {'node_modules', '.git', 'tests'} for p in rel.parts):
                continue
            if path.is_file() and path.name not in {'.env', 'hot'} and not path.name.endswith(('.sqlite','.log')):
                archive.write(path, rel.as_posix())
    shutil.copyfile(args.php_archive, target/'runtime.zip')
    ini = ('extension_dir="ext"\nextension=curl\nextension=fileinfo\nextension=gd\n'
           'extension=intl\nextension=mbstring\nextension=openssl\nextension=pdo_mysql\n'
           'extension=pdo_sqlite\nextension=sqlite3\nextension=zip\nmemory_limit=256M\n'
           'display_errors=Off\nlog_errors=On\nexpose_php=Off\nshort_open_tag=Off\n'
           'upload_max_filesize=32M\npost_max_size=36M\n'
           'zend_extension=php_opcache.dll\nopcache.enable=1\nopcache.enable_cli=1\n'
           'opcache.memory_consumption=128\nopcache.interned_strings_buffer=16\n'
           'opcache.max_accelerated_files=20000\nopcache.validate_timestamps=1\n'
           'opcache.revalidate_freq=0\nopcache.file_update_protection=0\nopcache.file_cache_fallback=1\n')
    with zipfile.ZipFile(target/'runtime.zip', 'a', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('php.ini', ini)
    shutil.copyfile(Path(__file__).with_name('native-website.sql'), target/'native-website.sql')
    shutil.copyfile(Path(__file__).with_name('README.txt'), target/'README.txt')
    shutil.copyfile(Path(__file__).with_name('Website.ps1'), payload/'Website.ps1')
    shutil.copyfile(Path(__file__).with_name('Website.cmd'), payload/'Website.cmd')
    shutil.copyfile(Path(__file__).with_name('Website Tools.cmd'), payload/'Website Tools.cmd')
    shutil.copyfile(Path(__file__).with_name('Admin.cmd'), payload/'Admin.cmd')
    component = {'schema':1, 'upstream':'Zyvelle44/aion-pts-website',
        'upstreamCommit':'d0dde5dd939f728f0b2c53e765f25188b15afc5c',
        'phpOriginalSha256':args.php_sha256,
        **{kind:{'sha256':digest(target/(kind+'.zip')), 'bytes':(target/(kind+'.zip')).stat().st_size}
           for kind in ('app','runtime')}}
    (target/'component.json').write_text(json.dumps(component, indent=2)+'\n',encoding='utf-8')
    files = [{'path':p.relative_to(payload).as_posix(), 'bytes':p.stat().st_size, 'sha256':digest(p)}
             for p in sorted(payload.rglob('*')) if p.is_file()]
    (out/'build-manifest.json').write_text(json.dumps({'schema':1,'files':files,'component':component},indent=2)+'\n')
    print('Portable website built: '+str(payload), flush=True)


if __name__ == '__main__':
    main()
