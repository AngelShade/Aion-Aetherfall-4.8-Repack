"""Load each feature with Core and only its declared transitive dependencies."""
import argparse
import os
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent
if os.environ.get('AION_LUA_TEST_DEPS'):
    sys.path.insert(0, os.environ['AION_LUA_TEST_DEPS'])
sys.path.insert(0, str(SOURCE))
sys.path.insert(0, str(HERE / 'trace'))
from lua_package import load_order
from lint import module_spec, tokens
from runtime import Runtime


class Recorder:
    scenario = 'isolated'

    def __init__(self):
        self.calls = []

    def write(self, kind, name, args=(), result=()):
        self.calls.append((kind, name, args, result))


def fresh(client, core):
    recorder = Recorder()
    runtime = Runtime(client, recorder, framework_only=True)
    runtime.lua.execute('debug.sethook()')
    for source in core:
        runtime.load(source, (SOURCE / source).read_bytes())
    for source, _ in load_order(SOURCE):
        if source.startswith(('datatexts/', 'tags/')):
            runtime.load(source, (SOURCE / source).read_bytes())
    runtime.lua.execute('AF.Native:SetResources(AFUI_DESIGN,AFUI_MENU,AFUI_LAZY)')
    assert runtime.g.AFUI is None and runtime.g.AFHUD is None, 'isolation must exclude Compat'
    return runtime, recorder


def closure(owner, specs):
    visiting, included = set(), set()

    def visit(name):
        if name in visiting:
            raise AssertionError('dependency cycle: ' + name)
        if name in included:
            return
        if name not in specs:
            raise AssertionError('missing dependency: ' + name)
        visiting.add(name)
        for dependency in specs[name][1]:
            visit(dependency)
        visiting.remove(name)
        included.add(name)

    visit(owner)
    return included


def core_contract(client, core):
    runtime, _ = fresh(client, core)
    runtime.lua.execute('''
        local bad=AF:NewModule('IsolatedFailure')
        AF.DB:RegisterTemplate('IsolationTemplate',{count={2,'int',1,4,1}})
        local good=AF:NewModule('IsolatedSurvivor',{defaults={amount={2,'int',1,4,1},
            personal={1,'int',1,4,1,scope='character'}, shared={1,'int',1,4,1,scope='account'}},
            instances={first={template='IsolationTemplate'},second={template='IsolationTemplate',defaults={count=3}}}})
        local badTicks,goodTicks=0,0
        bad:Every(1,function() badTicks=badTicks+1 end)
        good:Every(1,function() goodTicks=goodTicks+1 end)
        function bad:Explode() error('isolated failure probe') end
        function good:Count() return goodTicks end
        AF:Start()
        assert(not pcall(function() good.db.amount=3 end),'settings must be read-only')
        assert(not pcall(function() AF.DB:Set('IsolatedSurvivor','amount',99) end),'schema must reject invalid settings')
        AF.DB:Set('IsolatedSurvivor','amount',3)
        assert(good.db.amount==3)
        assert(good.db.first.count==2 and good.db.second.count==3,'template instance defaults leaked')
        assert(not pcall(function() good.db.first.count=4 end),'instance view is mutable')
        AF.DB:Set('IsolatedSurvivor','first.count',4)
        assert(good.db.first.count==4 and good.db.second.count==3,'instance write changed its sibling')
        assert(AF.DB:Scope('IsolatedSurvivor','amount')=='profile')
        assert(AF.DB:Scope('IsolatedSurvivor','personal')=='character')
        assert(AF.DB:Scope('IsolatedSurvivor','shared')=='account')
        assert(AF.DB:SetModuleScope('IsolatedSurvivor','character'))
        assert(AF.DB:Scope('IsolatedSurvivor','amount')=='character' and good.db.amount==3)
        assert(AF.DB:Scope('IsolatedSurvivor','shared')=='account','module scope overrode explicit account scope')
        local public=AF:GetModule('IsolatedSurvivor')
        assert(not pcall(function() return public.db end),'module state leaked through GetModule')
        assert(not pcall(function() public.extra=1 end),'module interface must be read-only')
        AF:Call('IsolatedFailure','Explode')
        assert(not AF:IsFailed('IsolatedFailure'),'one transient error must not disable a module')
        AF:Call('IsolatedFailure','Explode'); AF:Call('IsolatedFailure','Explode')
        assert(AF:IsFailed('IsolatedFailure'))
        AF.Events:Advance(1)
        assert(badTicks==0,'failed module retained a timer')
        assert(public:Count()==1,'another module stopped after its neighbour failed')
    ''')


def exercise(client, owner, specs, module_order, core, elements):
    included = closure(owner, specs)
    runtime, recorder = fresh(client, core)
    for name in module_order:
        if name in included:
            source = specs[name][0]
            runtime.load(source, (SOURCE / source).read_bytes())
            for source in elements.get(name, ()):
                runtime.load(source, (SOURCE / source).read_bytes())
    runtime.character = 'IsolatedCharacter'
    runtime.lua.execute('''
        AF:Start()
        for _,name in ipairs(AF:Names()) do AF:Call(name,'OnLoad') end
        AF:Enable()
        AF.Events:Client('PLAYER_ENTERING_WORLD')
        for tick=1,45 do AF.Events:Client('ADDON_TIMER'); AF.Events:Advance(tick) end
    ''')
    runtime.g.ISOLATED_OWNER = owner
    # Public setting writes must work without another feature's implementation
    # object or a mutable compatibility table supplied by the test.
    runtime.lua.execute('''
        local changed={}
        for _,old in AF.DB:Settings() do
            local owner,key=AF.DB:Owner(old)
            if owner==ISOLATED_OWNER and not changed[key] then
                local rule=AF.DB:Rule(owner,key)
                AF.DB:Set(owner,key,rule[1]);changed[key]=true
            end
        end
        AF:Call(ISOLATED_OWNER,'OnProfile')
        for _,name in ipairs(AF:Names()) do assert(not AF:IsFailed(name) and not AF:Error(name),'failed module: '..name..': '..tostring(AF:Error(name))) end
        AF:Disable()
        for _,name in ipairs(AF:Names()) do assert(not AF:IsFailed(name),'disable failed module: '..name) end
        assert(AFUI==nil and AFHUD==nil,'a feature created a legacy compatibility table')
    ''')
    return len(included), len(recorder.calls)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    args = parser.parse_args()
    order = load_order(SOURCE)
    core = [source for source, _ in order if source.startswith('core/')]
    specs, module_order, elements = {}, [], {}
    for source, _ in order:
        if Path(source).parent.as_posix() == 'modules':
            owner, needs = module_spec(tokens((SOURCE / source).read_text(encoding='utf-8-sig')))
            if owner in specs:
                raise SystemExit('Duplicate module: ' + owner)
            specs[owner] = source, needs
            module_order.append(owner)
        elif Path(source).parent.as_posix() == 'modules/unitframes':
            elements.setdefault('UnitFrames', []).append(source)
        elif source.startswith('modules/'):
            raise SystemExit('Undeclared module provider: ' + source)
    if elements and 'UnitFrames' not in specs:
        raise SystemExit('UnitFrames elements have no owning module')
    if not specs or not core:
        raise SystemExit('The manifest must contain Core and feature modules')
    failures = []
    try:
        core_contract(args.client, core)
        print('PASS: Core state isolation, schema validation and failure containment')
    except Exception as error:
        failures.append('Core')
        print('FAIL: Core:', error)
    for owner in module_order:
        try:
            count, calls = exercise(args.client, owner, specs, module_order, core, elements)
            print(f'PASS: {owner} with {count - 1} declared dependencies ({calls} recorded boundary calls)')
        except Exception as error:
            failures.append(owner)
            print(f'FAIL: {owner}: {error}')
    if failures:
        raise SystemExit('FAILED isolation: ' + ', '.join(failures))
    print(f'PASS: {len(specs)} modules without Compat or undeclared feature implementations')


if __name__ == '__main__':
    main()
