"""Install a staged native lobby revision with backups and exact hash guards.

Only the selected closed client is changed. A repack's existing file manifest is
updated in the same transaction; native binaries and unrelated entries survive.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", required=True, type=Path)
    parser.add_argument("--backup", required=True, type=Path)
    args = parser.parse_args()
    stage, backup = args.stage.resolve(), args.backup.resolve()
    revision = read(stage / "manifest.json")
    assert revision["feature"] == "aetherfall-native-lobby-image"
    assert revision["localizedLabels"] == 10
    client = Path(revision["clientRoot"]).resolve()
    assert not stage.is_relative_to(client) and not backup.is_relative_to(client)
    assert not backup.exists()
    state = read(client / "DXVK/graphics-menu/installed.json")
    baseline = (Path(state["backupRoot"]) / "L10N/enu/Data/data.pak").resolve()
    assert baseline.is_relative_to(client / "DXVK-backups")
    expected = {"Data/ui/ui.pak", "L10N/enu/Data/data.pak",
        "Textures/ui/Aetherfall/aetherfall_logo.dds",
        "DXVK/graphics-menu/package/L10N/enu/Data/data.pak",
        "DXVK/graphics-menu/installed.json", "DXVK/graphics-menu/package/manifest.json",
        baseline.relative_to(client).as_posix()}
    assert {e["path"] for e in revision["files"]} == expected
    for entry in revision["files"]:
        rel = entry["path"]
        assert (client / rel).resolve().is_relative_to(client)
        assert (stage / rel).resolve().is_relative_to(stage)
        assert (sha(client / rel) if (client / rel).is_file() else None) == entry["before"], rel
        assert sha(stage / rel) == entry["after"], rel

    result = subprocess.run(["powershell.exe", "-NoProfile", "-Command",
        "$ErrorActionPreference='Stop'; @(Get-CimInstance Win32_Process -Filter "
        "\"Name='aion.bin' OR Name='aion.exe'\" | Select-Object ProcessId,ExecutablePath) | ConvertTo-Json -Compress"],
        capture_output=True, text=True, check=True)
    rows = json.loads(result.stdout.strip() or "[]")
    if isinstance(rows, dict):
        rows = [rows]
    for row in rows:
        assert row.get("ExecutablePath"), "Cannot identify a running Aion process"
        assert not Path(row["ExecutablePath"]).resolve().is_relative_to(client), "Close this Aion client normally"

    protected = ["bin64/Game.dll", "bin64/crysystem.dll", "bin64/AionRememberLogin.dll",
        "bin64/AionIconBridge.dll", "bin64/AionGraphicsMenu.dll", "bin64/AionMarketShortcut.dll",
        "bin64/XRenderD3D9.dll", "Data/ui/game/game.pak", "Textures/ui/ui.pak",
        "Plugin/RelicCalc/RelicCalc.pak", "Plugin/RelicCalc/RelicCalc.pak.sig", "Pub.key", "Addon.key"]
    preserved = {rel: sha(client / rel) for rel in protected}
    writes = {client / e["path"]: (stage / e["path"]).read_bytes() for e in revision["files"]}
    repack = client.parent
    if client.name == "Client" and (repack / "REPACK.json").is_file():
        manifest = read(repack / "FILES.json")
        old_entries = {e["path"]: dict(e) for e in manifest["files"]}
        canonical = {p.casefold(): p for p in old_entries}
        assert len(canonical) == len(manifest["files"])
        release = read(repack / "REPACK.json")
        assert release["name"] == "Aion Aetherfall"
        release["clientLobby"] = "Original AION image with matching Aetherfall image subtitle; native English news and adventure panel"
        allowed = {"REPACK.json"}
        for entry in revision["files"]:
            path = canonical.get(("Client/" + entry["path"]).casefold(), "Client/" + entry["path"])
            if path in old_entries:
                assert old_entries[path]["sha256"] == entry["before"], path
            else:
                assert entry["before"] is None
                manifest["files"].append(dict(path=path, sha256=entry["after"], bytes=len(writes[client / entry["path"]]), mutable=False))
            allowed.add(path)
            release["transforms"].append(dict(path=path, sourceSha256=entry["before"],
                sha256=entry["after"], reason="Native lobby localization and original-client parity"))
        writes[repack / "REPACK.json"] = (json.dumps(release, indent=2) + "\n").encode()
        for entry in manifest["files"]:
            if entry["path"] in allowed:
                data = writes[repack / entry["path"]]
                entry.update(sha256=hashlib.sha256(data).hexdigest(), bytes=len(data))
        assert all(e == old_entries[e["path"]] for e in manifest["files"] if e["path"] not in allowed)
        writes[repack / "FILES.json"] = json.dumps(manifest, separators=(",", ":")).encode()

    backup.mkdir(parents=True, exist_ok=False)
    owner = repack if (repack / "REPACK.json").is_file() else client
    for path in writes:
        target = backup / path.relative_to(owner)
        target.parent.mkdir(parents=True, exist_ok=True)
        if path.is_file():
            shutil.copy2(path, target)
    shutil.copy2(stage / "manifest.json", backup / "change-manifest.json")
    written = []
    try:
        for path, data in writes.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            temporary = path.with_name(path.name + ".aetherfall-" + backup.name)
            assert not temporary.exists()
            temporary.write_bytes(data)
            os.replace(temporary, path)
            written.append(path)
        for entry in revision["files"]:
            assert sha(client / entry["path"]) == entry["after"]
        for rel, digest in preserved.items():
            assert sha(client / rel) == digest, rel
    except BaseException:
        for path in reversed(written):
            previous = backup / path.relative_to(owner)
            if previous.is_file():
                shutil.copy2(previous, path)
            else:
                assert path.resolve().is_relative_to(client)
                path.unlink()
        raise
    receipt = dict(status="installed", feature=revision["feature"], clientRoot=str(client),
        sourceStage=str(stage), backup=str(backup), files=revision["files"],
        localizedLabels=revision["localizedLabels"], archiveChecks=revision["archiveChecks"],
        preservedFiles=[dict(path=rel, sha256=digest) for rel, digest in preserved.items()],
        installedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
        startup="No components started, stopped or attached", visualAcceptance="User fresh client launch")
    (backup / "receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
    print(json.dumps(dict(status="installed", client=str(client), backup=str(backup),
        localizedLabels=revision["localizedLabels"], protectedFiles=len(preserved), deliveredFiles=len(writes))))


if __name__ == "__main__":
    main()
