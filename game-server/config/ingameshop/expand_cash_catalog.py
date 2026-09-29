"""Expand the Kinah storefront from the validated legacy offers.

The historical TSV files remain optional import sources and are not served by
the storefront. Existing
cash offers, including Featured, keep their order, prices, and descriptions.
Running this script again is idempotent by item ID.
"""

from __future__ import annotations

import argparse
import csv
from pathlib import Path
import xml.etree.ElementTree as ET


SECTION = {
    "starter": "essentials", "supplies": "essentials", "travel": "essentials",
    "weapons": "equipment", "armor": "equipment", "upgrades": "upgrades",
    "dyes": "fashion", "hair": "fashion", "hats": "fashion",
    "outfits": "fashion", "emotes": "collectibles", "titles": "collectibles",
    "food": "essentials", "potions": "essentials", "pets": "companions",
    "mounts": "companions", "services": "convenience",
    "weapon_skins": "weapon_looks", "wings": "wings",
}
WEAPONS = {"SWORD", "DAGGER", "MACE", "STAFF", "GREATSWORD", "POLEARM",
           "BOW", "ORB", "SPELLBOOK", "GUN", "CANNON", "HARP", "KEYBLADE", "SHIELD"}


def read_rows(path: Path) -> list[list[str]]:
    with path.open(encoding="utf-8", newline="") as stream:
        return [row for row in csv.reader(stream, delimiter="\t")
                if row and not row[0].startswith("#")]


def item_data(path: Path, wanted: set[str]) -> dict[str, tuple[str, str, set[str], int]]:
    found = {}
    for _, item in ET.iterparse(path, events=("end",)):
        if item.tag != "item_template":
            continue
        item_id = item.get("id")
        if item_id in wanted:
            actions = item.find("actions")
            found[item_id] = (item.get("name", ""), item.get("item_group", ""),
                              {action.tag for action in actions} if actions is not None else set(),
                              int(item.get("max_stack_count", "1")))
        item.clear()
    if missing := wanted - found.keys():
        raise ValueError(f"Missing item templates: {sorted(missing)}")
    return found


def category(old: str, item_id: str, group: str) -> str:
    if old == "style":
        return "wings" if group == "WING" else "fashion"
    if old == "gm":
        return "weapon_looks" if group in WEAPONS else "fashion"
    return SECTION[old]


def subcategory(parent: str, item_id: str, name: str, group: str, actions: set[str]) -> str:
    """Place future archive imports in the same browse groups as today's offers."""
    if parent == "fashion":
        if "dye" in actions: return "fashion_dyes"
        if "cosmetic" in actions: return "fashion_hair"
        return "fashion_headwear" if group in {"HEAD", "EARRING"} else "fashion_outfits"
    if parent == "weapon_looks":
        if group in {"BOW", "GUN", "CANNON"}: return "weapon_ranged"
        if group in {"KEYBLADE", "ORB", "HARP", "SPELLBOOK"}: return "weapon_arcane"
        return "weapon_melee"
    if parent == "equipment": return "equipment_weapons" if group in WEAPONS else "equipment_armor"
    if parent == "wings": return "wing_skins" if "skin" in name.lower() else "wing_permanent"
    if parent == "companions": return "mounts" if "ride" in actions else "pets"
    if parent == "collectibles": return "titles" if "titleadd" in actions else "emotes"
    if parent == "convenience": return "storage" if "expandinventory" in actions else "character_services"
    if parent == "essentials":
        if item_id.startswith("160010"): return "transformation_candy"
        if item_id.startswith("160"): return "food_drink"
        if item_id.startswith(("161", "162")): return "recovery"
        return "supply_scrolls"
    if parent == "upgrades":
        if group in {"ENCHANTMENT", "MANASTONE"}: return "upgrade_stones"
        if group in {"POWER_SHARDS", "STIGMA_SHARD"}: return "upgrade_shards"
        return "upgrade_tools"
    if parent == "bundles": return "gift_boxes" if "box" in name.lower() else "travel_bundles"
    return parent


def description(name: str, group: str, actions: set[str], count: int, category: str) -> str:
    lower = name.lower()
    clean = name.replace("[Emotion Card] ", "").replace("[Title Card] ", "").replace("[Title] ", "")
    units = f"The offer contains {count} items." if count != 1 else "One item is delivered by Black Cloud mail."
    if "adoptpet" in actions:
        return f"Adopt a companion with {name}. Use the egg from your inventory after it arrives by Black Cloud mail."
    if "ride" in actions:
        return f"Register {name} as a rideable mount for travel. Use the delivered mount item from your inventory."
    if "titleadd" in actions:
        return f"Add the {clean} title to your character. Use the delivered title card to learn it."
    if "learnemotion" in actions:
        return f"Unlock the {clean} expression or dance for your character. Use the delivered emotion card."
    if "dye" in actions:
        return f"Color compatible equipment with {name}. This offer contains {count} dye items."
    if "cosmetic" in actions and "hair" in lower:
        return f"A hairstyle ticket for {name}. Check its faction and gender requirements before buying."
    if "expandinventory" in actions or ("cube" in lower and "expand" in lower):
        return "Expand your character's cube inventory with this ticket. Check your current cube tier before use."
    if "expandwarehouse" in actions or ("warehouse" in lower and "expand" in lower):
        return "Expand your character's warehouse storage with this ticket. Check your current warehouse tier before use."
    if category == "equipment":
        slot = group.lower().replace("rb_", "cloth ").replace("lt_", "leather ").replace("ch_", "chain ").replace("pl_", "plate ").replace("_", " ")
        return f"{name} is level 65 {slot} equipment from the Sauro collection. Check class and item attributes on its detail page before equipping."
    if category == "wings":
        return f"Equip {name} in the wing slot for its listed flight attributes and appearance. Check its level requirement before purchase."
    if "remodel" in actions:
        slot = "headgear" if group == "HEAD" else "armor" if group in {"TORSO", "PANTS", "SHOULDER", "GLOVE", "SHOES"} else group.lower().replace("_", " ")
        return f"Use {name} to remodel compatible {slot} and carry its appearance into battle. {units}"
    if category == "weapon_looks":
        return f"{name} is a {group.lower().replace('_', ' ')} with a distinctive weapon appearance. Check its class, level, and attributes before buying."
    if category == "fashion":
        slot = "headpiece" if group == "HEAD" else "accessory" if group == "EARRING" else "outfit piece"
        article = "an" if slot == "outfit piece" or slot == "accessory" else "a"
        return f"{name} is {article} {slot} for your character's look. Check the item details for its equipment requirements."
    if "pack" in actions or "box" in lower or "bundle" in lower:
        return f"Open {name} to receive its server-defined contents. The unopened item arrives through Black Cloud mail."
    if "candy" in lower:
        return f"Use {name} to change your character's form and receive its listed effect. This offer contains {count} candies."
    if category == "upgrades":
        if "enchantment stone" in lower:
            return f"Attempt to enchant compatible gear with {name}. This offer contains {count} stones."
        if "manastone" in lower:
            return f"Socket {name} into compatible equipment for the attributes named on the stone. This offer contains {count}."
        if "supplement" in lower:
            return f"Use {name} during a compatible equipment enhancement attempt. This offer contains {count} supplements."
        if "shard" in lower:
            return f"Keep {name} ready for combat or stigma use. This offer contains {count} shards."
        return f"{name} supports equipment extraction or enhancement. Check its use action in item details. {units}"
    if "scroll" in lower:
        if "sanctum" in lower:
            return f"Return to Sanctum with {name}. This offer contains {count} scrolls for Elyos characters."
        if "pandaemonium" in lower:
            return f"Return to Pandaemonium with {name}. This offer contains {count} scrolls for Asmodian characters."
        return f"Use {name} for its listed travel or combat effect. This offer contains {count} scrolls."
    if "shard" in lower:
        return f"Keep {name} ready for combat or stigma use. This offer contains {count} shards."
    if "potion" in lower or "elixir" in lower or "panacea" in lower:
        effect = "HP and MP" if "panacea" in lower and not any(x in lower for x in ("life", "mana")) else "HP" if any(x in lower for x in ("life", "health")) else "MP" if "mana" in lower else "recovery"
        return f"Carry {name} for {effect} recovery during travel or combat. This offer contains {count} consumables."
    if "skilluse" in actions:
        kind = "meal" if any(x in lower for x in ("soup", "salad", "omelette", "curry", "steak", "skewer", "bread")) else "drink" if any(x in lower for x in ("juice", "tea", "coffee", "drink")) else "consumable"
        return f"Use the {name} {kind} from your inventory for its listed effect. This offer contains {count}."
    if group in WEAPONS:
        return f"{name} is a {group.lower().replace('_', ' ')} item. Check its class, level, and combat attributes before buying."
    return f"{name} is a {category.replace('_', ' ')} item from this server's Aion 4.8 data. Check its use details before buying. {units}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    shop = args.server_root / "config/ingameshop"
    current = read_rows(shop / "marketplace_cash.tsv")
    archived = read_rows(shop / "marketplace.tsv") + read_rows(shop / "marketplace_extra.tsv")
    seen = {row[0] for row in current}
    new = [row for row in archived if row[0] not in seen]
    details = item_data(args.server_root / "data/static_data/items/item_templates.xml", {row[0] for row in new})
    expanded = current[:]
    for row in new:
        item_id, count, price, old_section, unlock, _ = row
        name, group, actions, stack = details[item_id]
        if not name or int(count) < 1 or int(count) > stack or int(price) < 1:
            raise ValueError(f"Invalid item: {item_id}")
        broad = category(old_section, item_id, group)
        target = subcategory(broad, item_id, name, group, actions)
        expanded.append([item_id, count, price, target, unlock,
                         description(name, group, actions, int(count), broad)])
        seen.add(item_id)
    output = args.output or shop / "marketplace_cash.tsv"
    with output.open("w", encoding="utf-8", newline="") as stream:
        stream.write("# item_id\tquantity\tprice_in_kinah\tsection\tunlock_level\tdescription\n")
        csv.writer(stream, delimiter="\t", lineterminator="\n").writerows(expanded)
    print(f"Wrote {len(expanded)} current offers ({len(seen)} unique IDs); {len(new)} added from {len(archived)} historical source rows")


if __name__ == "__main__":
    main()
