"""Offline fake-memory checks; never open a process or load a native DLL."""
import json
import struct
import unittest
from window_timing import COUNTERS, ADDON_TREE, TOKEN, DIALOGS, UI_QUEUE, GAME_STATE, snapshot, summary


class Memory:
    base = 0x10000000

    def __init__(self):
        self.data = {}

    def put(self, at, value):
        self.data.update({at+i: b for i, b in enumerate(value)})

    def word(self, at, value):
        self.put(at, struct.pack('<Q', value))

    def read(self, at, size):
        return bytes(self.data.get(at+i, 0) for i in range(size))

    def u64(self, at):
        return struct.unpack('<Q', self.read(at, 8))[0]


def fixture():
    memory = Memory()
    head, node, addon = 0x1000, 0x2000, 0x3000
    memory.word(memory.base + ADDON_TREE, head)
    memory.word(head+8, node)
    memory.word(node, head)
    memory.word(node+0x10, head)
    memory.word(node+0x30, 9)
    memory.word(node+0x38, 15)
    memory.put(node+0x20, b'RelicCalc')
    memory.word(node+0x40, addon)
    memory.word(addon+0x28, 2)
    return memory


class TimingChecks(unittest.TestCase):
    def test_head_opcode_without_arguments(self):
        memory = fixture()
        memory.word(memory.base + COUNTERS['uiCommands'], 1)
        memory.put(memory.base + UI_QUEUE, struct.pack('<QQQ', 0x5000, 8, 2))
        memory.word(0x5010, 0x6000)
        memory.put(0x6000, struct.pack('<I', 12))
        memory.put(0x6008, b'private-url-or-token')
        memory.put(memory.base + GAME_STATE, struct.pack('<I', 11))
        value = snapshot(memory)
        self.assertEqual(value['uiHeadCommand'], 12)
        self.assertEqual(value['gameState'], 11)
        self.assertNotIn('private-url', json.dumps(value))

    def test_queue_layout_and_visible_slot(self):
        memory = fixture()
        memory.word(memory.base + COUNTERS['uiCommands'], 4)
        memory.word(memory.base + DIALOGS + 0x210*8, 0x4000)
        memory.word(0x4030, 1)
        value = snapshot(memory)
        self.assertEqual(value['relicEvents'], 2)
        self.assertEqual(value['uiCommands'], 4)
        self.assertEqual(value['visibleAddonSlots'], [0x210])

    def test_key_value_never_leaves_snapshot(self):
        memory = fixture()
        secret = b'not-a-real-key!!'
        memory.put(memory.base + TOKEN, secret)
        value = snapshot(memory)
        self.assertIs(value['sessionKeyPresent'], True)
        self.assertNotIn(secret.decode(), json.dumps(value))
        self.assertNotIn(secret.hex(), json.dumps(value))

    def test_missing_addon_is_not_zero_pending(self):
        value = snapshot(Memory())
        self.assertIsNone(value['relicEvents'])
        self.assertFalse(value['sessionKeyPresent'])

    def test_tree_cycle_fails_closed(self):
        memory = fixture()
        memory.word(0x2000, 0x2000)
        with self.assertRaisesRegex(ValueError, 'tree changed'):
            snapshot(memory)

    def test_invalid_count_fails_closed(self):
        memory = fixture()
        memory.word(memory.base + COUNTERS['browserCommands'], 0xffffffffffffffff)
        with self.assertRaisesRegex(ValueError, 'bounds'):
            snapshot(memory)

    def test_distinguish_addon_backlog_from_browser_backlog(self):
        samples = [dict(ms=ms, relicEvents=n, uiCommands=0, browserCommands=0,
                        browserEvents=0) for ms, n in ((0, 1), (29000, 1), (30000, 0))]
        result = summary(samples)
        self.assertEqual(result['relicEvents']['longestNonemptyMs'], 30000)
        self.assertEqual(result['browserCommands']['longestNonemptyMs'], 0)

    def test_empty_or_unobserved_capture_is_not_success_evidence(self):
        self.assertIsNone(summary([])['relicEvents']['peak'])
        self.assertEqual(summary([{'ms': 0, 'relicEvents': None}])['relicEvents']['observed'], 0)


if __name__ == '__main__':
    unittest.main()
