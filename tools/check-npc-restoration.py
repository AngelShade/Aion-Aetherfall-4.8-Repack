"""Require original native NPC behavior against the normal pre-patrol build.

Added compatibility methods may remain, but every original native method must
retain its original executable instructions. No World, IDs or server lifecycle.
"""
import hashlib,json,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'client-mods/playerbots'))
import stage_companion_update as shared

BASELINE=shared.DEV_ROOT/'staging/target/release-20261011-214044-035423/game-server/game-server-4.8-SNAPSHOT.jar'
BASELINE_SHA='2db067b4bd12ec3f7fd4ff79db1713e3dd0eb3b4c3fe894a3e53060fa0478849'
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
