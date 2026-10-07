"""Read-only installed-mod inventory. Receipts are history, not gameplay acceptance."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zipfile
import os

HERE=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(HERE/'expanded-warehouse'))
sys.path.insert(0,str(HERE/'transmog-menu'))
from codec import read_pak,binary_xml
import patch_game_dll as hooks


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True);parser.add_argument('--server',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();client=args.client.resolve();server=args.server.resolve()
    hashes={}
    def sha(path):
        if path not in hashes:
            if path.is_file():
                with path.open('rb') as stream:hashes[path]=hashlib.file_digest(stream,'sha256').hexdigest()
            else:hashes[path]=None
        return hashes[path]
    def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
    live_paths=['bin64/Game.dll','bin64/crysystem.dll','bin64/Awesomium.dll','bin64/XRenderD3D9.dll',
                'bin64/AionGraphicsMenu.dll','bin64/AionIconBridge.dll','bin64/AionIconBridge.index',
                'bin64/AionMarketShortcut.dll','bin64/AionRememberLogin.dll','bin64/AionSpeechBubbles.dll',
                'Data/ui/ui.pak','Data/ui/game/game.pak','L10N/enu/Data/data.pak','Data/Items/Items.pak',
                'Textures/ui/ui.pak','bin32/bin32.pak','Data/func_pet/func_pet.pak','Addon.key','Pub.key',
                'Plugin/RelicCalc/RelicCalc.pak','Plugin/RelicCalc/RelicCalc.pak.sig',
                'bin32/bin32.pak.sig','Data/func_pet/func_pet.pak.sig','Aion Start.bat',
                'DXVK/installed.json','DXVK/graphics-menu/installed.json','DXVK/graphics-menu/package/manifest.json',
                'DXVK/renderer.ini','DXVK/renderer-env.bat','system.cfg','SystemOptionGraphics.cfg']
    checks={}
    game=(client/'bin64/Game.dll').read_bytes()
    routes=['http://127.0.0.1:8091/shop','http://127.0.0.1:8091/market','http://127.0.0.1:8091/market/wardrobe',
            'http://127.0.0.1:8091/journey','http://127.0.0.1:8091/market/pass','http://127.0.0.1:8091/market/companions']
    checks['all_six_native_browser_routes']=all(game[offset:offset+len(code)]==code for offset,code in [
        (hooks.BROWSER_HOOK_RVA,hooks.build_browser_hook_code(routes)),
        (hooks.MARKET_AUTH_HOOK_RVA,hooks.build_market_auth_code(routes[1:],compact=True))])
    with read_pak(client/'Plugin/RelicCalc/RelicCalc.pak') as pak:
        menu=pak.read('PrivateMenus.lua').decode('utf-8-sig')
        checks['existing_menu_actions']=all(word in menu for word in ['PRIVATECOMPANIONS','PRIVATESEASONPASS','PRIVATEJOURNEY','PRIVATECASHSHOP','PRIVATEWAREHOUSE','PRIVATEWARDROBE'])
        checks['native_wardrobe_entries']=all(name in pak.namelist() for name in ['Wardrobe.xml','WardrobeNative.lua','WardrobeIcons.lua','WardrobeTheme.lua'])
    checks['base_and_english_layouts']=True
    for rel,prefix in [('Data/ui/game/game.pak',''),('L10N/enu/Data/data.pak','ui/game/')]:
        with read_pak(client/rel) as pak:
            for name in ['inventory_dialog.xml','warehouse_dialog.xml','player_info_dialog.xml']:
                root=binary_xml(pak.read(prefix+name))
                checks['base_and_english_layouts'] &= 'movable' in root.get('flag','').split(';') and not root.get('align_type')
                if name=='inventory_dialog.xml':checks['base_and_english_layouts'] &= root.find(".//Widget[@name='inventory_search']") is not None
                if name=='warehouse_dialog.xml':checks['base_and_english_layouts'] &= all(root.find(".//Widget[@name='"+n+"']") is not None for n in ['warehouse_account_search','warehouse_character_search'])
    with read_pak(client/'L10N/enu/Data/data.pak') as pak:
        login=binary_xml(pak.read('ui/ui_login.xml'))
        checks['remember_login_english']=login.find(".//Widget[@name='remember_login']") is not None and login.find(".//Widget[@name='password']").get('flag')=='password'
        checks['season_pass_ticket_both_huds']=all(binary_xml(pak.read(f'ui/game_hud_s{s}/start_dialog.xml')).find("Widget[@name='season_pass_button']") is not None for s in [1,2])
    pe=struct.unpack_from('<I',game,60)[0];count=struct.unpack_from('<H',game,pe+6)[0]
    table=pe+24+struct.unpack_from('<H',game,pe+20)[0]
    sections=[struct.unpack_from('<8sIIII',game,table+i*40) for i in range(count)]
    def offset(rva):return next(s[4]+rva-s[2] for s in sections if s[2]<=rva<s[2]+s[3])
    hook_receipts=['RememberLogin-backups/20261003-074802-916/manifest.json','RememberLogin-backups/20261003-190013-967/manifest.json',
                   'SpeechBubbles-backups/menu-revision-20261002-000305-836/manifest.json']
    checks['remember_login_and_speech_hooks']=True
    for rel in hook_receipts:
        for record in read(client/rel)['hooks']:
            site=int(record['site'],0) if isinstance(record['site'],str) else record['site']
            target=int(record['hook'],0) if isinstance(record['hook'],str) else record['hook']
            expected=b'\xe9'+struct.pack('<i',target-site-5)
            checks['remember_login_and_speech_hooks'] &= game[offset(site):offset(site)+5]==expected
    index=(client/'bin64/AionIconBridge.index').read_bytes()
    checks['native_icon_index_matches_items']=index[24:56].hex()==sha(client/'Data/Items/Items.pak') and struct.unpack_from('<Q',index,16)[0]==(client/'Data/Items/Items.pak').stat().st_size
    graphics=read(client/'DXVK/graphics-menu/installed.json');dxvk=read(client/'DXVK/installed.json')
    checks['graphics_package_and_recovery']=graphics['files']==read(client/'DXVK/graphics-menu/package/manifest.json')['files']
    for entry in graphics['files']:
        checks['graphics_package_and_recovery'] &= sha(client/entry['path'])==entry['installed'] and sha(client/'DXVK/graphics-menu/package'/entry['path'])==entry['installed']
        if entry['original'] is not None:checks['graphics_package_and_recovery'] &= sha(Path(graphics['backupRoot'])/entry['path'])==entry['original']
    checks['cursor_tracking_and_recovery']=True
    for entry in dxvk['nativeCursorPatch']['files']:
        checks['cursor_tracking_and_recovery'] &= sha(client/entry['path'])==entry['installed'].lower() and sha(Path(entry['backupPath']))==entry['original'].lower()
    checks['browser_probe_remains_removed']=not (client/'bin64/AionBrowserProbe.dll').exists()
    with read_pak(client/'Plugin/RelicCalc/RelicCalc.pak') as addon:
        if 'PlayerBotBar.lua' in addon.namelist():
            checks['playerbot_party_bar']=all(name in addon.namelist() for name in ('PlayerBotBar.lua','PlayerBotBar.xml')) and all(name.encode() in addon.read('RelicCalc.toc') for name in ('PlayerBotBar.lua','PlayerBotBar.xml'))
    receipts=[str(p.relative_to(client)) for folder in client.glob('*-backups') if folder.is_dir() for p in folder.glob('*/manifest.json')]
    archive_root=Path(os.environ.get('AION_DEV_ROOT','D:/Proiecte/Project Restructure/Aion Development Workspace'))/'archives/server/game-server/backups'
    server_receipts=[str(p.relative_to(server)) for p in (server/'backups').glob('*/manifest.json')]
    server_receipts += [str(p.resolve()) for p in archive_root.glob('*/manifest.json')]
    jar=server/'libs/game-server-4.8-SNAPSHOT.jar'
    with zipfile.ZipFile(jar) as archive:
        names=archive.namelist()
        checks['rejected_afk_extension_remains_removed']=not any('AfkKeepAlive' in name for name in names) and not (server/'config/main/afk.properties').exists()
        checks['playerbot_service_deployed']='com/aionemu/gameserver/services/playerbot/PlayerBotService.class' in names
    overrides=[]
    override=server/'libs/playerbot-recruitment-fix.jar'
    if override.exists():
        override_receipts=sorted(list((server/'backups').glob('playerbots-recruitment-*/manifest.json'))+list(archive_root.glob('playerbots-recruitment-*/manifest.json')),key=lambda p:p.parent.name)
        assert override_receipts,'Companion override has no installation receipt'
        latest=read(override_receipts[-1])
        effective={e['path']:e['installed'] for e in latest['files']}
        ui_receipts=sorted((server/'backups').glob('playerbots-ui-*/manifest.json'))
        for ui_path in ui_receipts:
            ui=read(ui_path)
            if not (ui_path.parent/'installed.json').exists():continue
            assert ui['feature']=='playerbot-window-ui' and Path(ui['deployment'])==server
            assert {e['path'] for e in ui['files']}=={'config/playerbots/media/bots.'+ext for ext in ('html','css','js')}
            for entry in ui['files']:
                # Only apply UI receipts based on the current cumulative override's media.
                if ui_path.parent.name.removeprefix('playerbots-ui-') < override_receipts[-1].parent.name.removeprefix('playerbots-recruitment-'):continue
                assert entry['original']==effective[entry['path']], 'Companion UI receipt baseline differs'
                assert sha(ui_path.parent/entry['path'])==entry['original'], 'Companion UI backup differs'
                effective[entry['path']]=entry['installed']

        def receipt_order(path):
            match=re.search(r'(\d{8})-(\d{6})',path.parent.name)
            return datetime.strptime(match.group(1)+'-'+match.group(2),'%Y%m%d-%H%M%S').replace(tzinfo=timezone.utc) if match else datetime.min.replace(tzinfo=timezone.utc)

        expected_override=effective.get('libs/playerbot-recruitment-fix.jar')
        saendukal_receipts=sorted((server/'backups').glob('saendukal-strong-protection-*/manifest.json'),key=receipt_order)
        checks['saendukal_strong_protection_classes']=True
        for receipt_path in saendukal_receipts:
            receipt=read(receipt_path)
            if receipt_order(receipt_path)>receipt_order(override_receipts[-1]):
                checks['saendukal_strong_protection_classes'] &= receipt.get('previousOverrideSha256')==expected_override
                rollback_path=receipt_path.parent/receipt.get('rollbackFile','playerbot-recruitment-fix.jar')
                checks['saendukal_strong_protection_classes'] &= sha(rollback_path)==expected_override
                expected_override=receipt.get('installedOverrideSha256')

        if saendukal_receipts:
            latest_saendukal=read(saendukal_receipts[-1])
            expected_classes=latest_saendukal.get('addedClassSha256',{})
            try:
                with zipfile.ZipFile(override) as archive:
                    checks['saendukal_strong_protection_classes'] &= all(
                        name in archive.namelist() and hashlib.sha256(archive.read(name)).hexdigest()==digest
                        for name,digest in expected_classes.items()) and bool(expected_classes)
            except (OSError,zipfile.BadZipFile):
                checks['saendukal_strong_protection_classes']=False

        checks['recruitment_override_and_launcher']=sha(jar)==latest['baseJarSha256'] and sha(override)==expected_override and all(sha(server/path)==digest for path,digest in effective.items() if path!='libs/playerbot-recruitment-fix.jar')
        checks['recruitment_override_and_launcher'] &= b'-cp "libs/playerbot-recruitment-fix.jar;libs/*"' in (server/'start.bat').read_bytes()
        receipt_path=override_receipts[-1]
        receipt_label=str(receipt_path.relative_to(server)) if receipt_path.is_relative_to(server) else str(receipt_path)
        overrides.append(dict(path='libs/playerbot-recruitment-fix.jar',sha256=sha(override),receipt=receipt_label,changedMethods=latest['changedMethods']))
    configs={}
    for path in (server/'config/main').glob('*.properties'):
        for line in path.read_text(encoding='utf-8-sig').splitlines():
            if line.strip().startswith('#') or '=' not in line:continue
            key,value=map(str.strip,line.split('=',1))
            if any(word in key for word in ['inventory.unified','warehouse.expanded','poeta.journey','playerbot','central.market.simulation','broker.market']):configs[key]=value
    report=dict(checkedAt=datetime.now(timezone.utc).isoformat(),clientRoot=str(client),serverRoot=str(server),
        checks=checks,clientFiles=[dict(path=rel,sha256=sha(client/rel)) for rel in live_paths],
        clientReceipts=sorted(receipts),serverReceipts=sorted(server_receipts),deployedConfig=configs,
        serverJarSha256=sha(jar),serverClassOverrides=overrides,serverLauncherSha256=sha(server/'start.bat'),graphicsBackup=graphics['backupRoot'],
        limitations=['Hashes/layouts/hooks do not establish live gameplay acceptance.',
                     'Quest Tooltip directory holds a prototype; installation is not established.',
                     'Browser replacement and shared webpage flash correction remain unfinished.',
                     'Full playerbot coverage remains unfinished; consult docs/PLAYERBOTS.md.'])
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(report,indent=2),encoding='utf-8')
    failed=[name for name,passed in checks.items() if not passed]
    if failed:raise ValueError('Installed mod checks failed: '+', '.join(failed))
    print('OK:',len(checks),'installed mod checks;',len(live_paths),'current file hashes;',len(receipts),'client and',len(server_receipts),'server historical receipts recorded:',args.output)


if __name__=='__main__':main()
