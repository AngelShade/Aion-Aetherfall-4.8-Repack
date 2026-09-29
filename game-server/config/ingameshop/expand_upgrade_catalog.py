"""Add supported service summons and equipment upgrades to the Kinah shop.

Selects Aion 4.8 templates with working item actions. Items explicitly labeled
event, stamp, reward, or test are excluded. Existing offer prices are preserved.
"""

from __future__ import annotations

import argparse
import csv
import math
import re
from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET


SUMMON_IDS = {str(i) for i in range(164002192, 164002202)}
SOCKET_AID_IDS = {str(i) for i in (166150003, 166150004, 166150014, 166150015, 166150016, 166150017, 166150018, 166150019)}
FORBIDDEN_PREFIXES = ("[Event]", "[Stamp]", "[Legion Reward]", "Test ")


def eligible(item_id: str, name: str, group: str) -> bool:
    if name.startswith(FORBIDDEN_PREFIXES) or "(Test)" in name:
        return False
    number = int(item_id)
    if item_id in SUMMON_IDS or item_id in SOCKET_AID_IDS:
        return True
    if 166100000 <= number <= 166100011:
        return True
    if group == "ENCHANTMENT":
        return 166000001 <= number <= 166000195 or number == 166020000
    if group == "MANASTONE":
        return name.startswith("Manastone:")
    if group == "SPECIAL_MANASTONE":
        return name.startswith("Ancient Manastone")
    return False


def load_items(path: Path) -> dict[str, dict]:
    items = {}
    for _, item in ET.iterparse(path, events=("end",)):
        if item.tag != "item_template":
            continue
        item_id = item.get("id")
        name = item.get("name", "")
        group = item.get("item_group", "")
        if eligible(item_id, name, group):
            actions = item.find("actions")
            action = list(actions)[0] if actions is not None and len(actions) else None
            items[item_id] = {
                "name": name, "group": group, "level": int(item.get("level", "1")),
                "quality": item.get("quality", "COMMON"), "base_price": int(item.get("price", "0")),
                "stack": int(item.get("max_stack_count", "1")), "race": item.get("race", ""),
                "action": action.tag if action is not None else None,
                "skill_id": action.get("skillid") if action is not None else None,
                "chance": action.get("chance") if action is not None else None,
                "max_level": action.get("max_level") if action is not None else None,
                "modifiers": [(m.get("name"), m.get("value")) for m in item.findall("modifiers/*")],
            }
        item.clear()
    return items


def group_for(item_id: str, item: dict) -> str:
    name, group = item["name"], item["group"]
    if item_id in SUMMON_IDS:
        return "summoned_npcs"
    if item_id in SOCKET_AID_IDS:
        return "socketing_aids"
    if item_id.startswith("166100"):
        return "enchant_supplements"
    if group == "ENCHANTMENT":
        return "upgrade_stones"
    if group == "SPECIAL_MANASTONE":
        return "manastone_ancient"
    # Lead with the first stat so dual-stat stones remain easy to browse.
    stat = name.split(":", 1)[1].split("/", 1)[0]
    if re.search(r"\b(HP|MP|Flight Time)\s*\+", stat):
        return "manastone_vitality"
    if any(word in stat for word in ("Magic Boost", "Magical Accuracy", "Crit Spell", "Healing Boost")):
        return "manastone_magic"
    if any(word in stat for word in ("Attack", "Accuracy", "Crit Strike", "Physical Critical Hit")):
        return "manastone_physical"
    if any(word in stat for word in ("Evasion", "Dodge", "Parry", "Block", "Resist", "Defense", "Suppression")):
        return "manastone_defense"
    raise ValueError(f"Unclassified manastone {item_id}: {name}")


def offer_terms(item_id: str, item: dict, section: str) -> tuple[int, int, int]:
    level = item["level"]
    if section == "summoned_npcs":
        return 1, 350000 if "(Group)" in item["name"] else 250000, 1
    if section == "socketing_aids":
        quality = {"UNIQUE": 1, "EPIC": 2, "MYTHIC": 3}[item["quality"]]
        return 5, quality * (500000 if level == 65 else 250000), level
    if section == "enchant_supplements":
        return 20, int(math.ceil(item["base_price"] * 20 * 1.05 / 5000) * 5000), level
    if section == "upgrade_stones":
        if item_id == "166020000":
            return 5, 7500000, 65
        if int(item_id) >= 166000191:
            special = int(item_id) - 166000190
            return 5, special * 500000, (20, 40, 55, 60, 65)[special - 1]
        stone_level = level
        return 5, max(1000, int(round(1.6 * stone_level ** 3 / 5000) * 5000)), min(65, max(1, stone_level - 20))
    if section.startswith("manastone_"):
        quality = item["quality"]
        base = {"COMMON": 10000, "RARE": 90000, "LEGEND": 240000, "UNIQUE": 450000, "EPIC": 700000}[quality]
        price = base + max(0, level - 10) * {"COMMON": 500, "RARE": 1500, "LEGEND": 3000, "UNIQUE": 5000, "EPIC": 7000}[quality]
        return 10, int(math.ceil(price / 5000) * 5000), min(65, max(1, level - 10))
    raise ValueError(f"Unpriced category: {section}")


def description(item_id: str, item: dict, section: str, count: int) -> str:
    name = item["name"]
    if section == "summoned_npcs":
        role = "trade broker" if "Trade Broker" in name else "general goods merchant" if "General Goods" in name else "warehouse manager"
        owner = "your group" if "(Group)" in name else "your character"
        faction = " Elyos item." if item["race"] == "ELYOS" else " Asmodian item." if item["race"] == "ASMODIANS" else ""
        return f"Use on the ground to summon a {role} for {owner} for 5 minutes.{faction} One summoning stone is delivered."
    if section == "socketing_aids":
        limit = item["max_level"] or item["level"]
        return f"Use {name} during a compatible manastone socketing attempt on equipment up to level {limit}. It increases the attempt's success chance. {count} items per offer."
    if section == "enchant_supplements":
        chance = item["chance"]
        grade = name.split("(")[-1].rstrip(")")
        return f"Use during an enchantment attempt on {grade} gear. Adds {chance} percentage points to the server's success calculation, subject to its cap. {count} supplements per offer."
    if section == "upgrade_stones":
        extra = " This is the stone allowed for amplified gear." if item_id == "166020000" else ""
        return f"Use {name} to attempt an enchantment on compatible equipment. Success depends on the target and server rates; it is not guaranteed.{extra} {count} stones per offer."
    bonus = name.split(":", 1)[1].strip().replace(" / ", " and ")
    kind = "an ancient manastone slot" if section == "manastone_ancient" else "a compatible manastone slot"
    return f"Socket {name} into {kind} to add {bonus}. Stone level {item['level']}; check the target equipment's level and free slots. {count} stones per offer."


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--catalog", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = args.server_root
    catalog = args.catalog or root / "config/ingameshop/marketplace_cash.tsv"
    with catalog.open(encoding="utf-8", newline="") as stream:
        rows = [r for r in csv.reader(stream, delimiter="\t") if r and not r[0].startswith("#")]
    items = load_items(root / "data/static_data/items/item_templates.xml")
    expected = Counter({"summoned_npcs": 10, "socketing_aids": 8, "enchant_supplements": 12,
                        "upgrade_stones": 196, "manastone_physical": 139, "manastone_magic": 138,
                        "manastone_vitality": 94, "manastone_defense": 114, "manastone_ancient": 60})
    categories = {item_id: group_for(item_id, item) for item_id, item in items.items()}
    actual = Counter(categories.values())
    if actual != expected:
        raise ValueError(f"Candidate counts changed: {actual - expected}; missing {expected - actual}")
    if any(item["action"] != ("skilluse" if item_id in SUMMON_IDS else "enchant") for item_id, item in items.items()):
        raise ValueError("A candidate does not have its expected server action")
    seen = set()
    changed = 0
    for row in rows:
        item_id = row[0]
        if item_id not in items:
            continue
        section = categories[item_id]
        new_desc = description(item_id, items[item_id], section, int(row[1]))
        if row[3] != section or row[5] != new_desc:
            row[3], row[5] = section, new_desc
            changed += 1
        seen.add(item_id)
    added = []
    for item_id, item in items.items():
        if item_id in seen:
            continue
        section = categories[item_id]
        count, price, unlock = offer_terms(item_id, item, section)
        if count > item["stack"] or price < 1 or unlock > 65:
            raise ValueError(f"Invalid offer: {item_id}")
        added.append([item_id, str(count), str(price), section, str(unlock), description(item_id, item, section, count)])
    rows.extend(added)
    output = args.output or catalog
    with output.open("w", encoding="utf-8", newline="") as stream:
        stream.write("# item_id\tquantity\tprice_in_kinah\tsection\tunlock_level\tdescription\n")
        csv.writer(stream, delimiter="\t", lineterminator="\n").writerows(rows)
    print(f"Updated {changed} offers; added {len(added)}; {len(rows)} total. Sections: {actual}")


if __name__ == "__main__":
    main()
