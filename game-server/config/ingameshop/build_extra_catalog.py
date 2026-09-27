"""Rebuild the optional, period-compatible Black Cloud Kinah catalog.

Run from the game-server directory with Python 3. The output is intentionally
checked in so the running server does not need Python.
"""

from collections import Counter
from pathlib import Path
import re
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data/static_data"
SHOP = Path(__file__).resolve().parent
CORE = SHOP / "marketplace.tsv"
OUTPUT = SHOP / "marketplace_extra.tsv"


def ids(path, tag, attribute):
    return {node.get(attribute) for _, node in ET.iterparse(path, events=("end",)) if node.tag == tag}


PETS = ids(DATA / "pets/pets.xml", "pet", "id")
RIDES = ids(DATA / "ride/ride.xml", "ride_info", "id")
TITLES = ids(DATA / "player_titles.xml", "title", "id")
BOXES = ids(DATA / "decomposable_items/decomposable_items.xml", "decomposable", "item_id")
CORE_IDS = {line.split("\t", 1)[0] for line in CORE.read_text(encoding="utf-8").splitlines()
            if line and not line.startswith("#")}

BAD_NAME = re.compile(r"\[(?:event|stamp|test|qa|expired)|\b(?:test|dummy|placeholder|for qa)\b|\((?:\d+\s*)?(?:days?|hours?|minutes?)\)|\b\d+[- ]day(?:s)?(?:\s+pass)?\b", re.I)
BAD_CNAME = re.compile(r"(?:^|_)(?:test|dummy|binding|day\d+|hour\d+|qa)(?:_|$)", re.I)
WEAPON_GROUPS = {"SWORD", "DAGGER", "MACE", "STAFF", "GREATSWORD", "POLEARM", "BOW", "ORB", "BOOK", "GUN", "CANNON", "HARP", "KEYBLADE", "SHIELD"}
ARMOR_GROUPS = {"TORSO", "PANTS", "SHOULDER", "GLOVE", "SHOES"}


def category(item):
    item_id = item.get("id", "")
    name = item.get("name", "")
    cname = item.get("cName", "").lower()
    group = item.get("item_group", "")
    action = item.find("actions")
    tags = {child.tag: child for child in action} if action is not None else {}
    if "gm" in name.lower() and item_id in {"101300992", "110900338", "110900339", "188052083", "188052086"} and (item_id not in {"188052083", "188052086"} or item_id in BOXES):
        return "gm", 2_000_000, "A rare GM themed collectible from the 4.8 item data."
    if item_id.startswith("1900") and "adoptpet" in tags and tags["adoptpet"].get("petId") in PETS:
        return "pets", 350_000, "Adopt a companion for your journey."
    if item_id.startswith("1901") and "ride" in tags and tags["ride"].get("npc_id") in RIDES:
        return "mounts", 750_000, "A permanent mount to travel in style."
    if item_id.startswith("169220") and "dye" in tags:
        return "dyes", 25_000, "Change the color of your favorite outfit."
    if item_id.startswith("169600") and "learnemotion" in tags:
        return "emotes", 90_000, "Learn a new expression or dance."
    if item_id.startswith("169610") and "titleadd" in tags and tags["titleadd"].get("titleid") in TITLES:
        return "titles", 180_000, "Add a title to your character."
    if item_id.startswith("169800") and "cosmetic" in tags and "Hair Change Ticket" in name:
        return "hair", 200_000, "Try a new hairstyle at the makeover station."
    if group in ARMOR_GROUPS and (cname.startswith("cash_") or cname.startswith("shop_")):
        return "outfits", 125_000, "A cosmetic look for your armor."
    if group == "HEAD" and (cname.startswith("cash_") or cname.startswith("shop_")):
        return "hats", 90_000, "Finish your look with a headpiece."
    if group in WEAPON_GROUPS and (cname.startswith("cash_") or cname.startswith("shop_")):
        return "weapon_skins", 175_000, "A distinctive look for your weapon."
    if group == "WING" and (cname.startswith("cash_") or cname.startswith("shop_")):
        return "wings", 600_000, "Take flight with a new pair of wings."
    if cname.startswith(("shop_food", "cash_food")) and "skilluse" in tags:
        return "food", 12_000, "A useful meal for an adventurer."
    if cname.startswith(("shop_potion", "cash_potion")) and "skilluse" in tags:
        return "potions", 15_000, "A ready supply for difficult encounters."
    if item_id.startswith(("169630", "169640", "169650")) and tags:
        return "services", 400_000, "An extra convenience for your character."
    return None


def acceptable(item, section):
    name = item.get("name", "")
    cname = item.get("cName", "")
    if not name or name.startswith("_") or BAD_NAME.search(name) or BAD_CNAME.search(cname):
        return False
    if item.get("expire_time") or item.get("duration") or item.get("disappear_time"):
        return False
    if int(item.get("level", "1")) > 65:
        return False
    if section == "gm":
        return True
    if section in {"pets", "mounts"} and ("temporary" in name.lower() or "temp" in cname.lower()):
        return False
    return True


def main():
    rows = []
    seen = set()
    for _, item in ET.iterparse(DATA / "items/item_templates.xml", events=("end",)):
        if item.tag != "item_template":
            continue
        item_id = item.get("id")
        classified = category(item)
        if item_id in CORE_IDS or classified is None:
            item.clear()
            continue
        section, base_price, description = classified
        if not acceptable(item, section):
            item.clear()
            continue
        race = item.get("race", "PC_ALL")
        limits = item.find("uselimits")
        gender = limits.get("gender", "") if limits is not None else ""
        key = (section, item.get("name", "").casefold(), race, gender)
        if key in seen and section != "gm":
            item.clear()
            continue
        seen.add(key)
        level = min(65, max(1, int(item.get("level", "1"))))
        unlock = 1 if section in {"dyes", "emotes", "titles", "hair", "pets", "mounts", "gm"} else level
        count = min(5, int(item.get("max_stack_count", "1"))) if section in {"food", "potions", "dyes"} else 1
        rows.append((section, item.get("name", "").casefold(), item_id, count, base_price,
                     unlock, description))
        item.clear()
    rows.sort()
    OUTPUT.write_text("# item_id\tquantity\tprice_in_kinah\tsection\tunlock_level\tdescription\n" +
                      "".join(f"{item_id}\t{count}\t{price}\t{section}\t{unlock}\t{description}\n"
                              for section, _, item_id, count, price, unlock, description in rows), encoding="utf-8")
    print(f"Wrote {len(rows)} offers to {OUTPUT.name}: {dict(Counter(row[0] for row in rows))}")


if __name__ == "__main__":
    main()
