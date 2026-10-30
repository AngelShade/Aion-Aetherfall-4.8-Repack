import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import javax.tools.*;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;
import com.sun.source.tree.*;
import com.sun.source.util.*;

/** Build-time reference extraction only: parses Java without loading or executing game scripts. */
public final class ScriptedSpawnCatalog {
    record Point(int npc, int map, Double x, Double y, Double z, String detail) {
        boolean fixed() { return map != 0 && x != null && y != null && z != null; }
    }
    static final class Script {
        String name, parent, ai = "";
        int map, quest;
        final Map<String, List<ExpressionTree>> fields = new HashMap<>();
        final Set<String> finalFields = new HashSet<>();
        final Map<String, List<ExpressionTree>> assigned = new HashMap<>();
        final List<Method> methods = new ArrayList<>();
    }
    static final class Method {
        Script script;
        MethodTree tree;
        final Map<String, List<ExpressionTree>> vars = new HashMap<>();
        final Set<String> written = new HashSet<>();
        final Set<String> locals = new HashSet<>();
        final Map<String, List<ExpressionTree>> assigned = new HashMap<>();
        final List<Call> calls = new ArrayList<>();
    }
    record Call(Method method, MethodInvocationTree tree, TreePath path, String source, long line) {}
    record Frame(Method method, Map<String, Set<Double>> args, int depth) {}
    final Map<Integer, String> names = new HashMap<>();
    final Map<Integer, String> questNames = new HashMap<>();
    final Map<String, Set<Integer>> aiNpcs = new HashMap<>();
    final Map<Integer, Set<Integer>> maps = new HashMap<>();
    final Map<String, Double> worldConstants = new HashMap<>();
    final Map<Integer, Set<Integer>> summons = new HashMap<>();
    final Map<Integer, Set<Integer>> skillSummons = new HashMap<>();
    final Map<Integer, Map<Integer, Point>> anchors = new HashMap<>();
    record AiSummon(int owner, int npc, Double x, Double y, Double z, String detail) {}
    final List<AiSummon> aiSummons = new ArrayList<>();
    final List<Script> scripts = new ArrayList<>();
    final Set<Point> points = new LinkedHashSet<>();
    final Set<String> unresolved = new TreeSet<>();
    final Map<Method, List<Frame>> frameCache = new IdentityHashMap<>();
    int spawnCalls;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("game-server source root and external output directory required");
        var catalog = new ScriptedSpawnCatalog();
        Path root = Path.of(args[0]).toAbsolutePath().normalize(), output = Path.of(args[1]);
        catalog.data(root);
        catalog.parse(root);
        catalog.inherit();
        // Propagate maps from scripted-only summoners to their adds. No coordinates are inferred from an owner.
        for (int pass = 0; pass < 32; pass++) {
            int before = catalog.maps.values().stream().mapToInt(Set::size).sum();
            catalog.extract(false);
            catalog.summons.forEach((child, owners) -> owners.forEach(owner -> {
                for (int map : Set.copyOf(catalog.maps.getOrDefault(owner, Set.of()))) catalog.addMap(child, map);
            }));
            if (before == catalog.maps.values().stream().mapToInt(Set::size).sum()) break;
            if (pass == 31) throw new IllegalStateException("Scripted spawn map dependencies did not converge");
        }
        catalog.extract(true);
        for (AiSummon summon : catalog.aiSummons) {
            Set<Integer> worlds = catalog.maps.getOrDefault(summon.owner, Set.of(0));
            for (int map : worlds) catalog.points.add(new Point(summon.npc, map, summon.x, summon.y, summon.z, summon.detail));
        }
        catalog.summons.forEach((child, owners) -> owners.forEach(owner -> {
            for (int map : catalog.maps.getOrDefault(owner, Set.of())) catalog.points.add(new Point(child, map, null, null, null,
                "Summoned during the encounter with " + catalog.names.getOrDefault(owner, "NPC " + owner)));
        }));
        catalog.write(output);
    }

    static int integer(Attributes a, String key) {
        String value = a.getValue(key); return value == null ? 0 : Integer.parseInt(value);
    }
    static void xml(Path file, DefaultHandler handler) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.newSAXParser().parse(file.toFile(), handler);
    }
    void data(Path root) throws Exception {
        xml(root.resolve("data/static_data/npcs/npc_templates.xml"), new DefaultHandler() {
            public void startElement(String u, String l, String tag, Attributes a) {
                if (!tag.equals("npc_template")) return;
                int id = integer(a, "npc_id"); names.put(id, a.getValue("name"));
                String ai = a.getValue("ai");
                if (ai != null) aiNpcs.computeIfAbsent(ai, _ -> new HashSet<>()).add(id);
            }
        });
        xml(root.resolve("data/static_data/quest_data/quest_data.xml"), new DefaultHandler() {
            public void startElement(String u, String l, String tag, Attributes a) {
                if (tag.equals("quest")) questNames.put(integer(a, "id"), a.getValue("name"));
            }
        });
        try (Stream<Path> files = Files.walk(root.resolve("data/static_data/spawns"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".xml")).sorted().toList()) {
                xml(file, new DefaultHandler() {
                    int map, npc;
                    public void startElement(String u, String l, String tag, Attributes a) {
                        if (tag.equals("spawn_map")) map = integer(a, "map_id");
                        if (tag.equals("spawn")) { npc = integer(a, "npc_id"); if (map != 0) addMap(npc, map); }
                        if (tag.equals("spot") && map != 0 && npc != 0) anchors.computeIfAbsent(npc, _ -> new HashMap<>()).putIfAbsent(map,
                            new Point(npc, map, Double.valueOf(a.getValue("x")), Double.valueOf(a.getValue("y")), Double.valueOf(a.getValue("z")), ""));
                    }
                });
            }
        }
        // These effects spawn relative to caster/target; never turn their template data into a fixed pin.
        xml(root.resolve("data/static_data/skills/skill_templates.xml"), new DefaultHandler() {
            int skill;
            public void startElement(String u, String l, String tag, Attributes a) {
                if (tag.equals("skill_template")) skill = integer(a, "skill_id");
                if (tag.startsWith("summon") && a.getValue("npc_id") != null) {
                    skillSummons.computeIfAbsent(skill, _ -> new HashSet<>()).add(integer(a, "npc_id"));
                    points.add(new Point(integer(a, "npc_id"), 0, null, null, null,
                        "Summoned by a skill; position follows the caster or target"));
                }
            }
        });
        xml(root.resolve("data/static_data/npc_skills/npc_skills.xml"), new DefaultHandler() {
            List<Integer> owners = List.of();
            public void startElement(String u, String l, String tag, Attributes a) {
                if (tag.equals("npc_skills")) owners = Arrays.stream(a.getValue("npc_ids").trim().split("[ ,]+")).map(Integer::valueOf).toList();
                if (tag.equals("npc_skill")) for (int child : skillSummons.getOrDefault(integer(a, "id"), Set.of()))
                    summons.computeIfAbsent(child, _ -> new HashSet<>()).addAll(owners);
                if (tag.equals("spawn_npc")) summons.computeIfAbsent(integer(a, "npc_id"), _ -> new HashSet<>()).addAll(owners);
            }
        });
        xml(root.resolve("data/static_data/world_raid/world_raids.xml"), new DefaultHandler() {
            int map; Double x, y, z;
            public void startElement(String u, String l, String tag, Attributes a) {
                if (tag.equals("world_raid_location")) { map = integer(a, "map_id"); x = Double.valueOf(a.getValue("x")); y = Double.valueOf(a.getValue("y")); z = Double.valueOf(a.getValue("z")); }
                if (tag.equals("world_raid_npc")) { int npc = integer(a, "npc_id"); addMap(npc, map); points.add(new Point(npc, map, x, y, z, "World raid; scheduled invasion and random boss selection")); }
            }
        });
        xml(root.resolve("data/static_data/ai/spawn_helpers.xml"), new DefaultHandler() {
            int owner, percent;
            public void startElement(String u, String l, String tag, Attributes a) {
                if (tag.equals("ai")) owner = integer(a, "npcId");
                if (tag.equals("percentage")) {
                    percent = integer(a, "percent");
                    for (int child : skillSummons.getOrDefault(integer(a, "skillId"), Set.of())) summons.computeIfAbsent(child, _ -> new HashSet<>()).add(owner);
                }
                if (tag.equals("summonGroup")) {
                    int child = integer(a, "npcId"); summons.computeIfAbsent(child, _ -> new HashSet<>()).add(owner);
                    boolean fixed = integer(a, "distance") == 0 && a.getValue("x") != null && a.getValue("y") != null && a.getValue("z") != null;
                    aiSummons.add(new AiSummon(owner, child, fixed ? Double.valueOf(a.getValue("x")) : null,
                        fixed ? Double.valueOf(a.getValue("y")) : null, fixed ? Double.valueOf(a.getValue("z")) : null,
                        "Summoned by " + names.getOrDefault(owner, "NPC " + owner) + " at " + percent + "% HP"));
                }
            }
        });
        try (Stream<Path> files = Files.walk(root.resolve("data/static_data/quest_script_data"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".xml")).sorted().toList()) xml(file, new DefaultHandler() {
                int quest;
                public void endElement(String u, String l, String tag) { if (tag.equals("kill_spawned")) quest = 0; }
                public void startElement(String u, String l, String tag, Attributes a) {
                    if (tag.equals("kill_spawned")) quest = integer(a, "id");
                    if (tag.equals("monster") && quest != 0 && a.getValue("spawner_object_id") != null && a.getValue("npc_ids") != null) {
                        int child = Integer.parseInt(a.getValue("npc_ids").trim().split("\\s+")[0]), owner = integer(a, "spawner_object_id");
                        String detail = "Quest " + quest + ": use " + names.getOrDefault(owner, "the quest object");
                        var recorded = anchors.getOrDefault(owner, Map.of()).values();
                        if (recorded.isEmpty()) points.add(new Point(child, 0, null, null, null, detail));
                        for (Point point : recorded) { addMap(child, point.map); points.add(new Point(child, point.map, point.x, point.y, point.z, detail)); }
                    }
                }
            });
        }
    }
    void addMap(int npc, int map) {
        if (npc != 0 && map != 0) maps.computeIfAbsent(npc, _ -> new HashSet<>()).add(map);
    }
    static String methodName(MethodInvocationTree call) {
        Tree select = call.getMethodSelect();
        return select instanceof MemberSelectTree member ? member.getIdentifier().toString() : select.toString();
    }
    static void variable(Map<String, List<ExpressionTree>> vars, VariableTree tree) {
        if (tree.getInitializer() != null) vars.computeIfAbsent(tree.getName().toString(), _ -> new ArrayList<>()).add(tree.getInitializer());
    }
    void inherit() {
        Set<Script> done = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Script script : scripts) inherit(script, done, new HashSet<>());
    }
    void inherit(Script script, Set<Script> done, Set<Script> visiting) {
        if (done.contains(script) || !visiting.add(script)) return;
        var parents = scripts.stream().filter(s -> s.name.equals(script.parent)).toList();
        if (parents.size() == 1) {
            Script parent = parents.getFirst(); inherit(parent, done, visiting);
            for (String name : parent.finalFields) if (!script.fields.containsKey(name)) script.finalFields.add(name);
            parent.fields.forEach(script.fields::putIfAbsent);
            parent.assigned.forEach(script.assigned::putIfAbsent);
            // Keep a concrete handler's overrides and annotation when inspecting inherited helpers.
            for (Method method : parent.methods) {
                String name = method.tree.getName().toString();
                if (name.equals("<init>") || name.equals("spawn") || name.equals("spawnAndSetRespawn")) continue;
                if (!name.equals("run") && script.methods.stream().anyMatch(m -> m.tree.getName().contentEquals(name) && m.tree.getParameters().size() == method.tree.getParameters().size())) continue;
                Method copy = new Method(); copy.script = script; copy.tree = method.tree;
                copy.vars.putAll(method.vars); copy.written.addAll(method.written);
                copy.locals.addAll(method.locals); copy.assigned.putAll(method.assigned);
                for (Call call : method.calls) copy.calls.add(new Call(copy, call.tree, call.path, call.source, call.line));
                script.methods.add(copy);
            }
        }
        done.add(script);
    }
    void parse(Path root) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("The normal server build requires a JDK");
        List<Path> paths = new ArrayList<>();
        for (String folder : List.of("src", "data/handlers")) try (Stream<Path> files = Files.walk(root.resolve(folder))) {
            paths.addAll(files.filter(p -> p.toString().endsWith(".java")).sorted().toList());
        }
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, files, diagnostics, List.of("-proc:none"), null, files.getJavaFileObjectsFromPaths(paths));
            for (CompilationUnitTree unit : task.parse()) {
                var positions = Trees.instance(task).getSourcePositions();
                new TreePathScanner<Void, Void>() {
                    Script script;
                    Method method;
                    public Void visitClass(ClassTree tree, Void ignored) {
                        if (tree.getSimpleName().isEmpty()) return super.visitClass(tree, ignored);
                        Script previous = script; Method previousMethod = method;
                        script = new Script(); method = null;
                        script.name = tree.getSimpleName().toString();
                        script.parent = tree.getExtendsClause() == null ? "" : tree.getExtendsClause().toString();
                        if (script.name.matches("_\\d+.*")) script.quest = Integer.parseInt(script.name.replaceFirst("^_(\\d+).*", "$1"));
                        for (var annotation : tree.getModifiers().getAnnotations()) {
                            String type = annotation.getAnnotationType().toString();
                            if (annotation.getArguments().size() != 1) continue;
                            Tree argument = annotation.getArguments().getFirst();
                            if (argument instanceof AssignmentTree assignment) argument = assignment.getExpression();
                            if (!(argument instanceof LiteralTree literal)) continue;
                            if (type.equals("InstanceID") && literal.getValue() instanceof Number number) script.map = number.intValue();
                            if (type.equals("AIName") && literal.getValue() instanceof String text) script.ai = text;
                        }
                        scripts.add(script);
                        super.visitClass(tree, ignored);
                        script = previous; method = previousMethod; return null;
                    }
                    public Void visitMethod(MethodTree tree, Void ignored) {
                        if (script == null) return null;
                        Method previous = method; method = new Method(); method.script = script; method.tree = tree; script.methods.add(method);
                        super.visitMethod(tree, ignored); method = previous; return null;
                    }
                    public Void visitVariable(VariableTree tree, Void ignored) {
                        if (script != null) variable(method == null ? script.fields : method.vars, tree);
                        if (method != null) method.locals.add(tree.getName().toString());
                        else if (script != null && tree.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.FINAL)) script.finalFields.add(tree.getName().toString());
                        // WorldMapType enum constants have one authoritative numeric constructor argument.
                        if (script != null && script.name.equals("WorldMapType") && tree.getInitializer() instanceof NewClassTree value
                            && !value.getArguments().isEmpty() && value.getArguments().getFirst() instanceof LiteralTree number && number.getValue() instanceof Number n)
                            worldConstants.put(tree.getName().toString(), n.doubleValue());
                        return super.visitVariable(tree, ignored);
                    }
                    public Void visitAssignment(AssignmentTree tree, Void ignored) {
                        if (method != null) method.written.add(tree.getVariable().toString());
                        if (method != null) method.assigned.computeIfAbsent(tree.getVariable().toString().replaceFirst("^this\\.", ""), _ -> new ArrayList<>()).add(tree.getExpression());
                        if (script != null) script.assigned.computeIfAbsent(tree.getVariable().toString().replaceFirst("^this\\.", ""), _ -> new ArrayList<>()).add(tree.getExpression());
                        return super.visitAssignment(tree, ignored);
                    }
                    public Void visitCompoundAssignment(CompoundAssignmentTree tree, Void ignored) {
                        if (method != null) method.written.add(tree.getVariable().toString());
                        return super.visitCompoundAssignment(tree, ignored);
                    }
                    public Void visitUnary(UnaryTree tree, Void ignored) {
                        if (method != null && Set.of(Tree.Kind.POSTFIX_INCREMENT, Tree.Kind.POSTFIX_DECREMENT, Tree.Kind.PREFIX_INCREMENT, Tree.Kind.PREFIX_DECREMENT).contains(tree.getKind()))
                            method.written.add(tree.getExpression().toString());
                        return super.visitUnary(tree, ignored);
                    }
                    public Void visitMethodInvocation(MethodInvocationTree tree, Void ignored) {
                        if (method != null) method.calls.add(new Call(method, tree, getCurrentPath(),
                            root.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\', '/'),
                            unit.getLineMap().getLineNumber(positions.getStartPosition(unit, tree))));
                        return super.visitMethodInvocation(tree, ignored);
                    }
                }.scan(unit, null);
            }
        }
        var errors = diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).toList();
        if (!errors.isEmpty()) throw new IllegalStateException("Cannot index invalid Java: " + errors.getFirst());
        // A callback's local variables must never become another callback's NPC alternatives.
        for (Script script : scripts) script.assigned.keySet().removeIf(name -> !script.fields.containsKey(name)
            || script.methods.stream().anyMatch(method -> method.locals.contains(name)));
    }

    Set<Double> values(ExpressionTree expression, Frame frame, Set<String> visiting) {
        if (expression == null || visiting.size() > 24) return Set.of();
        if (expression instanceof LiteralTree literal && literal.getValue() instanceof Number n) return Set.of(n.doubleValue());
        if (expression instanceof ParenthesizedTree p) return values(p.getExpression(), frame, visiting);
        if (expression instanceof TypeCastTree cast) return values(cast.getExpression(), frame, visiting);
        if (expression instanceof NewArrayTree array && array.getInitializers() != null) {
            Set<Double> out = new HashSet<>();
            for (ExpressionTree element : array.getInitializers()) out.addAll(values(element, frame, new HashSet<>(visiting)));
            return out;
        }
        if (expression instanceof ArrayAccessTree array) return arrayValues(array, frame, visiting, false);
        if (expression instanceof UnaryTree unary && unary.getKind() == Tree.Kind.UNARY_MINUS) {
            var out = new HashSet<Double>(); for (double value : values(unary.getExpression(), frame, visiting)) out.add(-value); return out;
        }
        if (expression instanceof ConditionalExpressionTree branch) {
            var out = new HashSet<>(values(branch.getTrueExpression(), frame, new HashSet<>(visiting)));
            out.addAll(values(branch.getFalseExpression(), frame, new HashSet<>(visiting))); return out;
        }
        if (expression instanceof BinaryTree binary) {
            var out = new HashSet<Double>();
            var left = values(binary.getLeftOperand(), frame, new HashSet<>(visiting));
            var right = values(binary.getRightOperand(), frame, new HashSet<>(visiting));
            if (left.size() * right.size() > 64) return Set.of();
            for (double a : left) for (double b : right) {
                double n = switch (binary.getKind()) { case PLUS -> a + b; case MINUS -> a - b; case MULTIPLY -> a * b; case DIVIDE -> a / b; default -> Double.NaN; };
                if (Double.isFinite(n)) out.add(n);
            }
            return out;
        }
        if (expression instanceof MethodInvocationTree call && methodName(call).equals("getId") && call.getMethodSelect() instanceof MemberSelectTree member) {
            String owner = member.getExpression().toString();
            if (owner.startsWith("WorldMapType.")) { Double value = worldConstants.get(owner.substring(owner.lastIndexOf('.') + 1)); return value == null ? Set.of() : Set.of(value); }
        }
        if (expression instanceof MethodInvocationTree call) {
            String select = call.getMethodSelect().toString();
            if (call.getArguments().isEmpty() && (select.equals("getNpcId") || select.equals("getOwner().getNpcId"))) {
                var out = new HashSet<Double>(); for (int npc : aiNpcs.getOrDefault(frame.method.script.ai, Set.of())) out.add((double) npc); return out;
            }
            if (select.equals("Rnd.get") && call.getArguments().size() == 1) return values(call.getArguments().getFirst(), frame, visiting);
            if ((select.equals("Rnd.get") && call.getArguments().size() == 2) || (select.equals("Rnd.nextInt") && call.getArguments().size() == 1)) {
                Double low = select.equals("Rnd.nextInt") ? 0d : single(call.getArguments().getFirst(), frame);
                Double high = single(call.getArguments().getLast(), frame);
                if (high != null && select.equals("Rnd.nextInt")) high--;
                if (low != null && high != null && low == Math.rint(low) && high == Math.rint(high) && high - low <= 64) {
                    Set<Double> out = new HashSet<>(); for (double n = low; n <= high; n++) out.add(n); return out;
                }
            }
            if (select.equals(methodName(call)) || select.equals("this." + methodName(call))) {
                String key = "return:" + select;
                if (!visiting.add(key)) return Set.of();
                var candidates = frame.method.script.methods.stream().filter(m -> m.tree.getName().contentEquals(methodName(call)) && m.tree.getParameters().size() == call.getArguments().size()).toList();
                if (candidates.size() == 1) {
                    Method target = candidates.getFirst(); Map<String, Set<Double>> args = new HashMap<>();
                    for (int i = 0; i < target.tree.getParameters().size(); i++) args.put(target.tree.getParameters().get(i).getName().toString(), values(call.getArguments().get(i), frame, new HashSet<>(visiting)));
                    Set<Double> out = new HashSet<>(); Frame next = new Frame(target, args, frame.depth + 1);
                    new TreeScanner<Void, Void>() {
                        public Void visitReturn(ReturnTree value, Void ignored) { out.addAll(values(value.getExpression(), next, new HashSet<>(visiting))); return null; }
                        public Void visitClass(ClassTree value, Void ignored) { return null; }
                    }.scan(target.tree.getBody(), null);
                    return out;
                }
            }
        }
        if (expression instanceof SwitchExpressionTree selection) {
            Set<Double> out = new HashSet<>();
            for (var branch : selection.getCases()) {
                if (branch.getBody() instanceof ExpressionTree value) out.addAll(values(value, frame, new HashSet<>(visiting)));
                new TreeScanner<Void, Void>() {
                    public Void visitYield(YieldTree value, Void ignored) { out.addAll(values(value.getValue(), frame, new HashSet<>(visiting))); return null; }
                }.scan(branch, null);
            }
            return out;
        }
        boolean field = expression instanceof MemberSelectTree member && member.getExpression().toString().equals("this");
        if (!(expression instanceof IdentifierTree) && !field) return Set.of();
        String name = expression.toString();
        if (name.startsWith("this.")) name = name.substring(5);
        if (frame.method.written.contains(name) || frame.method.script.assigned.containsKey(name)) return Set.of();
        if (!field && frame.args.containsKey(name)) return frame.args.get(name);
        if (!visiting.add(name)) return Set.of();
        if ((field || !frame.method.locals.contains(name)) && !frame.method.script.finalFields.contains(name)) return Set.of();
        var declarations = !field && frame.method.locals.contains(name) ? frame.method.vars.getOrDefault(name, List.of()) : frame.method.script.fields.getOrDefault(name, List.of());
        return declarations.size() == 1 ? values(declarations.getFirst(), frame, visiting) : Set.of();
    }
    Set<Double> arrayValues(ArrayAccessTree array, Frame frame, Set<String> visiting, boolean mutable) {
        List<ExpressionTree> definitions = new ArrayList<>();
        String name = array.getExpression().toString().replaceFirst("^this\\.", "");
        if (!mutable && frame.method.script.assigned.containsKey(name)) return Set.of();
        definitions.addAll(frame.method.vars.getOrDefault(name, frame.method.script.fields.getOrDefault(name, List.of())));
        if (mutable) definitions.addAll((frame.method.locals.contains(name) ? frame.method.assigned : frame.method.script.assigned).getOrDefault(name, List.of()));
        Set<Double> out = new HashSet<>(); Set<Double> indices = values(array.getIndex(), frame);
        for (var definition : definitions) if (definition instanceof NewArrayTree initial && initial.getInitializers() != null) {
            if (indices.isEmpty()) for (var element : initial.getInitializers()) out.addAll(values(element, frame, new HashSet<>(visiting)));
            else for (double index : indices) if (index == Math.rint(index) && index >= 0 && index < initial.getInitializers().size())
                out.addAll(values(initial.getInitializers().get((int) index), frame, new HashSet<>(visiting)));
        }
        return out;
    }
    Set<Double> npcValues(ExpressionTree expression, Frame frame) {
        Set<Double> out = new HashSet<>(values(expression, frame));
        if (!out.isEmpty()) return out;
        if (expression instanceof ArrayAccessTree array) return arrayValues(array, frame, new HashSet<>(), true);
        String name = expression.toString().replaceFirst("^this\\.", "");
        List<ExpressionTree> alternatives = new ArrayList<>((frame.method.locals.contains(name) ? frame.method.assigned : frame.method.script.assigned).getOrDefault(name, List.of()));
        alternatives.addAll(frame.method.vars.getOrDefault(name, frame.method.script.fields.getOrDefault(name, List.of())));
        for (var alternative : alternatives) out.addAll(values(alternative, frame));
        return out;
    }
    Set<Double> values(ExpressionTree expression, Frame frame) { return values(expression, frame, new HashSet<>()); }
    Double single(ExpressionTree expression, Frame frame) { var found = values(expression, frame); return found.size() == 1 ? found.iterator().next() : null; }
    List<Frame> frames(Method method, Set<Method> visiting) {
        if (!visiting.add(method) || visiting.size() > 8) return List.of();
        if (method.tree.getParameters().isEmpty()) return List.of(new Frame(method, Map.of(), 0));
        List<Frame> out = new ArrayList<>();
        Set<Map<String, Set<Double>>> seen = new HashSet<>();
        for (Method caller : method.script.methods) for (Call call : caller.calls) {
            String select = call.tree.getMethodSelect().toString();
            if ((!select.equals(method.tree.getName().toString()) && !select.equals("this." + method.tree.getName()))
                || call.tree.getArguments().size() != method.tree.getParameters().size()) continue;
            for (Frame parent : frames(caller, new HashSet<>(visiting))) {
                Map<String, Set<Double>> args = new HashMap<>();
                for (int i = 0; i < method.tree.getParameters().size(); i++) args.put(method.tree.getParameters().get(i).getName().toString(), values(call.tree.getArguments().get(i), parent));
                if (seen.add(args)) out.add(new Frame(method, args, parent.depth + 1));
                if (out.size() >= 10000) throw new IllegalStateException("Too many spawn helper contexts in " + method.script.name);
            }
        }
        if (out.isEmpty()) out.add(new Frame(method, Map.of(), 0));
        return out;
    }
    Set<Integer> scriptMaps(Script script, Set<Script> visiting) {
        if (!visiting.add(script)) return Set.of();
        if (script.map != 0) return Set.of(script.map);
        Set<Integer> found = new TreeSet<>();
        for (int npc : aiNpcs.getOrDefault(script.ai, Set.of())) found.addAll(maps.getOrDefault(npc, Set.of()));
        if (found.isEmpty() && !script.parent.isEmpty()) {
            var parents = scripts.stream().filter(s -> s.name.equals(script.parent)).toList();
            if (parents.size() == 1) found.addAll(scriptMaps(parents.getFirst(), visiting));
        }
        return found;
    }
    String condition(Call call, Frame frame) {
        Script script = call.method.script;
        if (script.quest != 0) return "Quest " + script.quest + ": " + questNames.getOrDefault(script.quest, "active quest step required");
        String event = call.method.tree.getName().toString();
        if (event.equals("onInstanceCreate")) return "Instance setup; an active run is required";
        if (event.equals("onDie")) {
            var targets = new LinkedHashSet<String>(); Integer count = null;
            for (TreePath path = call.path.getParentPath(); path != null; path = path.getParentPath()) {
                if (path.getLeaf() instanceof IfTree branch && branch.getThenStatement() == childOf(path, call.path)) {
                    ExpressionTree expression = branch.getCondition();
                    while (expression instanceof ParenthesizedTree p) expression = p.getExpression();
                    if (expression instanceof BinaryTree binary && binary.getKind() == Tree.Kind.EQUAL_TO) {
                        var vars = frame.method.vars.get(binary.getLeftOperand().toString());
                        Double number = single(binary.getRightOperand(), frame);
                        if (vars != null && vars.size() == 1 && vars.getFirst() instanceof MethodInvocationTree increment
                            && methodName(increment).equals("incrementAndGet") && number != null && number > 0 && number == Math.rint(number)) count = number.intValue();
                    }
                }
                if (path.getLeaf() instanceof CaseTree branch && path.getParentPath() != null && path.getParentPath().getLeaf() instanceof SwitchTree selection
                    && selection.getExpression().toString().contains("getNpcId()")) {
                    // Colon-style cases may share a body (normal/difficult IDs).
                    List<? extends CaseTree> cases = selection.getCases(); int at = cases.indexOf(branch);
                    for (int i = at; i >= 0; i--) {
                        var label = cases.get(i); if (i != at && label.getStatements() != null && !label.getStatements().isEmpty()) break;
                        for (CaseLabelTree value : label.getLabels()) if (value instanceof ConstantCaseLabelTree constant)
                            for (double id : values(constant.getConstantExpression(), frame)) if (names.containsKey((int) id)) targets.add(names.get((int) id));
                    }
                    break;
                }
            }
            if (!targets.isEmpty()) return "After defeating " + (count == null ? "" : count + " x ") + String.join(" / ", targets);
        }
        Set<Integer> owners = aiNpcs.getOrDefault(script.ai, Set.of());
        if (!owners.isEmpty()) {
            var labels = owners.stream().map(names::get).filter(Objects::nonNull).distinct().sorted().limit(3).toList();
            return (event.equals("handleDied") ? "After defeating " : "During the encounter with ") + String.join(" / ", labels);
        }
        return "Scripted spawn; encounter, quest or event conditions apply";
    }
    static Tree childOf(TreePath parent, TreePath path) {
        for (; path != null && path.getParentPath() != parent; path = path.getParentPath()) {}
        return path == null ? null : path.getLeaf();
    }
    void extract(boolean record) {
        if (record) { spawnCalls = 0; unresolved.clear(); }
        for (Script script : scripts) for (Method method : script.methods) for (Call call : method.calls) {
            String name = methodName(call.tree), select = call.tree.getMethodSelect().toString();
            List<? extends ExpressionTree> args = call.tree.getArguments();
            int npcAt, xyzAt; ExpressionTree world = null;
            if (select.startsWith("SpawnEngine.") && (name.equals("newSingleTimeSpawn") || name.equals("newSpawn")) && args.size() >= 6) {
                npcAt = 1; xyzAt = 2; world = args.getFirst();
            } else if (Set.of("spawn", "spawnAndSetRespawn", "spawnForFiveMinutes", "spawnTemporarily", "spawnInFrontOf", "spawnForFiveMinutesInFront", "spawnForFiveMinutesInFrontOf").contains(name)
                && (select.equals(name) || select.equals("this." + name) || select.equals("AbstractQuestHandler." + name)) && !args.isEmpty()) {
                if ((name.equals("spawn") || name.equals("spawnAndSetRespawn")) && args.size() < 5) continue;
                // A locally declared helper has its own signature; inspect its body and bind its call arguments instead.
                if (script.methods.stream().anyMatch(m -> m.tree.getName().contentEquals(name) && m.tree.getParameters().size() == args.size())) continue;
                npcAt = 0; xyzAt = script.quest != 0 || name.startsWith("spawnFor") || name.equals("spawnTemporarily") ? 2 : 1;
                if (name.contains("Front") || args.size() < xyzAt + 3) xyzAt = -1;
            } else continue;
            if (record) spawnCalls++;
            for (Frame frame : frameCache.computeIfAbsent(method, m -> frames(m, new HashSet<>()))) {
                Set<Double> ids = npcValues(args.get(npcAt), frame);
                if (ids.isEmpty() && record) unresolved.add(call.source + ":" + call.line + "\tNPC ID calculated at runtime: " + args.get(npcAt));
                Set<Integer> worlds = new TreeSet<>();
                if (world != null) {
                    for (double id : values(world, frame)) if (id == Math.rint(id) && id > 0) worlds.add((int) id);
                    if (world.toString().equals("mapId") || world.toString().equals("getWorldId()") || world.toString().equals("getOwner().getWorldId()")) worlds.addAll(scriptMaps(script, new HashSet<>()));
                } else worlds.addAll(scriptMaps(script, new HashSet<>()));
                if (worlds.isEmpty()) worlds.add(0);
                if (worlds.size() > 1 && ids.size() > 1) worlds = new TreeSet<>(Set.of(0));
                Double x = xyzAt < 0 ? null : single(args.get(xyzAt), frame);
                Double y = xyzAt < 0 ? null : single(args.get(xyzAt + 1), frame);
                Double z = xyzAt < 0 ? null : single(args.get(xyzAt + 2), frame);
                // Multi-map AI coordinates cannot be assumed to apply to every owner map.
                if (world == null && worlds.size() > 1) x = y = z = null;
                for (double id : ids) {
                    if (id != Math.rint(id) || !names.containsKey((int) id)) continue;
                    for (int map : worlds) {
                        addMap((int) id, map);
                        if (record) points.add(new Point((int) id, map, x, y, z, condition(call, frame)));
                    }
                }
            }
        }
    }
    static String cell(String value) { return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '); }
    void write(Path output) throws Exception {
        Files.createDirectories(output);
        var sorted = points.stream().filter(p -> names.containsKey(p.npc)).sorted(Comparator.comparingInt(Point::npc).thenComparingInt(Point::map)
            .thenComparing(p -> String.valueOf(p.x)).thenComparing(p -> String.valueOf(p.y)).thenComparing(Point::detail)).toList();
        List<String> rows = new ArrayList<>(List.of("# Aetherfall scripted spawn catalog v1; generated by tools/codex/ScriptedSpawnCatalog.java", "# npc\tmap\tx\ty\tz\tcondition"));
        for (Point point : sorted) rows.add(point.npc + "\t" + point.map + "\t" + (point.fixed() ? point.x + "\t" + point.y + "\t" + point.z : "\t\t") + "\t" + cell(point.detail));
        Files.write(output.resolve("scripted-spawns.tsv"), rows, StandardCharsets.UTF_8);
        Files.write(output.resolve("unresolved-spawns.tsv"), unresolved, StandardCharsets.UTF_8);
        long fixed = sorted.stream().filter(Point::fixed).count(), npcs = sorted.stream().map(Point::npc).distinct().count();
        String summary = "Scripts/classes: " + scripts.size() + "; spawn calls: " + spawnCalls + "; NPCs: " + npcs + "; fixed points: " + fixed
            + "; conditional references: " + (sorted.size() - fixed) + "; unresolved ID expressions: " + unresolved.size();
        Files.writeString(output.resolve("coverage.txt"), summary + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println(summary);
    }
}
