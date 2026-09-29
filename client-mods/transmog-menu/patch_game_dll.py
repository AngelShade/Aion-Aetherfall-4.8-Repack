"""Build a version-checked menu hook. This module never patches a running process."""
import hashlib
import struct
from pathlib import Path

ORIGINAL_SHA256 = '5334cf2164468678e45fe1a5decf58a0fbc4fd7f22cfdcbb87d28edce8d2c11c'
HOOK_RVA = 0x144d100
CALLER_RVA = 0x629e93
SEND_CHAT_RVA = 0x33d790
ORIGINAL_EXEC_RVA = 0x628270
BROWSER_HOOK_RVA = 0x144d800
BROWSER_AUTH_RVA = 0x61e580
BROWSER_LOAD_RVA = 0x12cc80
BROWSER_MANAGER_RVA = 0x130b850
BROWSER_PROLOGUE = bytes.fromhex('4883ec284889742440')
PREVIEW_DOCK_RVA = 0x806e29
PREVIEW_DOCK_HOOK_RVA = 0x144dc00
PREVIEW_DOCK_ORIGINAL = bytes.fromhex('3d6b0100000f8581000000')
PREVIEW_DOCK_POSITION_RVA = 0x806e34
PREVIEW_DOCK_SKIP_RVA = 0x806eb5


def build_preview_dock_code():
    """Let the native paper-doll positioning use the private Cash Shop bounds."""
    asm = Assembler(PREVIEW_DOCK_HOOK_RVA)
    asm.emit(b'\x3d\x6b\x01\0\0')  # keep the original supported dialog
    asm.branch(b'\x0f\x84', 'position')
    # Native addon dialogs use the client's reserved 0x20e..0x221 pool.
    asm.emit(b'\x3d\x0e\x02\0\0')
    asm.branch(b'\x0f\x82', 'skip')
    asm.emit(b'\x3d\x21\x02\0\0')
    asm.branch(b'\x0f\x87', 'skip')
    # The native widget lookup verifies the exact Cash Shop browser child.
    # The surrounding native function already owns the ABI shadow space.
    asm.emit(b'\x48\x89\xf9')  # rcx = focused dialog (rdi)
    asm.branch(b'\x48\x8d\x15', 'browser_name')
    asm.emit(b'\x41\xb8\x27\x20\0\0\x48\x8b\x07\xff\x90\x38\x03\0\0')
    asm.emit(b'\x48\x85\xc0')
    asm.branch(b'\x0f\x84', 'skip')
    asm.label('position')
    asm.relative(b'\xe9', PREVIEW_DOCK_POSITION_RVA)
    asm.label('skip')
    asm.relative(b'\xe9', PREVIEW_DOCK_SKIP_RVA)
    asm.label('browser_name')
    asm.emit(b'PrivateCashShopBrowser\0')
    return asm.finish()


class Assembler:
    def __init__(self, base):
        self.base = base
        self.code = bytearray()
        self.labels = {}
        self.fixups = []

    def emit(self, data):
        self.code.extend(data)

    def label(self, name):
        self.labels[name] = len(self.code)

    def branch(self, opcode, label):
        self.emit(opcode)
        self.fixups.append((len(self.code), label))
        self.emit(bytes(4))

    def relative(self, opcode, target):
        self.emit(opcode)
        self.emit(struct.pack('<i', target - self.base - len(self.code) - 4))

    def finish(self):
        for pos, name in self.fixups:
            self.code[pos:pos + 4] = struct.pack('<i', self.labels[name] - pos - 4)
        return bytes(self.code)


def build_hook_code(commands):
    asm = Assembler(HOOK_RVA)
    for index, command in enumerate(commands):
        # Compare one byte at a time, including NUL. Never read beyond a short command.
        for offset, value in enumerate(('/say .' + command).encode('ascii') + b'\0'):
            asm.emit(b'\x80\x79' + bytes([offset, value]))  # cmp byte [rcx+offset], value
            asm.branch(b'\x0f\x85', 'next_' + str(index))
        asm.emit(b'\x48\x83\xec\x28\x31\xd2')  # ABI stack alignment/shadow space; public chat
        asm.branch(b'\x4c\x8d\x05', 'text_' + str(index))  # r8 = wide chat message
        asm.relative(b'\xe8', SEND_CHAT_RVA)
        asm.emit(b'\xb8\x01\0\0\0\x48\x83\xc4\x28\xc3')
        asm.label('next_' + str(index))
    asm.relative(b'\xe9', ORIGINAL_EXEC_RVA)
    for index, command in enumerate(commands):
        asm.label('text_' + str(index))
        asm.emit(('.' + command + '\0').encode('utf-16le'))
    return asm.finish()


def build_browser_hook_code(url):
    # The publisher authentication path redirects through its login service.
    # Our local shop authenticates on the private server; queue its exact URL
    # directly on the same browser view. All other URLs retain the original path.
    payload = url.encode('ascii') + b'\0'
    if not url.startswith('http://127.0.0.1:8091/') or len(payload) > 128 or any(c < 32 for c in payload[:-1]):
        raise ValueError('Embedded shop navigation supports only the local marketplace URL, at most 127 ASCII characters')
    asm = Assembler(BROWSER_HOOK_RVA)
    asm.emit(b'\x48\x85\xd2')  # test rdx, rdx
    asm.branch(b'\x0f\x84', 'original')
    for offset, value in enumerate(payload):
        asm.emit(b'\x80\x7a' + bytes([offset, value]))
        asm.branch(b'\x0f\x85', 'original')
    asm.emit(b'\x48\x8b\x41\x10\x48\x85\xc0')  # native browser = [wrapper+0x10]
    asm.branch(b'\x0f\x84', 'return')
    asm.emit(b'\x49\x89\xd0')  # r8 = URL
    asm.emit(b'\x8b\x90\x40\x03\0\0\x85\xd2')  # edx = browser view index
    asm.branch(b'\x0f\x88', 'return')
    asm.relative(b'\x48\x8d\x0d', BROWSER_MANAGER_RVA)
    asm.relative(b'\xe9', BROWSER_LOAD_RVA)  # tail call preserves the caller's ABI frame
    asm.label('return')
    asm.emit(b'\xc3')
    asm.label('original')
    asm.emit(BROWSER_PROLOGUE)  # displaced complete instructions, no relative operands
    asm.relative(b'\xe9', BROWSER_AUTH_RVA + len(BROWSER_PROLOGUE))
    return asm.finish()


def build_dll(original_path, commands, cash_shop_url):
    data = bytearray(Path(original_path).read_bytes())
    if hashlib.sha256(data).hexdigest() != ORIGINAL_SHA256:
        raise ValueError('Unsupported original Game.dll. This patch supports only the verified 4.8 NA build.')
    if data[CALLER_RVA:CALLER_RVA + 5] != bytes.fromhex('e8d8e3ffff'):
        raise ValueError('Original menu call does not match')
    hook = build_hook_code(commands)
    if len(hook) > BROWSER_HOOK_RVA - HOOK_RVA or any(data[HOOK_RVA:HOOK_RVA + len(hook)]):
        raise ValueError('Hook does not fit the verified empty code region')
    data[HOOK_RVA:HOOK_RVA + len(hook)] = hook
    data[CALLER_RVA:CALLER_RVA + 5] = b'\xe8' + struct.pack('<i', HOOK_RVA - CALLER_RVA - 5)
    if data[BROWSER_AUTH_RVA:BROWSER_AUTH_RVA + len(BROWSER_PROLOGUE)] != BROWSER_PROLOGUE:
        raise ValueError('Original browser authentication entry does not match')
    browser_hook = build_browser_hook_code(cash_shop_url)
    if len(browser_hook) > PREVIEW_DOCK_HOOK_RVA - BROWSER_HOOK_RVA or any(data[BROWSER_HOOK_RVA:BROWSER_HOOK_RVA + len(browser_hook)]):
        raise ValueError('Browser hook does not fit the verified empty code region')
    data[BROWSER_HOOK_RVA:BROWSER_HOOK_RVA + len(browser_hook)] = browser_hook
    data[BROWSER_AUTH_RVA:BROWSER_AUTH_RVA + len(BROWSER_PROLOGUE)] = (
        b'\xe9' + struct.pack('<i', BROWSER_HOOK_RVA - BROWSER_AUTH_RVA - 5) + b'\x90' * 4)
    if data[PREVIEW_DOCK_RVA:PREVIEW_DOCK_RVA + len(PREVIEW_DOCK_ORIGINAL)] != PREVIEW_DOCK_ORIGINAL:
        raise ValueError('Native preview positioning does not match the verified client')
    preview_dock = build_preview_dock_code()
    if len(preview_dock) > 0x400 or any(data[PREVIEW_DOCK_HOOK_RVA:PREVIEW_DOCK_HOOK_RVA + len(preview_dock)]):
        raise ValueError('Native preview docking hook does not fit the empty code region')
    data[PREVIEW_DOCK_HOOK_RVA:PREVIEW_DOCK_HOOK_RVA + len(preview_dock)] = preview_dock
    data[PREVIEW_DOCK_RVA:PREVIEW_DOCK_RVA + len(PREVIEW_DOCK_ORIGINAL)] = (
        b'\xe9' + struct.pack('<i', PREVIEW_DOCK_HOOK_RVA - PREVIEW_DOCK_RVA - 5)
        + b'\x90' * (len(PREVIEW_DOCK_ORIGINAL) - 5))
    return bytes(data)
