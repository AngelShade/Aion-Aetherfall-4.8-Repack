"""Add the native client quest/NPC registration missing from the first delivery."""
import argparse
import copy
import json
from pathlib import Path
import sys
from xml.etree import ElementTree as ET

from prepare import ROOT, ASSETS, sha, load_registration
from codec import binary_xml, encode_binary_xml, read_pak
from prepare_client import rebuild, parts


def prepare(client, output):
    assert not output.exists() and ROOT not in output.parents and client not in output.parents
    relative = 'Data/Quest/Quest.pak'
    live = client/relative
    original = live.read_bytes()
    source = ASSETS/'registration.xml'
    registration = load_registration()
    # Native reference: the installed client has mixed Talk/Hunt descriptors
    # (e.g. Q25032), loaded independently from the server's Java quest handler.
    assert registration.tag == 'quest_data_driven' and registration.findtext('id') == '3999'
    assert registration.findtext('name') == 'Q3999'
    expected = [('Talk','Rosina'),('Talk','LF2a_Tavern_TrollBodyguard'),
                ('Hunt','LF2A_ShugoMaleNMQ_50_An 1;'),('Talk','Shugo_LF2a_14'),('Talk','Rosina')]
    assert [(x.findtext('category_progress_'),x.findtext('value0_progress_')) for x in registration.findall('progress_info/data')] == expected
    with read_pak(client/'Data/Npcs/Npcs.pak') as npc:
        names = {}
        for name in ('client_npcs_npc.xml','client_npcs_monster.xml'):
            for node in binary_xml(npc.read(name)):
                if node.findtext('id') in ('798193','798190','798192','214577'):
                    names[node.findtext('id')] = node.findtext('name')
    assert names == {'798193':'Shugo_LF2a_14','798190':'Rosina',
                     '798192':'LF2a_Tavern_TrollBodyguard','214577':'LF2A_ShugoMaleNMQ_50_An'}
    with read_pak(live) as z:
        quest = next(q for q in binary_xml(z.read('quest.xml')) if q.findtext('id') == '3999')
        assert quest.findtext('finished_quest_cond1') == 'Q3060'
        assert quest.findtext('minlevel_permitted') == '48'
        tree = binary_xml(z.read('data_driven_quest.xml'))
        assert all(q.findtext('id') != '3999' for q in tree), 'Registration already exists'
        reference = next(q for q in tree if q.findtext('id') == '25032')
        assert reference.findtext('category_acquire_') == 'Talk'
        reference_categories = {x.findtext('category_progress_') for x in reference.findall('progress_info/data')}
        assert {'Talk','Hunt'} <= reference_categories
        before = [ET.tostring(q) for q in tree]
        tree.append(copy.deepcopy(registration))
        encoded = encode_binary_xml(tree)
        assert ET.tostring(binary_xml(encoded)) == ET.tostring(tree)
        patched, _ = rebuild(original, {'data_driven_quest.xml':encoded}, {e.filename:e for e in z.infolist()})
        output.mkdir(parents=True)
        staged = output/'Quest.pak'
        staged.write_bytes(patched)
        with read_pak(staged) as check:
            assert check.namelist() == z.namelist()
            for name in z.namelist():
                assert check.read(name) == (encoded if name == 'data_driven_quest.xml' else z.read(name)), name
            after = binary_xml(check.read('data_driven_quest.xml'))
            assert len(after) == len(before)+1
            assert [ET.tostring(q) for q in after[:-1]] == before
        old_records,_,_,version = parts(original)
        new_records,_,_,new_version = parts(patched)
        assert version == new_version
        assert all(old_records[n][0] == new_records[n][0] for n in old_records if n != 'data_driven_quest.xml')
    manifest = dict(feature='red-journal-native-registration',questId=3999,client=str(client),path=relative,
                    staged=str(staged),before=sha(original),after=sha(patched),
                    preservedExistingRegistrations=len(before),preservedCompressedMembers=len(old_records)-1,
                    nativeNpcNames=names,referenceQuest=25032,progress=expected,
                    sourceHashes={str(p.relative_to(ROOT)):sha(p.read_bytes()) for p in (Path(__file__),source,Path(__file__).with_name('prepare.py'))},
                    serverChanged=False,gameplayVerified=False,installed=False)
    (output/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('Prepared native Q3999 registration: Nagrunerk start, five matching stages, Rosina reward; existing registrations and other archive members preserved.')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    a = p.parse_args()
    prepare(a.client.resolve(strict=True),a.output.resolve())
