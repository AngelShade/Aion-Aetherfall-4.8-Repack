"""Player-facing release notes from the changelog shipped in the downloader."""
import re
from dataclasses import dataclass
from datetime import datetime


VERSION_HEADING = re.compile(
    r'^##\s+(?:(?:Prepared local update|Latest published release|Update)\s*[-–—]\s*)?'
    r'\[?(v?\d+(?:\.\d+){1,3})\]?(?=\s|$)', re.I)
PRIVATE_SECTION = re.compile(
    r'^(?:How to update.*|Installation|Update instructions|Internal|Build|Tooling|'
    r'Compatibility and update status)$', re.I)
PRIVATE_NOTE = re.compile(
    r'\b(?:prepared locally|publication is separate|publication pending|publication is pending|'
    r'build/delivery|delivery receipt|Maven|RVA)\b|'
    r'\b(?:acceptance|verification|handoff)\b.*\bowner\b|'
    r'\bowner\b.*\b(?:acceptance|verification|handoff)\b', re.I)
PRIVATE_PATH = re.compile(r'\b[A-Za-z]:[\\/]')
RELEASE_HEADING = re.compile(
    r'^## (v?\d+(?:\.\d+){1,3}) - (.+) \((\d{1,2} [A-Za-z]+ \d{4})\)$')


@dataclass
class ChangelogSection:
    heading: str
    body: str
    line: int

    @property
    def version(self):
        match = VERSION_HEADING.match(self.heading)
        return match[1].lstrip('vV') if match else None

    @property
    def details(self):
        match = RELEASE_HEADING.fullmatch(self.heading)
        if not match:
            raise ValueError(f'Line {self.line}: invalid release heading: {self.heading}')
        date = datetime.strptime(match[3], '%d %B %Y').date()
        return match[2], date

    @property
    def intro(self):
        return self.body.split('### ', 1)[0].strip()


def changelog_sections(text):
    """Share section boundaries with lint, promotion and shipped-file generation."""
    matches = list(re.finditer(r'^## .+$', text.lstrip('\ufeff'), re.M))
    text = text.lstrip('\ufeff')
    return [ChangelogSection(match[0].strip(),
                             text[match.end():matches[i + 1].start() if i + 1 < len(matches) else len(text)].strip(),
                             text.count('\n', 0, match.start()) + 1)
            for i, match in enumerate(matches)]


def release_section(text, version):
    version_key(version)
    matches = [section for section in changelog_sections(text)
               if section.version and version_key(section.version) == version_key(version)]
    if len(matches) != 1:
        raise ValueError(f'Expected exactly one changelog section for {version}; found {len(matches)}')
    return matches[0]


def section_text(section):
    return section.heading + '\n\n' + section.body + '\n'


def shipped_changelog(text):
    """Unreleased notes belong in source, never in a downloadable release."""
    for section in changelog_sections(text):
        if section.heading == '## Next update':
            start = text.index(section.heading)
            following = re.search(r'^## ', text[start + len(section.heading):], re.M)
            end = start + len(section.heading) + following.start() if following else len(text)
            text = text[:start] + text[end:]
    return text.rstrip() + '\n'


def player_notes(text, version):
    """The exact published section, without folding in earlier releases."""
    return version_notes(section_text(release_section(text, version)), version, '0.0')


def version_key(version):
    version = version.lstrip('vV')
    if not re.fullmatch(r'\d+(?:\.\d+){1,3}', version):
        raise ValueError('A numeric release version is required')
    return tuple(int(part) for part in version.split('.')) + (0,) * (4 - len(version.split('.')))


def previous_published_version(version, releases):
    """Find the preceding public release, excluding the target when republishing."""
    target = version_key(version)
    previous = []
    for release in releases:
        if release.get('draft') or release.get('prerelease') or not release.get('published_at'):
            continue
        tag = release.get('tag_name', '')
        try:
            key = version_key(tag)
        except ValueError:
            continue
        if key < target:
            previous.append((key, tag.lstrip('vV')))
    if not previous:
        raise ValueError(f'Previous published release before {version} could not be determined; publication stopped')
    return max(previous)[1]


def version_notes(text, version, previous_version=None):
    """Include the target and every changelog version since the preceding release."""
    target = version_key(version)
    if not previous_version:
        raise ValueError('Previous published release is required; publication stopped')
    previous = version_key(previous_version)
    if previous >= target:
        raise ValueError('Previous published release must be older than the target; publication stopped')
    sections = {}
    active = False
    target_notes = False
    heading = 'Changes'
    block = []

    def flush():
        nonlocal target_notes
        if block:
            content = '\n'.join(block).strip()
            if (not PRIVATE_SECTION.fullmatch(heading) and not PRIVATE_NOTE.search(content)
                    and (heading.casefold() == 'for server hosts' or not PRIVATE_PATH.search(content))):
                sections.setdefault(heading, []).append(content)
                if current == target:
                    target_notes = True
            block.clear()

    for raw in text.lstrip('\ufeff').splitlines():
        line = raw.strip()
        if line.startswith('## '):
            flush()
            match = VERSION_HEADING.match(line)
            current = version_key(match[1]) if match else None
            active = current is not None and previous < current <= target
            heading = 'Changes'
            continue
        if not active:
            continue
        if line.startswith('### '):
            flush()
            heading = line[4:].strip()
        elif not line:
            flush()
        elif re.match(r'^[-*]\s+', line):
            flush()
            block.append(line)
        else:
            block.append(raw.rstrip())
    flush()
    if not target_notes:
        raise ValueError(f'No player-facing changelog found for {version}; publication stopped')
    return '\n\n'.join(('### ' + heading + '\n\n' if heading != 'Changes' else '') +
                         '\n\n'.join(blocks) for heading, blocks in sections.items())


def github_release_body(text, version, previous_version=None):
    changes = version_notes(text, version, previous_version)
    return (f'## What\'s changed in {version}\n\n{changes}\n\n'
            '## Compatibility\n\n'
            'This cumulative update installs over repack **0.86 or any later version**. '
            'Your accounts, characters, progress, connections, settings and interface layouts are kept. '
            'No full reinstall or manual database reset/import is required. '
            'Optional language packs and HD textures remain separate.\n\n'
            '## How to update\n\n'
            '1. Close Aion and the three game servers normally. The repack database may remain running.\n'
            '2. Open **Aetherfall Setup/Hub** and choose **Update Repack**. '
            'Let Setup restart if it updates itself, then choose **Update Repack** again if needed.\n'
            '3. Start the servers and client normally.\n\n'
            'For a new installation, download **Aetherfall-Downloader.zip** from this release. '
            'The updater downloads only what you need and checks every file.\n')
