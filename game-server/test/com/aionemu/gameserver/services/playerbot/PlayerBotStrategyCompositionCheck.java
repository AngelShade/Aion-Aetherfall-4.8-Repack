package com.aionemu.gameserver.services.playerbot;

import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import com.aionemu.gameserver.services.playerbot.PlayerBotStrategyComposition.*;

/** Behavior fixtures: no server, world actor constructors, database or character writes. */
public final class PlayerBotStrategyCompositionCheck {
	private static int checks;
	private static final List<String> trace = new ArrayList<>();
	private record TestAction(String key, BooleanSupplier useful, BooleanSupplier possible,
		BooleanSupplier result, List<Action> prerequisites, List<Action> alternatives,
		List<String> continuers) implements ContinuingAction {
		public String name() { return key; }
		public boolean isUseful() { return useful.getAsBoolean(); }
		public boolean isPossible() { return possible.getAsBoolean(); }
		public boolean execute() { trace.add(key); return result.getAsBoolean(); }
	}
	private static Action action(String name) { return linked(name, List.of()); }
	private static TestAction linked(String name, List<String> continuers) {
		return new TestAction(name, () -> true, () -> true, () -> true, List.of(), List.of(), continuers);
	}
	private static Trigger trigger(Action action, double score) { return new Trigger(() -> true, action, () -> score); }
	private static boolean tick(PlayerBotEngine engine, Trigger... triggers) {
		return engine.tick(State.COMBAT, List.of(new Strategy("test", List.of(triggers))), List.of(), 64);
	}
	private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label + " / " + trace); }

	public static void main(String[] args) throws Exception {
		var engine = new PlayerBotEngine();
		var source = linked("opener", List.of("successor"));
		check(tick(engine, trigger(source, 40)), "successful action schedules continuer");
		trace.clear();
		var fresh = linked("successor", List.of());
		check(tick(engine, trigger(action("filler"), 10), trigger(fresh, 5)), "next snapshot resolves continuer");
		check(trace.equals(List.of("successor")), "continuer inherits successful action relevance");
		trace.clear();
		check(tick(engine, trigger(action("filler"), 10), trigger(fresh, 5)) && trace.equals(List.of("filler")), "continuation consumed once");
		var failed = new TestAction("failed opener", () -> true, () -> true, () -> false, List.of(), List.of(), List.of("successor"));
		check(!tick(engine, trigger(failed, 40)), "failed action has no successful continuation");
		trace.clear(); check(tick(engine, trigger(action("filler"), 10), trigger(fresh, 5)) && trace.equals(List.of("filler")), "failed opener never boosts successor");
		tick(engine, trigger(source, 40)); tick(engine, trigger(action("filler"), 10)); trace.clear();
		check(tick(engine, trigger(action("filler"), 10), trigger(fresh, 5)) && trace.equals(List.of("filler")), "absent/inactive successor dropped rather than resurrected later");
		tick(engine, trigger(source, 40)); trace.clear();
		check(tick(engine, trigger(action("emergency heal"), 95), trigger(fresh, 5)) && trace.equals(List.of("emergency heal")), "emergency preempts continuer");
		trace.clear(); check(tick(engine, trigger(action("filler"), 10), trigger(fresh, 5)) && trace.equals(List.of("successor")), "preempted eligible continuer survives next decision");

		// The expired snapshot action must never execute; only the new object can run.
		var stale = new TestAction("successor", () -> true, () -> true,
			() -> { throw new AssertionError("retained old actor action"); }, List.of(), List.of(), List.of());
		tick(engine, trigger(source, 40), trigger(stale, 5)); trace.clear();
		check(tick(engine, trigger(fresh, 5)) && trace.equals(List.of("successor")), "only fresh action object retained");
		tick(engine, trigger(source, 40)); trace.clear();
		var invalid = new TestAction("successor", () -> false, () -> true,
			() -> { throw new AssertionError("useless continuer"); }, List.of(), List.of(), List.of());
		check(tick(engine, trigger(invalid, 5), trigger(action("filler"), 10)) && trace.equals(List.of("filler")), "continuer rechecks usefulness");
		tick(engine, trigger(source, 40)); trace.clear();
		check(engine.tick(State.COMBAT, List.of(new Strategy("test", List.of(trigger(fresh, 5)))), List.of(a -> 0), 64)==false && trace.isEmpty(), "multiplier can suppress continuer");

		for (State changed : List.of(State.NON_COMBAT, State.DEAD)) {
			tick(engine, trigger(source, 40)); trace.clear();
			engine.tick(changed, List.of(), List.of(), 64);
			tick(engine, trigger(action("filler"), 10), trigger(fresh, 5));
			check(trace.equals(List.of("filler")), "state change clears " + changed);
		}
		for (String context : List.of("map", "instance", "party", "order", "role", "flight", "command")) {
			PlayerBotArbitration.context(engine, "before"); tick(engine, trigger(source, 40));
			PlayerBotArbitration.context(engine, context); trace.clear();
			check(tick(engine, trigger(action("filler"), 10), trigger(fresh, 5)) && trace.equals(List.of("filler")), "context invalidation " + context);
		}
		tick(engine, trigger(source, 40)); PlayerBotArbitration.clear(engine); trace.clear();
		check(tick(engine, trigger(action("filler"), 10), trigger(fresh, 5)) && trace.equals(List.of("filler")), "dismiss/preferences invalidate immediately");

		// Upstream pops raw queue relevance, THEN multiplies; it does not globally re-sort weighted seeds.
		var weights = new HashMap<String, Double>();
		weights.put("impossible", .1); weights.put("alternative", 3d);
		var alternative = action("alternative");
		var impossible = new TestAction("impossible", () -> true, () -> false, () -> false, List.of(), List.of(alternative), List.of());
		trace.clear();
		check(engine.tick(State.COMBAT, List.of(new Strategy("weighted", List.of(trigger(impossible, 100), trigger(action("filler"), 20)))),
			List.of(a -> weights.getOrDefault(a.name(), 1d)), 64) && trace.equals(List.of("filler")), "alternative inherits evaluated parent relevance, not seed weight");
		trace.clear();
		var weightedSuccess = linked("alternative", List.of("successor"));
		var weightedParent = new TestAction("impossible", () -> true, () -> false, () -> false, List.of(), List.of(weightedSuccess), List.of());
		check(engine.tick(State.COMBAT, List.of(new Strategy("weighted", List.of(trigger(weightedParent, 100)))),
			List.of(a -> weights.getOrDefault(a.name(), 1d)), 64), "positive expanded multiplier evaluated");
		trace.clear();
		check(tick(engine, trigger(action("filler"), 25), trigger(fresh, 5)) && trace.equals(List.of("successor")), "expanded positive weight reaches continuer");
		trace.clear();
		check(engine.tick(State.COMBAT, List.of(new Strategy("seed", List.of(trigger(source, 100)))), List.of(a -> .2), 64), "positive seed multiplier evaluated once");
		trace.clear();
		check(tick(engine, trigger(action("filler"), 30), trigger(fresh, 5)) && trace.equals(List.of("filler")), "seed weight not squared");

		var reached = new boolean[] {false};
		var reach = new TestAction("reach", () -> !reached[0], () -> true, () -> { reached[0]=true; return true; }, List.of(), List.of(), List.of());
		var cast = new TestAction("cast", () -> true, () -> true, () -> true, List.of(reach), List.of(), List.of());
		trace.clear(); check(tick(engine, trigger(cast, 40)) && trace.equals(List.of("reach")), "movement precedes action");
		trace.clear(); check(tick(engine, trigger(action("filler"), 10), trigger(cast, 5)) && trace.equals(List.of("cast")), "successful prerequisite preserves parent intention with fresh gates");
		var blocked = new TestAction("blocked", () -> true, () -> false, () -> false, List.of(), List.of(), List.of());
		var unsafe = new TestAction("unsafe", () -> true, () -> true, () -> { throw new AssertionError("movement bypass"); }, List.of(blocked), List.of(), List.of());
		check(!tick(engine, trigger(unsafe, 100)), "failed movement never authorizes parent");
		var suppressedMove = new TestAction("move", () -> true, () -> true, () -> true, List.of(), List.of(), List.of());
		var requiresMove = new TestAction("cast", () -> true, () -> true, () -> { throw new AssertionError("suppressed move bypass"); }, List.of(suppressedMove), List.of(), List.of());
		check(!engine.tick(State.COMBAT, List.of(new Strategy("suppression", List.of(trigger(requiresMove, 100)))), List.of(a -> a.name().equals("move") ? 0 : 1), 64), "expanded prerequisite suppression");

		var plan = new Plan(State.NON_COMBAT);
		plan.triggers("follow", State.NON_COMBAT).add(trigger(action("follow"), 5));
		plan.triggers("combat", State.COMBAT).add(trigger(action("combat"), 100));
		trace.clear(); check(plan.tick(engine, 64) && trace.equals(List.of("follow")), "state-specific composition excludes combat");
		plan.enable("follow", false); check(!plan.tick(engine, 64), "strategy disable removes pending/defaults");
		plan.enable("follow", true); trace.clear(); check(plan.tick(engine, 64) && trace.equals(List.of("follow")), "strategy enable restores fresh triggers");
		plan.multiplier(a -> 0); check(!plan.tick(engine, 64), "strategy multipliers connected");
		trace.clear(); check(!tick(engine, trigger(action("nan"), Double.NaN), trigger(action("infinite"), Double.POSITIVE_INFINITY)), "nonfinite input dropped");
		check(!engine.tick(State.COMBAT, List.of(new Strategy("bad multiplier", List.of(trigger(source, 20)))), List.of(a -> Double.NaN), 64), "nonfinite multiplier blocked");
		check(!engine.tick(State.COMBAT, List.of(new Strategy("no budget", List.of(trigger(source, 20)))), List.of(), 0) && engine.getAttempts()==0, "zero budget");
		Action cycle = new Action() {
			public String name() { return "cycle"; } public boolean isUseful() { return true; }
			public boolean isPossible() { return false; } public boolean execute() { throw new AssertionError(); }
			public List<Action> alternatives() { return List.of(this); }
		};
		check(!engine.tick(State.COMBAT, List.of(new Strategy("cycle", List.of(trigger(cycle, 20)))), List.of(), 3) && engine.getAttempts()<=3, "cycle/budget bounded");
		var defaultPlan = new Plan(State.NON_COMBAT);
		defaultPlan.defaults("follow", action("follow"), DEFAULT, State.NON_COMBAT);
		trace.clear(); check(defaultPlan.tick(engine, 64) && trace.equals(List.of("follow")), "state default action registered");
		nativeChains();
		System.out.println("OK: " + checks + " state composition, weighted expansion, fresh continuation and movement safety checks");
	}
	static final class Actor extends com.aionemu.gameserver.model.gameobjects.player.Player {
		int id; boolean casting; com.aionemu.gameserver.skillengine.model.ChainSkills chains;
		Actor(){super(null,null);}
		@Override public int getObjectId(){return id;}
		@Override public int getWorldId(){return 210010000;}
		@Override public int getInstanceId(){return 1;}
		@Override public String getName(boolean displayCustomTag){return "same NPC name";}
		@Override public boolean isCasting(){return casting;}
		@Override public com.aionemu.gameserver.skillengine.model.ChainSkills getChainSkills(){return chains;}
	}
	private static void nativeChains() throws Exception {
		var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
		var unsafe=(sun.misc.Unsafe)field.get(null);
		var bot=(Actor)unsafe.allocateInstance(Actor.class);bot.id=1;bot.chains=new com.aionemu.gameserver.skillengine.model.ChainSkills();
		var target=(Actor)unsafe.allocateInstance(Actor.class);target.id=2;
		var other=(Actor)unsafe.allocateInstance(Actor.class);other.id=3;
		var context=javax.xml.bind.JAXBContext.newInstance(com.aionemu.gameserver.dataholders.SkillData.class);
		var skills=(com.aionemu.gameserver.dataholders.SkillData)context.createUnmarshaller().unmarshal(new java.io.StringReader("""
		 <skill_data>
		 <skill_template skill_id="990001" name="opener" activation="ACTIVE" skilltype="PHYSICAL" skillsubtype="ATTACK">
		 <properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/>
		 <startconditions><chain category="STRIKE_1TH" time="3000"/></startconditions><effects><skillatk value="100"/></effects></skill_template>
		 <skill_template skill_id="990002" name="successor" activation="ACTIVE" skilltype="PHYSICAL" skillsubtype="ATTACK">
		 <properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/>
		 <startconditions><chain category="STRIKE_2TH" precategory="STRIKE_1TH" precount="2" time="3000"/></startconditions><effects><skillatk value="100"/></effects></skill_template>
		 <skill_template skill_id="990003" name="wrong predecessor" activation="ACTIVE" skilltype="PHYSICAL" skillsubtype="ATTACK">
		 <properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/>
		 <startconditions><chain category="OTHER_2TH" precategory="OTHER_1TH" time="3000"/></startconditions><effects><skillatk value="100"/></effects></skill_template>
		 </skill_data>
		 """));
		var learned=new ArrayList<PlayerBotSkills.Entry>();
		for(int id=990001;id<=990003;id++)learned.add(new PlayerBotSkills.Entry(skills.getSkillTemplate(id),1,PlayerBotRules.SkillKind.DAMAGE,20));
		var opener=learned.getFirst();var successor=learned.get(1);
		var action=(ContinuingAction)PlayerBotStrategyComposition.skill(linked("cast opener",List.of()),bot,opener,target,learned);
		check(action.continuers().equals(List.of(PlayerBotStrategyComposition.skillKey(successor,target))),"asynchronous native cast schedules exact metadata successor");
		check(!PlayerBotSkills.chainAvailable(bot,successor),"native chain must actually activate before fresh admission");
		bot.chains.updateChain("STRIKE_1TH",3000);
		check(!PlayerBotSkills.chainAvailable(bot,successor),"native predecessor activation count respected");
		bot.chains.updateChain("STRIKE_1TH",3000);
		int count=bot.chains.getCurrentChainSkill().getUseCount();
		check(action.continuers().equals(List.of(PlayerBotStrategyComposition.skillKey(successor,target))),"native eligible learned successor only");
		check(bot.chains.getCurrentChainSkill().getUseCount()==count,"planning never consumes or resets native chain");
		check(target.getName().equals(other.getName()) && !PlayerBotStrategyComposition.skillKey(successor,target).equals(PlayerBotStrategyComposition.skillKey(successor,other)),"same names retain distinct native recipient identity");
		check(PlayerBotStrategyComposition.threat(true).value(action)==0,"native threat hold suppresses expanded class damage");
		check(PlayerBotStrategyComposition.threat(false).value(action)==1,"normal threat permits class damage");
		bot.chains.resetChain();check(!PlayerBotSkills.chainAvailable(bot,successor),"native failed/reset chain is not admitted");
	}
}
