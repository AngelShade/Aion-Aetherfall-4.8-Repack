"""Generate release copy from CHANGELOG.md, without PHP or local release credentials."""
import argparse
import html
import json
import re
import sys
import urllib.request
from datetime import date, datetime, timezone
from pathlib import Path

from release_notes import (changelog_sections, github_release_body, player_notes,
                           release_section, section_text, shipped_changelog, version_key)


ROOT = Path(__file__).resolve().parents[2]
RELEASES = 'https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases'
API = 'https://api.github.com/repos/AngelShade/Aion-Aetherfall-4.8-Repack/releases'
CONTENT = Path('website/config/aetherfall_content.php')
BASELINES = Path('website/resources/content/aetherfall-v5.json')
INFO = Path('website/resources/views/pages/info.blade.php')
GROUPS = ('New', 'Improved', 'Fixed', 'For server hosts')
# Developer terms forbidden by the player-note rules. Game terms such as Stigma builds stay valid.
BANNED = re.compile(
    r'\b(?:cache|caches|native|runtime|payload|bootstrap|schema|SQL|Lua|Maven|RVA|API|'
    r'backend|frontend|refactor(?:ed|ing)?|pipeline|manifest|regression|handoff|'
    r'bounded|retained|preserved|guarded|eligibility|admission|server-owned|worker|thread|'
    r'packet|addon|handler|parity|acceptance|'
    r'prepared locally|publication pending|publication is separate)\b', re.I)
UPDATE_LINE = 'Update through Aetherfall Setup: choose Update Repack.'
EMPTY_GROUP = re.compile(r'^### .+\n\s*(?=^### |^## |\Z)', re.M)


def read(path):
    return Path(path).read_text(encoding='utf-8-sig')


def json_text(value):
    return json.dumps(value, ensure_ascii=False, indent=2) + '\n'


def php_string(value):
    return "'" + value.replace('\\', '\\\\').replace("'", "\\'") + "'"


class PhpContent:
    """Read only literal PHP arrays; retain source spans for focused replacements.

    No PHP execution, expressions, interpolation or general PHP syntax is accepted.
    """
    token = re.compile(r"\s+|//[^\n]*|/\*[\s\S]*?\*/|'(?:\\[\s\S]|[^'\\])*'|=>|\[|\]|,|;|true\b|false\b|\d+(?:\.\d+)?")

    def __init__(self, text):
        self.text, self.spans = text, {}
        self.pos = text.index('return [') + len('return ')
        self.value = self.parse(())

    def next(self):
        while True:
            match = self.token.match(self.text, self.pos)
            if not match:
                raise ValueError(f'Unsupported website content near character {self.pos}')
            self.pos = match.end()
            if not match[0].isspace() and not match[0].startswith(('//', '/*')):
                return match

    def peek(self):
        pos = self.pos
        match = self.next()
        self.pos = pos
        return match[0]

    def parse(self, path):
        match = self.next()
        token = match[0]
        if token == '[':
            values, index = {}, 0
            associative = False
            while self.peek() != ']':
                pos = self.pos
                key = self.parse(path + (index,))
                if self.peek() == '=>':
                    associative = True
                    self.next()
                    value = self.parse(path + (key,))
                else:
                    # Parse list elements again with their final path.
                    self.pos = pos
                    key, value = index, self.parse(path + (index,))
                values[key] = value
                index += 1
                if self.peek() != ',':
                    break
                self.next()
            if self.next()[0] != ']':
                raise ValueError('Unclosed website content array')
            value = values if associative else list(values.values())
        elif token.startswith("'"):
            value = re.sub(r"\\([\\'])", r'\1', token[1:-1])
        elif token in ('true', 'false'):
            value = token == 'true'
        elif token.isdigit():
            value = int(token)
        elif token[0].isdigit():
            value = float(token)
        else:
            raise ValueError('Unsupported website content: ' + token)
        self.spans[path] = (match.start(), self.pos)
        return value

    def replace(self, changes, insertions=()):
        edits = list(insertions)
        for path, value in changes.items():
            start, end = self.spans[path]
            encoded = php_string(value) if isinstance(value, str) else json.dumps(value)
            if self.text[start:end] != encoded:
                edits.append((start, end, encoded))
        text = self.text
        for start, end, value in sorted(edits, reverse=True):
            text = text[:start] + value + text[end:]
        return text


def lint(text):
    problems, seen = [], set()
    sections = changelog_sections(text)
    if not sections:
        return ['CHANGELOG.md has no release sections']
    if not sections or sections[0].heading != '## Next update':
        problems.append('CHANGELOG.md must start with ## Next update')
    versions = []
    for section in sections:
        label = f'CHANGELOG.md:{section.line} ({section.heading[3:]})'
        if section.heading in seen:
            problems.append(label + ': duplicate heading')
        seen.add(section.heading)
        upcoming = section.heading == '## Next update'
        if not upcoming:
            try:
                section.details
                key = version_key(section.version)
                if key in versions:
                    problems.append(label + ': duplicate release version')
                if versions and key >= versions[-1]:
                    problems.append(label + ': releases must be newest first')
                versions.append(key)
            except (ValueError, TypeError) as error:
                problems.append(label + ': ' + str(error))
        bullets = re.findall(r'^[-*] .+(?:\n(?!#|[-*] |\s*$).+)*', section.body, re.M)
        if (not upcoming or bullets) and (not section.intro or not re.search(r'[.!?](?:["”])?$', section.intro)):
            problems.append(label + ': add an intro sentence before the groups')
        if not upcoming and not bullets:
            problems.append(label + ': add player notes')
        order, group = [], None
        for block in re.split(r'(?m)(^### .+$)', section.body):
            if block.startswith('### '):
                group = block[4:].strip()
                if group not in GROUPS:
                    problems.append(label + ': unknown group ' + group)
                else:
                    order.append(GROUPS.index(group))
            else:
                if group != 'For server hosts':
                    for word in sorted(set(BANNED.findall(block))):
                        problems.append(label + ': developer word outside For server hosts: ' + word)
                    if re.search(r'`|\b[A-Za-z]:[\\/]', block):
                        problems.append(label + ': put exact files and settings under For server hosts')
                for bullet in re.findall(r'^[-*] .+(?:\n(?!#|[-*] |\s*$).+)*', block, re.M):
                    count = len(bullet[2:].split())
                    if count > 25:
                        problems.append(label + f': bullet has {count} words (maximum 25): ' + bullet[:85])
        if order != sorted(set(order)):
            problems.append(label + ': groups must appear once in New, Improved, Fixed, For server hosts order')
        if upcoming and order != [0, 1, 2]:
            problems.append(label + ': Next update needs New, Improved and Fixed groups')
    # A misspelled ## heading must not disappear into the preceding section.
    for line_no, line in enumerate(text.splitlines(), 1):
        if re.match(r'^\s*##(?:\s|$)', line) and not line.startswith('## '):
            problems.append(f'CHANGELOG.md:{line_no}: invalid section heading')
    return problems


def inline(text):
    text = html.escape(text, quote=False)
    text = re.sub(r'`([^`]+)`', r'<code>\1</code>', text)
    return re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', text)


def article_body(section):
    output, in_list = [], False
    for block in re.split(r'\n\s*\n|(?m:^)(?=### |[-*] )', section.body):
        block = block.strip()
        if not block:
            continue
        bullet = block.startswith(('- ', '* '))
        if in_list and not bullet:
            output.append('</ul>')
            in_list = False
        if bullet:
            if not in_list:
                output.append('<ul>')
                in_list = True
            output.append('<li>' + inline(' '.join(block[2:].splitlines())) + '</li>')
        elif block.startswith('### '):
            output.append('<h2>' + inline(block[4:]) + '</h2>')
        else:
            output.append('<p>' + inline(' '.join(block.splitlines())) + '</p>')
    if in_list:
        output.append('</ul>')
    output.append('<p>' + UPDATE_LINE + '</p>')
    output.append(f'<p><a href="/download">Download {section.version}</a> &middot; '
                  f'<a href="{RELEASES}/tag/v{section.version}" target="_blank" '
                  'rel="noopener noreferrer">Official release notes</a></p>')
    return ''.join(output)


def snapshot(content):
    """Keep every shipped row as a recognizable revision, excluding unrelated feature copy."""
    result = {key: content[key] for key in ('settings', 'articles', 'downloads', 'faqs')}
    result = json.loads(json.dumps(result))
    # Dates and feature choices belong to the owner and are never refreshed, so they can't mark an edit.
    for article in result['articles']:
        article.pop('is_featured', None)
        article.pop('published_at', None)
    return result


def generated_files(root, version, metadata=None):
    text = read(root / 'CHANGELOG.md')
    problems = lint(text)
    if problems:
        raise ValueError('\n'.join(problems))
    section = release_section(text, version)
    title, released = section.details
    version, intro = section.version, ' '.join(section.intro.split())
    first_paragraph = ' '.join(section.intro.split('\n\n')[0].split())
    published_on = f'{released.day} {released:%B %Y}'
    slug = 'aetherfall-' + version.replace('.', '-')
    url = RELEASES + '/tag/v' + version
    readme = read(root / 'README.md')
    latest = f'**Latest release: [{version}]({url}), released {published_on}.** {first_paragraph}'
    readme, count = re.subn(r'^\*\*Latest release:.*$', lambda _: latest, readme, flags=re.M)
    if count != 1:
        raise ValueError('README.md must have exactly one Latest release paragraph')
    readme, count = re.subn(
        r'<a href="[^"]+"><img src="https://img.shields.io/badge/Latest_Release-[^"]+" alt="[^"]+"></a>',
        lambda _: f'<a href="{url}"><img src="https://img.shields.io/badge/Latest_Release-v{version}-eab308?style=for-the-badge" alt="Latest Release v{version}"></a>', readme)
    if count != 1:
        raise ValueError('README.md must have exactly one Latest Release badge')
    if "[What's new](CHANGELOG.md)" not in readme:
        raise ValueError('README.md needs [What\'s new](CHANGELOG.md)')

    content = PhpContent(read(root / CONTENT))
    data = content.value
    changes = {('settings', 'release_url'): url, ('settings', 'repack_version'): version,
               ('release', 'version'): version, ('release', 'published_on'): published_on,
               ('release', 'url'): url}
    insertions = []
    desired_article = dict(slug=slug, title=f'Aetherfall {version}: {title}', category='Update',
                           published_at=f'{released.isoformat()} 00:00:00', is_featured=True,
                           excerpt=first_paragraph, cover='aetherfall/screenshots/companions.jpg',
                           body=article_body(section))
    matches = [i for i, item in enumerate(data['articles']) if item['slug'] == slug]
    if len(matches) > 1:
        raise ValueError('Duplicate website news slug: ' + slug)
    if matches:
        index = matches[0]
        old_date = data['articles'][index].get('published_at', '')
        if old_date.startswith(released.isoformat()):
            desired_article['published_at'] = old_date
        for key, value in desired_article.items():
            path = ('articles', index, key)
            if path in content.spans:
                changes[path] = value
            else:
                end = content.spans[('articles', index)][1] - 1
                insertions.append((end, end, f'    {php_string(key)} => {json.dumps(value) if not isinstance(value, str) else php_string(value)},\n        '))
    else:
        pos = content.spans[('articles',)][0] + 1
        fields = '\n'.join('            ' + php_string(k) + ' => ' +
                           (php_string(v) if isinstance(v, str) else json.dumps(v)) + ','
                           for k, v in desired_article.items())
        insertions.append((pos, pos, '\n        [\n' + fields + '\n        ],'))
    for i, item in enumerate(data['articles']):
        if item.get('category') == 'Update' and item['slug'] != slug and item.get('is_featured'):
            changes[('articles', i, 'is_featured')] = False

    sizes = {'aetherfall-installer-kit': ('Aetherfall-Downloader.zip', 'downloaderBytes'),
             'aetherfall-setup': ('Aetherfall-Setup.exe', 'setupExeBytes'),
             'aetherfall-manual-update': ('Aetherfall-Setup.exe', 'setupExeBytes')}
    if metadata and str(metadata.get('version')) != version:
        raise ValueError('Prepared metadata version differs from ' + version)
    for slug_key, (filename, size_key) in sizes.items():
        matches = [i for i, item in enumerate(data['downloads']) if item['slug'] == slug_key]
        if len(matches) != 1:
            raise ValueError('Expected one website download: ' + slug_key)
        index = matches[0]
        changes[('downloads', index, 'version')] = version
        changes[('downloads', index, 'download_url')] = RELEASES + '/download/v' + version + '/' + filename
        if metadata and size_key in metadata:
            size = metadata[size_key]
            if not isinstance(size, int) or isinstance(size, bool) or size <= 0:
                raise ValueError('Invalid prepared download size: ' + size_key)
            changes[('downloads', index, 'file_size_bytes')] = size
    website = content.replace(changes, insertions)
    info = read(root / INFO)
    info, count = re.subn(r'<p>[^\n]*</p>(?=\s*</div>\s*<div class="info-release-actions">)',
                          lambda _: '<p>' + inline(intro) + '</p>', info)
    if count != 1:
        raise ValueError('Website info page must have one release blurb')
    info, count = re.subn(r"route\('news.show', 'aetherfall-[\d-]+'\)(?= }}\">WHAT'S NEW)",
                          lambda _: f"route('news.show', '{slug}')", info)
    if count != 1:
        raise ValueError('Website info page must have one WHAT\'S NEW link')
    # Capture before AND after. This recognizes intervening versions on the next deploy.
    baseline_path = root / BASELINES
    baselines = json.loads(read(baseline_path)) if baseline_path.exists() else []
    for value in (snapshot(data), snapshot(PhpContent(website).value)):
        if value not in baselines:
            baselines.append(value)
    return {Path('README.md'): readme, CONTENT: website, INFO: info, BASELINES: json_text(baselines)}


def check_release_text(root=ROOT, package=None, version=None):
    """With a version, check a release about to publish; README and website sync after it."""
    root = Path(root)
    text = read(root / 'CHANGELOG.md')
    problems = lint(text)
    if version and not problems:
        try:
            release_section(text, version)
        except ValueError as error:
            problems.append(str(error))
    elif not problems:
        newest = next((s.version for s in changelog_sections(text) if s.version), None)
        try:
            for path, expected in generated_files(root, newest).items():
                if not (root / path).is_file() or read(root / path) != expected:
                    problems.append(f'{path.as_posix()}: does not match {newest}; run sync {newest}')
        except (ValueError, KeyError) as error:
            problems.append(str(error))
    if package and read(Path(package) / 'CHANGELOG.md') != shipped_changelog(text):
        problems.append('Packaged CHANGELOG.md differs from source player notes; refresh it before publishing')
    if problems:
        raise ValueError('Release text check failed:\n- ' + '\n- '.join(problems))
    print('Release text check passed.')


def sync_release_text(version, root=ROOT, metadata=None):
    root = Path(root)
    generated = generated_files(root, version, metadata)
    changed = []
    for path, value in generated.items():
        target = root / path
        if not target.is_file() or read(target) != value:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(value, encoding='utf-8', newline='\n')
            changed.append(path.as_posix())
    print(f'sync {version}: ' + (', '.join(changed) if changed else 'no changes'))
    return changed


def start_release(version, released, root=ROOT):
    version_key(version)
    released = date.fromisoformat(released)
    path = Path(root) / 'CHANGELOG.md'
    text = read(path)
    problems = lint(text)
    if problems:
        raise ValueError('\n'.join(problems))
    sections = changelog_sections(text)
    upcoming = sections[0]
    if not re.search(r'^[-*] ', upcoming.body, re.M) or not upcoming.intro:
        raise ValueError('Next update needs player notes and an intro sentence before start-release')
    if any(s.version and version_key(s.version) >= version_key(version) for s in sections):
        raise ValueError('The new version must be newer than every published section')
    if released < sections[1].details[1]:
        raise ValueError('The new release date precedes the latest release')
    title = re.split(r'(?<=[.!?])\s', ' '.join(upcoming.intro.split()), maxsplit=1)[0].rstrip('.!?')
    heading = f'## {version.lstrip("vV")} - {title} ({released.day} {released:%B %Y})'
    released_body = EMPTY_GROUP.sub('', upcoming.body + '\n').strip()
    start = text.index('## Next update')
    end = text.index(sections[1].heading, start)
    result = (text[:start] + '## Next update\n\n### New\n\n### Improved\n\n### Fixed\n\n' +
              heading + '\n\n' + released_body + '\n\n' + text[end:])
    problems = lint(result)
    if problems:
        raise ValueError('\n'.join(problems))
    path.write_text(result, encoding='utf-8', newline='\n')
    print(f'start-release {version}: promoted Next update; added a fresh empty section')


def github_body(text, version):
    section = release_section(text, version)
    return github_release_body(section_text(section), section.version, '0.0')


def github_request(url, method='GET', data=None, token=None):
    # Match publish_update.py's certifi handling. Never disable certificate verification.
    import os
    try:
        import certifi
        os.environ.setdefault('SSL_CERT_FILE', certifi.where())
    except ImportError:
        pass
    headers = {'Accept': 'application/vnd.github+json', 'User-Agent': 'Aetherfall-Release-Text'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(url, headers=headers, method=method,
                                     data=json.dumps(data).encode('utf-8') if data is not None else None)
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode('utf-8'))


def published_releases():
    releases, page = [], 1
    while True:
        batch = github_request(API + f'?per_page=100&page={page}')
        if not isinstance(batch, list):
            raise ValueError('GitHub did not return a release list')
        releases.extend(r for r in batch if not r.get('draft') and not r.get('prerelease') and r.get('published_at'))
        if len(batch) < 100:
            return releases
        page += 1


def backfill_plan(text, releases):
    newest = max(version_key(s.version) for s in changelog_sections(text) if s.version)
    plan = []
    for release in releases:
        if release.get('draft') or release.get('prerelease') or not release.get('published_at'):
            continue
        tag = release.get('tag_name', '')
        old = release.get('body') or ''
        item = dict(tag=tag, id=release['id'], old=old, new=None, reason=None)
        try:
            key = version_key(tag)
        except ValueError:
            item['reason'] = 'unrecognized tag'
            plan.append(item)
            continue
        if key >= newest:
            continue
        if key == version_key('0.86'):
            item['reason'] = 'base release v0.86 keeps its installation instructions'
        else:
            try:
                notes = player_notes(text, tag)
                # Screenshots carry over; other links may be download routes only that page has.
                images = re.findall(r'<img\b[^>]*>', old)
                item['new'] = '\n\n'.join([notes.rstrip(), *images, UPDATE_LINE]) + '\n'
            except ValueError:
                item['reason'] = 'no player notes in CHANGELOG.md'
            if re.search(r'https?://', re.sub(r'<img\b[^>]*>', '', old)):
                item['reason'] = 'existing links would be lost'
        plan.append(item)
    return plan


def backfill(root=ROOT, apply=False):
    root = Path(root)
    text = read(root / 'CHANGELOG.md')
    problems = lint(text)
    if problems:
        raise ValueError('\n'.join(problems))
    plan = backfill_plan(text, published_releases())
    token, backup = None, None
    for item in plan:
        sizes = f'{len(item["old"].encode("utf-8"))} -> {len((item["new"] or "").encode("utf-8"))} bytes'
        if item['reason']:
            print(f'{item["tag"]}: skipped ({item["reason"]}); {sizes}')
            continue
        print(f'{item["tag"]}: {sizes}' + ('; applying' if apply else '; dry run'))
        if not apply or item['old'] == item['new']:
            continue
        if token is None:
            from publish_update import get_github_token
            token = get_github_token()
            backup = root / 'tools/distribution/.release-text-backups' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
            backup.mkdir(parents=True, exist_ok=False)
        endpoint = API + '/' + str(item['id'])
        current = github_request(endpoint, token=token)
        if (current.get('body') or '') != item['old']:
            raise ValueError(item['tag'] + ': release body changed during backfill; stopped')
        (backup / (str(item['id']) + '.json')).write_text(json_text(current), encoding='utf-8')
        github_request(endpoint, method='PATCH', data={'body': item['new']}, token=token)
        if github_request(endpoint, token=token).get('body') != item['new']:
            raise ValueError(item['tag'] + ': release body readback mismatch; old body saved in ' + str(backup))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT, help='Source root (also supports isolated fixtures)')
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('check')
    sync = commands.add_parser('sync')
    sync.add_argument('version')
    sync.add_argument('--metadata', type=Path, help='Prepared latest-update.json; sizes otherwise stay unchanged')
    notes = commands.add_parser('release-notes')
    notes.add_argument('version')
    notes.add_argument('--output', type=Path)
    start = commands.add_parser('start-release')
    start.add_argument('version')
    start.add_argument('date', help='YYYY-MM-DD')
    back = commands.add_parser('backfill')
    back.add_argument('--apply', action='store_true')
    args = parser.parse_args(argv)
    try:
        if args.command == 'check':
            check_release_text(args.root)
        elif args.command == 'sync':
            sync_release_text(args.version, args.root, json.loads(read(args.metadata)) if args.metadata else None)
        elif args.command == 'release-notes':
            body = github_body(read(args.root / 'CHANGELOG.md'), args.version)
            if args.output:
                args.output.write_text(body, encoding='utf-8', newline='\n')
            else:
                sys.stdout.buffer.write(body.encode('utf-8'))
        elif args.command == 'start-release':
            start_release(args.version, args.date, args.root)
        else:
            backfill(args.root, args.apply)
    except (ValueError, OSError, KeyError) as error:
        parser.exit(1, str(error) + '\n')


if __name__ == '__main__':
    main()
