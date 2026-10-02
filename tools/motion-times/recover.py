"""Recover the reported motion gaps from an installed 4.8 client, read-only."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "client-mods/expanded-warehouse"))
from codec import read_pak, binary_xml

NAMES = """sumrobot quest_drinkpoint pr_root_g3 cash_social_diving areaatklf
areaatkrf normalfireth normalfiremo pointfirerh pointfirelh areaatkrh areaatklh
normalfirehd areafireod phburst1 movingatk sanctuaryfire poweratk2 areaatk2
normalfire3 normalfire4 sanctin sanctout normalfire5 sanctuary talk poweratk1
areafire1 normalfire1 backatk say transform summon1 rangeatk poweratk3 teleport
earthquake spinatk open shapechange breathl breathm breathr deadlyatk breathl4s
breathl8s breathm4s breathm8s breathr4s breathr8s openariel biteatk closeariel
chainatk areafire3 cidle herb axe mine""".split()
PLAYERS = {"df.xml": "asmodian_female", "dm.xml": "asmodian_male",
           "lf.xml": "elyos_female", "lm.xml": "elyos_male", "robot.xml": "robot"}
WEAPONS = "1hand 2hand 2weapon polearm dagger mace staff noweapon book orb 1gun 2gun cannon bow harp keyblade".split()
GATHER = {"herb": ("30001", "gathering_a", "ngathering_gathering_a_001"),
          "axe": ("30002", "gathering_b", "ngathering_gathering_b_001"),
          "mine": ("30003", "aerial_gathering", "fgathering_aerialgathering_001")}
MARKER = b"  <!-- Recovered from the installed Aion 4.8 NA client; see docs/MOTION_TIMES.md. -->"


def xml(payload):
    return binary_xml(payload) if payload[:1] == b"\x80" else ET.fromstring(payload.lstrip(b"`"))


def priority(row):
    name = row["name"].lower()
    if name.startswith("cfire") or row.get("action_alias"):
        return 0
    if name.startswith("ccast"):
        return 3
    if name.startswith(("xfire", "xcast")):
        return 9
    if name.startswith(("ngatherstart", "ngatherend", "nharvest")):
        return 8
    return 1


def collect(client):
    pattern = re.compile(r"(?:^|_|^n)(" + "|".join(re.escape(n) for n in sorted(NAMES, key=len, reverse=True))
                         + r")(?:_(?:stance\d+|fly|run|loop|end|start))*(?:_\d+)?$", re.I)
    rows = {n: [] for n in NAMES}
    errors = []
    with read_pak(client / "Data/animationmarkers/animationmarkers.pak") as archive:
        for filename in archive.namelist():
            if not filename.lower().endswith(".xml"):
                continue
            try:
                animations = xml(archive.read(filename))
            except (ValueError, ET.ParseError, IndexError) as error:
                errors.append({"file": filename, "error": str(error)})
                continue
            for animation in animations:
                name = animation.get("name", "")
                match = pattern.search(name)
                entry = {"file": filename, "name": name, "length": animation.get("animation_length"),
                         "markers": [(c.tag, c.attrib) for c in animation]}
                if match:
                    rows[match.group(1).lower()].append(entry)
                if filename.lower() in PLAYERS:
                    for motion, (_, _, alias) in GATHER.items():
                        if name.lower() == alias:
                            rows[motion].append(dict(entry, action_alias=True))
    meshes = {}
    with read_pak(client / "Data/Npcs/Npcs.pak") as archive:
        for filename in ("client_npcs_monster.xml", "client_npcs_npc.xml"):
            for npc in xml(archive.read(filename)):
                mesh = (npc.findtext("mesh") or "").lower()
                if mesh:
                    meshes.setdefault(mesh, set()).add(int(npc.findtext("id")))
    with read_pak(client / "Data/skills/skills.pak") as archive:
        skills = {s.findtext("id"): {c.tag: c.text for c in s} for s in xml(archive.read("client_skills.xml"))}
    for motion, (skill_id, action_name, _) in GATHER.items():
        if skills[skill_id]["name"].lower() != action_name:
            raise ValueError("Unexpected gathering action for " + motion)
    return rows, meshes, errors, skills


def recover(candidates, meshes):
    recovered = ET.Element("motion_times")
    unresolved = []
    for motion, rows in sorted(candidates.items()):
        element = ET.Element("motion_time", name=motion)
        groups = {}
        for row in rows:
            filename = row["file"].lower()
            if not row["length"] or filename.startswith("_") or priority(row) >= 8:
                continue
            match = re.search(r"_(\d+)$", row["name"])
            groups.setdefault((filename, match.group(1) if match else "1"), []).append(row)
        ordered = sorted(groups.items(), key=lambda item: (
            list(PLAYERS).index(item[0][0]) if item[0][0] in PLAYERS else 6, item[0]))
        for (filename, motion_id), variants in ordered:
            model = Path(filename).stem
            if filename not in PLAYERS and model not in meshes:
                continue
            row = min(variants, key=lambda r: (priority(r), len(r["name"]), r["name"].lower()))
            hits = [a["when"] for tag, a in row["markers"] if tag == "hitpoint" and "when" in a]
            values = {"id": str(int(motion_id)), "min": min(hits, key=float) if hits else "0",
                      "max": max(hits, key=float) if hits else "0", "animation_length": row["length"],
                      "source": row["file"] + "/" + row["name"]}
            if not hits:
                values["hitpoints"] = "false"
            if row["name"].lower().startswith("ccast"):
                values["cast_animation"] = "true"
            weapon_match = re.match(r"[cx](?:fire|cast)_([^_]+)_", row["name"], re.I)
            if filename in PLAYERS:
                if motion in GATHER and not row.get("action_alias"):
                    continue
                tag = PLAYERS[filename]
                weapons = ["keyblade"] if tag == "robot" else ([weapon_match.group(1).lower()] if weapon_match else WEAPONS)
                for weapon in weapons:
                    ET.SubElement(element, tag, weapon=weapon, **values)
            else:
                ET.SubElement(element, "npc", weapon=weapon_match.group(1).lower() if weapon_match else "noweapon",
                              npc_ids=" ".join(str(i) for i in sorted(meshes[model])), **values)
        if len(element):
            recovered.append(element)
        else:
            unresolved.append(motion)
    return recovered, unresolved


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client", type=Path, required=True)
    parser.add_argument("--write", action="store_true", help="Update source XML and the provenance manifest")
    args = parser.parse_args()
    candidates, meshes, errors, skills = collect(args.client)
    recovered, unresolved = recover(candidates, meshes)
    manifest = {"client": "Aion 4.8 NA", "archives": {},
                "motions": {e.get("name"): [dict(t.attrib, actor=t.tag) for t in e] for e in recovered},
                "unresolved": unresolved, "malformed_client_marker_files": errors,
                "native_instant_skills": {i: s["motion_name"] for i, s in skills.items()
                                          if s.get("instant_skill") == "1" and (s.get("motion_name") or "").lower() in NAMES}}
    for filename in ("Data/animationmarkers/animationmarkers.pak", "Data/Npcs/Npcs.pak", "Data/skills/skills.pak"):
        manifest["archives"][filename] = hashlib.sha256((args.client / filename).read_bytes()).hexdigest()
    path = ROOT / "game-server/data/static_data/skills/motion_times.xml"
    current = path.read_bytes()
    if args.write:
        if MARKER in current:
            current = current[:current.index(MARKER)].rstrip(b"\r\n") + b"\n</motion_times>\n"
        ET.indent(recovered, space="  ")
        block = b"\n" + MARKER + b"\n" + b"\n".join(("  " + ET.tostring(e, encoding="unicode").strip()).encode() for e in recovered) + b"\n"
        path.write_bytes(current.replace(b"</motion_times>", block + b"</motion_times>"))
        (ROOT / "docs/motion-times/recovered-4.8.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    else:
        loaded = {e.get("name"): [dict(t.attrib, actor=t.tag) for t in e] for e in ET.fromstring(current)}
        for name, rows in manifest["motions"].items():
            if loaded.get(name) != rows:
                raise ValueError("Client/source timing mismatch: " + name)
    print("Verified %d recovered motions, %d timing rows." % (len(recovered), sum(len(e) for e in recovered)))
    print("No timing recovered:", ", ".join(unresolved))
    print("Malformed client marker documents:", ", ".join(e["file"] for e in errors))


if __name__ == "__main__":
    main()
