"""Offline delivery failure-boundary tests; temporary files and simulated SQL proof only."""
import importlib
import json
import tempfile
import unittest
from contextlib import contextmanager
from pathlib import Path
from unittest.mock import patch

migration = importlib.import_module('migrate-playerbot-population')


@contextmanager
def unlocked(directory, rows):
    yield


class Retirement(unittest.TestCase):
    def setUp(self):
        diagnostics = migration.shared.DEV_ROOT / 'diagnostics/population-migration-tool-tests'
        diagnostics.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(dir=diagnostics)
        assert Path(self.temporary.name).resolve().is_relative_to(diagnostics.resolve())
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.server = self.root / 'server'
        self.directory = self.server / 'config/playerbots'
        self.directory.mkdir(parents=True)
        self.jar = self.server / 'libs/game-server-4.8-SNAPSHOT.jar'
        self.jar.parent.mkdir(); self.jar.write_bytes(b'normal builder jar')
        self.review = self.root / 'review'; self.output = self.review / 'population-import'
        self.output.mkdir(parents=True)
        self.backup = self.root / 'backup'; self.backup.mkdir()
        self.metadata = dict(legacy={}, jarSha256=migration.shared.sha(self.jar), schemaSha256='schema')
        for name in migration.FILES:
            if name == 'world-population.json': self.metadata['legacy'][name] = None; continue
            (self.directory / name).write_text('original ' + name)
            (self.backup / name).write_bytes((self.directory / name).read_bytes())
            self.metadata['legacy'][name] = migration.shared.sha(self.directory / name)
        (self.directory / 'unrelated.properties').write_text('keep this')
        (self.output / 'active-backup.json').write_text(json.dumps(dict(path=str(self.backup))))
        (self.output / 'prepared.json').write_text('{}')
        (self.backup / 'committed.json').write_text('{}')
        self.receipt = self.root / 'receipt'; self.receipt.mkdir()
        self.manifest = self.receipt / 'manifest.json'
        self.manifest.write_text(json.dumps(dict(scope='PB-SCOPE-012D', configFiles={
            'config\\playerbots\\' + name: digest for name, digest in self.metadata['legacy'].items() if digest is not None}, retiredFiles=[])))
        (self.receipt / 'installed.json').write_text('{}')
        self.addCleanup(patch.stopall)
        patch.object(migration, 'SERVER', self.server).start()
        patch.object(migration, 'guard', return_value=(self.output, self.metadata)).start()
        patch.object(migration.shared, 'receipt_paths', return_value=[self.manifest]).start()
        patch.object(migration, 'unused_files', unlocked).start()
        self.proof = patch.object(migration, 'run', side_effect=self.verify).start()

    def verify(self, output, metadata, mode, report):
        self.assertEqual(mode, 'retire')
        self.assertTrue(all((self.directory / name).exists() for name, digest in metadata['legacy'].items() if digest is not None))
        report.write_text('independent native read proves exact legacy values')

    def test_exact_retirement_preserves_other_config_and_recovery(self):
        migration.finish(self.review)
        self.assertEqual((self.directory / 'unrelated.properties').read_text(), 'keep this')
        self.assertTrue(all(not (self.directory / name).exists() for name in migration.FILES))
        manifest = json.loads(self.manifest.read_text())
        self.assertEqual(len(manifest['retiredFiles']), 4)
        self.assertEqual(manifest['configFiles'], {})
        self.assertTrue(all(migration.shared.sha(self.backup / name) == digest for name, digest in self.metadata['legacy'].items() if digest is not None))
        self.assertEqual(manifest['populationImport']['recovery'], str(self.backup))

    def test_sql_proof_failure_never_retires(self):
        self.proof.side_effect = RuntimeError('SQL verification refused')
        with self.assertRaises(RuntimeError): migration.finish(self.review)
        self.assertEqual(migration.legacy(), self.metadata['legacy'])
        self.assertFalse((self.backup / 'retired.json').exists())

    def test_reader_must_be_installed_before_retirement(self):
        self.jar.write_bytes(b'old file-backed reader')
        with self.assertRaises(AssertionError): migration.finish(self.review)
        self.proof.assert_not_called()
        self.assertEqual(migration.legacy(), self.metadata['legacy'])

    def test_partial_file_retirement_restores_exact_originals(self):
        original = Path.unlink
        failed = self.directory / 'world-server-population.json'
        def deny(path, *args, **kwargs):
            if path == failed: raise PermissionError('Synthetic sharing violation')
            return original(path, *args, **kwargs)
        with patch.object(Path, 'unlink', deny):
            with self.assertRaises(PermissionError): migration.finish(self.review)
        self.assertEqual(migration.legacy(), self.metadata['legacy'])
        self.assertFalse((self.backup / 'retired.json').exists())
        self.assertEqual(json.loads(self.manifest.read_text())['retiredFiles'], [])

    def test_changed_backup_refuses_removal(self):
        (self.backup / 'world-bots.properties').write_text('changed')
        with self.assertRaises(AssertionError): migration.finish(self.review)
        self.assertEqual(migration.legacy(), self.metadata['legacy'])


if __name__ == '__main__': unittest.main()
