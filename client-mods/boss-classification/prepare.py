"""Stage matching native frames for the server's Lannok/Triroan Hero classification.

Never installs or modifies the original client. Requires an externally supplied
input hash, preserves other archive payloads, and verifies every NPC field.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "client-mods/expanded-warehouse"))
from codec import binary_xml, encode_binary_xml, encode_pak, read_pak

BOSSES = {"214669": "Unstable Triroan", "214696": "Lord Lannok"}
ARCHIVE = Path("Data/Npcs/Npcs.pak")
ENTRY = "client_npcs_monster.xml"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def tree(node):
    return (node.tag, tuple(sorted(node.attrib.items())), node.text or "",
            node.tail or "", tuple(tree(child) for child in node))


def decode(data):
    return binary_xml(data) if data[:1] == b"\x80" else ET.fromstring(data)


def validate_server():
    path = ROOT / "game-server/data/static_data/npcs/npc_templates.xml"
    data = path.read_bytes()
    found = set()
    for node in ET.fromstring(data):
        identity = node.get("npc_id")
        if identity in BOSSES:
            require(identity not in found, "Duplicate server boss " + identity)
            require((node.get("rating"), node.get("rank"), node.get("hpgauge"))
                    == ("HERO", "VETERAN", "21"), "Incompatible server boss " + identity)
            found.add(identity)
    require(found == set(BOSSES), "Missing matching server bosses")
    return path, sha(data)


def validate_change(original, changed):
    before, after = decode(original), decode(changed)
    require(len(before) == len(after), "NPC count changed")
    found, differing = set(), set()
    for left, right in zip(before, after):
        identity = left.findtext("id")
        require(identity == right.findtext("id"), "NPC order changed")
        if tree(left) == tree(right):
            continue
        require(identity in BOSSES and identity not in found, "Unexpected changed NPC " + str(identity))
        found.add(identity)
        old_gauge, new_gauge = left.find("hpgauge_level"), right.find("hpgauge_level")
        require(old_gauge is not None and new_gauge is not None, "Missing native gauge")
        require(old_gauge.text == "14" and new_gauge.text == "21", "Unexpected native grade transition")
        new_gauge.text = "14"
        require(tree(left) == tree(right), "Other boss properties changed: " + identity)
        differing.add(identity)
    require(differing == set(BOSSES), "Candidate must change both boss gauges only")
    return len(before)


def prepare(client, output, expected_hash):
    client, output = client.resolve(strict=True), output.resolve()
    require(not output.exists(), "Use a new external output directory")
    for protected in (ROOT, client):
        require(not output.is_relative_to(protected) and not protected.is_relative_to(output),
                "Output must be outside source and original client trees")
    server_path, server_hash = validate_server()
    source = client / ARCHIVE
    original_archive = source.read_bytes()
    require(sha(original_archive) == expected_hash.lower(), "Original client archive hash changed")
    entries = []
    with read_pak(source) as archive:
        original = archive.read(ENTRY)
        root = decode(original)
        found = set()
        for node in root:
            identity = node.findtext("id")
            if identity in BOSSES:
                require(identity not in found, "Duplicate native boss " + identity)
                found.add(identity)
                gauge = node.find("hpgauge_level")
                require(gauge is not None and gauge.text == "14", "Unexpected current boss grade " + identity)
                gauge.text = "21"
        require(found == set(BOSSES), "Native bosses missing")
        changed = encode_binary_xml(root) if original[:1] == b"\x80" else ET.tostring(root, encoding="utf-8")
        require(tree(decode(changed)) == tree(root), "Native XML roundtrip changed data")
        count = validate_change(original, changed)
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as packaged:
            packaged.comment = archive.comment
            for info in archive.infolist():
                content = archive.read(info.filename)
                payload = changed if info.filename == ENTRY else content
                packaged.writestr(info, payload)
                entries.append({"name": info.filename, "before": sha(content), "after": sha(payload)})
        archive_comment = archive.comment
    output.mkdir(parents=True)
    target = output / "client" / ARCHIVE
    target.parent.mkdir(parents=True)
    target.write_bytes(encode_pak(buffer.getvalue()))
    with read_pak(target) as archive:
        require(archive.namelist() == [entry["name"] for entry in entries], "Archive entries changed")
        require(archive.comment == archive_comment, "Archive comment changed")
        for entry in entries:
            require(sha(archive.read(entry["name"])) == entry["after"], "Archive payload changed")
        validate_change(original, archive.read(ENTRY))
    require(sha(source.read_bytes()) == expected_hash.lower(), "Original client changed during preparation")
    require(sha(server_path.read_bytes()) == server_hash, "Server source changed during preparation")
    report = {"status": "STAGED; not installed or gameplay verified", "bosses": BOSSES,
              "classification": "HERO/VETERAN; native gauge 21 (Hero Grade 2)",
              "server": {"path": str(server_path), "sha256": server_hash},
              "input": {"path": str(source), "sha256": expected_hash.lower()},
              "output": {"path": str(target), "sha256": sha(target.read_bytes())},
              "nativeNpcCount": count, "changedNpcIds": sorted(BOSSES),
              "changedFields": ["hpgauge_level"], "entries": entries,
              "preparerSha256": sha(Path(__file__).read_bytes()),
              "codecSha256": sha((ROOT / "client-mods/expanded-warehouse/codec.py").read_bytes()),
              "limits": "Requested coherent boss classification, not certification of exact retail 4.8 stats or timings."}
    (output / "classification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("OK: two native gauges; all other NPC fields and archive payloads preserved; inputs unchanged")
    print(output / "classification.json")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--expected-client-sha256", required=True)
    args = parser.parse_args()
    prepare(args.client, args.output, args.expected_client_sha256)
