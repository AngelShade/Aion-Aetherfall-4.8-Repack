"""Refresh shop consumables from the server's item and skill templates.

Reads a catalog TSV and writes an updated TSV. It changes only food/recovery
descriptions, moves transformation candy to its existing section, and adds the
standard instant HP/MP serums missing from the current shop.
"""

from __future__ import annotations

import argparse
import csv
import xml.etree.ElementTree as ET
from pathlib import Path


SERUMS = {
    # The regular 30-second serums, sold in stacks of 20.
    "162000012": (20, 8000), "162000017": (20, 8000),
    "162000013": (20, 14000), "162000018": (20, 14000),
    "162000014": (20, 28000), "162000019": (20, 28000),
    "162000015": (20, 48000), "162000020": (20, 48000),
    "162000016": (20, 72000), "162000021": (20, 72000),
    "162000077": (20, 100000), "162000078": (20, 100000),
    # The stronger 5-minute serums, sold in stacks of five.
    "162000032": (5, 30000), "162000035": (5, 30000),
    "162000033": (5, 50000), "162000036": (5, 50000),
    "162000034": (5, 75000), "162000037": (5, 75000),
    "162000069": (5, 100000), "162000070": (5, 100000),
}
STAT_NAMES = {
    "PHYSICAL_ATTACK": "Physical Attack", "BOOST_MAGICAL_SKILL": "Magic Boost",
    "MAXHP": "maximum HP", "MAXMP": "maximum MP",
    "PHYSICAL_ACCURACY": "Physical Accuracy", "EVASION": "Evasion",
    "MAGICAL_ACCURACY": "Magical Accuracy", "MAGICAL_RESIST": "Magic Resist",
    "REGEN_HP": "HP regeneration", "REGEN_MP": "MP regeneration",
}
SHULACK_SIDE_EFFECTS = {
    "10241": "Also applies Stomachache, lowering Physical Accuracy with a chance of reduced MP regeneration.",
    "10242": "Also applies Befuddled, lowering Magical Accuracy with a chance of reduced MP regeneration.",
    "10243": "Also applies Full Stomach, lowering Evasion with a chance of reduced MP regeneration.",
}


def templates(path: Path, wanted: set[str], tag: str, key: str) -> dict[str, ET.Element]:
    found = {}
    for _, element in ET.iterparse(path, events=("end",)):
        if element.tag != tag:
            continue
        if element.get(key) in wanted:
            # Copy the small target element before the parser clears it.
            found[element.get(key)] = ET.fromstring(ET.tostring(element))
        element.clear()
    if missing := wanted - found.keys():
        raise ValueError(f"Missing {tag} IDs: {sorted(missing)}")
    return found


def duration(milliseconds: str) -> str:
    minutes = int(milliseconds) // 60000
    if minutes >= 60 and minutes % 60 == 0:
        return f"{minutes // 60} hr"
    if minutes:
        return f"{minutes} min"
    return f"{int(milliseconds) // 1000} sec"


def effect_description(item: ET.Element, skill: ET.Element, count: int) -> str:
    name = item.get("name", "")
    skill_level = int(item.find("actions/skilluse").get("level", "1"))
    effect_container = skill.find("effects")
    effects = list(effect_container) if effect_container is not None else []
    tags = {effect.tag for effect in effects}
    parts = []
    if "statup" in tags:
        changes = []
        for effect in effects:
            if effect.tag != "statup":
                continue
            for change in effect.findall("change"):
                stat = change.get("stat")
                if stat not in STAT_NAMES or change.get("func") != "ADD":
                    raise ValueError(f"Unsupported food stat on {name}: {ET.tostring(change)}")
                amount = int(change.get("value", "0")) + int(change.get("delta", "0")) * skill_level
                changes.append(f"{amount:+d} {STAT_NAMES[stat]}")
        durations = {effect.get("duration2") for effect in effects if effect.tag == "statup"}
        if len(durations) != 1:
            raise ValueError(f"Mixed food durations for {name}: {durations}")
        parts.append(f"Grants {', '.join(changes)} for {duration(durations.pop())}.")
        if "skilllauncher" in tags:
            parts.append(SHULACK_SIDE_EFFECTS[skill.get("skill_id")])
    elif "shapechange" in tags:
        shape = next(effect for effect in effects if effect.tag == "shapechange")
        parts.append(f"Transforms your character for {duration(shape.get('duration2'))}.")
        if shape.get("cantUseSkills") == "true" and shape.get("cantFly") == "true":
            parts.append("Skills and flight are unavailable in this form.")
    else:
        immediate = []
        ongoing = []
        for effect in effects:
            if effect.tag in {"prochealinstant", "procmphealinstant"}:
                resource = "HP" if effect.tag == "prochealinstant" else "MP"
                immediate.append(f"{effect.get('value')} {resource}")
            elif effect.tag in {"heal", "mpheal"}:
                resource = "HP" if effect.tag == "heal" else "MP"
                ongoing.append(f"{effect.get('value')} {resource} every {duration(effect.get('checktime'))} for {duration(effect.get('duration2'))}")
            elif effect.tag == "resurrect":
                parts.append("Resurrects one fallen allied character within range.")
            elif effect.tag == "dispeldebuffphysical":
                parts.append("Removes a physical altered state from your character.")
            elif effect.tag == "procfphealinstant":
                parts.append("Instantly restores flight time.")
            else:
                raise ValueError(f"Unsupported recovery effect on {name}: {effect.tag}")
        if immediate:
            parts.insert(0, f"Restores {' and '.join(immediate)} immediately.")
        if ongoing:
            parts.append(f"Then restores {' and '.join(ongoing)}.")
    limits = item.find("uselimits")
    if limits is not None and int(limits.get("usedelay", "0")) > 0:
        parts.append(f"Reuse: {duration(limits.get('usedelay'))}.")
    parts.append(f"Offer contains {count} {name} {'items' if count != 1 else 'item'}.")
    return " ".join(parts)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--catalog", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = args.server_root
    catalog = args.catalog or root / "config/ingameshop/marketplace_cash.tsv"
    with catalog.open(encoding="utf-8", newline="") as stream:
        rows = [row for row in csv.reader(stream, delimiter="\t") if row and not row[0].startswith("#")]
    if any(len(row) != 6 for row in rows):
        raise ValueError("Every catalog row must have six fields")
    existing = {row[0] for row in rows}
    selected = {row[0] for row in rows if row[3] in {"food_drink", "recovery"}}
    selected.update({"160003531", "160003532"} & existing)
    wanted = selected | SERUMS.keys()
    items = templates(root / "data/static_data/items/item_templates.xml", wanted, "item_template", "id")
    skill_ids = {item.find("actions/skilluse").get("skillid") for item in items.values()}
    skills = templates(root / "data/static_data/skills/skill_templates.xml", skill_ids, "skill_template", "skill_id")
    changed = 0
    for row in rows:
        if row[0] not in selected:
            continue
        item = items[row[0]]
        desc = effect_description(item, skills[item.find("actions/skilluse").get("skillid")], int(row[1]))
        if row[5] != desc:
            row[5] = desc
            changed += 1
        if row[3] == "food_drink" and item.find("actions/skilluse").get("skillid") in {"10239", "10240"}:
            row[3] = "transformation_candy"
    additions = []
    for item_id, (count, price) in SERUMS.items():
        if item_id in existing:
            continue
        item = items[item_id]
        if int(item.get("level")) > 65 or count > int(item.get("max_stack_count")) or "test" in item.get("name", "").lower():
            raise ValueError(f"Invalid serum offer: {item_id}")
        skill = skills[item.find("actions/skilluse").get("skillid")]
        if {effect.tag for effect in skill.find("effects")} - {"prochealinstant", "procmphealinstant"}:
            raise ValueError(f"Serum is not instant-only: {item_id}")
        additions.append([item_id, str(count), str(price), "recovery", item.get("level"), effect_description(item, skill, count)])
    if additions:
        index = next(i for i, row in enumerate(rows) if row[3] == "recovery")
        rows[index:index] = additions
    output = args.output or catalog
    with output.open("w", encoding="utf-8", newline="") as stream:
        stream.write("# item_id\tquantity\tprice_in_kinah\tsection\tunlock_level\tdescription\n")
        csv.writer(stream, delimiter="\t", lineterminator="\n").writerows(rows)
    print(f"Updated {changed} descriptions; added {len(additions)} serums; {len(rows)} offers total")


if __name__ == "__main__":
    main()
