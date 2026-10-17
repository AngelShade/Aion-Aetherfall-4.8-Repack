"""Guarded client-first delivery of the reviewed paired boss classification.

Run the canonical server --review --install immediately afterwards, keeping
GameServer and Aion closed between both copies. This tool never controls either
component and never rewrites earlier server receipts.
"""
import argparse
import copy
from datetime import datetime, timezone
import hashlib
import importlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
import zipfile
from contextlib import contextmanager

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "client-mods/playerbots"))
import stage_companion_update as shared
from remove_follow_recovery_offline import stopped

spec = importlib.util.spec_from_file_location("boss_classification_prepare", Path(__file__).with_name("prepare.py"))
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)
require = prepare.require
NPC_SOURCE = "game-server/data/static_data/npcs/npc_templates.xml"
NPC_MEMBER = "game-server/data/static_data/npcs/npc_templates.xml"
INVENTORY = ROOT / "docs/INSTALLED_MODS.json"


def sha(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def key(path):
    return path.replace("\\", "/").casefold()


def atomic_bytes(target, payload):
    """Same-directory replacement, with the temporary file removed on failure."""
    descriptor, temporary = tempfile.mkstemp(prefix=".boss-classification-", dir=target.parent)
    temporary = Path(temporary)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, target)
    finally:
        if temporary.exists():
            temporary.unlink()


@contextmanager
def release_lock():
    # Use the same exclusion lock as tools/release-game-server.py.
    import msvcrt
    path = shared.DEV_ROOT / "tooling/locks/game-server-release.lock"
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a+b") as stream:
        if path.stat().st_size == 0:
            stream.write(b"0")
            stream.flush()
        stream.seek(0)
        msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
        try:
            yield
        finally:
            stream.seek(0)
            msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)


def client_guards(inventory, client, npc_hash=None):
    verifier = importlib.import_module("verify-source-delivery")
    entries = verifier.protected_client_files(inventory["clientFiles"])
    paths = [key(entry["path"]) for entry in entries]
    require(len(paths) == len(set(paths)) and entries, "Duplicate or missing client guards")
    for entry in entries:
        relative = Path(entry["path"].replace("\\", "/"))
        require(not relative.is_absolute() and ".." not in relative.parts, "Invalid client guard path")
        expected = npc_hash if key(entry["path"]) == key(str(prepare.ARCHIVE)) and npc_hash else entry["sha256"]
        require(sha(client / relative) == expected, "Client guard changed: " + entry["path"])


def verify_review(review, stage_report):
    build = Path(read(review / "release-context.json")["build"]).resolve(strict=True)
    shared.validate_output(build)
    build_manifest = importlib.import_module("build-manifest").verify(build)
    require("BUILD SUCCESS" in (build / "maven.log").read_text(), "Normal Maven build did not succeed")
    importlib.import_module("check-source-build").validate(build, review / "regression")
    importlib.import_module("check-source-browser").validate(build, review / "browser")
    plan = read(review / "release-plan.json")
    delivery = read(review / "delivery/manifest.json")
    require(delivery["feature"] == "playerbots-source-build" and delivery["scope"] == plan["scope"], "Wrong canonical review")
    require(Path(delivery["deployment"]).resolve() == ROOT / "target-deploy/game-server", "Wrong runtime review")
    require(delivery["buildManifestSha256"] == plan["buildManifestSha256"] == sha(build / "build-manifest.json"), "Mixed build review")
    require(delivery["releasePlanSha256"] == sha(review / "release-plan.json"), "Release plan changed")
    for folder, field in [("regression", "regressionEvidenceSha256"), ("browser", "browserEvidenceSha256")]:
        require(sha(review / folder / "evidence.json") == delivery[field], "Canonical proof changed: " + folder)
    require(plan["handlerCount"] > 0 and plan["handlerCount"] == delivery["handlerCount"]
            and plan["handlerCompile"].startswith("passed"), "Native script compilation is incomplete")
    require(delivery["fullRuntimeLinkage"].startswith("OK:"), "Missing canonical linkage proof")
    require(delivery["fullRuntimeLinkage"] in (review / "delivery/linkage.txt").read_text(), "Canonical linkage report changed")
    require(delivery["preservedJarEntries"] > 0 and delivery["classComparison"], "Missing preservation review")
    static = review / "static-check"
    evidence = read(static / "static-data-load.json")
    require(evidence["reportSha256"] == sha(static / "static-data-load.txt"), "Static report changed")
    require("OK: complete static XML merge, schema and production JAXB load;" in (static / "static-data-load.txt").read_text(), "Static load did not pass")
    require(evidence["builderJarSha256"] == sha(build / "game-server/game-server-4.8-SNAPSHOT.jar"), "Static proof belongs to another JAR")
    require(evidence["questCoverageSha256"] == delivery["questCoverageSha256"] == sha(static / "quest-coverage.json"), "Native quest proof changed")
    require(evidence["handlerInventoryToolSha256"] == sha(ROOT / "tools/quest-handler-inventory.py"), "Native quest checker changed")
    server = ROOT / "target-deploy/game-server"
    country = re.search(r"^\s*gameserver\.country\.code\s*=\s*(\d+)", (server / "config/main/gameserver.properties").read_text(), re.MULTILINE)
    require(country and int(country.group(1)) == evidence["countryCode"], "Runtime country changed")
    for relative, digest in evidence["unchangedRuntimeInputs"].items():
        require(sha(server / relative) == digest, "Runtime static input changed: " + relative)
    with zipfile.ZipFile(build / "game-server/game-server.zip") as archive:
        for relative, digest in evidence["builderResources"].items():
            data = archive.read("game-server/" + relative)
            require(hashlib.sha256(data).hexdigest() == digest and data == (ROOT / "game-server" / relative).read_bytes(), "Builder static resource changed: " + relative)
        npc_bytes = archive.read(NPC_MEMBER)
    require(npc_bytes == (ROOT / NPC_SOURCE).read_bytes(), "Builder NPC data differs from maintained source")
    require(hashlib.sha256(npc_bytes).hexdigest() == stage_report["server"]["sha256"]
            == build_manifest["inputs"][NPC_SOURCE], "Client stage and normal server NPC data disagree")
    prior = shared.receipt_paths(server, "playerbots-source-build-*/manifest.json")[-1]
    require(str(prior) == plan["priorReceipt"] == delivery["previousReceipt"], "A newer server was delivered after this review")
    game = next(entry for entry in delivery["files"] if entry["path"] == "libs/game-server-4.8-SNAPSHOT.jar")
    require(sha(server / game["path"]) == game["original"], "Server baseline changed before paired client copy")
    for entry in delivery["guardFiles"]:
        require(sha(server / entry["path"]) == entry["installed"], "Server preservation guard changed: " + entry["path"])
    for entry in delivery["files"]:
        target = server / entry["path"]
        require((not target.exists() and not target.is_symlink()) if entry["original"] is None
                else target.is_file() and sha(target) == entry["original"], "Server replacement baseline changed: " + entry["path"])
    return build, delivery


def verify_stage(stage, report, client):
    target = client / prepare.ARCHIVE
    candidate = stage / "client" / prepare.ARCHIVE
    require(Path(report["input"]["path"]).resolve() == target.resolve(), "Wrong original client archive")
    require(Path(report["output"]["path"]).resolve() == candidate.resolve(), "Candidate must be inside this stage")
    require(Path(report["server"]["path"]).resolve() == ROOT / NPC_SOURCE, "Wrong server source binding")
    require(set(report["changedNpcIds"]) == set(prepare.BOSSES) and report["changedFields"] == ["hpgauge_level"], "Wrong patch scope")
    require(report["preparerSha256"] == sha(Path(prepare.__file__)), "Client preparer changed after staging")
    require(report["codecSha256"] == sha(ROOT / "client-mods/expanded-warehouse/codec.py"), "Client codec changed after staging")
    require(sha(target) == report["input"]["sha256"] and sha(candidate) == report["output"]["sha256"], "Client input or stage changed")
    rows = report["entries"]
    require(len(rows) == len({row["name"] for row in rows}), "Duplicate staged archive entry")
    changed = [row["name"] for row in rows if row["before"] != row["after"]]
    require(changed == [prepare.ENTRY], "Unexpected changed archive entries")
    with prepare.read_pak(target) as original, prepare.read_pak(candidate) as packaged:
        require(original.namelist() == packaged.namelist() == [row["name"] for row in rows], "Archive entry list changed")
        require(original.comment == packaged.comment, "Archive comment changed")
        for row in rows:
            require(hashlib.sha256(original.read(row["name"])).hexdigest() == row["before"], "Original archive payload changed")
            require(hashlib.sha256(packaged.read(row["name"])).hexdigest() == row["after"], "Staged archive payload changed")
        count = prepare.validate_change(original.read(prepare.ENTRY), packaged.read(prepare.ENTRY))
        require(count == report["nativeNpcCount"], "Native NPC semantic proof changed")
    prepare.validate_server()
    return target, candidate


def install(stage, review, backup):
    stage, review, backup = stage.resolve(strict=True), review.resolve(strict=True), backup.resolve()
    shared.validate_output(stage)
    shared.validate_output(review)
    require(backup.is_relative_to(shared.DEV_ROOT / "archives") and not backup.exists(), "Use a fresh external archive backup")
    for protected in (stage, review):
        require(not backup.is_relative_to(protected) and not protected.is_relative_to(backup), "Backup overlaps release inputs")
    report_path = stage / "classification.json"
    report_bytes = report_path.read_bytes()
    report = json.loads(report_bytes)
    inventory_bytes = INVENTORY.read_bytes()
    inventory = json.loads(inventory_bytes)
    client = Path(inventory["clientRoot"]).resolve(strict=True)
    require(client == shared.CLIENT_ROOT and Path(inventory["serverRoot"]).resolve() == ROOT / "target-deploy/game-server", "Wrong installed inventory roots")
    require(inventory["checks"] and all(inventory["checks"].values()), "Installed mod inventory is not verified")
    client_guards(inventory, client)
    target, candidate = verify_stage(stage, report, client)
    build, delivery = verify_review(review, report)
    verifier = importlib.import_module("verify-source-delivery")
    verifier.verify_client_files(client, delivery["clientFiles"])
    server_jar = ROOT / "target-deploy/game-server/libs/game-server-4.8-SNAPSHOT.jar"
    require(inventory["serverJarSha256"] == sha(server_jar), "Inventory server baseline changed")
    new_inventory = copy.deepcopy(inventory)
    matches = [entry for entry in new_inventory["clientFiles"] if key(entry["path"]) == key(str(prepare.ARCHIVE))]
    require(len(matches) <= 1, "Duplicate NPC archive inventory guard")
    if matches:
        matches[0]["sha256"] = report["output"]["sha256"]
    else:
        new_inventory["clientFiles"].append({"path": prepare.ARCHIVE.as_posix(), "sha256": report["output"]["sha256"]})
    new_inventory_bytes = (json.dumps(new_inventory, indent=2) + "\n").encode("utf-8")
    bindings = {str(path): sha(path) for path in [report_path, build / "build-manifest.json", review / "release-plan.json",
                review / "delivery/manifest.json", review / "regression/evidence.json", review / "browser/evidence.json",
                review / "static-check/static-data-load.json", ROOT / NPC_SOURCE, Path(__file__), Path(prepare.__file__),
                ROOT / "client-mods/expanded-warehouse/codec.py"]}
    def guard():
        stopped()
        require(all(sha(Path(path)) == digest for path, digest in bindings.items()), "Paired release binding changed")
        require(INVENTORY.read_bytes() == inventory_bytes, "Inventory changed before client copy")
        require(sha(target) == report["input"]["sha256"] and sha(candidate) == report["output"]["sha256"], "Original or candidate changed")
        require(sha(server_jar) == inventory["serverJarSha256"], "Server changed before client copy")
        client_guards(inventory, client)
    guard()
    backup.mkdir(parents=True)
    archive_backup = backup / prepare.ARCHIVE
    archive_backup.parent.mkdir(parents=True)
    inventory_backup = backup / "INSTALLED_MODS.json"
    shutil.copy2(target, archive_backup)
    shutil.copy2(INVENTORY, inventory_backup)
    require(sha(archive_backup) == report["input"]["sha256"] and inventory_backup.read_bytes() == inventory_bytes, "Recovery backup verification failed")
    shutil.copy2(report_path, backup / "classification.json")
    receipt = {"status": "PREPARED", "scope": delivery["scope"], "stage": str(stage), "review": str(review),
               "backup": str(backup), "createdUtc": datetime.now(timezone.utc).isoformat(), "bindings": bindings,
               "archive": {"path": str(target), "original": report["input"]["sha256"], "installed": report["output"]["sha256"]},
               "inventory": {"path": str(INVENTORY), "original": hashlib.sha256(inventory_bytes).hexdigest(),
                             "installed": hashlib.sha256(new_inventory_bytes).hexdigest()},
               "serverJarOriginal": inventory["serverJarSha256"], "builderJarSha256": sha(build / "game-server/game-server-4.8-SNAPSHOT.jar"),
               "next": "Keep GameServer and Aion closed; run canonical server --review --install before starting either component."}
    (backup / "prepared.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    try:
        guard()
        atomic_bytes(target, candidate.read_bytes())
        require(sha(target) == report["output"]["sha256"], "Client archive replacement failed verification")
        client_guards(inventory, client, report["output"]["sha256"])
        stopped()
        require(INVENTORY.read_bytes() == inventory_bytes, "Inventory changed during client copy")
        atomic_bytes(INVENTORY, new_inventory_bytes)
        require(INVENTORY.read_bytes() == new_inventory_bytes, "Updated inventory failed verification")
        client_guards(new_inventory, client)
        require(sha(server_jar) == inventory["serverJarSha256"], "Server changed during client copy")
        stopped()
        receipt["status"] = "CLIENT_INSTALLED_AWAITING_CANONICAL_SERVER_COPY"
        atomic_bytes(backup / "receipt.json", (json.dumps(receipt, indent=2) + "\n").encode("utf-8"))
    except BaseException as error:
        failures = []
        try:
            stopped()
            current_archive = sha(target)
            require(current_archive in {report["input"]["sha256"], report["output"]["sha256"]}, "Cannot overwrite an unexpected newer NPC archive during rollback")
            if current_archive == report["output"]["sha256"]:
                atomic_bytes(target, archive_backup.read_bytes())
            current_inventory = INVENTORY.read_bytes()
            require(current_inventory in (inventory_bytes, new_inventory_bytes), "Cannot overwrite an unexpected newer inventory during rollback")
            if current_inventory == new_inventory_bytes:
                atomic_bytes(INVENTORY, inventory_backup.read_bytes())
            require(sha(target) == report["input"]["sha256"] and INVENTORY.read_bytes() == inventory_bytes, "Rollback hash mismatch")
        except BaseException as rollback_error:
            failures.append(str(rollback_error))
        receipt.update(status="FAILED_ROLLBACK_INCOMPLETE" if failures else "FAILED_ROLLED_BACK",
                       error=str(error), rollbackErrors=failures)
        (backup / "failed.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
        if failures:
            raise RuntimeError("Client delivery failed; rollback requires closed components/review. Recovery: " + str(backup)) from error
        raise
    print("OK: client archive and inventory delivered; all other client guards preserved. Receipt:", backup / "receipt.json")
    print("KEEP GAMESERVER AND AION CLOSED until the canonical server --review --install completes.")


def main():
    if not __debug__:
        raise RuntimeError("Delivery validation cannot run with Python optimization enabled")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", type=Path, required=True)
    parser.add_argument("--review", type=Path, required=True)
    parser.add_argument("--backup", type=Path, required=True)
    args = parser.parse_args()
    with release_lock():
        install(args.stage, args.review, args.backup)


if __name__ == "__main__":
    main()
