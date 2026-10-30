"""Player-owned settings that repack updates must not overwrite once the player has changed them.

The updater keeps such a file when it differs from the copy the installed release
shipped (its FILES.json hash). Unchanged copies are replaced as usual, so new
defaults still reach players who never touched them. A kept .properties file also
gains any keys the update adds; the player's existing values stay as they are.
Records that track installed archives (Graphics menu, renderer, custom quests)
and web media are not listed here: they must follow the shipped files.
"""

KEEP = [
    'Client/system.cfg',
    'Client/SystemOptionGraphics.cfg',
    'Client/PlayerBotBar.ini',
    'Client/SpeechBubbles.ini',
    'Client/Mods/AetherfallUI/state.json',
    'Client/Mods/Launcher/preferences.json',
    'Client/Mods/Launcher/connection.json',
    'Client/Mods/GraphicsOverhaul/PostFX/ReShade.ini',
    'Client/Mods/GraphicsOverhaul/PostFX/AionEnhanced.ini',
]


def preserve_rules(entries):
    """Rules for the patch manifest, taken from mutable entries of the packaged index."""
    keep = {path.casefold() for path in KEEP}
    rules = []
    for entry in entries:
        path = entry['path'].replace('\\', '/')
        key = path.casefold()
        if not entry.get('mutable'):
            continue
        if key in keep:
            rules.append({'path': path, 'mode': 'keep'})
        elif key.startswith('server/') and '/config/' in key and key.endswith('.properties'):
            rules.append({'path': path, 'mode': 'properties'})
    return sorted(rules, key=lambda rule: rule['path'].casefold())
