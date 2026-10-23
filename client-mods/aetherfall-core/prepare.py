"""Build and stage the AetherfallCore Lua bridge; never starts or attaches to Aion.

Stages bin64/AetherfallCore.dll, Game.dll with the added import (active copy,
Graphics menu package/baseline and native-cursor baseline, so no restore can
drop the import while the DLL stays required), the signed RelicCalc plugin
cohort with AetherfallCore.lua (/afcore report) and the updated recovery records.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys

HERE = Path(__file__).resolve().parent
NATIVE_VERSION = '1.2.51'  # Generated shared UI catalogue, square shortcuts and derived mover previews; Shared widget resolver, read-only tree reflection and numeric kinds; 1.2.50: correct opaque minimap frame to native transparent-centre border; revert 1.2.48 broad tooltip hit-policy change; 1.2.49: clock, zoom and conditional vendor notice each have an independent mover; Native bridge releases are independent of Lua-only updates.
PAK = 'Plugin/RelicCalc/RelicCalc.pak'
PLUGIN_COHORT = [PAK, PAK + '.sig', 'Addon.key', 'bin32/bin32.pak.sig', 'Data/func_pet/func_pet.pak.sig']
VERSION = '1.2.51'  # 1.2.50: correct opaque minimap frame to native transparent-centre border; revert 1.2.48 broad tooltip hit-policy change; 1.2.49: clock, zoom and conditional vendor notice each have an independent mover; 1.2.48: separate minimap/buttons/area/sword movers, native minimap border and bounded click-through parent canvases; 1.2.47: scale-safe MoveUI grid and passive full-name captions; native bridge retained; 1.2.46: native minimap viewport resize, separate footer controls and attached native area/PvP strip; bridge 1.2.45 retained; 1.2.45: verified native post-anchor holds, mouse-admitted placement saves, closed-window catalogue and native shortcut artwork bounds; 1.2.43: borderless resizable minimap, attached label, uniform shortcut grid and native background opacity;  1.2.42: frameless target readout with queued forced placement after Show; plugin-only retains native Core 1.2.41; 1.2.41: render-space pins and hit bounds, continuous focus recovery, actual daily/system notification mover; 1.2.40: post-native target aura anchor hold, explicit daily notification mover, equal-height target details and persistent companion bar; 1.2.39: target/target-of-target/aura/cast movers publish the draft before native events and refresh pins throughout dragging; native Core 1.2.38 retained; 1.2.38: native non-item map hover panels share one bounded cursor/mover group; alternate tooltip slot retained; Lua 1.2.37 retained on DLL-only delivery; 1.2.37: native MoveUI grid, draggable control panel, explicit transactional Save/Cancel, selection/filter/temporary hide and precise positioning; native Core 1.2.34 retained; 1.2.36: action-bar resizing fits viewport with aligned movers; separated-bar switch handles/restores both verified windows; native Core 1.2.34 retained; 1.2.35: profile switches preserve and restore each profile's settings, new/partial profiles use their own defaults; plugin-only retains native 1.2.34; 1.2.34: native nested-widget coordinate conversion/effective visibility, authoritative aura remaining milliseconds, unavailable aura counts, and real bridge build diagnostics; 1.2.33: reconnect readiness and character profile isolation; focus-resume cache recovery and settling, paused background geometry, cancelled stale mover drags;  1.2.32: existing shared one-shot timer owner resumes after world transitions and before timer work; single-chain editor wakeup and owner diagnostics;  1.2.31: plugin editors use only queued widget size/visibility operations; remove direct native editor writes added in 1.2.30 after immediate MoveUI rendering crash;  1.2.30: exact mover geometry, movable-window ownership and name-bound persistence, zero-sized target aura canvas, isolated event dispatch and post-world HUD recovery; retained-layout bridge/Lua builder;  1.2.29: XP mover preserves native 5px bounds for dragging, clamping and snapping, with hover/nudge label; 1.2.28: data texts remove native clock line endings and use separate screen-width columns; plugin-only build retains generated layouts and native bridge; 1.2.27: native item comparison columns share one cursor/mover anchor and fit on screen as a group, retaining native sizes and 5/20-pixel spacing; 1.2.26: cursor/mover/hidden policies apply to the live DlgTooltip outer windows before first native placement, with guarded class hooks; tooltip templates and internal item/text layout remain native; 1.2.25: the core decides a tooltip's anchor when the game places it (mouse over a game window: UI anchor, else world anchor), so it opens at its spot with no jump and the target frame counts as UI; nothing is re-placed while the game is not in front or the screen size is settling (Alt+Tab moved windows); 1.2.24: start-menu submenus and context menus work again (slot 0x1ec is a shared pop-up, never a tooltip; unnamed windows get no mover), NPC tooltips are the game's tooltip_proto window and count as world tooltips when the mouse is over the world (UI tooltips over windows), /moveui fixed (hideNudge was called before it was defined), the layout pass leaves held windows alone (a taller bar kept its bottom edge), the offline check fails on any interface error in chat; 1.2.23: up to 128 held window classes (24 ran out, so the world tooltip's pin was refused and it stayed under the minimap), /afcore test names why a pin is refused, Broker button 34x32 with hover and pressed art (interface 1.3.10); 1.2.22: pins copy only a class vtable's real length (a fixed 8 KiB copy was refused for classes near the end of a memory region, e.g. the plugin windows and likely the tooltips, which were then only put back a tick later), /afcore test lists held windows whose pin was refused; 1.2.21: Broker finds the chat box by name, world and UI tooltips with their own anchors (UI tooltips native by default), an unnamed window counts as the world tooltip only once seen small, movers: nudge panel (exact position, arrows) and snap to frames, action bars: buttons per row and button size, world map draggable at any time and remembered, /afcore test (in-game PASS/FAIL of the native parts) and /afcore watch; core: widgets by child index, movable flag, hit-test probe, window class in dialog; 1.2.20: Broker button sends /say .broker through the chat box's own submit (the slash routine ignored /say), cursor-anchored tooltips follow the mouse, /afcore shows how tooltips are placed, the server's browser windows (market, pass, companions, shop, journey) get a web view at screen size and fit the screen, big closed windows get no mover; 1.2.19: start menu takes the mouse only over its buttons (core hit test; the transparent flag had stopped its hover pop-ups), Broker and Interface are native start-menu buttons (interface 1.3.8) run from the menu's own command handler, the once-a-second pass no longer fights pinned windows (buttons bounced), no stray start mover, pins never touch windows the game deleted (reconnect), back to 5 plugin windows; 1.2.18: every start-menu button on its own mover (the menu window is click-through and holds them wherever they are; the core pins each button, also against market-shortcut's re-placing), Broker and Aetherfall Interface buttons (own windows, own movers; 7 of the 20 plugin windows), HUD entries left out of Additional Functions while the interface is on, 56 movers; core: widget pins, size, transparent flag, chat command; 1.2.17: Tooltip anchor mover (fixed-size box; the unnamed text tooltip and the item/skill tooltips grow from its chosen corner) and an ElvUI-style Tooltip options page (show, anchor mover/cursor, grow corner, cursor offset X/Y, keep on screen); pin modes for corners, cursor offset and hidden; no unnamed mover; 1.2.16: moved windows are pinned in the core (the game's own re-placing keeps the mover spot, so the target no longer shows at the top for a frame when a target is picked); 1.2.15: named movers for the progress / enchant bars and target-of-target cast bar, windows placed with movers are pinned on the game's USER_CHANGED_TARGET and GAME_UI_DIALOG_VISIBLE_CHANGED events (no flash) and every tick; movers for windows seen this session, the notification icons (help_indicator) and Movement / GM Indicator; 1.2.14: start-menu design position for the market-shortcut-aligned grid (interface 1.3.5); 1.2.13: transparent mover overlay (preset without a skin), no movers for party buffs / gm indicator / move state, named Quest Tracker and Zone Name movers, five distinct colour themes (data texts, status line, minimap label, options accents), eight real client fonts, backdrop opacity 30-100%, design positions for raised unit frames (interface 1.3.4); 1.2.12: ElvUI-style options window (category sidebar, titled panel, label and control rows, one-line hints), window switches apply at once and show windows again when turned back on, honest names for data-text settings; 1.2.11: design position for the taller player frame (interface 1.3.3, readable DP bar); 1.2.10: options window and movers built with the plugin (built at runtime their clicks never reached Lua), status line at a fixed spot under the chat input, click counter in /afcore; 1.2.9: movers are one overlay window with a box per frame dragged via AFCore.Cursor (all frames at once, live native moves, Shift+click reset), re-anchored frames held every tick, options Close button, 5 of the 20 plugin windows; 1.2.8: fits the client's 20 plugin window slots (RelicCalc uses 7): 12 Aetherfall windows, 8 paged movers, no aura timer labels, slot usage in /afcore; 1.2.7: options window and movers built on first use from the addon timer with the client's own XML loader (built at load they ignored SetRect/Show in game); 1.2.6: /afui, /moveui and /afcore open|move only queue the request; the addon timer tick shows the window or movers (windows shown from chat dispatch never appear in this client); 1.2.5: aura timer labels only under icons inside their own window (or around the target frame), aura geometry in /afcore; design positions for the taller player frame and action bars 4-6 (interface 1.3.2), movers for both separated bars, design moves only on the matching window; 1.2.4: widget rectangles made absolute (aura timers under their icons), target frames, cast bars and target auras held at their design spots when the game re-anchors them; 1.2.3: options command on its own slash key with a state-based toggle, /afcore open|move and dispatch diagnostics, status line yields to the chat input, start-menu design position under the minimap label; 1.2.2: layout load cost halved (client drops widget calls beyond 4000 queued), named movers for every HUD frame incl. hidden ones, state and settings read natively, layout diagnostics; 1.2.1: settings saved through the bridge (client io has no write), calibrated movers, /afui and /aetherfall commands, slash errors in chat; 1.2.0: design table for the skin-pack HUD (quickbar controls inside the bar); 1.1.1: loader-safe plugin XML, design positions, diagnostics, mod-folder resolution; 1.1.0: options window, movers, native placement commands; 1.0.1: registration waits for Game.dll's own relocation; 1.0.2: Kinah from the bag map
LUA = 'AetherfallCore.lua'
ADDON = ['AetherfallCore.lua', 'AetherfallLayout.lua', 'AetherfallLazy.lua', 'AetherfallHUD.lua', 'AetherfallOptions.lua',
         'AetherfallHUD.xml']
GENERATED = ('AetherfallLayout.lua', 'AetherfallLazy.lua')
# Native HUD dialog type ids for the design layout (Game.dll dialog table).
DESIGN_IDS = {'basic_status_dialog': 0x0a, 'quickbar_dialog': 0x10, 'quickbar_dialog_ctrl': 0x12, 'quickbar_dialog_alt': 0x13,
              'exp_bar_dialog': 0x14, 'radar_dialog': 0x0e, 'start_dialog': 0x11, 'target_dialog': 0x0b,
              'target_assist_dialog': 0x0c, 'target_buff_status_dialog': 0x20, 'buff_status_dialog': 0xb0,
              'debuff_status_dialog': 0xb1, 'party_dialog': 0x21, 'chat_dialog': 0x0d, 'chat_input_dialog': 0xcf,
              'skill_delay_dialog': 0x15a, 'target_delay_dialog': 0x17d, 'quickbar_dialog_extra': 0x16,
              'quickbar_dialog_sep': 0x17, 'target_assist_delay_dialog': 0x17c}
# Multi-copy dialogs take consecutive ids: the second separated bar is 0x18, stacked above the first.
SECOND_SEP = 0x18

# The client queues almost every widget call and drops calls beyond 4000 pending
# ("Too many calls from addon"); the whole RelicCalc plugin is built in one pass,
# so this layout keeps its load cost small (measure_calls.py).
POOL = 0     # aura timer labels: none (see BUDGET)
MOVERS = 56  # mover boxes: child widgets of one overlay window (frames, tooltip, start-menu buttons)
# All plugins together get 20 dialog slots (Game.dll 0x635093: ids 0x20e..0x221; a dialog that
# finds no free slot is deleted and its calls do nothing). RelicCalc's own windows use 7.
BUDGET = 5   # data bar, status line, minimap label, options, movers overlay: 8 slots stay free for other addons


def layout_lua(client, mods):
    """AFUI_DESIGN: the native HUD design positions (reference 1280x960 + alignment) from hud_native."""
    sys.path.insert(0, str(HERE.parent / 'aetherfall-ui'))  # sibling maintained folder
    import hud_native
    import library
    archive = library.codec.read_pak(client / library.L10N_DATA)
    rows = []
    for base, ident in sorted(DESIGN_IDS.items(), key=lambda kv: kv[1]):
        name = ('ui/game_hud_s1/' if base in ('basic_status_dialog', 'quickbar_dialog', 'exp_bar_dialog', 'radar_dialog', 'start_dialog') else 'ui/game/') + base + '.xml'
        root = hud_native.transform(name, archive.read(library.pak_entry(archive, name)))
        x, y, w, h = (int(float(v)) for v in root.get('frame').split(','))
        rows.append(f"    [{ident}] = {{{x}, {y}, {w}, {h}, '{root.get('h_pos_align') or 'left'}', '{root.get('v_pos_align') or 'top'}', '{base}'}},")
        if base == 'quickbar_dialog_sep':
            rows.append(f"    [{SECOND_SEP}] = {{{x}, {y - h - hud_native.SEP_GAP}, {w}, {h}, '{root.get('h_pos_align') or 'left'}', '{root.get('v_pos_align') or 'top'}', '{base}'}},")
    # Start-menu buttons inside the menu (reference pixels): each one's own mover starts there.
    name = 'ui/game_hud_s1/start_dialog.xml'
    start = hud_native.transform(name, archive.read(library.pak_entry(archive, name)))
    menu = []
    for widget in ('fly_gauge', 'start_button', 'prophet_button', 'btn_radar_map', 'ingame_web', 'companion_menu_button',
                   'season_pass_button', 'journey_menu_button', 'wardrobe_menu_button', 'central_market_button',
                   'item_shop_container', 'mail_notice', 'aetherfall_broker_button', 'aetherfall_menu_button'):
        node = hud_native.find(start, widget)
        assert node is not None, widget
        x, y, w, h = (int(float(v)) for v in node.get('frame').split(','))
        menu.append(f"    {widget} = {{{x}, {y}, {w}, {h}}},")
    return ('-- Generated by prepare.py from aetherfall-ui/hud_native.py: Aetherfall HUD design positions\n'
            '-- {x, y, width, height in the 1280x960 reference, horizontal align, vertical align, layout}.\n'
            'AFUI_DESIGN = {\n' + '\n'.join(rows) + '\n}\n'
            '-- Start-menu buttons {x, y, width, height} inside start_dialog (incl. the interface\'s Broker and Interface).\n'
            'AFUI_MENU = {\n' + '\n'.join(menu) + '\n}\n').encode('utf-8')


def screens():
    """Plugin dialogs: (built at load, built on first use). All start hidden.

    The HUD data layer loads with the plugin. The options window and movers are built
    on first use from the addon timer (AetherfallLazy.lua): dialogs late in the
    plugin's one-pass load lost their own setup (rect 0, Show ignored) in game.

    Uses only the XML vocabulary of the plugin layouts that load in this client
    (PlayerBotBar, Wardrobe, RelicCalc): dialog name/frame/text/preset/flag/title_*/
    close_*, widget type/style/name/frame/preset/flag/text/font/HAlign. Any other
    attribute or element can abort the client's loader for the whole file.
    """
    import xml.etree.ElementTree as E
    root = E.Element('Screen')
    lazy = E.Element('Screen')
    label_flag = 'not_active;truncate;not_multiline;not_wordwrap'

    def dialog(name, frame, preset=None, flag='not_visible;not_movable', parent=None, **extra):
        d = E.SubElement(root if parent is None else parent, 'Dialog', name=name, frame=frame, flag=flag)
        if preset:
            d.set('preset', preset)
        for k, v in extra.items():
            d.set(k, v)
        return d

    def label(parent, name, frame, align=None, text=None, font='ui_default'):
        w = E.SubElement(parent, 'Widget', type='static', name=name, frame=frame, font=font, flag=label_flag)
        if align:
            w.set('HAlign', align)
        if text:
            w.set('text', text)
        return w

    def image(parent, name, frame, preset):
        return E.SubElement(parent, 'Widget', type='image', name=name, frame=frame, preset=preset)

    def button(parent, name, frame, text=None, click=None, preset='v5_button'):
        w = E.SubElement(parent, 'Widget', type='button', style='pushbutton', name=name, frame=frame, font='ui_default', preset=preset)
        if text:
            w.set('text', text)
        if click:
            E.SubElement(E.SubElement(w, 'Scripts'), 'OnClick').text = click
        return w

    # HUD data layer (AetherfallHUD.lua). Panels use the HUD panel preset, flat in the layout state.
    top = dialog('AFHTop', '0,0,1280,18', 'v5_hud_quickbar_s1')
    label(top, 'AFHTopLeft', '8,0,420,18')
    label(top, 'AFHTopCenter', '330,0,620,18', 'center')
    label(top, 'AFHTopRight', '852,0,420,18', 'right')
    scripts = E.SubElement(top, 'Scripts')
    # The data bar also carries the options/movers events (their dialogs are built on demand).
    E.SubElement(scripts, 'OnLoad').text = 'AFHUD_OnLoad(); AFUI_OnLoad();'
    E.SubElement(scripts, 'OnEvent').text = 'AFHUD_Dispatch(event);'
    label(dialog('AFHChat', '0,0,380,18', 'v5_hud_quickbar_s1'), 'AFHChatText', '6,0,368,18')
    label(dialog('AFHMap', '0,0,180,16'), 'AFHMapText', '0,0,180,16', 'center')
    for i in range(1, POOL + 1):
        label(dialog(f'AFHT{i}', '0,0,40,12'), f'AFHT{i}Text', '0,0,40,12', 'center')

    # Options window (/afui), ElvUI-style: category sidebar, a panel with the category title and a
    # divider, 10 rows of label | control (value button with - / + or < / > beside it), a one-line
    # hint and Close. Built with the plugin like every plugin window whose buttons work.
    opts = dialog('AFUIOptions', '320,250,640,460', 'v5_dialog', 'close;title;not_visible', text='Aetherfall Interface',
                  title_height='32', title_offset='12,6', close_preset='v5_close', close_offset='6,6')
    image(opts, 'AFUISide', '6,34,144,388', 'v5_staticbox')
    image(opts, 'AFUIPanel', '156,34,478,388', 'v5_staticbox')
    for i in range(1, 11):
        button(opts, f'AFUITab{i}', f'12,{40 + (i - 1) * 32},132,28', click=f"AFUI.Click('Tab{i}');", preset='v5_tabbutton')
    label(opts, 'AFUIHeader', '170,42,450,22', font='window')
    image(opts, 'AFUIRuler', '170,66,450,2', 'v5_ruler')
    for i in range(1, 11):
        y = 74 + (i - 1) * 34
        label(opts, f'AFUIRow{i}Label', f'170,{y},270,28')
        button(opts, f'AFUIRow{i}Less', f'456,{y},26,28', '<', f"AFUI.Click('Row{i}Less');")
        button(opts, f'AFUIRow{i}Value', f'486,{y},110,28', click=f"AFUI.Click('Row{i}Value');")
        button(opts, f'AFUIRow{i}More', f'600,{y},26,28', '>', f"AFUI.Click('Row{i}More');")
    label(opts, 'AFUIStatus', '12,428,540,24')
    button(opts, 'AFUIClose', '566,428,68,24', 'Close', "AFUI.Click('Close');")

    # Keep the editor generator shared with retained-layout updates.
    from mover_editor import dialog as mover_dialog
    root.append(mover_dialog(MOVERS))
    count = sum(1 for r in (root, lazy) for d in r if d.tag == 'Dialog')
    assert count <= BUDGET, f'{count} plugin dialogs exceed the {BUDGET} Aetherfall may use'
    for r in (root, lazy):
        r.set('widget_cnt', str(sum(1 for n in r.iter() if n.tag in ('Dialog', 'Widget'))))
        E.indent(r, '  ')
    return root, lazy


def hud_xml():
    """AetherfallHUD.xml: the dialogs built when the plugin loads."""
    import xml.etree.ElementTree as E
    return E.tostring(screens()[0], encoding='utf-8', xml_declaration=True)


def lazy_xml():
    """The on-demand dialogs as one document (for checks and previews)."""
    import xml.etree.ElementTree as E
    return E.tostring(screens()[1], encoding='utf-8', xml_declaration=True)


def lazy_lua():
    """AetherfallLazy.lua: each on-demand dialog's XML, built in game with the client's own
    loader (lxp.lom.parse + LoadXMLWidget) from the addon timer, a few per tick."""
    import xml.etree.ElementTree as E
    rows = []
    for d in screens()[1]:
        text = E.tostring(d, encoding='unicode')
        assert ']==]' not in text
        rows.append(f"    {{'{d.get('name')}', [==[{text}]==]}},")
    return ('-- Generated by prepare.py: options window and movers, built on first use (see AetherfallOptions.lua).\n'
            'AFUI_LAZY = {\n' + '\n'.join(rows) + '\n}\n').encode('utf-8')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--update', action='store_true', help='bridge and plugin only, for a client whose Game.dll already imports the bridge')
    parser.add_argument('--dll-only', action='store_true', help='rebuild only bin64/AetherfallCore.dll for a client that already imports it')
    parser.add_argument('--plugin-only', action='store_true', help='build only the maintained Lua and its signed plugin cohort; retain installed generated layout and native bridge')
    parser.add_argument('--retain-layout', action='store_true', help='with --update, build bridge/Lua while retaining installed generated layouts and load order')
    parser.add_argument('--shared-timer-update', action='store_true', help='with retained layout, include the maintained RelicCalc shared timer owner (WardrobeNative.lua)')
    parser.add_argument('--mover-editor-update', action='store_true', help='replace only the installed MoveUI dialog and stage its grid-line texture')
    parser.add_argument('--ui-registry-update', action='store_true', help='install the generated native/resource UI catalogue; future updates refresh it automatically')
    parser.add_argument('--shortcut-geometry-update', action='store_true', help='normalize native shortcut artwork, nested shop geometry and square control bounds')
    parser.add_argument('--hud-repair-update', action='store_true', help='update target details, notification mover and persistent companion-bar controls')
    parser.add_argument('--hud-customization-update', action='store_true', help='borderless minimap resources and label, retaining other installed layouts')
    parser.add_argument('--minimap-border-update', action='store_true', help='restore the native minimap border on an independently pinned child image')
    parser.add_argument('--minimap-renderer-update', action='store_true', help='repair the fixed native radar viewport with matching graphics/cursor recovery')
    parser.add_argument('--minimap-elements-update', action='store_true', help='update independent minimap element movers and their affected inventory records')
    parser.add_argument('--refresh-hud-inventory', action='store_true', help='stage only current HUD cohort inventory hashes; do not repack, sign or rebuild binaries')
    parser.add_argument('--target-overlay-update', action='store_true', help='repair only the installed target readout dialog and its Lua; retain native layouts and bridge')
    parser.add_argument('--client-mods', type=Path, default=HERE.parent, help='maintained client-mods folder (helpers)')
    args = parser.parse_args()
    if not args.plugin_only and f'CORE_VERSION[]="{NATIVE_VERSION}"' not in (HERE / 'aetherfall_core.cpp').read_text(encoding='utf-8'):
        parser.error('Native CORE_VERSION and builder NATIVE_VERSION must match')
    if args.plugin_only and (args.dll_only or not args.update):
        parser.error('--plugin-only requires --update and cannot be combined with --dll-only')
    if args.retain_layout and (not args.update or args.dll_only):
        parser.error('--retain-layout requires --update and cannot be combined with --dll-only')
    if args.shared_timer_update and not (args.plugin_only or args.retain_layout):
        parser.error('--shared-timer-update requires --plugin-only or --retain-layout')
    if args.mover_editor_update and not (args.plugin_only or args.retain_layout):
        parser.error('--mover-editor-update requires --plugin-only or --retain-layout')
    if args.shortcut_geometry_update and (not args.retain_layout or args.hud_customization_update):
        parser.error('--shortcut-geometry-update requires --retain-layout and a separate resource update')
    if args.hud_repair_update and not args.retain_layout:
        parser.error('--hud-repair-update requires --retain-layout')
    if args.target_overlay_update and not (args.plugin_only or args.retain_layout):
        parser.error('--target-overlay-update requires --plugin-only or --retain-layout')
    if args.target_overlay_update and args.hud_repair_update:
        parser.error('Choose the initial target installation or its overlay repair')
    if args.hud_customization_update and (not args.retain_layout or args.hud_repair_update):
        parser.error('--hud-customization-update requires --retain-layout and cannot repeat initial HUD repair')
    if args.minimap_renderer_update and (not args.retain_layout or args.hud_customization_update or args.refresh_hud_inventory):
        parser.error('--minimap-renderer-update requires --retain-layout and cannot combine with resource/inventory updates')
    if args.minimap_border_update and not args.hud_customization_update:
        parser.error('--minimap-border-update requires --hud-customization-update')
    if args.minimap_elements_update and not args.retain_layout:
        parser.error('--minimap-elements-update requires --retain-layout')
    mods = args.client_mods.resolve(strict=True)
    sys.path.insert(0, str(mods / 'speech-bubbles'))
    sys.path.insert(0, str(HERE))
    from patch_binary import patch
    # The Red Journal archive helper shares this module's name; load it by path.
    spec = importlib.util.spec_from_file_location('red_journal_prepare', mods / 'quest-red-journal/prepare.py')
    journal = importlib.util.module_from_spec(spec)
    sys.path.insert(0, str(mods / 'quest-red-journal'))
    spec.loader.exec_module(journal)
    extend_archive, parts, read_pak = journal.extend_archive, journal.parts, journal.read_pak

    client, output = args.client.resolve(strict=True), args.output.resolve()
    repo = mods.parent
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(repo):
        raise ValueError('Use a fresh external output directory')
    output.mkdir(parents=True)
    files = {}
    before = {}
    customization_inventory = None

    def stage(relative, data):
        if relative in files and files[relative] != data:
            raise ValueError('Conflicting staged payload: ' + relative)
        files[relative] = data
        path = output / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def original(relative):
        path = client / relative
        before.setdefault(relative, sha(path.read_bytes()) if path.exists() else None)
        return path.read_bytes() if path.exists() else None

    if args.refresh_hud_inventory:
        if args.update or args.dll_only or args.plugin_only or args.retain_layout or args.hud_customization_update:
            raise ValueError('Inventory refresh is a standalone metadata operation')
        from hud_customization import refresh_inventory
        original('Mods/InstalledMods.json')
        stage('Mods/InstalledMods.json', json.dumps(refresh_inventory(client), indent=2).encode())
        write_manifest(output, client, 'unchanged (inventory refresh only)', files, before)
        return

    if not args.dll_only:
        from mover_editor import LINE_PATH, line_texture
        if original(LINE_PATH) != line_texture():
            stage(LINE_PATH, line_texture())

    if args.dll_only or args.update:
        if b'AetherfallCore.dll\0' not in (client / 'bin64/Game.dll').read_bytes():
            raise ValueError('Game.dll does not import AetherfallCore.dll; run the full prepare')
        info = 'unchanged (already imports AetherfallCore.dll)'
        original('bin64/AetherfallCore.dll')
    else:
        game, info = patch(original('bin64/Game.dll'))
        stage('bin64/Game.dll', game)

    if args.minimap_renderer_update:
        from minimap_renderer import patch as repair_radar
        sys.path.insert(0, str(mods / 'transmog-menu'))
        from graphics_compat import prepare_incremental, compatibility_paths
        repaired = repair_radar(original('bin64/Game.dll'))
        repaired, graphics = prepare_incremental(client, output, repaired)
        if not graphics:
            raise ValueError('Expected the original Graphics/cursor recovery cohort')
        stage('bin64/Game.dll', repaired)
        for rel in sorted(compatibility_paths(graphics)):
            prior = original(rel)
            payload = (output / rel).read_bytes()
            if payload != prior:
                stage(rel, payload)

    if not args.plugin_only:
        # Native bridge, normal MSVC build of the maintained source.
        work = output.parent / (output.name + '-compile')
        work.mkdir()
        vcvars = Path(os.environ.get('ProgramFiles(x86)', r'C:\Program Files (x86)')) / 'Microsoft Visual Studio/2022/BuildTools/VC/Auxiliary/Build/vcvars64.bat'
        dll = work / 'AetherfallCore.dll'
        script = work / 'build.cmd'
        script.write_text(f'@echo off\ncall "{vcvars}" >nul\ncl /nologo /std:c++17 /EHsc /O2 /MT /LD /W4 /WX /Fo:"{work / "core.obj"}" "{HERE / "aetherfall_core.cpp"}" /link /OUT:"{dll}" /IMPLIB:"{work / "core.lib"}"\n')
        subprocess.run('cmd.exe /d /s /c ""' + str(script) + '""', check=True)
        stage('bin64/AetherfallCore.dll', dll.read_bytes())
    if args.dll_only:
        write_manifest(output, client, info, files, before, version=NATIVE_VERSION)
        return
    if args.shortcut_geometry_update:
        from shortcut_geometry import prepare as prepare_shortcuts
        prepare_shortcuts(client, stage, original, extend_archive, read_pak)

    if not args.update:
        # Keep the import in the active package and both restore baselines.
        state = json.loads((client / 'Mods/GraphicsMenu/installed.json').read_text(encoding='utf-8-sig'))
        manifest = json.loads((client / 'Mods/GraphicsMenu/package/manifest.json').read_text(encoding='utf-8-sig'))
        if state['files'] != manifest['files']:
            raise ValueError('Graphics recovery metadata differs')
        entry = next(e for e in state['files'] if e['path'] == 'bin64/Game.dll')
        backup_root = Path(state['backupRoot']).resolve()
        if not backup_root.is_relative_to(client):
            raise ValueError('Graphics recovery root is outside the selected client')
        baseline = backup_root / 'bin64/Game.dll'
        if sha(baseline.read_bytes()) != entry['original'] or sha((client / 'bin64/Game.dll').read_bytes()) != entry['installed']:
            raise ValueError('Graphics recovery baseline differs: bin64/Game.dll')
        data = patch(original(baseline.relative_to(client).as_posix()))[0]
        stage(baseline.relative_to(client).as_posix(), data)
        entry.update(original=sha(data), installed=sha(game))
        package = 'Mods/GraphicsMenu/package/bin64/Game.dll'
        original(package)
        stage(package, game)
        manifest['files'] = state['files']
        original('Mods/GraphicsMenu/installed.json')
        original('Mods/GraphicsMenu/package/manifest.json')
        stage('Mods/GraphicsMenu/installed.json', json.dumps(state, indent=2).encode())
        stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(manifest, indent=2).encode())
        cursor = json.loads(original('Mods/Renderer/installed.json').decode('utf-8-sig'))
        centry = next(e for e in cursor['nativeCursorPatch']['files'] if e['path'] == 'bin64/Game.dll')
        cbase = Path(centry['backupPath']).resolve()
        if not cbase.is_relative_to(client) or sha(cbase.read_bytes()) != centry['original'] or centry['installed'].lower() != before['bin64/Game.dll']:
            raise ValueError('Cursor baseline differs')
        data = patch(original(cbase.relative_to(client).as_posix()))[0]
        stage(cbase.relative_to(client).as_posix(), data)
        centry.update(original=sha(data), installed=sha(game))
        stage('Mods/Renderer/installed.json', json.dumps(cursor, indent=2).encode())
    if not args.plugin_only:
        original('Mods/AetherfallCore/mod.json')
        metadata = json.dumps(dict(
            Title='Aetherfall Core', Listed=True, Files=['bin64/AetherfallCore.dll'],
            Description='Lua bridge for the Aetherfall Interface: Kinah, experience, auras, action slots, casts and FPS, plus the /afui (or /aetherfall) options window and /moveui movers for native windows. /afcore prints what it reports.'),
            indent=2).encode()
        if metadata != (client / 'Mods/AetherfallCore/mod.json').read_bytes():
            stage('Mods/AetherfallCore/mod.json', metadata)

    # Plugin: add the bridge and HUD data layer to the installed RelicCalc archive, keeping
    # every other compressed member byte-identical, then sign with the normal signer.
    addon = {name: (HERE / name).read_bytes() for name in ADDON if name.endswith('.lua') and name not in GENERATED}
    if args.shared_timer_update:
        addon['WardrobeNative.lua'] = (mods / 'transmog-menu/WardrobeNative.lua').read_bytes()
    if args.hud_repair_update:
        addon['PlayerBotBar.lua'] = (mods / 'playerbots/PlayerBotBar.lua').read_bytes()
    if not (args.plugin_only or args.retain_layout):
        addon['AetherfallLayout.lua'] = layout_lua(client, mods)
        addon['AetherfallLazy.lua'] = lazy_lua()
        addon['AetherfallHUD.xml'] = hud_xml()
    for rel in PLUGIN_COHORT:
        original(rel)
    with read_pak(client / PAK) as old:
        changes = dict(addon)
        if args.plugin_only or args.retain_layout:
            # This mode updates installed code only. Generated layouts and their
            # load order are authoritative for the current native interface.
            toc = old.read('RelicCalc.toc').decode('utf-8-sig').replace('\r', '').splitlines()
            if any(name not in old.namelist() or name not in toc for name in addon):
                raise ValueError('Plugin-only update requires the existing Aetherfall Lua modules')
            changes = {name: data for name, data in changes.items() if old.read(name) != data}
            # Retain all installed geometry and widgets. Only replace this
            # controller's verified event script so nested native events cannot
            # swallow its timer between the two Lua controllers.
            xml = old.read('AetherfallHUD.xml')
            if b'name="AFUIEditorPanel"' not in xml and not args.mover_editor_update:
                raise ValueError('This Lua version requires --mover-editor-update for the installed older MoveUI layout')
            previous = b'AFHUD_OnEvent(event); AFUI_OnEvent(event);'
            if xml.count(previous) == 1:
                changes['AetherfallHUD.xml'] = xml.replace(previous, b'AFHUD_Dispatch(event);')
            elif b'AFHUD_Dispatch(event);' not in xml:
                raise ValueError('Unexpected Aetherfall HUD event dispatcher')
            if args.mover_editor_update:
                from mover_editor import update_xml
                changes['AetherfallHUD.xml'] = update_xml(changes.get('AetherfallHUD.xml', xml))
            if args.hud_repair_update:
                import target_details
                changes['AetherfallHUD.xml'] = target_details.update_xml(changes.get('AetherfallHUD.xml', xml))
                changes['AetherfallLayout.lua'] = target_details.update_design(old.read('AetherfallLayout.lua'))
                target_details.prepare(client, stage, original, extend_archive, read_pak)
            if args.target_overlay_update:
                from target_details import repair_overlay_xml
                changes['AetherfallHUD.xml'] = repair_overlay_xml(changes.get('AetherfallHUD.xml', xml))
            if args.hud_customization_update:
                import hud_customization
                changes['AetherfallHUD.xml'] = hud_customization.plugin_xml(changes.get('AetherfallHUD.xml', xml))
                customization_inventory = hud_customization.prepare(client, stage, original, extend_archive, read_pak, restore_border=args.minimap_border_update)
        else:
            toc = old.read('RelicCalc.toc').decode('utf-8-sig').replace('\r', '').splitlines()
            toc = [n for n in toc if n not in addon] + ADDON
            changes['RelicCalc.toc'] = ('\r\n'.join(toc) + '\r\n').encode('utf-8')
        if args.ui_registry_update or 'AetherfallRegistry.lua' in old.namelist():
            from ui_registry import generate
            changes['AetherfallRegistry.lua'] = generate(client, output, changes)
            toc = changes.get('RelicCalc.toc', old.read('RelicCalc.toc')).decode('utf-8-sig').replace('\r', '').splitlines()
            toc = [name for name in toc if name != 'AetherfallRegistry.lua']
            toc.insert(toc.index('AetherfallCore.lua') + 1, 'AetherfallRegistry.lua')
            changes['RelicCalc.toc'] = ('\r\n'.join(toc) + '\r\n').encode('utf-8')
        # Retained-layout transformations can return identical XML. Keep its
        # original compressed bytes too, including newer MoveUI layouts.
        changes = {n: d for n, d in changes.items() if n not in old.namelist() or old.read(n) != d}
        replacements = {n: d for n, d in changes.items() if n in old.namelist()}
        additions = {n: d for n, d in changes.items() if n not in old.namelist()}
        source = (client / PAK).read_bytes()
        payload = extend_archive(source, replacements, additions)
        (output / PAK).parent.mkdir(parents=True, exist_ok=True)
        (output / PAK).write_bytes(payload)
        with read_pak(output / PAK) as new:
            assert new.testzip() is None
            for name, data in changes.items():
                assert new.read(name) == data
            old_parts, new_parts = parts(source)[0], parts(payload)[0]
            for name in old.namelist():
                if name not in changes:
                    assert old_parts[name][0] == new_parts[name][0], name
    pub = sha((client / 'Pub.key').read_bytes())
    subprocess.run(['java', str(mods / 'transmog-menu/SignClientPackages.java'), str(client), str(output)], check=True)
    if sha((output / 'Pub.key').read_bytes()) != pub:
        raise ValueError('Signer changed the stock model key')
    (output / 'Pub.key').unlink()  # verified and never deployed
    subprocess.run(['java', str(mods / 'season-pass/VerifySignatures.java'), str(output), str(client)], check=True)
    for rel in PLUGIN_COHORT:
        files[rel] = (output / rel).read_bytes()

    if args.minimap_renderer_update or args.minimap_elements_update:
        customization_inventory = json.loads(original('Mods/InstalledMods.json'))
        customization_inventory['features']['AetherfallMinimap'] = [
            dict(path=rel, installedSha256=sha(data), bytes=len(data)) for rel, data in files.items()]
    if customization_inventory is not None:
        for records in customization_inventory['features'].values():
            for entry in records:
                rel = next((name for name in files if name.lower() == entry.get('path', '').lower()), None)
                if rel:
                    entry.update(installedSha256=sha(files[rel]), bytes=len(files[rel]))
        records = customization_inventory['features']['AetherfallHudCustomization']
        for rel in ('bin64/AetherfallCore.dll', PAK):
            data = files.get(rel) or (client / rel).read_bytes()
            records.append(dict(path=rel, installedSha256=sha(data), bytes=len(data)))
    if 'AetherfallRegistry.lua' in changes:
        if customization_inventory is None:
            customization_inventory = json.loads(original('Mods/InstalledMods.json'))
        for records in customization_inventory['features'].values():
            for entry in records:
                rel = next((name for name in files if name.lower() == entry.get('path', '').lower()), None)
                if rel:
                    entry.update(installedSha256=sha(files[rel]), bytes=len(files[rel]))
        customization_inventory['features']['AetherfallUIFoundation'] = [
            dict(path=rel, installedSha256=sha(data), bytes=len(data)) for rel, data in files.items()]
    if customization_inventory is not None:
        stage('Mods/InstalledMods.json', json.dumps(customization_inventory, indent=2).encode())

    extra_sources = {'../transmog-menu/WardrobeNative.lua': sha(addon['WardrobeNative.lua'])} if args.shared_timer_update else {}
    if args.hud_repair_update:
        extra_sources['../playerbots/PlayerBotBar.lua'] = sha(addon['PlayerBotBar.lua'])
    write_manifest(output, client, info, files, before, extra_sources)


def write_manifest(output, client, info, files, before, extra_sources=None, version=VERSION):
    record = dict(feature='aetherfall-core', version=version, clientRoot=str(client), gameDllImport=info,
                  files=[dict(path=rel, original=before.get(rel), installed=sha(data)) for rel, data in files.items()],
                  sourceHashes={p.name: sha(p.read_bytes()) for p in HERE.iterdir() if p.is_file() and p.suffix in ('.py', '.cpp', '.h', '.lua', '.md')},
                  testsRun=False, gameStarted=False)
    if 'bin64/AetherfallCore.dll' in files:
        record['nativeVersion'] = NATIVE_VERSION
    record['sourceHashes'].update(extra_sources or {})
    (output / 'manifest.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
    print('Built/staged', len(files), 'files; live client unchanged.')


if __name__ == '__main__':
    main()
