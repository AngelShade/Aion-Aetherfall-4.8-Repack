"""Audit or refresh Black Cloud offers against maintained native Aion item data.

Run without --apply for an audit. Only prices and ineligible offer rows change;
item IDs, quantities, ordering and descriptions are retained. This is a source
maintenance tool, not a saved-player-data migration.
"""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET


WEAPONS = {"SWORD", "GREATSWORD", "DAGGER", "MACE", "ORB", "SPELLBOOK",
           "POLEARM", "STAFF", "BOW", "HARP", "GUN", "CANNON", "KEYBLADE", "NOWEAPON"}
BODY_PARTS = {"TORSO", "GLOVE", "SHOULDER", "PANTS", "SHOES"}
ARMOR = BODY_PARTS | {f"{material}_{part}" for material in ("RB", "CL", "LT", "CH", "PL")
                     for part in BODY_PARTS} | {"SHIELD", "CL_SHIELD", "HEAD", "LT_HEADS", "CL_HEADS",
                                               "CL_MULTISLOT", "EARRING", "RING", "NECKLACE", "BELT", "WING", "POWER_SHARDS"}
SLOTTED = WEAPONS | ARMOR | {"STIGMA", "PLUME", "NPC_MACE", "TOOLRODS", "TOOLHOES", "TOOLPICKS"}
EQUIPMENT_SECTIONS = {"equipment", "equipment_weapons", "equipment_armor"}
WEAPON_LOOKS = {"weapon_looks", "weapon_melee", "weapon_ranged", "weapon_arcane"}
COSMETIC_SECTIONS = WEAPON_LOOKS | {"fashion", "fashion_outfits", "fashion_headwear", "fashion_hair",
                                      "fashion_dyes", "wings", "wing_permanent", "wing_skins"}
# These match ItemTemplate's cached native classification. Boundaries deliberately
# preserve names such as Proposal and consumables that summon actual NPCs.
DEVELOPMENT_NAME = re.compile(r"(?:^|[^a-z0-9])(?:test|debug|dummy|prototype|placeholder|unused)(?:$|[^a-z0-9])")
GM_NAME = re.compile(r"(?:^|[^A-Za-z])GMs?(?:$|[^A-Za-z])")
UNTRANSLATED_NAME = re.compile(r"[A-Za-z0-9]+(?:_[A-Za-z0-9]+)+")
NON_PLAYER_NAME = re.compile(r"(?:^|[^a-z0-9])(?:npc|prop)(?:$|[^a-z0-9])")
NON_PLAYER_CLIENT_NAME = re.compile(r"(?:^|_)(?:npc|prop)(?:_|$)")


@dataclass(frozen=True)
class NativeItem:
    attributes: dict[str, str]
    acquisition: dict[str, str]
    remodel_type: int
    modifiers: tuple[float, ...]
    weapon: dict[str, str]

    @property
    def name(self) -> str:
        return self.attributes.get("name", "")

    @property
    def group(self) -> str:
        return self.attributes.get("item_group", "NONE")

    @property
    def price(self) -> int:
        return int(self.attributes.get("price", "0"))

    def player_item(self) -> bool:
        name = self.name.lower()
        client = self.attributes.get("cName", "").lower()
        development = (client.startswith("test") or "_test" in client or "test_" in client
                       or name.startswith("[temp") or DEVELOPMENT_NAME.search(name)
                       or GM_NAME.search(self.name) or UNTRANSLATED_NAME.fullmatch(self.name))
        non_player = self.group in SLOTTED and (NON_PLAYER_CLIENT_NAME.search(client)
                     or NON_PLAYER_NAME.search(name) or "login stage" in name)
        return bool(name.strip()) and not development and not non_player

    def appearance_only(self) -> bool:
        if self.group not in WEAPONS | ARMOR:
            return False
        if "changeskin" in self.attributes.get("cName", "").lower() or self.remodel_type == 2:
            return True
        # ItemEligibility's statless WardrobeRules Costumes fallback.
        if any(self.modifiers) or int(self.attributes.get("rnd_bonus", "0")):
            return False
        if any(int(self.weapon.get(key, "0")) for key in ("min_damage", "max_damage", "boost_magical_skill",
                                                         "magical_accuracy", "physical_accuracy", "critical", "parry")):
            return False
        return self.group in BODY_PARTS or self.group.startswith("CL_") and self.group not in {"CL_HEADS", "CL_SHIELD"}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_items(path: Path, wanted: set[str]) -> dict[str, NativeItem]:
    result = {}
    for _, element in ET.iterparse(path, events=("end",)):
        if element.tag != "item_template":
            continue
        item_id = element.get("id")
        if item_id in wanted:
            acquisition = element.find("acquisition")
            remodel = element.find("actions/remodel")
            weapon = element.find("weapon_stats")
            result[item_id] = NativeItem(dict(element.attrib), dict(acquisition.attrib) if acquisition is not None else {},
                int(remodel.get("type", "0")) if remodel is not None else 0,
                tuple(float(stat.get("value", "0")) for stat in element.findall("modifiers/*")),
                dict(weapon.attrib) if weapon is not None else {})
        element.clear()
    if missing := wanted - result.keys():
        raise ValueError(f"Unknown native item IDs: {sorted(missing)}")
    return result


def read_properties(path: Path) -> dict[str, str]:
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.lstrip().startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def guaranteed_bundle_children(folder: Path, wanted: set[str]) -> tuple[dict[str, dict[str, tuple[str, int]]], list[Path]]:
    """Recognize one unconditional fixed reward for each playable faction.

    Weighted/random, selectable, class-dependent or level-dependent boxes are
    deliberately outside this narrow appraisal. Local override precedence is
    the same as DecomposableItemsData's native loader.
    """
    definitions = {}
    overrides = {}
    sources = sorted(folder.glob("*.xml"))
    for path in sources:
        for _, element in ET.iterparse(path, events=("end",)):
            if element.tag != "decomposable":
                continue
            item_id = element.get("item_id")
            if item_id in wanted:
                target = overrides if element.get("override", "false") == "true" else definitions
                if item_id in target:
                    raise ValueError(f"Duplicate native decomposable definition for {item_id}.")
                target[item_id] = ET.fromstring(ET.tostring(element))
            element.clear()
    definitions.update(overrides)
    result = {}
    for item_id, definition in definitions.items():
        if definition.get("selectable", "false") == "true":
            continue
        factions = {}
        for race in ("ELYOS", "ASMODIANS"):
            branches = [branch for branch in definition.findall("set") if branch.get("race", "PC_ALL") in ("PC_ALL", race)]
            if len(branches) != 1:
                break
            branch = branches[0]
            rewards = list(branch)
            if (float(branch.get("chance", "100")) != 100 or branch.get("player_classes")
                    or int(branch.get("min_level", "1")) > 1 or int(branch.get("max_level", "80")) < 80
                    or len(rewards) != 1 or rewards[0].tag != "item"):
                break
            reward = rewards[0]
            count = int(reward.get("count", "1"))
            if (float(reward.get("chance", "100")) != 100 or count <= 0
                    or int(reward.get("max_count", "0")) not in (0, count)):
                break
            factions[race] = (reward.get("id"), count)
        if len(factions) == 2:
            result[item_id] = factions
    return result, sources


def resale_rates(pets: Path, prices: Path, trades: Path, goods: Path) -> tuple[int, dict[str, int]]:
    standard = int(read_properties(prices).get("gameserver.prices.vendor.sellmod", "20"))
    pet_rates = [int(function.get("rate_price", "0")) for function in ET.parse(pets).iter("petfunction")
                 if function.get("type") == "MERCHANT"]
    standard = max([standard, *pet_rates])
    tabs = {entry.get("id"): [item.get("id") for item in entry.findall("item")]
            for entry in ET.parse(goods).getroot().findall("purchase_list")}
    special = {}
    for npc in ET.parse(trades).getroot().findall("purchase_template"):
        # ABYSS purchase templates award AP in CM_BUY_ITEM, not Kinah.
        if npc.get("npc_type", "NORMAL") == "ABYSS":
            continue
        rate = int(npc.get("buy_price_rate", "0"))
        for tab in npc.findall("tradelist"):
            for item_id in tabs.get(tab.get("id"), []):
                special[item_id] = max(special.get(item_id, 0), rate)
    return standard, special


# Player feedback (owner, 9 Nov 2026). Level stones: Alpha to Epsilon replace them. Stigma Shards are unused.
# GM items and the all-ailment potion are GM strength.
LEVEL_STONE = re.compile(r"L\d+ Enchantment Stone")
GM_ITEM = re.compile(r"\bGM\b")
ALL_ALTERED_STATE_POTION = "162000028"
# Players sell the power shards they drop below the merchant's price.
POWER_SHARD_MERCHANT_PERCENT = 75
# Economy audit (9 Nov 2026): Black Cloud delivers anywhere, which is worth a premium over a town merchant, but
# not 20x (teleport scrolls were 30,000 against the merchant's 1,500; foods 2,400 against 200).
NPC_CONVENIENCE_PERCENT = 300


def live_npc_rates(trades: Path, goods: Path, spawns: list[Path]) -> dict[str, int]:
    """Cheapest Kinah rate of a merchant that actually spawns, per item (sell_price_rate, percent of native price)."""
    spawned = set()
    for folder in spawns:
        for path in folder.rglob("*.xml") if folder.is_dir() else []:
            spawned.update(re.findall(r'npc_id="(\d+)"', path.read_text(encoding="utf-8", errors="replace")))
    tabs = {entry.get("id"): [item.get("id") for item in entry.findall("item")]
            for entry in ET.parse(goods).getroot().findall("list")}
    rates = {}
    for npc in ET.parse(trades).getroot().findall("tradelist_template"):
        if npc.get("npc_type", "NORMAL") != "NORMAL" or npc.get("npc_id") not in spawned:
            continue
        rate = int(npc.get("sell_price_rate", "100"))
        for tab in npc.findall("tradelist"):
            for item_id in tabs.get(tab.get("id"), []):
                rates[item_id] = min(rates.get(item_id, rate), rate)
    return rates


def feedback_removed(item_id: str, item: NativeItem) -> bool:
    return (item.group == "ENCHANTMENT" and bool(LEVEL_STONE.fullmatch(item.name)) or item.group == "STIGMA_SHARD"
            or bool(GM_ITEM.search(item.name)) or item_id == ALL_ALTERED_STATE_POTION)


def removal_reason(row: list[str], item: NativeItem) -> str | None:
    section = row[3]
    if feedback_removed(row[0], item):
        return "player_feedback_removed"
    if section in EQUIPMENT_SECTIONS:
        return "combat_equipment_collection"
    if not item.player_item():
        return "native_development_or_non_player_item"
    ap = int(item.acquisition.get("ap", "0"))
    if ap and item.acquisition.get("type") in {"AP", "ABYSS"} and (section in COSMETIC_SECTIONS
            or section == "featured" and item.group in WEAPONS | ARMOR):
        return "native_ap_cosmetic"
    if (section in WEAPON_LOOKS or section == "featured" and item.group in WEAPONS) and not item.appearance_only():
        return "combat_item_in_appearance_collection"
    return None


def main() -> None:
    server = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=server / "config/ingameshop/marketplace_cash.tsv")
    parser.add_argument("--items", type=Path, default=server / "data/static_data/items/item_templates.xml")
    parser.add_argument("--goods", type=Path, default=server / "data/static_data/goodslists/goodslists.xml")
    parser.add_argument("--trades", type=Path, default=server / "data/static_data/npc_trade_list.xml")
    parser.add_argument("--pets", type=Path, default=server / "data/static_data/pets/pets.xml")
    parser.add_argument("--prices", type=Path, default=server / "config/main/prices.properties")
    parser.add_argument("--decomposables", type=Path, default=server / "data/static_data/decomposable_items")
    parser.add_argument("--apply", action="store_true", help="Write the corrected source catalog.")
    parser.add_argument("--report", type=Path, help="Write detailed JSON audit outside source/runtime.")
    args = parser.parse_args()
    before = args.catalog.read_bytes()
    lines = before.decode("utf-8").splitlines(keepends=True)
    rows = [line.rstrip("\r\n").split("\t") for line in lines if line.strip() and not line.startswith("#")]
    if any(len(row) != 6 for row in rows):
        raise ValueError("Catalog rows must have exactly six TSV fields.")
    bundles, bundle_sources = guaranteed_bundle_children(args.decomposables,
        {row[0] for row in rows if row[3] in {"gift_boxes", "travel_bundles"}})
    child_ids = {item_id for factions in bundles.values() for item_id, _ in factions.values()}
    items = read_items(args.items, {row[0] for row in rows} | child_ids)
    bundle_floors = {item_id: max(items[child].price * count for child, count in factions.values())
                     for item_id, factions in bundles.items()}
    standard, special = resale_rates(args.pets, args.prices, args.trades, args.goods)
    data = args.items.parents[1]
    merchants = live_npc_rates(args.trades, args.goods, [data / "spawns", data / "town_spawns", data / "events"])
    removed, changed, before_loops, after_loops, output = [], [], [], [], []
    remaining = 0
    for line in lines:
        if not line.strip() or line.startswith("#"):
            output.append(line)
            continue
        row = line.rstrip("\r\n").split("\t")
        item_id, count, old_price = row[0], int(row[1]), int(row[2])
        if count <= 0 or old_price <= 0:
            raise ValueError(f"Invalid quantity or price for item {item_id}.")
        item = items[item_id]
        sellable = int(item.attributes.get("mask", "0")) & 4
        rate = max(standard if sellable else 0, special.get(item_id, 0))
        resale = item.price * rate // 100 * count
        detail = {"item_id": int(item_id), "name": item.name, "section": row[3], "quantity": count,
                  "old_price": old_price, "native_unit_price": item.price, "maximum_kinah_resale": resale}
        if item_id in bundle_floors:
            detail["guaranteed_bundle_native_retail"] = bundle_floors[item_id] * count
        if resale > old_price:
            before_loops.append({**detail, "resale_to_cost": resale / old_price})
        if reason := removal_reason(row, item):
            removed.append({**detail, "reason": reason})
            continue
        if item.group == "POWER_SHARDS":
            # Below the merchant, but never below what a merchant pays back.
            price = max(item.price * POWER_SHARD_MERCHANT_PERCENT // 100 * count, resale + 1, 1)
        else:
            price = max(old_price, item.price * count, resale + 1 if resale > 0 else 0,
                        bundle_floors.get(item_id, 0) * count)
            if item_id in merchants and item.acquisition.get("type") is None:
                merchant = item.price * merchants[item_id] // 100 * count
                price = max(min(price, merchant * NPC_CONVENIENCE_PERCENT // 100), merchant, resale + 1, 1)
        if price > 999_999_999_999_999:
            raise ValueError(f"Item {item_id} exceeds the native market Kinah limit.")
        if price != old_price:
            changed.append({**detail, "new_price": price})
            row[2] = str(price)
            ending = line[len(line.rstrip("\r\n")):]
            output.append("\t".join(row) + ending)
        else:
            output.append(line)
        if resale >= price:
            after_loops.append({**detail, "new_price": price})
        remaining += 1
    after = "".join(output).encode("utf-8")
    report = {"policy": "native-retail-and-resale-floor-v3", "applied": args.apply,
              "offers_before": len(rows), "offers_after": remaining, "removed_count": len(removed),
              "removed_by_reason": dict(Counter(row["reason"] for row in removed)), "repriced_count": len(changed),
              "maximum_generic_resale_percent": standard, "profitable_vendor_routes_before": len(before_loops),
              "profitable_vendor_routes_after": len(after_loops), "catalog_before_sha256": sha256(before),
              "catalog_after_sha256": sha256(after), "changed_prices": changed, "removed_offers": removed,
              "top_previous_vendor_loops": sorted(before_loops, key=lambda entry: entry["resale_to_cost"], reverse=True)[:20],
              "remaining_vendor_loops": after_loops,
              "guaranteed_single_reward_bundles": {item_id: {"native_retail_floor": bundle_floors[item_id],
                    "faction_rewards": {race: {"item_id": int(child), "count": count}
                        for race, (child, count) in factions.items()}} for item_id, factions in bundles.items()},
              "native_source_sha256": {str(path.resolve()): sha256(path.read_bytes())
                for path in (args.items, args.goods, args.trades, args.pets, args.prices, *bundle_sources)}}
    if after_loops:
        raise ValueError("Corrected catalog still has a profitable native vendor route.")
    if args.apply:
        # Refuse to replace work changed by another process during the native data read.
        if args.catalog.read_bytes() != before:
            raise RuntimeError("Catalog changed while it was being audited; rerun against current files.")
        args.catalog.write_bytes(after)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items()
                      if key not in {"changed_prices", "removed_offers", "top_previous_vendor_loops", "remaining_vendor_loops", "native_source_sha256"}}, indent=2))


if __name__ == "__main__":
    main()
