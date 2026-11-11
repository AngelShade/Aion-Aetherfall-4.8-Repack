"""Reviewed presentation-only differences from the immutable monolith trace.

No scenario, native game mutation, input, saved byte or unrelated widget is excluded.
The replacement options window owns the first group of widget-name prefixes.
Editor geometry and interaction remain compared; only its Skin setters differ.
"""
from collections import Counter
import gzip
import json
import re


OPTIONS_PREFIXES = (
    'AFUIOptions', 'AFUISide', 'AFUIPanel', 'AFUITab', 'AFUIHeader',
    'AFUIRuler', 'AFUIRow', 'AFUICode', 'AFUIStatus', 'AFUIClose',
)
EDITOR_PREFIXES = ('AFUIEditor', 'AFUINudge', 'AFUIMove', 'AFUIGrid', 'AFUIGuide', 'AFUISelection')
# New passive plugin artwork, never a native game dialog/widget. Native
# WidgetShown/Backdrop calls remain in the strict cumulative-state comparison.
UNITFRAME_WIDGET = re.compile(r'^AFHF(?:Player|TargetTarget|Target|Party[1-6])(?:Backdrop|Health|Power|DP|Level|Identity|State|Star[1-5])')
# The outline of a mover box: four line images beside the box, new with the flat skin.
MOVER_BORDER = re.compile(r'^AFUIMove\d+Edge(Top|Bottom|Left|Right)$')
SKIN_SETTERS = frozenset((
    'SetWidgetFont', 'SetWidgetPreset', 'SetWidgetUIImage',
    'SetWidgetUIImageById', 'SetWidgetAlpha',
))
SKIN_ATTRIBUTES = frozenset(('color', 'font_color', 'text_color', 'image_color',
                             'textColor', 'textColorOver', 'textColorSelected', 'textColorDisabled'))


def widget_name(record):
    if record[1] != 'widget':
        return None
    args = record[3]
    index = 2 if record[2] == 'AddNewWidget' else 1
    return args[index] if len(args) > index and isinstance(args[index], str) else None


# Aetherfall's own bot labels, added inside the stock target and party layouts by
# identity_frames.py. Their wording is interface presentation ("Equip: Good", "Rank 3/5"
# since 1.4.3); every stock widget's text and visibility stay strictly compared.
BOT_LABEL_TEXT = re.compile(r'(?:^|/)af_identity_(?:rank|gear_[a-z]+|name_[a-z]+)$')


def excluded(record):
    if (record[1], record[2]) in (('AFCore', 'SetText'), ('bridge', 'settext')) and len(record[3]) > 1             and isinstance(record[3][1], str) and BOT_LABEL_TEXT.search(record[3][1]):
        return 'Aetherfall bot label text'
    name = widget_name(record)
    # The preserved-scenario fixture reserves the monolith's original plugin
    # slots by dialog name. Its options backdrop policy is presentation of that
    # same owned AFUIOptions widget, even at the AFCore/native boundary.
    if (record[1], record[2]) in (('AFCore','Backdrop'), ('bridge','backdrop'),
                                    ('AFCore','Dialog'), ('bridge','dialog')):
        if record[3] and record[3][0] == 0x217:
            name = 'AFUIOptions'
    if name is None:
        return None
    if name == 'AFHFrames' or UNITFRAME_WIDGET.match(name):
        return 'unit-frame plugin presentation'
    if name.startswith(OPTIONS_PREFIXES):
        return 'options widget presentation'
    if MOVER_BORDER.match(name):
        return 'mover box border line'
    # Attaching a border line names the overlay first and the line second.
    if record[2] == 'AddWidgetChildWidget' and len(record[3]) > 2 and isinstance(record[3][2], str) and MOVER_BORDER.match(record[3][2]):
        return 'mover box border line'
    if name.startswith(EDITOR_PREFIXES):
        if record[2] in SKIN_SETTERS:
            return 'editor Skin setter'
        if (record[2] == 'SetWidgetAttr' and len(record[3]) > 2
                and record[3][2] in SKIN_ATTRIBUTES):
            return 'editor Skin color'
    return None


def records(path, excluded_counts=None):
    with gzip.open(path, 'rt', encoding='utf-8') as stream:
        for raw in stream:
            record = json.loads(raw)
            reason = excluded(record)
            if reason:
                if excluded_counts is not None:
                    excluded_counts[reason] += 1
                continue
            yield record


def self_test():
    def widget(name, api='SetWidgetText', tail=('value',)):
        args = ['RelicCalc', name, *tail]
        if api == 'AddNewWidget':
            args = ['RelicCalc', 'static', name]
        return ['unchanged-scenario', 'widget', api, args, [None]]
    assert excluded(widget('AFUIOptionsTitle'))
    assert excluded(widget('AFUIRow3Value', 'AddNewWidget'))
    assert excluded(widget('AFUIEditorTitle', 'SetWidgetFont'))
    assert excluded(widget('AFUISelection1', 'SetWidgetUIImage'))
    assert excluded(widget('AFUISelection1', 'SetWidgetRect')) is None
    assert excluded(['options', 'bridge', 'backdrop', [0x217, .9, None, 'any', False], [True]])
    assert excluded(widget('AFUINudgeName', 'SetWidgetAttr', ('color', '1,1,1,1')))
    assert excluded(widget('AFHFrames', 'SetWidgetRect'))
    assert excluded(widget('AFHFParty6HealthFill', 'SetWidgetUIImage'))
    assert excluded(widget('AFHFTargetStar5', 'SetWidgetUIImage'))
    assert excluded(['s', 'bridge', 'settext', [11, 'container7/af_identity_rank', 'Rank 3/5'], [True]])
    assert not excluded(['s', 'bridge', 'settext', [11, 'container7/name', 'Stock'], [True]])
    assert not excluded(['s', 'bridge', 'widgetshown', [33, 'ctn_member1/af_identity_rank', False], [True]])
    # Prefix filtering must not erase movers, native operations, input, saves,
    # editor geometry/visibility, or an entire scenario containing options work.
    for record in (
        widget('AFUIMovers'), widget('AFHTop'), widget('AFUIEditorTitle'),
        widget('AFUIEditorPanel', 'SetWidgetRect'),
        widget('AFUIEditorPanel', 'SetWidgetVisible'),
        widget('AFUIEditorTitle', 'SetWidgetAttr', ('enabled', '1')),
        ['options', 'bridge', 'pin', [11, 1, 2], [True]],
        ['options', 'bridge', 'widgetshown', [11, 'container7/hp', False], [True]],
        ['options', 'AFCore', 'WidgetShown', [11, 'container7/hp', False], []],
        ['options', 'bridge', 'backdrop', [11, 0, None, 'any', False], [True]],
        ['options', 'saved', 'ui-test.cfg', ['eA=='], []],
        ['options', 'input', 'click', ['Row1Value'], []],
        ['options', 'AFCore', 'SaveFile', ['ui-test.cfg', 'x'], []],
    ):
        assert excluded(record) is None, record


if __name__ == '__main__':
    self_test()
    print('PASS: presentation exclusions preserve native behavior and saved bytes')
