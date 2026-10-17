"""Shared original/repack support-file paths. Native game resources retain their paths."""
import re

PREFIXES = [
    ('DXVK/renderer.ini', 'DXVK/renderer.ini'),
    ('DXVK/graphics-menu', 'Mods/GraphicsMenu'),
    ('DXVK/cursor-fix', 'Mods/CursorFix'),
    ('DXVK-backups', 'Mods/Backups/Renderer'),
    ('GraphicsOverhaul-backups', 'Mods/Backups/GraphicsOverhaul'),
    ('VisualOverhaul-backups', 'Mods/Backups/VisualOverhaul'),
    ('BrowserFrame-backups', 'Mods/Backups/BrowserFrame'),
    ('BundleOpening-backups', 'Mods/Backups/BundleOpening'),
    ('FreeFlight-backups', 'Mods/Backups/FreeFlight'),
    ('Journey-startup-backups', 'Mods/Backups/JourneyStartup'),
    ('MarketShortcut-backups', 'Mods/Backups/MarketShortcut'),
    ('RememberLogin-backups', 'Mods/Backups/RememberLogin'),
    ('SeasonPass-backups', 'Mods/Backups/SeasonPass'),
    ('SpeechBubbles-backups', 'Mods/Backups/SpeechBubbles'),
    ('TransmogMenu-backups', 'Mods/Backups/TransmogMenu'),
    ('GraphicsOverhaul', 'Mods/GraphicsOverhaul'),
    ('VisualOverhaul', 'Mods/VisualOverhaul'),
    ('DXVK', 'Mods/Renderer'),
    ('Enable-FreeFlight.ps1', 'Mods/FreeFlight/Install-FreeFlight.ps1'),
    ('VirusTotal Reports.html', 'Mods/Security/VirusTotal Reports.html'),
    ('portable-client.json', 'Mods/portable-client.json'),
]
RENAMES = {
    'ApplyGraphicsMenu.ps1': 'Install-GraphicsMenu.ps1',
    'InstallNativeCursorPatch.ps1': 'Install-CursorFix.ps1',
    'ApplyGlobal.ps1': 'Install-GraphicsOverhaul.ps1',
    'ApplyPackage.ps1': 'Install-VisualOverhaul.ps1',
}


def mapped(relative):
    relative = relative.replace('\\', '/')
    for old, new in PREFIXES:
        if relative.casefold() == old.casefold() or relative.casefold().startswith(old.casefold() + '/'):
            relative = new + relative[len(old):]
            break
    parent, separator, name = relative.rpartition('/')
    return parent + separator + RENAMES.get(name, name)


_patterns = [(re.escape(old).replace('/', r'[\\/]'), new) for old, new in PREFIXES]
_path = re.compile(r'(?<![\w-])(?<!Mods/)(?<!Mods\\)(?:' +
                   '|'.join('(' + p + ')' for p, _ in _patterns) + r")(?=[\\/'\"]|$)", re.I)


def rewrite(text):
    text = re.sub(r'%~dp0([^"\r\n;]+)', lambda m: '%~dp0' + mapped(m[1]), text)
    text = text.replace("Join-Path $modRoot 'graphics-menu/", "Join-Path $clientRoot 'Mods/GraphicsMenu/")
    def replace(match):
        for i, (_, new) in enumerate(_patterns, 1):
            if match.group(i) is not None:
                windows = '\\' in match[0] or text[match.end():match.end() + 1] == '\\'
                return new.replace('/', '\\') if windows else new
        raise AssertionError('Unmatched path rule')
    text = _path.sub(replace, text)
    for old, new in RENAMES.items():
        text = text.replace(old, new)
    return text


def rewrite_json(value):
    if isinstance(value, str):
        return rewrite(value)
    if isinstance(value, list):
        return [rewrite_json(v) for v in value]
    if isinstance(value, dict):
        return {k: rewrite_json(v) for k, v in value.items()}
    return value
