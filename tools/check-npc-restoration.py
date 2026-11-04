"""Preserve reviewed native NPC behavior against the installed normal build.

Added compatibility methods may remain, but every original native method must
retain its original executable instructions. No World, IDs or server lifecycle.
"""
import hashlib,json,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'client-mods/playerbots'))
import stage_companion_update as shared

# Receipted Warden normal output, installed 2026-11-04. The twelve changes
# since the pre-patrol baseline are committed in b233e8bc45, 725851b0a9,
# and upstream 7840be30d9/ca2155e6dc (imported by 6a36f85611).
# Pin every method in all five classes, including their compatibility methods.
BASELINE=shared.DEV_ROOT/'staging/target/npc-preservation-baseline-20261104/game-server/game-server-4.8-SNAPSHOT.jar'
BASELINE_SHA='6144b2a2681999e9c4bca187412421078c61a72f656d2053295cb0a0f9fbdad0'
CLASSES=['controllers/movement/NpcMoveController','spawnengine/ClusteredNpc','spawnengine/WalkerGroup','ai/manager/WalkManager','world/geo/GeoService']

def verify(build):
    assert shared.sha(BASELINE)==BASELINE_SHA,'Original normal NPC baseline changed'
    jar=Path(build)/'game-server/game-server-4.8-SNAPSHOT.jar'
    result={}
    for name in CLASSES:
        name='com/aionemu/gameserver/'+name
        before=shared.methods(BASELINE,name);after=shared.methods(jar,name)
        assert before and all(key in after and code==after[key] for key,code in before.items()),'Original NPC method changed: '+name
        result[name]=dict(originalMethods=len(before),retainedCompatibilityMethods=sorted(after.keys()-before.keys()))
    return dict(baselineSha256=BASELINE_SHA,gameServerSha256=shared.sha(jar),classes=result)
