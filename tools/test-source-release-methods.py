"""Release method-preservation regressions; no installation or JVM/world startup."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import json
import hashlib
import zipfile
from tempfile import TemporaryDirectory

spec = importlib.util.spec_from_file_location('source_delivery', Path(__file__).with_name('deliver-source-component.py'))
delivery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(delivery)
configuration = delivery.import_module('document-playerbot-config')
verification = delivery.import_module('verify-source-delivery')
quest_inventory = delivery.import_module('quest-handler-inventory')


def resource_fixture():
    root = (delivery.shared.DEV_ROOT / 'tooling/tests/source-delivery').resolve()
    assert root.is_relative_to(delivery.shared.DEV_ROOT.resolve())
    root.mkdir(parents=True, exist_ok=True)
    fixture = TemporaryDirectory(dir=root)
    assert Path(fixture.name).resolve().parent == root
    return fixture


class MethodPreservationCheck(unittest.TestCase):
    def test_configuration_move_preserves_owner_destination(self):
        migration=delivery.import_module('move-playerbot-configuration')
        with resource_fixture() as folder:
            server=Path(folder);old=server/migration.paths.LEGACY;old.parent.mkdir(parents=True)
            old.write_bytes(b'# owner\r\ngameserver.playerbots.population.max=1500\r\n')
            before=old.read_bytes();_,new=migration.validate_locations(server)
            new.parent.mkdir(parents=True);new.write_bytes(b'# newer owner choices\n')
            with self.assertRaisesRegex(AssertionError,'already exists'):migration.validate_locations(server)
            self.assertEqual(old.read_bytes(),before)
            self.assertEqual(new.read_bytes(),b'# newer owner choices\n')

    def test_configuration_move_refuses_missing_source_or_directory(self):
        migration=delivery.import_module('move-playerbot-configuration')
        with resource_fixture() as folder:
            server=Path(folder)
            with self.assertRaisesRegex(AssertionError,'legacy'):migration.validate_locations(server)
            old=server/migration.paths.LEGACY;old.mkdir(parents=True)
            with self.assertRaisesRegex(AssertionError,'legacy'):migration.validate_locations(server)

    def test_configuration_tools_use_new_location_without_fallback(self):
        paths=delivery.import_module('playerbot-config-paths')
        with resource_fixture() as folder:
            server=Path(folder);old=server/paths.LEGACY;old.parent.mkdir(parents=True);old.write_bytes(b'# old\n')
            self.assertEqual(paths.active(server),server/paths.CURRENT)
            self.assertFalse(paths.active(server).exists())

    def test_level_spread_preserves_every_unrelated_owner_byte(self):
        module=delivery.import_module('configure-playerbot-level-spread')
        before=b'# owner\r\ngameserver.playerbots.population.min_level_percent = 10\r\ngameserver.playerbots.population.max_level_percent = 65\r\ngameserver.playerbots.population.max = 1500\r\n'
        builder=before.replace(b'= 10',b'= 0').replace(b'= 65',b'= 0')
        self.assertEqual(module.replacement(before,builder),builder)
        module.validate(before,builder,builder)
        with self.assertRaises(AssertionError):module.validate(before,builder.replace(b'1500',b'160'),builder)

    def test_level_spread_rejects_missing_duplicate_or_nonuniform_builder(self):
        module=delivery.import_module('configure-playerbot-level-spread')
        data=b'gameserver.playerbots.population.min_level_percent = 0\ngameserver.playerbots.population.max_level_percent = 0\n'
        for bad in (data+data,data.splitlines(keepends=True)[0],data.replace(b'= 0',b'= 65',1)):
            with self.assertRaises(AssertionError):module.replacement(data,bad)

    def test_feature_defaults_are_added_once_and_owner_edits_are_preserved(self):
        with resource_fixture() as folder:
            target=Path(folder)/delivery.FEATURE_CONFIGURATION
            self.assertTrue(delivery.initial_feature_configuration(target))
            target.parent.mkdir(parents=True)
            target.write_bytes(b'gameserver.playerbots.quests.enable = false\n')
            self.assertFalse(delivery.initial_feature_configuration(target))
            self.assertEqual(target.read_bytes(),b'gameserver.playerbots.quests.enable = false\n')

    def test_navigation_defaults_seed_once_and_preserve_owner_edits(self):
        with resource_fixture() as folder:
            target = Path(folder) / delivery.NAVIGATION_CONFIGURATION
            self.assertTrue(delivery.initial_feature_configuration(target))
            target.parent.mkdir(parents=True)
            choices = b'gameserver.playerbots.navigation.budget_ms_per_second = 200\n'
            target.write_bytes(choices)
            self.assertFalse(delivery.initial_feature_configuration(target))
            self.assertEqual(target.read_bytes(), choices)
            delivery.verify_original_resource(target, delivery.shared.sha(target))
            with self.assertRaises(AssertionError): delivery.verify_original_resource(target, None)

    def test_navigation_path_directory_is_rejected(self):
        with resource_fixture() as folder:
            target = Path(folder) / delivery.NAVIGATION_CONFIGURATION
            target.mkdir(parents=True)
            with self.assertRaisesRegex(AssertionError, 'not a regular file'):
                delivery.initial_feature_configuration(target)

    def test_navigation_and_feature_settings_have_documented_unique_homes(self):
        catalogue = configuration.source_catalogue()
        navigation = configuration.properties(configuration.NAVIGATION_SOURCE.read_text())
        self.assertEqual(len(navigation), 7)
        self.assertTrue(all(key.startswith('gameserver.playerbots.navigation.') for key in navigation))
        self.assertTrue(set(navigation) <= set(catalogue))
        self.assertEqual(delivery.OWNER_CONFIGURATIONS, {delivery.FEATURE_CONFIGURATION, delivery.NAVIGATION_CONFIGURATION, delivery.PLAYERBOT_CONFIGURATION, delivery.INSTANCE_CONFIGURATION})

    def test_seeded_navigation_is_guarded_in_final_configuration_receipt(self):
        existing = {'config/main/playerbots.properties': 'owner choices', delivery.FEATURE_CONFIGURATION: 'owner switches'}
        seeded = dict(path=delivery.NAVIGATION_CONFIGURATION, original=None, installed='normal builder defaults')
        final = delivery.resulting_configurations(existing, [seeded])
        self.assertEqual(final, existing | {delivery.NAVIGATION_CONFIGURATION: 'normal builder defaults'})
        self.assertNotIn(delivery.NAVIGATION_CONFIGURATION, existing)
        with self.assertRaises(AssertionError):
            delivery.resulting_configurations(existing, [dict(seeded, original='owner file')])

    def test_only_exact_feature_configuration_can_be_a_release_resource(self):
        self.assertTrue(delivery.allowed_resource(delivery.FEATURE_CONFIGURATION))
        self.assertTrue(delivery.allowed_resource(delivery.NAVIGATION_CONFIGURATION))
        self.assertTrue(delivery.allowed_resource(delivery.PLAYERBOT_CONFIGURATION))
        self.assertTrue(delivery.allowed_resource('data/handlers/quest/example.java'))
        for name in ['config/main/playerbots.properties','config/mygs.properties','config/playerbots/other.properties',
                     'config/playerbots/../features.properties','config\\playerbots\\features.properties',
                     'data/../config/main/playerbots.properties','data/geo/map.geo']:
            self.assertFalse(delivery.allowed_resource(name),name)

    def test_feature_configuration_directory_cannot_silently_disable_delivery(self):
        with resource_fixture() as folder:
            target=Path(folder)/delivery.FEATURE_CONFIGURATION
            target.mkdir(parents=True)
            with self.assertRaisesRegex(AssertionError,'not a regular file'):
                delivery.initial_feature_configuration(target)

    def test_absent_resource_changed_to_directory_is_rejected_by_fresh_guard(self):
        with resource_fixture() as folder:
            root=Path(folder);target=root/delivery.FEATURE_CONFIGURATION
            manifest=dict(files=[],runtimeResourceInputs={delivery.FEATURE_CONFIGURATION:None})
            delivery.verify_original_resource(target,None)
            verification.verify_runtime_resource_inputs(root,manifest)
            target.mkdir(parents=True)
            with self.assertRaisesRegex(AssertionError,'Expected absent resource'):
                delivery.verify_original_resource(target,None)
            with self.assertRaises(AssertionError):
                verification.verify_runtime_resource_inputs(root,manifest)

    def test_fresh_resource_guard_preserves_exact_existing_owner_file(self):
        with resource_fixture() as folder:
            target=Path(folder)/delivery.FEATURE_CONFIGURATION
            target.parent.mkdir(parents=True);target.write_bytes(b'owner choices')
            original=delivery.shared.sha(target)
            delivery.verify_original_resource(target,original)
            with self.assertRaises(AssertionError): delivery.verify_original_resource(target,None)
            target.write_bytes(b'owner changed choices')
            with self.assertRaises(AssertionError): delivery.verify_original_resource(target,original)

    def test_client_generated_configuration_does_not_block_server_release(self):
        with resource_fixture() as folder:
            root=Path(folder);(root/'system.cfg').write_bytes(b'client exit settings')
            (root/'client-mod.dll').write_bytes(b'installed client mod')
            entries=[dict(path='system.cfg',sha256=hashlib.sha256(b'old generated settings').hexdigest()),
                     dict(path='client-mod.dll',sha256=verification.sha(root/'client-mod.dll'))]
            verification.verify_client_files(root,entries)
            self.assertEqual((root/'system.cfg').read_bytes(),b'client exit settings')
            (root/'system.cfg').unlink()
            verification.verify_client_files(root,entries)
            self.assertEqual(len(entries),2)
            self.assertEqual(verification.protected_client_files(entries),entries[1:])

    def test_actual_client_modification_stays_guarded(self):
        with resource_fixture() as folder:
            root=Path(folder);(root/'client-mod.dll').write_bytes(b'changed mod')
            entries=[dict(path='client-mod.dll',sha256=hashlib.sha256(b'installed mod').hexdigest())]
            with self.assertRaisesRegex(AssertionError,'client-mod.dll'):
                verification.verify_client_files(root,entries)

    def test_generated_configuration_exception_is_root_only(self):
        self.assertEqual(verification.protected_client_files([dict(path='SYSTEM.CFG',sha256='old')]),[])
        for name in ['config/system.cfg','config\\system.cfg','SystemOptionGraphics.cfg','system.cfg.backup']:
            entries=[dict(path=name,sha256='guarded')]
            self.assertEqual(verification.protected_client_files(entries),entries)

    def regression_fixture(self, root):
        regression=delivery.import_module('check-source-build')
        restoration=delivery.import_module('check-npc-restoration')
        restored=patch.object(restoration,'verify',return_value={'fixture':'original native behavior'})
        restored.start();self.addCleanup(restored.stop)
        build=root/'build';out=root/'reports';(build/'checks').mkdir(parents=True);out.mkdir()
        (out/'npc-restoration.json').write_text(json.dumps({'fixture':'original native behavior'}))
        (build/'checks/Test.class').write_bytes(b'compiled fixture')
        result='OK: 3 actual checks'
        (out/'Test.txt').write_text(result)
        (out/'checks.json').write_text(json.dumps({'Test':result,'ShutdownHookCheck':'OK: 14 shutdown checks','DatabaseShutdownCheck':'OK: 17 database shutdown checks','PlayerSkillCancellationCheck':'OK: 41 player cancellation checks'}))
        (out/'ShutdownHookCheck.txt').write_text('OK: 14 shutdown checks')
        (out/'DatabaseShutdownCheck.txt').write_text('OK: 17 database shutdown checks')
        db=out/'database-shutdown';(db/'classes').mkdir(parents=True)
        (db/'checks.txt').write_text('OK: 17 database shutdown checks');(db/'compile.txt').write_text('compiled isolated shutdown fixture')
        jars=[build/'game-server/game-server-4.8-SNAPSHOT.jar',build/'commons/commons-4.8-SNAPSHOT.jar']
        for jar in jars:jar.parent.mkdir(parents=True);jar.write_bytes(b'bound native artifact')
        (db/'classes/Stub.class').write_bytes(b'inert dependency fixture')
        (db/'abi.json').write_text('{"fixture":"native descriptors"}')
        (db/'evidence.json').write_text(json.dumps(dict(mode='normal-builder-artifact',count=17,inputs={str(jar):regression.sha(jar) for jar in jars},classes={'Stub.class':regression.sha(db/'classes/Stub.class')},reportSha256=regression.sha(db/'checks.txt'),compileSha256=regression.sha(db/'compile.txt'),abiSha256=regression.sha(db/'abi.json'))))
        cancellation=out/'player-skill-cancellation';(cancellation/'classes').mkdir(parents=True)
        text='OK: 41 player cancellation checks'
        (out/'PlayerSkillCancellationCheck.txt').write_text(text)
        (cancellation/'checks.txt').write_text(text);(cancellation/'compile.txt').write_text('compiled cancellation fixture');(cancellation/'abi.json').write_text('{"fixture":"native packet descriptors"}')
        (cancellation/'classes/Stub.class').write_bytes(b'inert packet fixture')
        (cancellation/'evidence.json').write_text(json.dumps(dict(mode='normal-builder-artifact',count=41,result=text,inputs={str(jar):regression.sha(jar) for jar in jars},classes={'Stub.class':regression.sha(cancellation/'classes/Stub.class')},checksSha256=regression.sha(cancellation/'checks.txt'),compileSha256=regression.sha(cancellation/'compile.txt'),abiSha256=regression.sha(cancellation/'abi.json'))))
        (out/'tooling.txt').write_text('Ran 3 tests\nOK\n');(out/'compile.txt').write_text('successful fixture compilation')
        evidence=dict(version=1,inputs={'builder':'bound'},classes={'Test.class':regression.sha(build/'checks/Test.class')},suites={name:dict(exitCode=0,count=count,result=text,reportSha256=regression.sha(out/(name+'.txt'))) for name,count,text in [('Test',3,result),('ShutdownHookCheck',14,'OK: 14 shutdown checks'),('DatabaseShutdownCheck',17,'OK: 17 database shutdown checks'),('PlayerSkillCancellationCheck',41,'OK: 41 player cancellation checks')]})
        evidence.update(tooling=dict(exitCode=0,count=3,reportSha256=regression.sha(out/'tooling.txt')),compileSha256=regression.sha(out/'compile.txt'))
        (out/'evidence.json').write_text(json.dumps(evidence))
        return regression,build,out,evidence

    def test_npc_restoration_rejects_changed_or_removed_original_behavior(self):
        restoration=delivery.import_module('check-npc-restoration')
        original={'public void spawn(float);':['original native spawn']}
        for changed in ({},{'public void spawn(float);':['geometry override']}):
            with patch.object(restoration.shared,'sha',return_value=restoration.BASELINE_SHA),patch.object(restoration.shared,'methods',side_effect=[original,changed]):
                with self.assertRaisesRegex(AssertionError,'Original NPC method changed'):restoration.verify(Path('fixture'))

    def test_npc_restoration_rejects_stale_or_missing_evidence(self):
        with resource_fixture() as folder:
            regression,build,out,evidence=self.regression_fixture(Path(folder))
            (out/'npc-restoration.json').write_text('{}')
            with self.assertRaisesRegex(AssertionError,'NPC restoration evidence changed'):regression.validate(build,out)
            (out/'npc-restoration.json').unlink()
            with self.assertRaises(FileNotFoundError):regression.validate(build,out)

    def test_regression_missing_failed_or_wrong_individual_suite_is_rejected(self):
        with resource_fixture() as folder:
            regression,build,out,evidence=self.regression_fixture(Path(folder))
            with patch.object(regression,'TESTS',{'Test':3}),patch.object(regression,'QUEST_TESTS',{}),patch.object(regression,'binding',return_value={'builder':'bound'}):
                regression.validate(build,out)
                for key,value in [('exitCode',1),('count',4),('result','OK: 4 forged count')]:
                    wrong=json.loads(json.dumps(evidence));wrong['suites']['Test'][key]=value
                    (out/'evidence.json').write_text(json.dumps(wrong))
                    with self.assertRaises(AssertionError):regression.validate(build,out)
                (out/'evidence.json').unlink()
                with self.assertRaises(FileNotFoundError):regression.validate(build,out)

    def test_regression_stale_inputs_reports_and_compiled_fixtures_are_rejected(self):
        with resource_fixture() as folder:
            regression,build,out,evidence=self.regression_fixture(Path(folder))
            with patch.object(regression,'TESTS',{'Test':3}),patch.object(regression,'binding',return_value={'builder':'different jar or source'}):
                with self.assertRaises(AssertionError):regression.validate(build,out)
            with patch.object(regression,'TESTS',{'Test':3}),patch.object(regression,'binding',return_value={'builder':'bound'}):
                (build/'checks/Test.class').write_bytes(b'altered fixture')
                with self.assertRaises(AssertionError):regression.validate(build,out)
                (build/'checks/Test.class').write_bytes(b'compiled fixture');(out/'Test.txt').write_text('OK: 3 copied report plus changed stderr')
                with self.assertRaises(AssertionError):regression.validate(build,out)

    def test_regression_suite_set_cannot_hide_a_failed_or_missing_suite(self):
        with resource_fixture() as folder:
            regression,build,out,evidence=self.regression_fixture(Path(folder))
            with patch.object(regression,'TESTS',{'Test':3,'Required':1}),patch.object(regression,'binding',return_value={'builder':'bound'}):
                with self.assertRaises(AssertionError):regression.validate(build,out)
    def test_database_shutdown_cannot_use_an_unbound_artifact_or_product_shadow(self):
        with resource_fixture() as folder:
            regression,build,out,evidence=self.regression_fixture(Path(folder))
            path=out/'database-shutdown/evidence.json';original=json.loads(path.read_text())
            with patch.object(regression,'TESTS',{'Test':3}),patch.object(regression,'QUEST_TESTS',{}),patch.object(regression,'binding',return_value={'builder':'bound'}):
                for kind in ['unbound','source-mode','shadow']:
                    wrong=json.loads(json.dumps(original))
                    if kind=='unbound':wrong['inputs']={}
                    elif kind=='source-mode':wrong['mode']='three-source-classes'
                    else:
                        shadow=out/'database-shutdown/classes/com/aionemu/commons/database/DatabaseFactory.class';shadow.parent.mkdir(parents=True);shadow.write_bytes(b'product replacement')
                        wrong['classes'][str(shadow.relative_to(out/'database-shutdown/classes'))]=regression.sha(shadow)
                    path.write_text(json.dumps(wrong))
                    with self.assertRaises(AssertionError):regression.validate(build,out)
    def test_player_cancellation_cannot_use_unbound_artifact_or_product_shadow(self):
        with resource_fixture() as folder:
            regression,build,out,evidence=self.regression_fixture(Path(folder))
            path=out/'player-skill-cancellation/evidence.json';original=json.loads(path.read_text())
            with patch.object(regression,'TESTS',{'Test':3}),patch.object(regression,'QUEST_TESTS',{}),patch.object(regression,'binding',return_value={'builder':'bound'}):
                regression.validate(build,out)
                for kind in ['unbound','source-mode','wrong-count','wrong-result','shadow']:
                    wrong=json.loads(json.dumps(original))
                    if kind=='unbound':wrong['inputs']={}
                    elif kind=='source-mode':wrong['mode']='native-controller-source'
                    elif kind=='wrong-count':wrong['count']=40
                    elif kind=='wrong-result':wrong['result']='OK: 41 substituted result'
                    else:
                        shadow=out/'player-skill-cancellation/classes/com/aionemu/gameserver/controllers/PlayerController.class';shadow.parent.mkdir(parents=True);shadow.write_bytes(b'product replacement')
                        wrong['classes'][str(shadow.relative_to(out/'player-skill-cancellation/classes'))]=regression.sha(shadow)
                    path.write_text(json.dumps(wrong))
                    with self.assertRaises(AssertionError):regression.validate(build,out)

    def test_player_cancellation_rejects_changed_reports_descriptors_fixtures_and_inputs(self):
        with resource_fixture() as folder:
            regression,build,out,evidence=self.regression_fixture(Path(folder))
            cancellation=out/'player-skill-cancellation';path=cancellation/'evidence.json';original=json.loads(path.read_text())
            with patch.object(regression,'TESTS',{'Test':3}),patch.object(regression,'QUEST_TESTS',{}),patch.object(regression,'binding',return_value={'builder':'bound'}):
                for relative in ['checks.txt','compile.txt','abi.json','classes/Stub.class']:
                    changed=cancellation/relative;before=changed.read_bytes();changed.write_bytes(b'changed bound evidence')
                    with self.assertRaises(AssertionError):regression.validate(build,out)
                    changed.write_bytes(before)
                source=cancellation/'bound-fixture.java';source.write_text('original inert fixture')
                wrong=json.loads(json.dumps(original));wrong['inputs'][str(source)]=regression.sha(source);path.write_text(json.dumps(wrong))
                regression.validate(build,out);source.write_text('changed inert fixture')
                with self.assertRaises(AssertionError):regression.validate(build,out)

    def test_pending_focus_is_in_required_suite_and_fixture_inputs(self):
        regression=delivery.import_module('check-source-build')
        self.assertEqual(regression.TESTS['PlayerBotPendingFocusCheck'],20)
        self.assertIn(regression.ROOT/'game-server/test/com/aionemu/gameserver/services/playerbot/PlayerBotPendingFocusCheck.java',regression.fixtures())

    def test_added_documented_defaults_preserve_existing_live_values(self):
        before='gameserver.playerbots.population.min = 1200\ngameserver.playerbots.population.max = 1500\n'
        defaults={'gameserver.playerbots.population.min':'2800','gameserver.playerbots.population.max':'3000','gameserver.playerbots.population.player_focus.enable':'true'}
        after=before+'gameserver.playerbots.population.player_focus.enable = true\n'
        self.assertEqual(configuration.unchanged_effective(before,after,defaults)['gameserver.playerbots.population.min'],'1200')
        for wrong in (after.replace('min = 1200','min = 2800'),after.replace('enable = true','enable = false'),before):
            with self.assertRaises(AssertionError): configuration.unchanged_effective(before,wrong,defaults)

    def test_existing_user_population_limits_preserve_other_bytes(self):
        review = delivery.import_module('review-playerbot-population-config')
        before = b'# user settings\r\ngameserver.playerbots.world.max_active = 3000\r\ngameserver.playerbots.population.min = 2800\r\ngameserver.playerbots.population.max = 3000\r\ngameserver.playerbots.population.city_percent = 0\r\n'
        after = before.replace(b'3000', b'1500').replace(b'2800', b'1200')
        previous, current = review.validate(before, after)
        self.assertEqual(previous['population.min'], 2800)
        self.assertEqual(current['population.min'], 1200)
        for wrong in (after.replace(b'city_percent = 0', b'city_percent = 20'), after+b'# unrelated edit\r\n', after.replace(b'# user',b'# removed')):
            with self.assertRaises(AssertionError): review.validate(before, wrong)

    def test_existing_user_population_limits_reject_invalid_or_duplicate(self):
        review = delivery.import_module('review-playerbot-population-config')
        before = b'gameserver.playerbots.world.max_active=1500\ngameserver.playerbots.population.min=1200\ngameserver.playerbots.population.max=1500\n'
        for wrong in (before+before, before.replace(b'min=1200', b'min=1600'), before.replace(b'max_active=1500',b'max_active=3001'), before.replace(b'max=1500', b'max=3001')):
            with self.assertRaises(AssertionError): review.validate(before, wrong)

    def test_field_preference_preserves_all_other_bytes(self):
        field = delivery.import_module('configure-playerbot-field')
        before = b'# live settings\r\ngameserver.playerbots.population.city_percent = 35\r\ngameserver.playerbots.population.max = 2500\r\n'
        after = field.field_bytes(before, 35)
        field.validate(before, after, 35)
        self.assertIn(b'city_percent = 0\r\n', after)
        for wrong in (after.replace(b'2500', b'3000'), after.replace(b'= 0', b'= 100'), after+b'# unrelated edit\n'):
            with self.assertRaises(AssertionError): field.validate(before, wrong, 35)
        for wrong in (before+before, before.replace(b'= 35', b'= 20')):
            with self.assertRaises(AssertionError): field.field_bytes(wrong, 35)
    def test_recorded_builder_resource_replacement_uses_installed_hash(self):
        with resource_fixture() as folder:
            server = Path(folder)
            target = server / 'Bot.java'
            target.write_bytes(b'original handler')
            original = verification.sha(target)
            target.write_bytes(b'normal builder handler')
            installed = verification.sha(target)
            manifest = dict(files=[dict(path='Bot.java', original=original, installed=installed)],
                            runtimeResourceInputs={'Bot.java': original})
            verification.verify_runtime_resource_inputs(server, manifest)
            target.write_bytes(b'unreviewed handler')
            with self.assertRaises(AssertionError):
                verification.verify_runtime_resource_inputs(server, manifest)

    def test_resource_replacement_cannot_misstate_original_or_duplicate_path(self):
        with resource_fixture() as folder:
            server = Path(folder)
            target = server / 'Bot.java'
            target.write_bytes(b'normal builder handler')
            entry = dict(path='Bot.java', original='wrong baseline', installed=verification.sha(target))
            with self.assertRaises(AssertionError):
                verification.verify_runtime_resource_inputs(server, dict(files=[entry],
                    runtimeResourceInputs={'Bot.java': 'reviewed baseline'}))
            with self.assertRaises(AssertionError):
                verification.verify_runtime_resource_inputs(server, dict(files=[entry, entry],
                    runtimeResourceInputs={'Bot.java': 'wrong baseline'}))

    def test_unreplaced_resource_and_absent_input_stay_guarded(self):
        with resource_fixture() as folder:
            server = Path(folder)
            target = server / 'untouched.xml'
            target.write_bytes(b'native resource')
            manifest = dict(files=[], runtimeResourceInputs={'untouched.xml': verification.sha(target),
                                                             'absent.xml': None})
            verification.verify_runtime_resource_inputs(server, manifest)
            (server / 'absent.xml').write_bytes(b'new unreviewed file')
            with self.assertRaises(AssertionError):
                verification.verify_runtime_resource_inputs(server, manifest)
            (server / 'absent.xml').unlink()
            target.write_bytes(b'changed unreviewed resource')
            with self.assertRaises(AssertionError):
                verification.verify_runtime_resource_inputs(server, manifest)
            documentation = dict(path='untouched.xml', original=manifest['runtimeResourceInputs']['untouched.xml'],
                                 installed=verification.sha(target))
            verification.verify_runtime_resource_inputs(server, manifest, documentation)
            with self.assertRaises(AssertionError):
                verification.verify_runtime_resource_inputs(server, manifest, documentation | dict(original='wrong baseline'))
            target.write_bytes(b'changed after documentation')
            with self.assertRaises(AssertionError):
                verification.verify_runtime_resource_inputs(server, manifest, documentation)

    def test_documented_configuration_preserves_explicit_values_and_missing_defaults(self):
        defaults = {'gameserver.playerbots.max_active': '100', 'gameserver.playerbots.world.max_active': '1000'}
        before = '# user settings\ngameserver.playerbots.max_active = 3000\n'
        after = '# companions\ngameserver.playerbots.max_active = 3000\n# world\ngameserver.playerbots.world.max_active = 1000\n'
        self.assertEqual(configuration.unchanged_effective(before, after, defaults), defaults | {'gameserver.playerbots.max_active': '3000'})
        for wrong in (after.replace('= 3000', '= 100'), after.replace('= 1000', '= 3000'),
                      after + 'gameserver.playerbots.world.max_active = 1000\n',
                      after + 'gameserver.playerbots.unknown = true\n', before, after + 'other.module = true\n'):
            with self.assertRaises(AssertionError):
                configuration.unchanged_effective(before, wrong, defaults)

    def test_every_compiled_configuration_key_has_a_source_entry_and_explanation(self):
        import re
        source = configuration.SOURCE.read_text()
        keys = re.findall(r'@Property\(key = "([^"]+)"', configuration.JAVA.read_text())
        values = configuration.source_catalogue()
        self.assertEqual(set(keys), set(values))
        self.assertEqual(len(keys), len(values))
        main = configuration.properties(source)
        self.assertEqual(configuration.properties(configuration.complete(source, main)), main)
        self.assertEqual(configuration.main_defaults(values), main)

    def test_empty_map_values_do_not_consume_following_comments(self):
        source = 'gameserver.playerbots.allowed_instance_maps =\n# mission distance\ngameserver.playerbots.mission_distance = 180\n# all maps\ngameserver.playerbots.population.maps =\n'
        values = configuration.properties(source)
        self.assertEqual(values['gameserver.playerbots.allowed_instance_maps'], '')
        self.assertEqual(values['gameserver.playerbots.population.maps'], '')
        self.assertEqual(values['gameserver.playerbots.mission_distance'], '180')
        documented = configuration.complete('# legacy list\n' + source, values)
        self.assertEqual(configuration.properties(documented), values)

    def test_explicit_companion_quota_change_preserves_other_bytes(self):
        before = b'# custom settings\r\ngameserver.playerbots.max_per_owner = 5\r\ngameserver.playerbots.generated.max_per_account = 20\r\nother=900\r\n'
        after = before.replace(b'owner = 5', b'owner = 23').replace(b'account = 20', b'account = 23')
        delivery.validate_companion_configuration(before, after, 5, 23, 20, 23)
        for changed in (after.replace(b'other=900', b'other=901'), after + b'new.setting=true\r\n', after.replace(b'# custom settings', b'# removed custom setting')):
            with self.assertRaises(AssertionError):
                delivery.validate_companion_configuration(before, changed, 5, 23, 20, 23)

    def test_configuration_review_cannot_hide_duplicate_or_wrong_quota(self):
        before = b'gameserver.playerbots.max_per_owner=5\ngameserver.playerbots.generated.max_per_account=20\n'
        after = before.replace(b'owner=5', b'owner=23').replace(b'account=20', b'account=23')
        for wrong in (after + b'gameserver.playerbots.max_per_owner=23\n', after.replace(b'owner=23', b'owner=24'), after.replace(b'account=23', b'account=20')):
            with self.assertRaises(AssertionError):
                delivery.validate_companion_configuration(before, wrong, 5, 23, 20, 23)

    def test_only_unchanged_builder_media_is_allowed(self):
        for file in ('bots.html', 'bots.css', 'bots.js'):
            name = 'playerbots/media/' + file
            source = delivery.ROOT / 'game-server/config' / name
            self.assertEqual(delivery.bundled_media_source(name, source.read_bytes()), str(source.relative_to(delivery.ROOT)))
            with self.assertRaises(AssertionError):
                delivery.bundled_media_source(name, source.read_bytes() + b'changed compiled output')
        for name in ('playerbots/media/../bots.js', 'playerbots/other.js', 'settings.properties', 'data/skills.xml'):
            self.assertIsNone(delivery.bundled_media_source(name, b'unreviewed'))

    def test_lock_modifier_keeps_exact_method(self):
        before = {'private synchronized void tick();': []}
        after = {'private void tick();': []}
        self.assertEqual(delivery.retained_methods(before, after), {'private synchronized void tick();': 'private void tick();'})

    def test_overloads_cannot_hide_deletion(self):
        before = {'private void work(int);': [], 'private void work(java.lang.String);': []}
        after = {'private void work(java.lang.String);': []}
        self.assertEqual(set(before) - delivery.retained_methods(before, after).keys(), {'private void work(int);'})

    def test_other_signature_changes_still_rejected(self):
        for before, after in [
            ('private void work(int);', 'private void work(long);'),
            ('public int work(int);', 'public long work(int);'),
            ('public static int work(int);', 'public int work(int);'),
            ('public int work(int);', 'private int work(int);'),
            ('public int work(int);', 'public int other(int);'),
        ]:
            with self.subTest(before=before, after=after):
                self.assertFalse(delivery.retained_methods({before: []}, {after: []}))


class QuestConstructorInventoryTests(unittest.TestCase):
    def info(self, code):
        import struct
        return dict(parent='native/Handler', pool=[None, (10, 2, 4), (7, 3), 'native/Handler', (12, 5, 6), '<init>', '(I)V', 900105],
            methods=[(1, '<init>', '()V', [('Code', b'\x00\x01\x00\x01' + struct.pack('>I', len(code)) + code)])])

    def test_cold_native_id_forms(self):
        for code, expected in [(b'\x2a\x08\xb7\x00\x01', 5), (b'\x2a\x10\x7f\xb7\x00\x01', 127),
            (b'\x2a\x11\x7f\xff\xb7\x00\x01', 32767), (b'\x2a\x12\x07\xb7\x00\x01', 900105),
            (b'\x2a\x13\x00\x07\xb7\x00\x01', 900105)]:
            self.assertEqual(quest_inventory.constructor_id(self.info(code)), expected)

    def test_nonliteral_or_wrong_superclass_cannot_claim_availability(self):
        with self.assertRaises(AssertionError): quest_inventory.constructor_id(self.info(b'\x2a\x1b\xb7\x00\x01'))
        wrong = self.info(b'\x2a\x12\x07\xb7\x00\x01'); wrong['parent'] = 'unrelated/Handler'
        with self.assertRaises(AssertionError): quest_inventory.constructor_id(wrong)

    def test_nonpublic_constructor_cannot_claim_native_loader_eligibility(self):
        wrong = self.info(b'\x2a\x12\x07\xb7\x00\x01')
        wrong['methods'][0] = (2, *wrong['methods'][0][1:])
        with self.assertRaises(StopIteration): quest_inventory.constructor_id(wrong)

class NativeBrowserReleaseTests(unittest.TestCase):
    def fixture(self, root):
        gate=delivery.import_module('check-source-browser')
        build=root/'build';out=root/'browser';source=root/'source';browser=root/'bin64'
        (build/'game-server').mkdir(parents=True);(out/'media').mkdir(parents=True);(out/'report').mkdir();(source/'tools').mkdir(parents=True);(source/'game-server/config/playerbots/media').mkdir(parents=True);browser.mkdir()
        (build/'build-manifest.json').write_text('normal source manifest')
        checker=source/'tools/check-playerbot-ui-behavior.py';checker.write_text('native browser fixture')
        with zipfile.ZipFile(gate.jar(build),'w') as archive:
            for name in gate.NAMES:
                data=('normal builder '+name).encode();archive.writestr('playerbots/media/'+name,data);(out/'media'/name).write_bytes(data);(source/'game-server/config/playerbots/media'/name).write_bytes(data)
        for name in gate.RENDERER:(browser/name).write_bytes(('native renderer '+name).encode())
        media=gate.media_hashes(build)
        inputs={str(checker.resolve()):gate.sha(checker)}|{str(out/'media'/name):digest for name,digest in media.items()}|{str(browser/name):gate.sha(browser/name) for name in gate.RENDERER}
        (out/'report/visual.png').write_bytes(b'native render image')
        (out/'report/report.json').write_text(json.dumps(dict(version=1,passed=True,authenticatedActions=43,inputs=inputs,screenshots={'visual.png':gate.sha(out/'report/visual.png')})))
        (out/'browser.txt').write_text('OK: native Aion WebKit verified')
        evidence=dict(version=1,exitCode=0,gameSha256=gate.sha(gate.jar(build)),buildManifestSha256=gate.sha(build/'build-manifest.json'),gateSha256=gate.sha(gate.__file__),checkerSha256=gate.sha(checker),media=media,testedMedia=str(out/'media'),browserBin=str(browser),reportSha256=gate.sha(out/'report/report.json'),stdoutSha256=gate.sha(out/'browser.txt'))
        (out/'evidence.json').write_text(json.dumps(evidence))
        return gate,source,build,out,evidence

    def test_browser_rejects_changed_source_builder_or_renderer(self):
        with resource_fixture() as folder:
            gate,source,build,out,e=self.fixture(Path(folder))
            with patch.object(gate,'ROOT',source):
                gate.validate(build,out)
                for path in [gate.jar(build),source/'game-server/config/playerbots/media/bots.js',Path(e['browserBin'])/'Awesomium.dll',source/'tools/check-playerbot-ui-behavior.py']:
                    original=path.read_bytes();path.write_bytes(b'changed input')
                    with self.assertRaises(AssertionError):gate.validate(build,out)
                    path.write_bytes(original)

    def test_browser_rejects_failed_or_incomplete_run_and_changed_visuals(self):
        with resource_fixture() as folder:
            gate,source,build,out,e=self.fixture(Path(folder))
            with patch.object(gate,'ROOT',source):
                for key,value in [('exitCode',1),('reportSha256','wrong')]:
                    (out/'evidence.json').write_text(json.dumps(e|{key:value}))
                    with self.assertRaises(AssertionError):gate.validate(build,out)
                (out/'evidence.json').write_text(json.dumps(e));image=out/'report/visual.png';image.write_bytes(b'changed screenshot')
                with self.assertRaises(AssertionError):gate.validate(build,out)
                image.write_bytes(b'native render image')
                report=json.loads((out/'report/report.json').read_text());report['authenticatedActions']=42;(out/'report/report.json').write_text(json.dumps(report))
                (out/'evidence.json').write_text(json.dumps(e|{'reportSha256':gate.sha(out/'report/report.json')}))
                with self.assertRaises(AssertionError):gate.validate(build,out)

    def test_browser_evidence_archive_retains_original_test_binding(self):
        import shutil
        with resource_fixture() as folder:
            gate,source,build,out,e=self.fixture(Path(folder));archive=Path(folder)/'receipt/browser';shutil.copytree(out,archive)
            with patch.object(gate,'ROOT',source):
                gate.validate(build,archive)
                (archive/'media/bots.css').write_bytes(b'changed archived media')
                with self.assertRaises(AssertionError):gate.validate(build,archive)

def load_tests(loader, tests, pattern):
    tests.addTests(loader.loadTestsFromModule(delivery.import_module('test-quest-action-inventory')))
    return tests

if __name__ == '__main__':
    unittest.main()
