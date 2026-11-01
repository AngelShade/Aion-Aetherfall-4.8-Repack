"""Run the real nativeDragTick from AetherfallOptions.lua on scripted cursor and window paths."""
from pathlib import Path
import os
import re
import sys

HERE = Path(__file__).resolve().parent
if os.environ.get('AION_LUA_TEST_DEPS'):
    sys.path.insert(0, os.environ['AION_LUA_TEST_DEPS'])
sys.path.insert(0, str(HERE.parents[1] / 'game-server/target/native-wardrobe-test-deps'))
from lupa.lua51 import LuaRuntime  # noqa: E402

PLUGIN_FIRST = 200
TRACKER = 186
SCREEN = (1920, 1080)
WIDTH, HEIGHT = 258.75, 756.0


def tick_source():
    text = (HERE / 'AetherfallOptions.lua').read_text(encoding='utf-8')
    match = re.search(r'local function nativeDragTick\(\).*?\n    U\.nativeDown=down\nend\n', text.replace('\r\n', '\n'), re.S)
    assert match, 'nativeDragTick not found'
    return match.group(0)


def drag(path):
    """path: (cursor x, cursor y, down, window x, window y, height) per tick. Returns the saved spot, or None."""
    lua = LuaRuntime(unpack_returned_tuples=True)
    g = lua.globals()
    state = dict(row=path[0], saved=None)

    def cursor(*_):
        return state['row'][0], state['row'][1], state['row'][2]

    def window(id):
        if id != TRACKER:
            return None
        row = state['row']
        return lua.table(name='quest_indicator_dialog', x=row[3], y=row[4], w=WIDTH, h=row[5], shown=True, flags=8, instance=1)

    def remember(id, d, x, y, sw, sh):
        state['saved'] = (x / sw, y / sh)

    g.AFCore = lua.table(Cursor=cursor)
    g.dialog, g.remember = window, remember
    g.screen = lambda: SCREEN
    g.PLUGIN_FIRST = PLUGIN_FIRST
    g.U = lua.table(settled=True, unlocked=False, nativeDown=False)
    lua.execute('''
        function nativeMovable() return true end
        function owned() return false end
        function identity(d) return d.name .. ':' .. tostring(d.instance) end
    ''')
    tick = lua.execute(tick_source().replace('local function nativeDragTick', 'function nativeDragTick') + ' return nativeDragTick')
    for row in path:
        state['row'] = row
        tick()
    return state['saved']


def line(start, end, steps):
    return [tuple(a + (b - a) * i / steps for a, b in zip(start, end)) for i in range(steps + 1)]


def main():
    h = HEIGHT
    # Dragged from 0.722 back to the right edge: the window stops, the cursor keeps going.
    edge = 1920 - WIDTH
    path = [(1400 + 275 * i / 10 + 40 * (i > 9), 340 + i * 3, True, min(edge, 1386.24 + 275 * i / 10 + 40 * (i > 9)), 324, h) for i in range(11)]
    saved = drag(path + [(1715, 370, False, edge, 324, h)])
    assert saved and abs(saved[0] * 1920 - edge) < 1 and abs(saved[1] - .3) < .001, saved
    # Bottom limit: a horizontal drag that drifts down, with the window held at that limit.
    saved = drag([(1500 + i * 10, 400 + i * 4, True, 1386 + i * 10, 324, h) for i in range(8)] + [(1570, 428, False, 1456, 324, h)])
    assert saved and abs(saved[0] * 1920 - 1456) < 1, saved
    # Free drags land anywhere, including near a screen corner.
    saved = drag([(800 + i * 20, 400 - i * 10, True, 700 + i * 20, 300 - i * 10, h) for i in range(10)] + [(980, 310, False, 880, 210, h)])
    assert saved and abs(saved[0] * 1920 - 880) < 1 and abs(saved[1] * 1080 - 210) < 1, saved
    # A window that moves under a still press is an animation, not a drag.
    assert drag([(1700, 400, True, 1400 + i * 30, 324, h) for i in range(6)]) is None
    # So is a window moving against the cursor.
    assert drag([(1700 - i * 5, 400, True, 1400 + i * 30, 324, h) for i in range(6)]) is None
    # A window resizing under the press is not a move.
    assert drag([(1700 + i * 5, 400, True, 1400 + i * 5, 324, h + i * 20) for i in range(6)]) is None
    # Attached windows sit beside their owner, and flip to its left at the right screen edge.
    source = (HERE / 'AetherfallOptions.lua').read_text(encoding='utf-8').replace('\r\n', '\n')
    beside = re.search(r'function AFUI_BesideOwner.*?\nend\n', source, re.S).group(0)
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute(beside)
    place = lambda ox, oy: lua.globals().AFUI_BesideOwner(lua.table(x=ox, y=oy, w=456), lua.table(w=456), 1920)
    assert place(0, 100) == (456, 100)
    assert place(81, 120) == (537, 120)
    assert place(1400, 90) == (944, 90)
    print('OK: real nativeDragTick saves free and edge-stopped drags; animation and resize stay unsaved; attached windows sit beside their owner')


if __name__ == '__main__':
    main()
