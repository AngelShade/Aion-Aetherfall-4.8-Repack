"""Checks for generated release copy; fixtures only, with no network."""
import contextlib
import io
import tempfile
import unittest
from pathlib import Path

from release_notes import github_release_body, shipped_changelog
from sync_release_text import (UPDATE_LINE, backfill_plan, check_release_text, lint, start_release,
                               sync_release_text)


CHANGELOG = """# Changelog

## Next update

### New

### Improved

### Fixed

## 0.2 - Road merchants (2 November 2026)

You can meet merchants on the roads.

### New

- **Merchants:** buy supplies while you travel.

## 0.1 - First steps (1 November 2026)

Your journey begins here.

### Fixed

- Quest windows close correctly.
"""
PENDING = CHANGELOG.replace('### New\n\n### Improved', '''Companions learn new tricks.

### New

- **Tricks:** teach your companions tricks.

### Improved''', 1)
README = """<a href="https://example.test/old"><img src="https://img.shields.io/badge/Latest_Release-v0.1-eab308" alt="Latest Release v0.1"></a>

**Latest release: old text.**

See [What's new](CHANGELOG.md).
"""
ARTICLE = """        [
            'slug' => 'aetherfall-0-1',
            'title' => 'Aetherfall 0.1',
            'category' => 'Update',
            'is_featured' => true,
            'body' => '<p>Old</p>',
        ],
"""
DOWNLOADS = ''.join(f"""        [
            'slug' => '{slug}',
            'version' => '0.1',
            'file_size_bytes' => 1,
            'download_url' => 'https://example.test/old',
        ],
""" for slug in ('aetherfall-installer-kit', 'aetherfall-setup', 'aetherfall-manual-update'))
CONTENT = f"""<?php

return [
    'settings' => [
        'release_url' => 'https://example.test/old',
        'repack_version' => '0.1',
    ],
    'articles' => [
{ARTICLE}    ],
    'downloads' => [
{DOWNLOADS}    ],
    'faqs' => [
    ],
    'release' => [
        'version' => '0.1',
        'published_on' => '1 November 2026',
        'url' => 'https://example.test/old',
    ],
];
"""
INFO = """<div>
                <p>Old blurb.</p>
            </div>
            <div class="info-release-actions">
                <a href="{{ route('news.show', 'aetherfall-0-1') }}">WHAT'S NEW</a>
"""


def quiet(action, *args, **kwargs):
    with contextlib.redirect_stdout(io.StringIO()) as output:
        result = action(*args, **kwargs)
    return result, output.getvalue()


class SyncReleaseTextTests(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name)
        (self.root / 'website/config').mkdir(parents=True)
        (self.root / 'website/resources/views/pages').mkdir(parents=True)
        for name, text in (('CHANGELOG.md', CHANGELOG), ('README.md', README),
                           ('website/config/aetherfall_content.php', CONTENT),
                           ('website/resources/views/pages/info.blade.php', INFO)):
            (self.root / name).write_text(text, encoding='utf-8')

    def text(self, name):
        return (self.root / name).read_text(encoding='utf-8')

    def test_check_accepts_good_notes(self):
        self.assertEqual([], lint(CHANGELOG))

    def test_check_reports_each_rule(self):
        cases = {
            'developer word': CHANGELOG.replace('buy supplies', 'use the native supplies'),
            'maximum 25': CHANGELOG.replace('buy supplies while you travel', ' '.join(['word'] * 30)),
            'must appear once': CHANGELOG.replace('### Fixed\n\n- Quest', '### Fixed\n\n- A.\n\n### New\n\n- Quest'),
            'intro sentence': CHANGELOG.replace('You can meet merchants on the roads.\n\n', ''),
            'must start with': CHANGELOG.replace('## Next update\n\n### New\n\n### Improved\n\n### Fixed\n\n', ''),
        }
        for expected, text in cases.items():
            with self.subTest(expected=expected):
                self.assertTrue(any(expected in problem for problem in lint(text)), lint(text))

    def test_host_settings_may_use_exact_terms(self):
        text = CHANGELOG.replace('## 0.1 -', '### For server hosts\n\n- Set the `native.cache` value.\n\n## 0.1 -')
        self.assertEqual([], lint(text))

    def test_next_update_never_ships(self):
        self.assertNotIn('Tricks', github_release_body(PENDING, '0.2', '0.1'))
        shipped = shipped_changelog(PENDING)
        self.assertNotIn('Next update', shipped)
        self.assertNotIn('Tricks', shipped)
        self.assertIn('## 0.2 -', shipped)

    def test_start_release_promotes_and_drops_empty_groups(self):
        (self.root / 'CHANGELOG.md').write_text(PENDING, encoding='utf-8')
        quiet(start_release, '0.3', '2026-11-03', self.root)
        text = self.text('CHANGELOG.md')
        self.assertIn('## Next update\n\n### New\n\n### Improved\n\n### Fixed\n\n## 0.3 - Companions learn new tricks (3 November 2026)', text)
        section = text.split('## 0.3 -')[1].split('## 0.2 -')[0]
        self.assertIn('**Tricks:**', section)
        self.assertNotIn('### Fixed', section)
        self.assertEqual([], lint(text))

    def test_start_release_refuses_empty_next_update(self):
        with self.assertRaisesRegex(ValueError, 'Next update needs'):
            start_release('0.3', '2026-11-03', self.root)

    def test_sync_is_repeatable_and_check_then_passes(self):
        with self.assertRaisesRegex(ValueError, 'run sync 0.2'):
            check_release_text(self.root)
        changed, _ = quiet(sync_release_text, '0.2', self.root)
        self.assertIn('README.md', changed)
        self.assertIn('Latest_Release-v0.2', self.text('README.md'))
        content = self.text('website/config/aetherfall_content.php')
        self.assertIn("'repack_version' => '0.2'", content)
        self.assertIn("'slug' => 'aetherfall-0-2'", content)
        self.assertIn("route('news.show', 'aetherfall-0-2')", self.text('website/resources/views/pages/info.blade.php'))
        self.assertEqual([], quiet(sync_release_text, '0.2', self.root)[0])
        self.assertIn('passed', quiet(check_release_text, self.root)[1])

    def test_sync_records_old_and_new_website_rows_for_deploys(self):
        quiet(sync_release_text, '0.2', self.root)
        baselines = self.text('website/resources/content/aetherfall-v5.json')
        self.assertIn('"repack_version": "0.1"', baselines)
        self.assertIn('"repack_version": "0.2"', baselines)
        self.assertNotIn('is_featured', baselines)

    def test_publish_check_stops_on_stale_repack_copy(self):
        package = self.root / 'package'
        package.mkdir()
        (package / 'CHANGELOG.md').write_text('old', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'differs from source'):
            check_release_text(self.root, package, '0.2')
        (package / 'CHANGELOG.md').write_text(shipped_changelog(CHANGELOG), encoding='utf-8')
        quiet(check_release_text, self.root, package, '0.2')
        with self.assertRaisesRegex(ValueError, 'Expected exactly one'):
            check_release_text(self.root, package, '0.3')

    def test_backfill_plan(self):
        def release(tag, body):
            return dict(tag_name=tag, id=tag, body=body, published_at='2026-11-01T00:00:00Z')
        image = '<img width="10" src="https://example.test/shot.png">'
        text = CHANGELOG + '\n## 0.0.5 - Old days (1 October 2026)\n\nOld days.\n\n### Fixed\n\n- Old fix.\n'
        plan = {item['tag']: item for item in backfill_plan(text, [
            release('v0.2', 'newest'), release('v0.1', 'Players: open `Setup.exe` to download. ' + image),
            release('v0.0.5', 'See https://example.test/mirror'), release('v0.86', 'install'),
            release('v0.0.3', 'no notes')])}
        self.assertNotIn('v0.2', plan)
        self.assertIn('Quest windows close correctly', plan['v0.1']['new'])
        self.assertIn(image, plan['v0.1']['new'])
        self.assertTrue(plan['v0.1']['new'].endswith(UPDATE_LINE + '\n'))
        self.assertIsNone(plan['v0.1']['reason'])
        self.assertIn('links', plan['v0.0.5']['reason'])
        self.assertIn('no player notes', plan['v0.0.3']['reason'])


if __name__ == '__main__':
    unittest.main()
