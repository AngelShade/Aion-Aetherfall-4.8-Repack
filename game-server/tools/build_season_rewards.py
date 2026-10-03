"""Generate Ascendant Dawn's native 4.8 event and progression rewards.
Class bundles resolve to existing chargeable stigma boxes. Native box contents
are retained; see docs/SEASON_PASS_REWARDS.md for provenance.
"""
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
ANCIENT, BLOOD, MEDALS = 186000237, 186000236, 188053666
OMEGA, TEMPER, SOCKET = 166020000, 166030013, 166150019
COMPOSITE, PLUME = 188052741, 188053321
NORMAL, GREATER, MAJOR = 188053750, 188053761, 188053772
patterns = [
    [(ANCIENT, 20), (BLOOD, 15), (MEDALS, 1), (COMPOSITE, 1), (TEMPER, 1), (ANCIENT, 20)],
    [(OMEGA, 1), (TEMPER, 1), (COMPOSITE, 2), (MEDALS, 2), (SOCKET, 2), (COMPOSITE, 1)],
    [(OMEGA, 2), (TEMPER, 2), (COMPOSITE, 3), (MEDALS, 3), (SOCKET, 3), (TEMPER, 1)],
]
milestones = {
    0: {5: (COMPOSITE, 2), 10: (PLUME, 1), 15: (OMEGA, 1), 20: (NORMAL, 2), 25: (GREATER, 1), 30: (188052319, 1)},
    1: {5: (NORMAL, 2), 10: (GREATER, 2), 15: (110900234, 1), 20: (MAJOR, 2), 25: (190100151, 1), 30: (190020133, 1)},
    2: {5: (GREATER, 2), 10: (MAJOR, 2), 15: (110900233, 1), 20: (PLUME, 2), 25: (SOCKET, 5), 30: (188053646, 1)},
}
details = {
    ANCIENT: 'Ancient Coins toward level-65 equipment and supplies from Ancient Coin merchants.',
    BLOOD: 'Blood Marks toward equipment and supplies from Blood Mark merchants.',
    MEDALS: 'Open each native event box for 1-3 Ceramium Medals toward Abyss equipment. Quantity is random.',
    OMEGA: 'Endgame equipment enchantment material, including eligible equipment amplification. Enchantment can fail.',
    TEMPER: 'Native event material for tempering eligible plumes and accessories. Tempering can fail.',
    SOCKET: '100% manastone socketing aid for eligible Mythic equipment up to level 65. Consumed with the socketing attempt.',
    COMPOSITE: 'Choose one of 18 level-60 composite manastones per bundle in the native selection window. Pick stats for your build.',
    PLUME: 'Choose an Attack or Magic Boost Empyrean Plume for your faction in the native selection window. Each chest gives one plume.',
    NORMAL: 'One random chargeable normal stigma of your advanced class per bundle. Duplicate copies are used for 4.8 stigma charging.',
    GREATER: 'One random chargeable greater stigma of your advanced class per bundle. Duplicate copies are used for 4.8 stigma charging.',
    MAJOR: 'One random chargeable major stigma of your advanced class per bundle. Duplicate copies are used for 4.8 stigma charging.',
    188052319: "Native event box containing Tiamat's Spectral Wings. A permanent Dragon Lord wing reward; equip at level 60.",
    110900234: 'Permanent native event Dynasty Light Armor appearance. An event edition for equipment remodeling.',
    110900233: 'Permanent native event Dynasty Heavy Armor appearance. An event edition for equipment remodeling.',
    190100151: 'Permanent character-bound Shugo Gyrocopter. Ride at level 60. This edition cannot be traded.',
    190020133: 'Adopt a permanent Stormwing pet from this native event egg. Supports automatic looting and automatic food/scroll use.',
    188053646: 'Choose one level-65 Mythic Nether Dragon King weapon or shield from 14 native options. Keep the original item stats and selection window.',
}
bundles = {NORMAL: 'NORMAL', GREATER: 'GREATER', MAJOR: 'MAJOR'}
items = {int(e.get('id')): dict(e.attrib) for _, e in ET.iterparse(ROOT/'data/static_data/items/item_templates.xml', events=['end']) if e.tag == 'item_template'}
rows = ['# level\ttrack (0 free, 1 premium, 2 advanced)\titem_id\tquantity\tname\tdescription\tclass_bundle (NONE/NORMAL/GREATER/MAJOR)']
for level in range(1, 31):
    for track, pattern in enumerate(patterns):
        item, quantity = milestones[track].get(level, pattern[(level-1) % len(pattern)])
        data = items[item]
        assert quantity <= int(data.get('max_stack_count', '1'))
        assert item // 1000000 != 162, 'No recovery consumable filler'
        rows.append(f"{level}\t{track}\t{item}\t{quantity}\t{data['name']}\t{details[item]}\t{bundles.get(item, 'NONE')}")
(ROOT/'config/season-pass/rewards.tsv').write_text('\n'.join(rows)+'\n', encoding='utf-8')
print('OK: 90 native 4.8 rewards; event capstones, class-specific chargeable stigmas, zero serum filler.')
