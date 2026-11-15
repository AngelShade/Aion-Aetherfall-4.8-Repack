"""Encode optional mod translations for a managed text pack."""
from collections import Counter
import json
from pathlib import Path
import re


RUNTIME_FILES = ('mod-text.tsv', 'web-text.json')
MAX_BYTES = 1024 * 1024
FORMATS = re.compile(
    r'%%|\[%[^\]]+\]|%[-+ #0]*\d*(?:\.\d+)?[cdiouxXeEfgGqs]|\{\d+\}'
)
WEB_FIELDS = re.compile(r'\{[a-zA-Z0-9_]+\}')


def unique_pairs(pairs):
    result = {}
    for source, target in pairs:
        if source in result:
            raise ValueError(f'Duplicate mod translation: {source!r}')
        result[source] = target
    return result


def translations(path, web=False):
    rows = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique_pairs)
    if not isinstance(rows, dict) or not rows:
        raise ValueError(f'Expected an object of mod translations in {path}')
    for source, target in rows.items():
        if not source.strip() or not isinstance(target, str) or not target.strip():
            raise ValueError(f'Empty or invalid mod translation in {path}: {source!r}')
        if any(ord(ch) < 32 and ch not in '\t\r\n' for ch in source + target):
            raise ValueError(f'Invalid control character in {path}: {source!r}')
        same_formats = (Counter(WEB_FIELDS.findall(source)) == Counter(WEB_FIELDS.findall(target)) if web
                        else FORMATS.findall(source) == FORMATS.findall(target))
        if not same_formats:
            raise ValueError(f'Mod format placeholders changed in {path}: {source!r}')
    return dict(sorted(rows.items()))


def tsv_field(value):
    return value.replace('%', '%25').replace('\t', '%09').replace('\r', '%0D').replace('\n', '%0A')


def catalog_members(folder: Path, *, include_web=True):
    members = {}
    counts = {}
    for name in RUNTIME_FILES:
        if name == 'web-text.json' and not include_web:
            continue
        source = folder / Path(name).with_suffix('.json')
        if not source.is_file():
            continue
        rows = translations(source, web=name.endswith('.json'))
        if name.endswith('.tsv'):
            text = 'AFTEXT1\n' + ''.join(f'{tsv_field(key)}\t{tsv_field(value)}\n' for key, value in rows.items())
        else:
            text = json.dumps(rows, ensure_ascii=False, separators=(',', ':'))
        payload = text.encode('utf-8')
        if len(payload) > MAX_BYTES:
            raise ValueError(f'Mod translation catalog exceeds {MAX_BYTES} bytes: {source}')
        members[f'aetherfall/{name}'] = payload
        counts[name] = len(rows)
    notice = folder / 'login-notice.json'
    if notice.is_file():
        data = json.loads(notice.read_text(encoding='utf-8'), object_pairs_hook=unique_pairs)
        if (not isinstance(data, dict) or set(data) != {'englishSha256', 'text'}
                or not isinstance(data['englishSha256'], str)
                or not re.fullmatch(r'[a-f0-9]{64}', data['englishSha256'])
                or not isinstance(data['text'], str) or not data['text'].strip()):
            raise ValueError(f'Invalid login notice translation: {notice}')
        payload = json.dumps(data, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
        if len(payload) > 16 * 1024:
            raise ValueError(f'Login notice exceeds 16 KiB: {notice}')
        members['aetherfall/login-notice.json'] = payload
        counts['login-notice.json'] = 1
    return members, counts
