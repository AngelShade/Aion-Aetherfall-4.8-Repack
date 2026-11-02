"""Focused regression checks for cumulative player-facing release notes."""
import io
import json
import unittest
from unittest.mock import patch

from publish_update import previous_release
from release_notes import github_release_body, previous_published_version, version_notes


CHANGELOG = """# Changelog

## 0.98.12 - Unpublished work
### New
- Future feature.

## 0.98.11 - Road merchants (2 November 2026)
You can meet merchants on the roads.
### New
- **Merchants:** buy supplies while travelling.
### For server hosts
- Set `gameserver.playerbots.generated.enable = true` in `Server/game-server/config/playerbots/playerbots.properties`.
- A host may install the server under `C:\\Games\\Aetherfall`.
### Internal
- Secret build detail.
### Fixed
- Prepared locally; publication is separate.
- Private receipt at C:\\Builds\\receipt.json.
- Quest windows close correctly.

## Prepared local update — 0.98.10 (2 November 2026)
### New
- **Companions:** collect new party members.
### Tooling
- Private tool details.

## 0.98.9 - Languages
### Fixed
- Old language fix.
"""


class ReleaseNotesTests(unittest.TestCase):
    def test_intermediate_versions_are_included(self):
        notes = version_notes(CHANGELOG, '0.98.11', '0.98.9')
        self.assertIn('**Merchants:**', notes)
        self.assertIn('**Companions:**', notes)
        self.assertNotIn('Future feature', notes)
        self.assertNotIn('Old language fix', notes)

    def test_folded_release_and_numeric_order(self):
        text = '## 0.98.11 - Collection\n### New\n- Collect companions.\n'
        self.assertIn('Collect companions', version_notes(text, 'v0.98.11', '0.98.9'))
        self.assertIn('**Merchants:**', version_notes(CHANGELOG, '0.98.11', '0.98.10'))
        self.assertNotIn('**Companions:**', version_notes(CHANGELOG, '0.98.11', '0.98.10'))

    def test_private_filters_keep_host_instructions(self):
        notes = version_notes(CHANGELOG, '0.98.11', '0.98.9')
        self.assertIn('### For server hosts', notes)
        self.assertIn('gameserver.playerbots.generated.enable = true', notes)
        self.assertIn('C:\\Games\\Aetherfall', notes)
        for private in ('Secret build', 'Private tool', 'Prepared locally', 'receipt.json'):
            self.assertNotIn(private, notes)

    def test_unknown_or_invalid_previous_release_stops(self):
        for previous in (None, '', '0.98.11', '0.98.12'):
            with self.subTest(previous=previous), self.assertRaisesRegex(ValueError, 'Previous published release'):
                version_notes(CHANGELOG, '0.98.11', previous)

    def test_missing_or_fully_filtered_target_notes_stop(self):
        for text in ('## 0.98.10\n- Earlier feature.\n',
                     '## 0.98.11\n- Prepared locally; publication is separate.\n'
                     '## 0.98.10\n- Earlier feature.\n'):
            with self.subTest(text=text), self.assertRaisesRegex(ValueError, 'No player-facing changelog'):
                version_notes(text, '0.98.11', '0.98.9')

    def test_github_body_keeps_update_guidance(self):
        body = github_release_body(CHANGELOG, '0.98.11', '0.98.9')
        self.assertIn('**Companions:**', body)
        self.assertIn('## Compatibility', body)
        self.assertIn('## How to update', body)
        self.assertIn('**Update Repack**', body)

    def test_previous_release_ignores_target_drafts_and_prereleases(self):
        releases = [dict(tag_name=tag, published_at='2026-11-02T00:00:00Z', **flags)
                    for tag, flags in [('v0.98.11', {}), ('v0.98.10', {'draft': True}),
                                       ('v0.98.10', {'prerelease': True}), ('v0.98.9', {}),
                                       ('v0.98.8', {}), ('not-a-version', {})]]
        self.assertEqual('0.98.9', previous_published_version('0.98.11', releases))
        with self.assertRaisesRegex(ValueError, 'could not be determined'):
            previous_published_version('0.98.11', [])

    def test_public_lookup_is_unauthenticated_and_paginated(self):
        first = [dict(tag_name='v0.98.11', published_at='2026-11-02T00:00:00Z')] * 100
        second = [dict(tag_name='v0.98.9', published_at='2026-11-01T00:00:00Z')]
        responses = [io.BytesIO(json.dumps(page).encode()) for page in (first, second)]
        with patch('publish_update.urllib.request.urlopen', side_effect=responses) as lookup:
            self.assertEqual('0.98.9', previous_release('0.98.11'))
        self.assertEqual(2, lookup.call_count)
        for call in lookup.call_args_list:
            self.assertFalse(call.args[0].has_header('Authorization'))
        self.assertIn('page=2', lookup.call_args_list[1].args[0].full_url)

    def test_failed_public_lookup_stops(self):
        with patch('publish_update.urllib.request.urlopen', side_effect=OSError('offline')):
            with self.assertRaisesRegex(ValueError, 'Previous published release could not be determined'):
                previous_release('0.98.11')


if __name__ == '__main__':
    unittest.main()
