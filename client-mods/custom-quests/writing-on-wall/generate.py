"""Author The Unbroken Field with the existing Custom Quests package contract.

Produces ordinary native quest resources and independent AbstractQuestHandler
scripts. This is an authoring tool, not a replacement quest/runtime engine.
"""
import argparse
import json
from pathlib import Path
import re
from xml.etree import ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]


def element(parent, name, value):
    result = ET.SubElement(parent, name)
    result.text = str(value)
    return result


def xml(path, root):
    # Contents is consumed as native rich text, including whitespace between
    # tags. Pretty-printing <p><font> inserts visible padding into objective
    # popups. Keep HTML inline like the classic quest documents; arc.json is
    # the readable authoring source.
    if root.tag != 'HtmlPages':
        ET.indent(root, space='  ')
    path.write_bytes(b'<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(root, encoding='utf-8') + b'\n')


def page(root, name, paragraphs, actions=()):
    node = ET.SubElement(root, 'HtmlPage', name=name)
    body = ET.SubElement(ET.SubElement(ET.SubElement(node, 'Contents', cdata='true'), 'html'), 'body')
    for index, paragraph in enumerate(paragraphs):
        if index:
            element(body, 'p', ' ')
        element(body, 'p', paragraph)
    if actions:
        selects = ET.SubElement(node, 'Selects')
        for action, label in actions:
            element(selects, 'Act', label).set('href', 'HACTION_' + action)
    return body


def objective(step, bindings):
    npc = step.get('npc', step.get('npcs', [None])[0])
    binding = bindings[str(npc)]
    target = '[%dic:' + binding['dictionary'] + ']' if binding['dictionary'] else binding['label']
    return step['objective'].replace('{npc}', target)


def handler(quest, previous, bindings):
    qid = quest['id']
    name = '_' + str(qid) + quest['slug']
    steps = quest['steps']
    talk = sorted({quest['start'], quest['end']} | {s['npc'] for s in steps if s['type'] != 'hunt'})
    kills = sorted({i for s in steps if s['type'] == 'hunt' for i in s['npcs']})
    collects = [(index, s) for index, s in enumerate(steps) if s['type'] == 'collect']
    objects = [(index, s['npc']) for index, s in enumerate(steps) if s['type'] in ('inspect', 'collect') and bindings[str(s['npc'])]['ai'] == 'quest_use_item']
    lines = [
        'package quest.verteron;', '',
        'import static com.aionemu.gameserver.model.DialogAction.*;', '',
        'import com.aionemu.gameserver.model.gameobjects.Npc;',
        'import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;',
        'import com.aionemu.gameserver.questEngine.model.QuestActionType;',
        'import com.aionemu.gameserver.questEngine.model.QuestEnv;',
        'import com.aionemu.gameserver.questEngine.model.QuestState;',
        'import com.aionemu.gameserver.questEngine.model.QuestStatus;', '',
        '/** The Unbroken Field: ' + quest['title'] + '. Native quest persistence; no core overrides. */',
        'public class ' + name + ' extends AbstractQuestHandler {', '',
        '\tpublic ' + name + '() {', '\t\tsuper(' + str(qid) + ');', '\t}', '',
        '\t@Override', '\tpublic void register() {',
        '\t\tqe.registerQuestNpc(' + str(quest['start']) + ').addOnQuestStart(questId);',
        '\t\tfor (int npc : new int[] { ' + ', '.join(map(str, talk)) + ' })',
        '\t\t\tqe.registerQuestNpc(npc).addOnTalkEvent(questId);'
    ]
    if kills:
        lines += ['\t\tfor (int npc : new int[] { ' + ', '.join(map(str, kills)) + ' })', '\t\t\tqe.registerQuestNpc(npc).addOnKillEvent(questId);']
    if collects:
        lines.insert(lines.index('import com.aionemu.gameserver.questEngine.model.QuestStatus;') + 1,
                     'import com.aionemu.gameserver.services.QuestService;')
        for _, step in collects:
            lines += ['\t\tqe.registerOnGetItem(' + str(step['item']) + ', questId);']
        lines += ['\t\tqe.registerOnEnterWorld(questId);']
    for _, npc in objects:
        lines += ['\t\tqe.registerCanAct(questId, ' + str(npc) + ');']
    lines += ['\t}', '', '\t@Override', '\tpublic boolean onDialogEvent(QuestEnv env) {',
              '\t\tif (env.getPlayer().getWorldId() != 210030000 || !(env.getVisibleObject() instanceof Npc))',
              '\t\t\treturn false;',
              '\t\tQuestState previous = env.getPlayer().getQuestStateList().getQuestState(' + str(previous) + ');',
              '\t\tif (previous == null || previous.getStatus() != QuestStatus.COMPLETE)', '\t\t\treturn false;',
              '\t\tQuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);',
              '\t\tif (state == null || state.isStartable())',
              '\t\t\treturn sendQuestNoneDialog(env, ' + str(quest['start']) + ');',
              '\t\tint npc = env.getTargetId(), action = env.getDialogActionId();',
              '\t\tif (state.getStatus() == QuestStatus.REWARD) {',
              '\t\t\tif (npc != ' + str(quest['end']) + ')', '\t\t\t\treturn false;',
              '\t\t\tboolean handled = sendQuestEndDialog(env);',
              '\t\t\t// Native completion may succeed before a follow-up dialogue declines.',
              '\t\t\t// Consume that confirmation instead of opening its action ID as HTML.',
              '\t\t\tif (!handled && state.getStatus() == QuestStatus.COMPLETE)',
              '\t\t\t\treturn sendQuestSelectionDialog(env);',
              '\t\t\treturn handled;', '\t\t}',
              '\t\t// A late/repeated confirmation must not reward again or fall through to page 23.',
              '\t\tif (state.getStatus() == QuestStatus.COMPLETE && npc == ' + str(quest['end']),
              '\t\t\t&& action >= SELECTED_QUEST_REWARD1 && action <= SELECTED_QUEST_NOREWARD)',
              '\t\t\treturn sendQuestSelectionDialog(env);',
              '\t\tif (state.getStatus() != QuestStatus.START)', '\t\t\treturn false;',
              '\t\tint step = state.getQuestVarById(0);']
    for index, step in enumerate(steps):
        if step['type'] == 'hunt':
            continue
        if step['type'] == 'collect':
            # quest_use_item owns the use bar, death and native loot window.
            # A successful interaction only admits looting; pickup advances it.
            lines += ['\t\tif (step == ' + str(index) + ' && npc == ' + str(step['npc']) + ')',
                      '\t\t\treturn action == USE_OBJECT;', '']
            continue
        lines += ['\t\tif (step == ' + str(index) + ' && npc == ' + str(step['npc']) + ') {',
                  '\t\t\tif (action == QUEST_SELECT || action == USE_OBJECT)',
                  '\t\t\t\treturn sendQuestDialog(env, SELECT' + str(index + 2) + ');',
                  '\t\t\tif (action == SETPRO1)',
                  '\t\t\t\treturn defaultCloseDialog(env, ' + str(index) + ', ' + str(index + 1) + ');', '\t\t}']
    if collects:
        # This authoring form has one collection followed by the report. Keep
        # its existing 0/1 stages so already accepted quests need no reset.
        assert len(steps) == 1 and len(collects) == 1
        lines += ['\t\tif (step == 1 && npc == ' + str(quest['end']) + ' && !QuestService.collectItemCheck(env, false)) {',
                  '\t\t\tchangeQuestStep(env, 1, 0);', '\t\t\treturn sendQuestDialog(env, SELECT6);', '\t\t}']
    lines += ['\t\tif (step == ' + str(len(steps)) + ' && npc == ' + str(quest['end']) + ') {',
              '\t\t\tif (action == QUEST_SELECT || action == USE_OBJECT)', '\t\t\t\treturn sendQuestDialog(env, SELECT5);',
              '\t\t\tif (action == SELECT_QUEST_REWARD) {',
              *(['\t\t\t\tif (!QuestService.collectItemCheck(env, true))', '\t\t\t\t\treturn false;'] if collects else []),
              '\t\t\t\tchangeQuestStep(env, step, step, true);', '\t\t\t\treturn sendQuestEndDialog(env);',
              '\t\t\t}', '\t\t}', '\t\treturn false;', '\t}', '']
    if collects:
        index, step = collects[0]
        lines += ['\t@Override', '\tpublic boolean onGetItemEvent(QuestEnv env) {',
                  '\t\tQuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);',
                  '\t\tif (state == null || state.getStatus() != QuestStatus.START || state.getQuestVarById(0) != 0)',
                  '\t\t\treturn false;',
                  '\t\treturn QuestService.collectItemCheck(env, false) && changeQuestStep(env, 0, 1);', '\t}', '',
                  '\t@Override', '\tpublic boolean onEnterWorldEvent(QuestEnv env) {',
                  '\t\tQuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);',
                  '\t\tif (state == null || state.getStatus() != QuestStatus.START)', '\t\t\treturn false;',
                  '\t\tboolean collected = QuestService.collectItemCheck(env, false);',
                  '\t\tint step = state.getQuestVarById(0);',
                  '\t\tif (step == 0 && collected)', '\t\t\treturn changeQuestStep(env, 0, 1);',
                  '\t\tif (step == 1 && !collected)', '\t\t\treturn changeQuestStep(env, 1, 0);',
                  '\t\treturn false;', '\t}', '']
    if kills:
        lines += ['\t@Override', '\tpublic boolean onKillEvent(QuestEnv env) {',
                  '\t\tQuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);',
                  '\t\tif (env.getPlayer().getWorldId() != 210030000 || state == null || state.getStatus() != QuestStatus.START)',
                  '\t\t\treturn false;', '\t\tint npc = env.getTargetId();']
        for index, step in enumerate(steps):
            if step['type'] != 'hunt':
                continue
            condition = ' || '.join('npc == ' + str(i) for i in step['npcs'])
            lines += ['\t\tif (state.getQuestVarById(0) == ' + str(index) + ' && (' + condition + ')) {',
                      '\t\t\tint count = state.getQuestVarById(1) + 1;',
                      '\t\t\tif (count >= ' + str(step['count']) + ') {',
                      '\t\t\t\tstate.setQuestVarById(1, 0);',
                      '\t\t\t\treturn changeQuestStep(env, ' + str(index) + ', ' + str(index + 1) + ');',
                      '\t\t\t}', '\t\t\tstate.setQuestVarById(1, count);', '\t\t\tupdateQuestStatus(env);',
                      '\t\t\treturn true;', '\t\t}']
        lines += ['\t\treturn false;', '\t}', '']
    if objects:
        lines += ['\t@Override', '\tpublic boolean onCanAct(QuestEnv env, QuestActionType type, Object... objects) {',
                  '\t\tQuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);',
                  '\t\tif (type != QuestActionType.ACTION_ITEM_USE || env.getPlayer().getWorldId() != 210030000',
                  '\t\t\t|| state == null || state.getStatus() != QuestStatus.START)', '\t\t\treturn false;',
                  '\t\treturn ' + '\n\t\t\t|| '.join('(state.getQuestVarById(0) == ' + str(i) + ' && env.getTargetId() == ' + str(npc) + ')' for i, npc in objects) + ';', '\t}', '']
    return name + '.java', '\n'.join(lines + ['}', ''])


def generate(root):
    arc = json.loads((HERE / 'arc.json').read_text(encoding='utf-8'))
    bindings = json.loads((HERE / 'bindings.json').read_text(encoding='utf-8'))
    quests = ET.Element('quests')
    previous = arc['prerequisite']
    for index, quest in enumerate(arc['quests']):
        qid = quest['id']
        assert qid == 14980 + index and len(quest['steps']) <= 3
        folder = root / 'client-mods/custom-quests/Quests' / (str(qid) + '-' + quest['slug'])
        folder.mkdir(parents=True, exist_ok=True)
        (folder / 'quest.json').write_text(json.dumps(dict(Id=qid, Title=quest['title']), indent=2) + '\n', encoding='utf-8')
        meta = ET.Element('quest')
        fields = dict(id=qid, name='Q' + str(qid), dev_name='', desc='STR_QUEST_NAME_Q' + str(qid),
                      category1='quest', category2='STR_QUEST_ZONE02', f_mission=0, max_repeat_count=1,
                      client_level=quest['level'] + 1, minlevel_permitted=quest['level'], maxlevel_permitted=0,
                      cannot_share=1, gender_permitted='all', reward_exp1=quest['exp'], reward_gold1=quest['gold'],
                      class_permitted='warrior scout mage cleric engineer artist fighter knight assassin ranger wizard elementallist chanter priest gunner bard rider',
                      race_permitted='pc_light', finished_quest_cond1='Q' + str(previous))
        for name, value in fields.items():
            element(meta, name, value)
        for stage, step in enumerate(quest['steps']):
            if step['type'] == 'collect':
                item_name = step['item_name']
                for name, value in dict(collect_progress=stage, collect_item1=f"{item_name} {step['count']}",
                                        drop_monster_1=bindings[str(step['npc'])]['key'], drop_item_1=item_name,
                                        drop_prob_1=100, drop_each_member_1=1, check_item1_1=f"{item_name} {step['count']}").items():
                    element(meta, name, value)
        xml(folder / 'quest.xml', meta)
        strings = ET.Element('strings')
        string = ET.SubElement(strings, 'string')
        for name, value in dict(id=1801365 + index, name='STR_QUEST_NAME_Q' + str(qid), body=quest['title']).items():
            element(string, name, value)
        xml(folder / 'strings.xml', strings)
        reg = ET.Element('quest_data_driven')
        for name, value in dict(id=qid, name='Q' + str(qid), dev_name='', category_acquire_='Talk',
                                value0_acquire_=bindings[str(quest['start'])]['key'], reward_npc_name=bindings[str(quest['end'])]['key']).items():
            element(reg, name, value)
        progress = ET.SubElement(reg, 'progress_info')
        rows = []
        for stage, step in enumerate(quest['steps']):
            data = ET.SubElement(progress, 'data')
            if step['type'] == 'hunt':
                element(data, 'category_progress_', 'Hunt')
                # Native comma-separated alternatives share one count; semicolons separate objectives.
                element(data, 'value0_progress_', ', '.join(bindings[str(i)]['key'] for i in step['npcs']) + ' ' + str(step['count']) + ';')
                for npc in step['npcs']:
                    # Progress(n) compares the entire packed quest variable.
                    # SECTION_0 is the stage; SECTION_1 is the saved kill count.
                    # Native Q10020 uses this condition until its quota is met.
                    # CSV num is the number of following NPC keys, not kills.
                    rows.append(f"{qid},Progress(SECTION_0=={stage}; SECTION_1<{step['count']}),,killedByUser,,1,{bindings[str(npc)]['key']}\r\n")
            elif step['type'] == 'collect':
                element(data, 'category_progress_', 'CollectItem')
                element(data, 'value0_progress_', bindings[str(quest['end'])]['key'])
                element(data, 'value1_progress_', bindings[str(step['npc'])]['key'])
                element(data, 'value5_progress_', stage)
                # Native Q1042 provides item-source guidance through this
                # existing script table. Keep it in the Custom Quests loader's
                # supported CSV rather than introducing another engine path.
                rows.append(f"{qid},Progress(SECTION_0=={stage}),{step['item_name']},dropMonster,,1,{bindings[str(step['npc'])]['key']}\r\n")
            else:
                element(data, 'category_progress_', 'TalkFOBJ' if step['type'] == 'inspect' else 'Talk')
                element(data, 'value0_progress_', bindings[str(step['npc'])]['key'] + (', 0' if step['type'] == 'inspect' else ''))
        data = ET.SubElement(progress, 'data')
        element(data, 'category_progress_', 'Talk')
        element(data, 'value0_progress_', bindings[str(quest['end'])]['key'])
        xml(folder / 'registration.xml', reg)
        (folder / 'quest_script_monster.csv').write_bytes(''.join(rows).encode('utf-8'))
        dialog = ET.Element('HtmlPages')
        page(dialog, 'select1', quest['offer'], [('ASK_QUEST_ACCEPT', quest['accept'])])
        page(dialog, 'ask_quest_accept', [quest['summary']], [('QUEST_ACCEPT_1', quest['accept']), ('QUEST_REFUSE_1', 'I need more time.')])
        page(dialog, 'quest_accept_1', [objective(quest['steps'][0], bindings)], [('FINISH_DIALOG', 'Set out.')])
        page(dialog, 'quest_refuse_1', ['Return when you are ready. The watch will keep your place in the investigation.'], [('FINISH_DIALOG', 'Leave.')])
        for stage, step in enumerate(quest['steps']):
            if step['type'] not in ('hunt', 'collect'):
                page(dialog, 'select' + str(stage + 2), step['text'], [('SETPRO1', step['reply'])])
        if any(s['type'] == 'collect' for s in quest['steps']):
            page(dialog, 'select6', ['Bring me the Copied Seal Impression from the stolen Tolbas goods. I must examine the wax itself.'], [('FINISH_DIALOG', 'I will recover it.')])
        page(dialog, 'select5', quest['turnin'], [('SELECT_QUEST_REWARD', 'Complete the report.')])
        page(dialog, 'select_quest_reward1', [quest['turnin'][-1]])
        body = page(dialog, 'quest_summary', [])
        summary_steps = ET.SubElement(body, 'steps')
        objectives = [objective(s, bindings) for s in quest['steps']]
        objectives += ['Report to [%dic:' + bindings[str(quest['end'])]['dictionary'] + '].']
        for stage, text in enumerate(objectives):
            if stage < len(quest['steps']) and quest['steps'][stage]['type'] == 'hunt':
                count = quest['steps'][stage]['count']
                # Classic Q10035/10504 use the third parameter of each step
                # for its live hunt count (0/required, 1/required, ...).
                counter = '([%' + str(stage * 3 + 2) + ']/' + str(count) + ')'
                fixed = '(' + str(count) + ')'
                text = text.replace(fixed, counter) if fixed in text else text.rstrip('.') + ' ' + counter + '.'
            # Native rich text supports paragraph alignment (credit.html uses
            # center/right). Be explicit for the shared tracker/update text.
            p = ET.SubElement(ET.SubElement(summary_steps, 'step'), 'p', visible='[%' + str(stage * 3) + ']', align='left')
            element(p, 'font', text).set('color', '[%' + str(stage * 3 + 1) + ']')
            if stage < len(quest['steps']) and quest['steps'][stage]['type'] == 'collect':
                # The classic supply quest renders its live inventory counter
                # after the objective paragraph with this native placeholder.
                p.tail = '[%collectitem]'
        element(ET.SubElement(body, 'p'), 'font', quest['summary']).set('font_xml', 'quest_summary')
        page(dialog, 'quest_complete', [quest['complete']])
        for name, title in [('select_acqusitive_quest_desc', 'New quest: [%questname]'), ('select_progressive_quest_desc', 'In progress: [%questname]')]:
            element(ET.SubElement(ET.SubElement(dialog, 'HtmlPage', name=name), 'Selects'), 'Act', title)
        xml(folder / 'dialog.html', dialog)
        template = ET.SubElement(quests, 'quest', id=str(qid), name=quest['title'], nameId=str(1801365 + index),
                                 quest_zone='Verteron', minlevel_permitted=str(quest['level']), max_repeat_count='1', cannot_share='true', race_permitted='ELYOS', category='QUEST')
        collects = [s for s in quest['steps'] if s['type'] == 'collect']
        if collects:
            items = ET.SubElement(template, 'collect_items')
            for step in collects:
                ET.SubElement(items, 'collect_item', item_id=str(step['item']), count=str(step['count']))
        ET.SubElement(template, 'rewards', gold=str(quest['gold']), exp=str(quest['exp']))
        for step in collects:
            ET.SubElement(template, 'quest_drop', npc_id=str(step['npc']), item_id=str(step['item']), chance='100', drop_each_member='1')
        ET.SubElement(ET.SubElement(template, 'start_conditions'), 'finished', quest_id=str(previous))
        if collects:
            work = ET.SubElement(template, 'quest_work_items')
            for step in collects:
                ET.SubElement(work, 'quest_work_item', item_id=str(step['item']))
        filename, script = handler(quest, previous, bindings)
        script_path = root / 'game-server/data/handlers/quest/verteron' / filename
        script_path.parent.mkdir(parents=True, exist_ok=True)
        script_path.write_text(script, encoding='utf-8', newline='\n')
        previous = qid
    # XmlMerger resolves imports only in static_data.xml, not inside imported
    # quest files. Keep these normal templates in the existing quests root.
    target = root / 'game-server/data/static_data/quest_data/quest_data.xml'
    current = target.read_bytes()
    newline = b'\r\n' if b'\r\n' in current else b'\n'
    start = b'\t<!-- BEGIN AETHERFALL THE UNBROKEN FIELD -->'
    end = b'\t<!-- END AETHERFALL THE UNBROKEN FIELD -->'
    ET.indent(quests, space='\t')
    entries = b''.join(ET.tostring(q, encoding='utf-8') for q in quests).replace(b'\n', newline)
    block = start + newline + b'\t' + entries.rstrip() + newline + end + newline
    if start in current:
        current = re.sub(re.escape(start) + rb'.*?' + re.escape(end) + rb'\r?\n', lambda _: block, current, flags=re.S)
    else:
        assert all(ET.fromstring(current).find(f"quest[@id='{q['id']}']") is None for q in arc['quests'])
        current = current.replace(b'</quests>', block + b'</quests>')
    # Replace atomically so an interrupted authoring run cannot truncate the
    # shared native quest table.
    temporary = target.with_name(target.name + '.writing-on-wall.tmp')
    temporary.write_bytes(current)
    temporary.replace(target)
    item = json.loads((HERE / 'loot-item.json').read_text(encoding='utf-8'))
    target = root / 'game-server/data/static_data/items/item_templates.xml'
    current = target.read_bytes()
    prototype = re.search(rb'\t<item_template id="' + str(item['prototype']).encode() + rb'"[^>]*>.*?</item_template>', current, re.S)
    assert prototype is not None
    native = ET.fromstring(prototype.group())
    native.attrib.update(id=str(item['id']), name=item['name'], cName=item['clientName'], desc=str(item['nameStringId']))
    ET.indent(native, space='\t')
    start = b'\t<!-- BEGIN AETHERFALL UNBROKEN FIELD ITEMS -->'
    end = b'\t<!-- END AETHERFALL UNBROKEN FIELD ITEMS -->'
    newline = b'\r\n' if b'\r\n' in current else b'\n'
    block = start + newline + b'\t' + ET.tostring(native, encoding='utf-8').replace(b'\n', newline) + newline + end + newline
    if start in current:
        current = re.sub(re.escape(start) + rb'.*?' + re.escape(end) + rb'\r?\n', lambda _: block, current, flags=re.S)
    else:
        assert b'id="' + str(item['id']).encode() + b'"' not in current
        current = current.replace(b'</item_templates>', block + b'</item_templates>')
    temporary = target.with_name(target.name + '.writing-on-wall.tmp')
    temporary.write_bytes(current)
    temporary.replace(target)
    print('Generated 16 Custom Quests packages, native templates, and independent quest handlers.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    generate(parser.parse_args().root.resolve())
