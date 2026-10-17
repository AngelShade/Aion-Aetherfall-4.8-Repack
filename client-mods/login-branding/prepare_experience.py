"""Stage native Aetherfall lobby content, preserving current archives and recovery.

The original promotion browser object is retained for native controller custody,
but hidden. Its replacement is ordinary native widgets, not a remote web page.
One new DDS subtitle uses native image widgets; existing textures, executable,
login actions, character slots, and native hooks remain unchanged.
"""
import argparse
import copy
import hashlib
import io
import json
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "expanded-warehouse"))
from codec import binary_xml, encode_binary_xml, encode_pak, read_pak

STRING_IDS = {name: str(990100100 + index) for index, name in enumerate([
    "aetherfall_wordmark", "aetherfall_panel_title", "aetherfall_panel_subtitle",
    "aetherfall_panel_invitation", "aetherfall_heading_0", "aetherfall_body_0",
    "aetherfall_heading_1", "aetherfall_body_1", "aetherfall_heading_2",
    "aetherfall_body_2", "aetherfall_panel_footer",
]) if name != "aetherfall_wordmark"}
LABELS = {}
LOGO = json.loads((HERE / "assets/aetherfall-subtitle.json").read_text(encoding="utf-8"))


def string_key(name):
    assert name in STRING_IDS, name
    return "STR_PRIVATE_" + name.upper()


def sha(data):
    return hashlib.sha256(data).hexdigest()


def label(parent, name, frame, text, size=14, color="0.87,0.90,0.93,1.0", align="left", wrap=False):
    key = string_key(name)
    if key in LABELS:
        assert LABELS[key] == text, key
    LABELS[key] = text
    return ET.SubElement(parent, "Widget", name=name, type="static",
        flag="transparent;not_active" + (";multiline;wordwrap" if wrap else ""),
        frame=frame, font="default", font_size=str(size), text=key,
        text_color=color, text_shade="True", halign=align, valign="middle")


def login(tree):
    dialog = tree.find(".//Dialog[@name='login_dialog']")
    if dialog is None or dialog.find(".//*[@name='remember_login']") is None:
        raise ValueError("Expected the installed native remembered-login layout")
    previous = dialog.find("Widget[@name='aetherfall_wordmark']")
    if previous is not None:
        assert previous.get("type") in {"static", "image"}
        dialog.remove(previous)
    # Both parts remain original image resources. The larger Aetherfall artwork
    # is centered below the smaller AION image and ends above account controls.
    banners = [e for e in dialog.findall("Widget")
        if e.find("Skin[@name='v5_lobby_aion_banner']") is not None]
    assert len(banners) == 1
    banners[0].set("frame", LOGO["aion_frame"])
    wordmark = ET.SubElement(dialog, "Widget", name="aetherfall_wordmark", type="image",
        flag="not_active", frame=LOGO["frame"], h_pos_align="center", v_pos_align="middle")
    ET.SubElement(wordmark, "Skin", name=LOGO["skin"], state="0")
    return tree


def stage_panel(tree):
    dialog = tree.find(".//Dialog[@name='select_char_promotion']")
    if dialog is None:
        raise ValueError("Native character-selection promotion panel missing")
    browser = dialog.find("Widget[@name='main_browser']")
    if browser is None or browser.get("type") != "browser":
        raise ValueError("Native promotion controller browser contract differs")
    previous = dialog.find("Widget[@name='aetherfall_atreia_panel']")
    if previous is not None:
        assert previous.get("type") == "static"
        dialog.remove(previous)
    # Keep the named browser/type: native code can retain and release it normally.
    # It supplies no visible content and no input surface in the replacement panel.
    browser.set("flag", "not_visible;not_active")
    panel = ET.SubElement(dialog, "Widget", name="aetherfall_atreia_panel",
        type="static", flag="not_active", frame="10,10,428,641")
    ET.SubElement(panel, "Skin", name="v5_static_bg4", state="0")
    label(panel, "aetherfall_panel_title", "18,17,392,28", "AION AETHERFALL", 23,
        "0.91,0.80,0.57,1.0", "center")
    label(panel, "aetherfall_panel_subtitle", "18,47,392,22", "TWO PEOPLES. ONE ATREIA.", 12,
        "0.62,0.86,0.92,1.0", "center")
    # Original client art, original aspect ratios, no extracted textures shipped.
    for name, frame, skin in [
        ("elyos", "8,80,206,188", "v5_create_race_light"),
        ("asmodians", "214,88,206,173", "v5_create_race_dark"),
    ]:
        image = ET.SubElement(panel, "Widget", name="aetherfall_art_" + name,
            type="image", flag="not_active", frame=frame)
        ET.SubElement(image, "Skin", name=skin, state="0")
    label(panel, "aetherfall_panel_invitation", "20,276,388,26", "YOUR ADVENTURE STARTS HERE", 16,
        "0.91,0.80,0.57,1.0", "center")
    sections = [
        (318, "COMPANIONS", "Build your party. Recruit companions and choose how they fight beside you."),
        (407, "CHOOSE YOUR JOURNEY", "Discover Poeta or Ishalgen, follow their stories, and explore the world beyond."),
        (496, "DUNGEONS & DISCOVERY", "Gather your allies, face challenging encounters, and discover the mysteries of Atreia."),
    ]
    for i, (y, heading, body) in enumerate(sections):
        label(panel, f"aetherfall_heading_{i}", f"28,{y},372,22", heading, 15,
            "0.62,0.86,0.92,1.0")
        label(panel, f"aetherfall_body_{i}", f"28,{y+25},372,49", body, 14, wrap=True)
    label(panel, "aetherfall_panel_footer", "28,598,372,23", "Spread your wings, Daeva.", 14,
        "0.91,0.80,0.57,1.0", "center")
    return tree


def register_strings(tree):
    assert tree.tag == "strings"
    assert len(LABELS) == len(STRING_IDS)
    for name, number in STRING_IDS.items():
        key = string_key(name)
        matches = [e for e in tree if e.findtext("name") == key or e.findtext("id") == number]
        if matches:
            assert len(matches) == 1
            assert matches[0].findtext("name") == key and matches[0].findtext("id") == number
            matches[0].find("body").text = LABELS[key]
            continue
        entry = ET.SubElement(tree, "string")
        ET.SubElement(entry, "id").text = number
        ET.SubElement(entry, "name").text = key
        ET.SubElement(entry, "body").text = LABELS[key]
    return tree


def archive(path, localized):
    keys = ("ui/ui_login.xml", "ui/ui_loginstage.xml", "ui/loginnotice.xml") if localized else (
        "UI_Login.xml", "ui_loginstage.xml", "loginnotice.xml")
    mutations = [login, stage_panel, None]
    changed = {}
    with read_pak(path) as source:
        preload = binary_xml(source.read("ui/ui_preload.xml" if localized else "UI_Preload.xml"))
        skins = {e.get("name"): e for e in preload.iter("Skin")}
        for name, rectangle in [("v5_create_race_light", "38,18,776,708"),
                                ("v5_create_race_dark", "47,35,839,704")]:
            assert skins[name].get("src_image") == rectangle, name
        assert "v5_static_bg4" in skins
        original_preload = copy.deepcopy(preload)
        category = preload.find("Category[@name='v5_lobby']")
        assert category is not None
        attrs = dict(name=LOGO["skin"], type="single", texture=LOGO["texture"], src_image=LOGO["src_image"])
        existing = category.find("Skin[@name='" + LOGO["skin"] + "']")
        if existing is None:
            ET.SubElement(category, "Skin", attrs)
            restored_preload = copy.deepcopy(preload)
            parent = restored_preload.find("Category[@name='v5_lobby']")
            parent.remove(parent.find("Skin[@name='" + LOGO["skin"] + "']"))
            assert ET.tostring(restored_preload) == ET.tostring(original_preload)
        else:
            assert existing.attrib == attrs
        changed["ui/ui_preload.xml" if localized else "UI_Preload.xml"] = encode_binary_xml(preload)
        for key, mutate in zip(keys, mutations):
            original = binary_xml(source.read(key))
            tree = copy.deepcopy(original)
            if mutate:
                mutate(tree)
            else:
                if tree.tag != "LoginNotice" or tree.find("Contents") is None:
                    raise ValueError("Expected native LoginNotice wrapper")
                tree.find("Contents").text = (HERE / "notice.html").read_text(encoding="utf-8").strip()
            # Independently remove only owned additions and restore the browser flag.
            restored = copy.deepcopy(tree)
            if mutate == login:
                dialog = restored.find(".//Dialog[@name='login_dialog']")
                dialog.remove(dialog.find("Widget[@name='aetherfall_wordmark']"))
                original_dialog = original.find(".//Dialog[@name='login_dialog']")
                previous = original_dialog.find("Widget[@name='aetherfall_wordmark']")
                if previous is not None:
                    dialog.insert(list(original_dialog).index(previous), copy.deepcopy(previous))
                original_banner = next(e for e in original_dialog.findall("Widget")
                    if e.find("Skin[@name='v5_lobby_aion_banner']") is not None)
                restored_banner = next(e for e in dialog.findall("Widget")
                    if e.find("Skin[@name='v5_lobby_aion_banner']") is not None)
                restored_banner.set("frame", original_banner.get("frame"))
            elif mutate == stage_panel:
                dialog = restored.find(".//Dialog[@name='select_char_promotion']")
                dialog.remove(dialog.find("Widget[@name='aetherfall_atreia_panel']"))
                original_dialog = original.find(".//Dialog[@name='select_char_promotion']")
                previous = original_dialog.find("Widget[@name='aetherfall_atreia_panel']")
                if previous is not None:
                    dialog.insert(list(original_dialog).index(previous), copy.deepcopy(previous))
                old_browser = original.find(".//Dialog[@name='select_char_promotion']/Widget[@name='main_browser']")
                restored_browser = dialog.find("Widget[@name='main_browser']")
                restored_browser.attrib.clear()
                restored_browser.attrib.update(old_browser.attrib)
            else:
                restored.find("Contents").text = original.find("Contents").text
            assert ET.tostring(restored) == ET.tostring(original), key
            changed[key] = encode_binary_xml(tree)
            assert ET.tostring(binary_xml(changed[key])) == ET.tostring(tree), key
        if localized:
            key = "strings/client_strings_ui.xml"
            original = binary_xml(source.read(key))
            tree = register_strings(copy.deepcopy(original))
            # Owned labels may receive editorial updates; all other strings and
            # each existing node's position, ID, attributes, and fields survive.
            restored_strings = copy.deepcopy(tree)
            for old, restored in zip(original, restored_strings):
                if old.findtext("name") in LABELS:
                    restored.find("body").text = old.findtext("body")
                assert ET.tostring(old) == ET.tostring(restored)
            changed[key] = encode_binary_xml(tree)
            strings = binary_xml(changed[key])
            for layout in keys[:2]:
                for widget in binary_xml(changed[layout]).iter("Widget"):
                    name = widget.get("name", "")
                    if name in STRING_IDS:
                        key = widget.get("text")
                        matches = [e for e in strings if e.findtext("name") == key]
                        assert key == string_key(name) and len(matches) == 1
                        assert matches[0].findtext("body") == LABELS[key]
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as target:
            for info in source.infolist():
                target.writestr(copy.copy(info), changed.get(info.filename, source.read(info.filename)))
        payload = bytes(encode_pak(stream.getvalue()))
        differences = tuple(key for key, data in changed.items() if data != source.read(key))
        return payload, differences, {name: sha(source.read(name)) for name in source.namelist()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root, out = args.client.resolve(), args.output.resolve()
    if out.exists() or out.is_relative_to(root):
        raise ValueError("Use fresh staging outside the client")
    out.mkdir(parents=True)
    staged, checks = {}, []

    def stage(relative, data):
        relative = Path(relative).as_posix()
        if relative in staged:
            assert staged[relative] == data, relative
            return
        target = out / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        staged[relative] = data

    logo_bytes = (HERE / "assets/aetherfall-subtitle.dds").read_bytes()
    assert sha(logo_bytes) == LOGO["sha256"]
    assert logo_bytes[:4] == b"DDS "
    stage(LOGO["path"], logo_bytes)

    def update(relative, localized):
        payload, keys, original = archive(root / relative, localized)
        stage(relative, payload)
        with read_pak(out / relative) as updated:
            assert list(original) == updated.namelist()
            assert updated.testzip() is None
            differences = [n for n, digest in original.items() if sha(updated.read(n)) != digest]
            assert set(differences) == set(keys), differences
            checks.append(dict(path=relative, entries=len(original), changed=sorted(differences)))

    for relative, localized in [("Data/ui/ui.pak", False), ("L10N/enu/Data/data.pak", True)]:
        update(relative, localized)

    # Compose with the existing graphics package and its real restore baseline.
    state_path = "Mods/GraphicsMenu/installed.json"
    manifest_path = "Mods/GraphicsMenu/package/manifest.json"
    state = json.loads((root / state_path).read_text(encoding="utf-8-sig"))
    manifest = json.loads((root / manifest_path).read_text(encoding="utf-8-sig"))
    assert state["files"] == manifest["files"]
    entry = next(e for e in state["files"] if e["path"].lower() == "l10n/enu/data/data.pak")
    assert sha((root / entry["path"]).read_bytes()) == entry["installed"]
    baseline = (Path(state["backupRoot"]) / entry["path"]).resolve()
    assert baseline.is_relative_to(root / "Mods/Backups/Renderer")
    assert sha(baseline.read_bytes()) == entry["original"]
    baseline_relative = baseline.relative_to(root).as_posix()
    update(baseline_relative, True)
    entry.update(original=sha(staged[baseline_relative]), installed=sha(staged[entry["path"]]))
    stage("Mods/GraphicsMenu/package/" + entry["path"], staged[entry["path"]])
    manifest["files"] = copy.deepcopy(state["files"])
    stage(state_path, (json.dumps(state, indent=2) + "\n").encode())
    stage(manifest_path, (json.dumps(manifest, indent=2) + "\n").encode())
    receipt = dict(feature="aetherfall-native-lobby-image", localizedLabels=len(LABELS), logo=LOGO, clientRoot=str(root),
        archiveChecks=checks, files=[dict(path=rel,
        before=sha((root / rel).read_bytes()) if (root / rel).is_file() else None, after=sha(data)) for rel, data in staged.items()],
        executableChanges=0, existingTextureChanges=0,
        addedTextures=int(not (root / LOGO["path"]).is_file()), gameplayVerification="User after launch")
    (out / "manifest.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
    print(json.dumps(dict(stagedFiles=len(staged), archives=checks, output=str(out))))


if __name__ == "__main__":
    main()
