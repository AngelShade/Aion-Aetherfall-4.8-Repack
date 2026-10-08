"""Exercise refused inspection and transaction rollback using tiny file fixtures."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import install_window_queue as installer

class Checks(unittest.TestCase):
    def test_denied_process_query_never_means_closed(self):
        with patch.object(installer.subprocess,'run',return_value=type('Result',(),{'returncode':3})()):
            with self.assertRaisesRegex(ValueError,'inspection failed'):installer.closed()
        with patch.object(installer.subprocess,'run',return_value=type('Result',(),{'returncode':2})()):
            with self.assertRaisesRegex(ValueError,'close every'):installer.closed()

    def test_mid_transaction_failure_restores_every_written_file(self):
        self.check_rollback('custom-window-queue-v1','window-queue',3)

    def test_login_failure_after_dll_replacement_restores_dll_and_native_records(self):
        self.check_rollback('remember-login-return-v2','remember-login-return',4)

    def test_reconnect_relocation_failure_restores_native_records(self):
        self.check_rollback('remember-login-reconnect-v3','remember-login-return',4)

    def check_rollback(self,feature,prefix,fail_at):
        diagnostics=installer.DEV_ROOT/'diagnostics';diagnostics.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='queue-install-test-',dir=diagnostics) as temporary:
            dev=Path(temporary).resolve();client=dev/'client';package=dev/'staging/output/package';package.mkdir(parents=True)
            backup='DXVK-backups/service-menu-graphics-20261007-000000-000000'
            names=['bin64/Game.dll','DXVK/installed.json','DXVK/graphics-menu/installed.json','DXVK/graphics-menu/package/manifest.json',
                   'DXVK/graphics-menu/package/bin64/Game.dll','DXVK/graphics-menu/package/Data/ui/game/game.pak',
                   backup+'/bin64/Game.dll',backup+'/Data/ui/game/game.pak',backup+'/cursor-base/bin64/Game.dll']
            if feature in ('remember-login-return-v2','remember-login-reconnect-v3'):names.insert(1,'bin64/AionRememberLogin.dll')
            entries=[]
            for i,name in enumerate(names):
                dest=package/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(b'updated')
                old=client/name;original=None
                if i<6:old.parent.mkdir(parents=True,exist_ok=True);old.write_bytes(b'original');original=installer.sha(old)
                entries.append(dict(path=name,original=original,staged=installer.sha(dest)))
            m=dict(feature=feature,clientRoot=str(client),files=entries,preservedFiles=[],
                   graphicsCompatibility=dict(backupName=backup.split('/')[-1],localized=False))
            (package/'manifest.json').write_text(json.dumps(m))
            replace=installer.replace;calls=0
            def fail_third(path,data):
                nonlocal calls
                calls+=1
                if calls==fail_at:raise OSError('simulated replacement failure')
                replace(path,data)
            with patch.object(installer,'DEV_ROOT',dev),patch.object(installer,'CLIENT',client),patch.object(installer,'closed'),patch.object(installer,'replace',side_effect=fail_third),patch.object(sys,'argv',['installer','--package',str(package)]):
                with self.assertRaisesRegex(OSError,'simulated'):installer.main()
            for e in entries:self.assertEqual(installer.sha(client/e['path']),e['original'],e['path'])
            receipt=next((dev/'archives/client').glob(prefix+'-*'))
            for e in entries:
                if e['original']:self.assertEqual(installer.sha(receipt/e['path']),e['original'])

if __name__=='__main__':unittest.main()
