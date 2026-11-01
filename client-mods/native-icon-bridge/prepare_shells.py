"""Embed the maintained window shells, retaining their existing message displays.

Only static HTML/CSS/JS are embedded. Player state, actions, purchases and tickets
always come from the authenticated server. Output belongs in the native build dir.
"""
import hashlib
import json
from pathlib import Path
import zlib


def inputs():
    root = Path(__file__).resolve().parents[2] / 'game-server/config'
    mappings = [('central-market', '/market', 'market.html'), ('playerbots', '/market/companions', 'bots.html'),
                ('season-pass', '/market/pass', 'pass.html'), ('account', '/market/account', 'account.html'),
                ('wardrobe', '/market/wardrobe', 'wardrobe.html'), ('codex', '/market/codex', 'codex.html'),
                ('journey', '/journey', 'journey.html'), ('ingameshop', '/shop', 'connection.html')]
    rows = [(prefix if p.name == index else prefix + '/media/' + p.name, p)
            for folder, prefix, index in mappings for p in sorted((root / folder / 'media').iterdir())
            if p.is_file() and p.suffix in ('.html', '.css', '.js')]
    rows.append(('/shop/media/quantity.js', root / 'central-market/media/quantity.js'))
    return rows


def prepare(output):
    rows, arrays, hashes = [], [], {}
    for i, (route, source) in enumerate(inputs()):
        data = source.read_bytes()
        hashes[str(source)] = hashlib.sha256(data).hexdigest()
        compressor = zlib.compressobj(9, wbits=-15)
        packed = compressor.compress(data) + compressor.flush()
        arrays.append('inline constexpr unsigned char data_' + str(i) + '[]={' + ','.join(map(str, packed)) + '};')
        mime = {'.html': 'text/html', '.css': 'text/css', '.js': 'application/javascript'}[source.suffix]
        rows.append('{' + json.dumps(route) + ',' + json.dumps(mime) + ',data_' + str(i) + ',sizeof(data_' + str(i) + '),' + str(len(data)) + '}')
    text = '#pragma once\nnamespace web_shells {\nstruct Record {const char* path;const char* mime;const unsigned char* data;size_t packed,size;};\n'
    text += '\n'.join(arrays) + '\ninline constexpr Record assets[]={' + ',\n'.join(rows) + '};\n}\n'
    Path(output).write_text(text, encoding='ascii')
    return hashes
