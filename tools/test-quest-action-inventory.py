"""Verify native Java AST evidence, including its deliberate authorization boundaries."""
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

TOOL = Path(__file__).with_name('QuestHandlerActionInventory.java').resolve()


class QuestActionInventoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        output_root = Path('D:/Proiecte/Project Restructure/Aion Development Workspace/staging/output')
        output_root.mkdir(parents=True, exist_ok=True)
        cls.temporary = tempfile.TemporaryDirectory(prefix='aion-quest-ast-', dir=output_root)
        cls.root = Path(cls.temporary.name)
        cls.sources = cls.root / 'source'
        cls.sources.mkdir()
        source = '''package quest.test;
class A extends AbstractQuestHandler {
 static final int questId=1001;
 A(){super(questId);}
 public boolean onDialogEvent(QuestEnv env) {
  int var=qs.getQuestVarById(0);
  if(qs==null)return false;
  String note="🤖 sendQuestDialog(env, 9999)";
  data.forEach(value -> value.inspect());
  // sendQuestDialog(env, 8888);
  if(qs.getStatus()==QuestStatus.START) {
   switch(env.getTargetId()) {
    case 203071:
    case 203072:
     if(var==0)return sendQuestDialog(env,1011);
     break;
    default:return false;
   }
  } else { return sendQuestEndDialog(env); }
  return false;
 }
}'''
        (cls.sources / 'A.java').write_text(source, encoding='utf-8')
        (cls.sources / 'B.java').write_text(source.replace('class A ', 'class B ').replace(' A()', ' B()')
                                          .replace('1001', '2001').replace('203071', '203518'), encoding='utf-8')
        cls.report = cls.root / 'facts.json'
        result = subprocess.run(['java', str(TOOL), str(cls.sources), str(cls.report)],
                                capture_output=True, text=True)
        if result.returncode:
            raise AssertionError(result.stdout + result.stderr)
        cls.facts = json.loads(cls.report.read_text(encoding='utf-8'))
        cls.row = cls.facts['sources'][0]
        cls.method = next(m for m in cls.row['methods'] if m['name'] == 'onDialogEvent')

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def test_no_resolution_or_game_initialization_needed(self):
        self.assertEqual(len(self.facts['sources']), 2)
        self.assertEqual(self.row['extends'], 'AbstractQuestHandler')

    def test_actual_calls_exclude_comments_and_string_literals(self):
        pages = [c for c in self.method['calls'] if c['call'] == 'sendQuestDialog']
        self.assertEqual(len(pages), 1)
        self.assertEqual(pages[0]['arguments'], ['env', '1011'])

    def test_exact_lexical_guards_and_switch_labels(self):
        call = next(c for c in self.method['calls'] if c['call'] == 'sendQuestDialog')
        self.assertTrue(any('QuestStatus.START' in c.get('expression', '') for c in call['lexicalBranches']))
        self.assertTrue(any('203072' in c.get('labels', []) for c in call['lexicalBranches']))
        self.assertTrue(any('var == 0' in c.get('expression', '') for c in call['lexicalBranches']))

    def test_source_positions_and_binding(self):
        call = next(c for c in self.method['calls'] if c['call'] == 'sendQuestDialog')
        source = (self.sources / 'A.java').read_bytes().decode('utf-8').encode('utf-16le')
        self.assertEqual(source[call['start'] * 2:call['end'] * 2].decode('utf-16le'), 'sendQuestDialog(env,1011)')
        import hashlib
        self.assertEqual(self.row['sha256'], hashlib.sha256((self.sources / 'A.java').read_bytes()).hexdigest())

    def test_aliases_fields_and_native_constructor_id(self):
        self.assertIn({'name': 'questId', 'initializer': '1001'}, self.row['fields'])
        self.assertTrue(any(v['name'] == 'var' and 'getQuestVarById(0)' in v['initializer'] for v in self.method['variables']))
        constructor = next(m for m in self.row['methods'] if m['name'] == '<init>')
        self.assertEqual(constructor['calls'][0]['arguments'], ['questId'])

    def test_grouping_is_shape_not_authorization(self):
        family = self.method['family']
        self.assertEqual(self.facts['families'][family], ['A.java', 'B.java'])
        self.assertFalse(self.row['declaresBotActions'])
        self.assertIn('not action authorization', self.facts['authority'])

    def test_guard_return_and_fallthrough_limit_are_explicit(self):
        self.assertTrue(any('guard-return dominance' in line and 'fallthrough' in line for line in self.facts['limits']))
        self.assertTrue(any(node['kind'] == 'RETURN' for node in self.method['requiresReview']))

    def test_bad_java_cannot_emit_success_evidence(self):
        bad = self.root / 'bad'
        bad.mkdir()
        (bad / 'Bad.java').write_text('class Bad { void broken( }')
        output = self.root / 'bad.json'
        output.write_text('stale success')
        result = subprocess.run(['java', str(TOOL), str(bad), str(output)], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
