import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Parse-only native handler evidence. Never loads handlers, initializes the game, or authorizes actions. */
public final class QuestHandlerActionInventory {
    private static final Set<String> ACTIONS = Set.of("sendQuestDialog", "sendDialogPacket", "sendQuestStartDialog",
        "sendQuestEndDialog", "sendQuestSelectionDialog", "defaultCloseDialog", "checkQuestItems", "checkItemExistence",
        "collectItemCheck", "useQuestObject", "defaultOnKillEvent", "defaultOnGetItemEvent", "changeQuestStep",
        "setQuestVar", "setQuestVarById", "setStatus", "startQuest", "finishQuest", "giveQuestItem", "removeQuestItem",
        "playQuestMovie", "questTimerStart", "questTimerEnd", "registerQuestNpc", "registerQuestItem",
        "registerQuestSkill", "addOnTalkEvent", "addOnKillEvent", "addOnQuestStart", "addOnAttackEvent",
        "applyEffectDirectly", "teleportTo", "teleportToNpc", "setFlightTeleportId", "getItemCountByItemId", "super");
    private static String name(ExpressionTree expression) {
        if (expression instanceof MemberSelectTree member) return member.getIdentifier().toString();
        if (expression instanceof IdentifierTree identifier) return identifier.getName().toString();
        return expression.toString();
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static final class Shape extends TreeScanner<Void, StringBuilder> {
        @Override public Void scan(Tree tree, StringBuilder result) {
            if (tree != null) result.append('|').append(tree.getKind());
            return super.scan(tree, result);
        }
        @Override public Void visitMethodInvocation(MethodInvocationTree node, StringBuilder result) {
            result.append(':').append(name(node.getMethodSelect()));
            return super.visitMethodInvocation(node, result);
        }
        @Override public Void visitMemberSelect(MemberSelectTree node, StringBuilder result) {
            result.append(':').append(node.getIdentifier());
            return super.visitMemberSelect(node, result);
        }
    }
    private static final class Facts extends TreeScanner<Void, Void> {
        final CompilationUnitTree unit;
        final SourcePositions positions;
        final List<Map<String, Object>> calls = new ArrayList<>(), branches = new ArrayList<>(), variables = new ArrayList<>(), exceptional = new ArrayList<>();
        final List<Map<String, Object>> path = new ArrayList<>();
        Facts(CompilationUnitTree unit, SourcePositions positions) { this.unit = unit; this.positions = positions; }
        Map<String, Object> location(Tree tree) {
            var result = new LinkedHashMap<String, Object>();
            long start = positions.getStartPosition(unit, tree), end = positions.getEndPosition(unit, tree);
            result.put("start", start); result.put("end", end);
            result.put("line", start < 0 ? -1 : unit.getLineMap().getLineNumber(start));
            return result;
        }
        void branch(String kind, String expression, String arm, Tree tree) {
            var entry = location(tree); entry.put("kind", kind); entry.put("expression", expression); entry.put("arm", arm);
            branches.add(entry); path.add(entry); scan(tree, null); path.removeLast();
        }
        @Override public Void visitIf(IfTree node, Void unused) {
            scan(node.getCondition(), null);
            branch("IF", node.getCondition().toString(), "true", node.getThenStatement());
            if (node.getElseStatement() != null) branch("IF", node.getCondition().toString(), "false", node.getElseStatement());
            return null;
        }
        @Override public Void visitSwitch(SwitchTree node, Void unused) {
            scan(node.getExpression(), null);
            for (CaseTree arm : node.getCases()) {
                var entry = location(arm); entry.put("kind", "SWITCH"); entry.put("expression", node.getExpression().toString());
                entry.put("labels", arm.getLabels().stream().map(Object::toString).toList());
                entry.put("form", arm.getCaseKind().toString()); branches.add(entry); path.add(entry);
                if (arm.getStatements() != null) arm.getStatements().forEach(statement -> scan(statement, null));
                if (arm.getBody() != null) scan(arm.getBody(), null);
                path.removeLast();
            }
            return null;
        }
        @Override public Void visitVariable(VariableTree node, Void unused) {
            var entry = location(node); entry.put("name", node.getName().toString()); entry.put("type", node.getType() == null ? "<inferred>" : node.getType().toString());
            entry.put("initializer", node.getInitializer() == null ? null : node.getInitializer().toString());
            variables.add(entry); return super.visitVariable(node, unused);
        }
        @Override public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            String call = name(node.getMethodSelect());
            if (ACTIONS.contains(call) || call.startsWith("spawn") || call.startsWith("registerOn") || call.equals("schedule")) {
                var entry = location(node); entry.put("call", node.getMethodSelect().toString());
                entry.put("arguments", node.getArguments().stream().map(Object::toString).toList());
                entry.put("lexicalBranches", List.copyOf(path)); calls.add(entry);
            }
            return super.visitMethodInvocation(node, unused);
        }
        @Override public Void scan(Tree tree, Void unused) {
            if (tree != null && Set.of(Tree.Kind.FOR_LOOP, Tree.Kind.ENHANCED_FOR_LOOP, Tree.Kind.WHILE_LOOP,
                Tree.Kind.DO_WHILE_LOOP, Tree.Kind.TRY, Tree.Kind.SWITCH_EXPRESSION, Tree.Kind.LAMBDA_EXPRESSION,
                Tree.Kind.NEW_CLASS, Tree.Kind.CONDITIONAL_EXPRESSION, Tree.Kind.RETURN).contains(tree.getKind())) {
                var entry = location(tree); entry.put("kind", tree.getKind().toString()); entry.put("source", tree.toString());
                exceptional.add(entry);
            }
            return super.scan(tree, unused);
        }
    }
    static Map<String, Object> inventory(List<Path> sources, Path root) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A JDK compiler is required for AST evidence");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var rows = new ArrayList<Map<String, Object>>();
        var families = new TreeMap<String, List<String>>();
        var bindings = new LinkedHashMap<Path, String>();
        var snapshots = new ArrayList<JavaFileObject>();
        for (Path input : sources) {
            Path source = input.toAbsolutePath().normalize(); byte[] bytes = Files.readAllBytes(source);
            bindings.put(source, sha(bytes));
            String content = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            snapshots.add(new SimpleJavaFileObject(source.toUri(), JavaFileObject.Kind.SOURCE) {
                @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return content; }
            });
        }
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null, snapshots);
            var trees = Trees.instance(task);
            for (var unit : task.parse()) {
                Path source = Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize();
                String relative = root.relativize(source).toString().replace('\\', '/');
                for (var declaration : unit.getTypeDecls()) if (declaration instanceof ClassTree type) {
                    var row = new LinkedHashMap<String, Object>();
                    row.put("source", relative); row.put("sha256", bindings.get(source));
                    row.put("class", (unit.getPackageName() == null ? "" : unit.getPackageName() + ".") + type.getSimpleName());
                    row.put("extends", type.getExtendsClause() == null ? null : type.getExtendsClause().toString());
                    var fields = new ArrayList<Map<String, Object>>();
                    var methods = new ArrayList<Map<String, Object>>();
                    boolean declared = false;
                    for (var member : type.getMembers()) {
                        if (member instanceof VariableTree field) {
                            var info = new LinkedHashMap<String, Object>(); info.put("name", field.getName().toString());
                            info.put("initializer", field.getInitializer() == null ? null : field.getInitializer().toString()); fields.add(info);
                        }
                        if (member instanceof MethodTree method) {
                            String methodName = method.getName().toString();
                            if (methodName.equals("getPlayerBotActions")) declared = true;
                            if (!(methodName.startsWith("on") || methodName.equals("register") || methodName.equals("<init>"))) continue;
                            var facts = new Facts(unit, trees.getSourcePositions()); facts.scan(method.getBody(), null);
                            var info = new LinkedHashMap<String, Object>(); info.put("name", methodName);
                            info.put("calls", facts.calls); info.put("branches", facts.branches); info.put("variables", facts.variables);
                            info.put("requiresReview", facts.exceptional);
                            var shape = new StringBuilder(); new Shape().scan(method.getBody(), shape);
                            String family = methodName + ":" + sha(shape.toString().getBytes(StandardCharsets.UTF_8));
                            info.put("family", family); methods.add(info);
                            families.computeIfAbsent(family, ignored -> new ArrayList<>()).add(relative);
                        }
                    }
                    row.put("fields", fields); row.put("methods", methods); row.put("declaresBotActions", declared); rows.add(row);
                }
            }
        }
        var errors = diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).toList();
        if (!errors.isEmpty()) throw new IllegalStateException("Native handler parse failures: " + errors);
        for (var entry : bindings.entrySet())
            if (!entry.getValue().equals(sha(Files.readAllBytes(entry.getKey()))))
                throw new IllegalStateException("Handler source changed during AST inventory: " + entry.getKey());
        var result = new LinkedHashMap<String, Object>(); result.put("version", 1);
        result.put("positionUnits", "UTF-16 code units in original source text; CRLF preserved");
        result.put("authority", "AST evidence only; not action authorization or whole-chain coverage");
        result.put("limits", List.of("Lexical branch paths omit prior guard-return dominance and switch fallthrough",
            "Aliases, field values, helper bodies and external native conditions require semantic review",
            "Family shape intentionally omits literal values; grouping is not semantic equivalence proof",
            "Declared adapters require native execution fixtures and exact normal-artifact validation"));
        result.put("sources", rows); result.put("families", families); return result;
    }
    static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            var entries = new ArrayList<String>(); map.forEach((key, item) -> entries.add(json(key.toString()) + ":" + json(item)));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof Collection<?> list) return "[" + String.join(",", list.stream().map(QuestHandlerActionInventory::json).toList()) + "]";
        var text = new StringBuilder("\"");
        for (char ch : value.toString().toCharArray()) {
            if (ch == '"' || ch == '\\') text.append('\\').append(ch);
            else if (ch < 32) text.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
            else text.append(ch);
        }
        return text.append('"').toString();
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: java QuestHandlerActionInventory.java HANDLER_ROOT OUTPUT_JSON");
        Path root = Path.of(args[0]).toAbsolutePath().normalize(), output = Path.of(args[1]).toAbsolutePath().normalize();
        Files.deleteIfExists(output); // A failed parse must not leave an old success report usable.
        List<Path> sources;
        try (var paths = Files.walk(root)) { sources = paths.filter(p -> p.toString().endsWith(".java")).sorted().toList(); }
        if (sources.isEmpty()) throw new IllegalArgumentException("No native handler sources");
        var report = inventory(sources, root);
        Files.createDirectories(output.getParent()); Files.writeString(output, json(report) + "\n", StandardCharsets.UTF_8);
        System.out.println("OK: " + sources.size() + " native handler sources parsed without game initialization; AST evidence only");
    }
}
