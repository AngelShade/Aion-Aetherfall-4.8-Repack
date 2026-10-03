package com.aionemu.gameserver.services.playerbot;

import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.SkillKind.*;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;

import javax.xml.bind.JAXBContext;

import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.skillengine.model.ChainSkills;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.model.Effect;
import com.aionemu.gameserver.skillengine.effect.AbstractDispelEffect;

/** Production skill-data coverage and chain safety. Does not claim complete class rotations. */
public final class PlayerBotSkillsCheck {
	private static int checks;
	private static void check(boolean condition, String message) {
		checks++;
		if (!condition) throw new AssertionError("FAIL: " + message);
	}
	public static void main(String[] args) throws Exception {
		JAXBContext context = JAXBContext.newInstance(SkillData.class);
		var fixture = (SkillData) context.createUnmarshaller().unmarshal(new StringReader("""
			<skill_data>
			<skill_template skill_id="900001" name="damage with stun" activation="ACTIVE" skilltype="PHYSICAL" skillsubtype="ATTACK">
			  <properties first_target="TARGET" first_target_range="20" target_relation="ENEMY" target_type="ONLYONE"/>
			  <effects><skillatk value="100"/><stun duration2="2000"/></effects>
			</skill_template>
			<skill_template skill_id="900002" name="pure crowd control" activation="ACTIVE" skilltype="MAGICAL" skillsubtype="DEBUFF">
			  <properties first_target="TARGET" first_target_range="20" target_relation="ENEMY" target_type="ONLYONE"/>
			  <effects><sleep duration2="2000"/></effects>
			</skill_template>
			<skill_template skill_id="900003" name="healing potion" activation="ACTIVE" skilltype="MAGICAL" skillsubtype="BUFF">
			  <properties first_target="ME" target_relation="FRIEND" target_type="ONLYONE"/>
			  <effects><healinstant value="100"/></effects>
			</skill_template>
			<skill_template skill_id="900004" name="unsafe mixed consumable" activation="ACTIVE" skilltype="MAGICAL" skillsubtype="BUFF">
			  <properties first_target="ME" target_relation="FRIEND" target_type="ONLYONE"/>
			  <effects><healinstant value="100"/><return/></effects>
			</skill_template>
			<skill_template skill_id="900005" name="chain followup" activation="ACTIVE" skilltype="PHYSICAL" skillsubtype="ATTACK">
			  <properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/>
			  <startconditions><chain category="STRIKE_2TH" precategory="STRIKE_1TH" precount="2" time="1000"/></startconditions>
			  <effects><skillatk value="100"/></effects>
			</skill_template>
			<skill_template skill_id="900006" name="mental cleanse" activation="ACTIVE" skilltype="MAGICAL" skillsubtype="BUFF">
			  <properties first_target="TARGET" target_relation="FRIEND" target_type="ONLYONE"/>
			  <effects><dispeldebuffmental dispel_level="1" power="30" value="1"/></effects>
			</skill_template>
			<skill_template skill_id="900007" name="physical cleanse" activation="ACTIVE" skilltype="MAGICAL" skillsubtype="BUFF">
			  <properties first_target="TARGET" target_relation="FRIEND" target_type="ONLYONE"/>
			  <effects><dispeldebuffphysical dispel_level="1" power="30" value="1"/></effects>
			</skill_template>
			<skill_template skill_id="900008" name="mental debuff" activation="ACTIVE" skilltype="MAGICAL" skillsubtype="DEBUFF"
			  tslot="DEBUFF" dispel_category="DEBUFF_MENTAL" req_dispel_level="1" req_dispel_count="60">
			  <effects><sleep duration2="2000"/></effects>
			</skill_template>
			<skill_template skill_id="900009" name="physical debuff" activation="ACTIVE" skilltype="PHYSICAL" skillsubtype="DEBUFF"
			  tslot="DEBUFF" dispel_category="DEBUFF_PHYSICAL" req_dispel_level="1" req_dispel_count="10">
			  <effects><poison duration2="2000"/></effects>
			</skill_template>
			<skill_template skill_id="900010" name="protected mental debuff" activation="ACTIVE" skilltype="MAGICAL" skillsubtype="DEBUFF"
			  tslot="DEBUFF" dispel_category="DEBUFF_MENTAL" req_dispel_level="2" req_dispel_count="10">
			  <effects><sleep duration2="2000"/></effects>
			</skill_template>
			</skill_data>
			"""));
		check(PlayerBotSkills.classify(fixture.getSkillTemplate(900001)) == DAMAGE, "damage with stun remains usable outside enemy casts");
		check(PlayerBotSkills.canInterrupt(fixture.getSkillTemplate(900001)), "damage/stun can interrupt enemy casts");
		check(PlayerBotSkills.classify(fixture.getSkillTemplate(900002)) == CONTROL, "pure crowd control remains control");
		check(PlayerBotSkills.classify(fixture.getSkillTemplate(900003)) == HEAL, "self heal recognized");
		check(PlayerBotConsumables.isRecovery(fixture.getSkillTemplate(900003)), "ordinary recovery potion eligible");
		check(!PlayerBotConsumables.isRecovery(fixture.getSkillTemplate(900004)), "healing plus teleport never auto-consumed");
		var mentalCleanse = (AbstractDispelEffect) fixture.getSkillTemplate(900006).getEffects().getEffects().getFirst();
		var physicalCleanse = (AbstractDispelEffect) fixture.getSkillTemplate(900007).getEffects().getEffects().getFirst();
		var mentalDebuff = new Effect(null, null, fixture.getSkillTemplate(900008), 1);
		var physicalDebuff = new Effect(null, null, fixture.getSkillTemplate(900009), 1);
		check(mentalCleanse.canDispelDebuff(mentalDebuff, 1), "partial dispel power can contribute to a stronger mental debuff");
		check(!mentalCleanse.canDispelDebuff(physicalDebuff, 1), "mental cleanse does not plan a physical debuff");
		check(physicalCleanse.canDispelDebuff(physicalDebuff, 1), "physical cleanse plans physical debuff");
		check(!physicalCleanse.canDispelDebuff(mentalDebuff, 1), "physical cleanse does not plan a mental debuff");
		check(!mentalCleanse.canDispelDebuff(new Effect(null, null, fixture.getSkillTemplate(900010), 1), 1), "dispel level respects protected debuffs");
		check(!mentalCleanse.canDispelDebuff(new Effect(null, null, fixture.getSkillTemplate(900006), 1), 1), "debuff cleansing cannot remove friendly buffs");
		check(mentalCleanse.canDispelDebuff(mentalDebuff, 1) && mentalDebuff.getPower() == 60,
			"repeated dispel planning never decrements existing effect power");
		check(PlayerBotDispel.severity(fixture.getSkillTemplate(900008), 100) > PlayerBotDispel.severity(fixture.getSkillTemplate(900009), 100),
			"disabling control is cleansed before ordinary damage over time");
		check(PlayerBotDispel.severity(fixture.getSkillTemplate(900009), 20) > PlayerBotDispel.severity(fixture.getSkillTemplate(900009), 90),
			"same removable debuff is more urgent on a wounded ally");
		var condition = fixture.getSkillTemplate(900005).getChainCondition();
		ChainSkills chain = new ChainSkills();
		check(!condition.isAvailable(chain) && condition.isFollowUp(), "followup unavailable before starter");
		chain.updateChain("STRIKE_1TH", 10000);
		check(!condition.isAvailable(chain), "multi-use chain starter must reach required count");
		chain.updateChain("STRIKE_1TH", 10000);
		check(condition.isAvailable(chain), "followup available after correct chain count");
		check(condition.isAvailable(chain) && chain.getCurrentChainCount("STRIKE_1TH") == 2, "repeated planning leaves chain untouched");
		chain.updateChain("OTHER", 10000);
		check(condition.isAvailable(chain), "previous-category followup matches normal chain semantics");
		chain.resetChain(); chain.updateChain("STRIKE_1TH", 1); Thread.sleep(15);
		check(!condition.isAvailable(chain), "expired chain cannot be planned");
		buffChecks(context);
		DataManager.SKILL_DATA = (SkillData) context.createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/skills/skill_templates.xml").toFile());
		encounterMetadataChecks();
		DataManager.SKILL_CHARGE_DATA = (SkillChargeData) JAXBContext.newInstance(SkillChargeData.class).createUnmarshaller()
			.unmarshal(Path.of("game-server/data/static_data/skills/skill_charge.xml").toFile());
		DataManager.PET_SKILL_DATA = (PetSkillData) JAXBContext.newInstance(PetSkillData.class).createUnmarshaller()
			.unmarshal(Path.of("game-server/data/static_data/pet_skills/pet_skills.xml").toFile());
		var tree = (SkillTreeData) JAXBContext.newInstance(SkillTreeData.class).createUnmarshaller()
			.unmarshal(Path.of("game-server/data/static_data/skill_tree/skill_tree.xml").toFile());
		Map<PlayerBotRules.SkillKind, Integer> total = new EnumMap<>(PlayerBotRules.SkillKind.class);
		int mixedInterrupts = 0, chains = 0;
		for (var template : DataManager.SKILL_DATA.getSkillTemplates()) {
			var kind = PlayerBotSkills.classify(template); total.merge(kind, 1, Integer::sum);
			if (kind == DAMAGE && PlayerBotSkills.canInterrupt(template)) mixedInterrupts++;
			if (kind != UNSUPPORTED && template.getChainCondition() != null && template.getChainCondition().isFollowUp()) chains++;
		}
		check(mixedInterrupts > 0 && chains > 0, "production damage interrupts and chains recognized");
		check(PlayerBotSkills.classify(DataManager.SKILL_DATA.getSkillTemplate(2767)) == MODE, "Aethertech Embark has a setup action");
		check(total.getOrDefault(PlayerBotRules.SkillKind.SUMMON, 0) > 0 && total.getOrDefault(PET_ORDER, 0) > 0,
			"production spirit summons and pet orders recognized");
		int charged = 0;
		for (var start : DataManager.SKILL_DATA.getSkillTemplates()) {
			if (!start.isCharge() || PlayerBotSkills.classify(start) == UNSUPPORTED) continue;
			charged++;
			var stages = DataManager.SKILL_CHARGE_DATA.getChargedSkillEntry(start.getSkillChargeCondition().getValue());
			int duration = stages.getSkills().stream().mapToInt(s -> s.getTime()).sum();
			check(PlayerBotCharge.releaseDelay(stages.getSkills().stream().map(s -> s.getTime()).toList(), stages.getMinTime(), 1f, duration, false) > 0,
				"production charge has a release window: " + start.getSkillId());
			for (var stage : stages.getSkills()) {
				var released = DataManager.SKILL_DATA.getSkillTemplate(stage.getId());
				check(released != null && PlayerBotSkills.classify(released) == PlayerBotSkills.classify(start),
					"early and full release have consistent action kinds: " + start.getSkillId());
			}
		}
		check(charged > 20, "production charged skill families supported");
		check(Objects.equals(DataManager.PET_SKILL_DATA.findPetOrderSkill(3835, 833288), 22107), "spirit order resolves correct native pet template");
		check(DataManager.PET_SKILL_DATA.findPetOrderSkill(3835, -1) == null && DataManager.PET_SKILL_DATA.findPetOrderSkill(-1, 833288) == null,
			"unavailable spirit orders are rejected without null-unboxing failures");
		for (Race race : List.of(Race.ELYOS, Race.ASMODIANS)) {
			for (var pc : PlayerClass.values()) {
				Set<Integer> ids = new HashSet<>();
				for (int level = 1; level <= 65; level++) {
					for (var learn : tree.getTemplatesFor(pc, level, race)) ids.add(learn.getSkillId());
					if (!pc.isStartingClass())
						for (var learn : tree.getTemplatesFor(pc.getStartingClass(), level, race)) ids.add(learn.getSkillId());
				}
				Map<PlayerBotRules.SkillKind, Integer> kinds = new EnumMap<>(PlayerBotRules.SkillKind.class);
				int active = 0, unsupported = 0;
				for (int id : ids) {
					SkillTemplate template = DataManager.SKILL_DATA.getSkillTemplate(id);
					if (template == null || template.isPassive() || template.isProvoked()) continue;
					active++;
					var kind = PlayerBotSkills.classify(template); kinds.merge(kind, 1, Integer::sum);
					if (kind == UNSUPPORTED) unsupported++;
				}
				check(kinds.getOrDefault(DAMAGE, 0) > 0, race + " " + pc + " has recognized damage skills");
				if (pc == PlayerClass.CLERIC || pc == PlayerClass.CHANTER || pc == PlayerClass.PRIEST || pc == PlayerClass.BARD)
					check(kinds.getOrDefault(HEAL, 0) > 0, race + " " + pc + " has recognized healing skills");
				if (pc == PlayerClass.TEMPLAR) check(kinds.getOrDefault(TAUNT, 0) > 0, race + " Templar has recognized taunts");
				System.out.println(race + " " + pc + ": " + (active - unsupported) + "/" + active + " active templates recognized " + kinds);
			}
		}
		System.out.println("Production templates: " + total + "; damage interrupts=" + mixedInterrupts + "; followups=" + chains);
		DataManager.ITEM_DATA = (ItemData) JAXBContext.newInstance(ItemData.class).createUnmarshaller()
			.unmarshal(Path.of("game-server/data/static_data/items/item_templates.xml").toFile());
		for (Race race : List.of(Race.ELYOS, Race.ASMODIANS))
			for (var gender : com.aionemu.gameserver.model.Gender.values())
				for (var pc : PlayerClass.values()) {
					if (pc.isStartingClass()) continue;
					for (int level : new int[] { 10, 30, 65 }) {
						Set<Integer> learned = new HashSet<>();
						for (int n = 1; n <= level; n++) {
							for (var learn : tree.getTemplatesFor(pc, n, race)) if (learn.isAutolearn()) learned.add(learn.getSkillId());
							if (n < 10) for (var learn : tree.getTemplatesFor(pc.getStartingClass(), n, race)) if (learn.isAutolearn()) learned.add(learn.getSkillId());
						}
						check(DataManager.ITEM_DATA.getItemTemplates().stream().anyMatch(t -> t.getItemGroup() == PlayerBotGenerated.weapon(pc)
							&& PlayerBotGenerated.eligible(t, pc, race, gender, level, learned::contains)),
							"generated companion compatible common weapon: " + race + " " + gender + " " + pc + " level " + level);
					}
				}
		System.out.println("OK: " + checks + " skill metadata, faction/class coverage, buff capacities and non-mutating planning checks");
	}
	private static void encounterMetadataChecks() {
		for (var emitter : PlayerBotHazards.EMITTERS.entrySet()) {
			var skill = DataManager.SKILL_DATA.getSkillTemplate(emitter.getValue());
			check(skill != null && skill.getProperties().getTargetType() == com.aionemu.gameserver.skillengine.properties.TargetRangeAttribute.AREA,
				"persistent emitter uses a real native area: " + emitter.getKey());
			var area = PlayerBotHazards.from(skill.getProperties(), 0, 0, 0, 0, 0);
			check(area != null && area.risk(new PlayerBotNavigation.Point(0, 0, 0)) > 0
				&& area.risk(new PlayerBotNavigation.Point(area.radius() + 2, 0, 0)) == 0, "native emitter geometry remains usable: " + emitter.getKey());
		}
		for (int shield : List.of(20530, 20531)) {
			int protection = shield == 20530 ? 20535 : 20536;
			int retaliation = shield == 20530 ? 8760 : 8761;
			var element = shield == 20530 ? com.aionemu.gameserver.model.SkillElement.DARK : com.aionemu.gameserver.model.SkillElement.LIGHT;
			var stat = shield == 20530 ? com.aionemu.gameserver.model.stats.container.StatEnum.ELEMENTAL_RESISTANCE_DARK
				: com.aionemu.gameserver.model.stats.container.StatEnum.ELEMENTAL_RESISTANCE_LIGHT;
			check(DataManager.SKILL_DATA.getSkillTemplate(shield).getEffects().getEffects().stream().anyMatch(e -> e.getElement() == element)
				&& DataManager.SKILL_DATA.getSkillTemplate(retaliation).getEffects().getEffects().stream().anyMatch(e -> e.getElement() == element),
				"native shield and retaliation use the expected element: " + shield);
			var protective = DataManager.SKILL_DATA.getSkillTemplate(protection);
			check(protective.getEffects().getEffects().stream().filter(e -> e.getChange() != null)
				.flatMap(e -> e.getChange().stream()).anyMatch(c -> c.getStat() == stat && c.getValue() >= 8000),
				"matching protection raises the retaliation resistance: " + protection);
			check(PlayerBotHazards.from(protective.getProperties(), 0, 0, 0, 0, 0).radius() == 3,
				"flame approach fits the native 3m pulse: " + protection);
		}
	}
	private static void buffChecks(JAXBContext context) throws Exception {
		var aura1 = buff(context, 1, "activation=\"TOGGLE\" skillsubtype=\"CHANT\" tslot=\"NOSHOW\"");
		var aura2 = buff(context, 2, "activation=\"TOGGLE\" skillsubtype=\"CHANT\" tslot=\"NOSHOW\"");
		var aura3 = buff(context, 3, "activation=\"TOGGLE\" skillsubtype=\"CHANT\" tslot=\"NOSHOW\"");
		var aura4 = buff(context, 4, "activation=\"TOGGLE\" skillsubtype=\"CHANT\" tslot=\"NOSHOW\"");
		check(PlayerBotBuffs.canAdd(PlayerClass.CHANTER, aura3, List.of(aura1, aura2)), "Chanter can maintain three mantras");
		check(!PlayerBotBuffs.canAdd(PlayerClass.CHANTER, aura4, List.of(aura1, aura2, aura3)), "fourth mantra cannot endlessly replace the first");
		check(!PlayerBotBuffs.canAdd(PlayerClass.CHANTER, aura1, List.of(aura1)), "active toggle is never turned off by rebuffing");
		var toggle1 = buff(context, 5, "activation=\"TOGGLE\" skillsubtype=\"BUFF\" tslot=\"NOSHOW\"");
		var toggle2 = buff(context, 6, "activation=\"TOGGLE\" skillsubtype=\"BUFF\" tslot=\"NOSHOW\"");
		var toggle3 = buff(context, 7, "activation=\"TOGGLE\" skillsubtype=\"BUFF\" tslot=\"NOSHOW\"");
		check(!PlayerBotBuffs.canAdd(PlayerClass.SORCERER, toggle2, List.of(toggle1)), "ordinary classes retain one native toggle");
		check(PlayerBotBuffs.canAdd(PlayerClass.RIDER, toggle2, List.of(toggle1)), "Aethertech allows two native toggles");
		check(!PlayerBotBuffs.canAdd(PlayerClass.RANGER, toggle3, List.of(toggle1, toggle2)), "Ranger toggle capacity remains stable");
		var focus = buff(context, 8, "activation=\"ACTIVE\" skillsubtype=\"BUFF\" tslot=\"BUFF\" cooldownId=\"2022\"");
		var aim = buff(context, 9, "activation=\"ACTIVE\" skillsubtype=\"BUFF\" tslot=\"BUFF\" cooldownId=\"2024\"");
		var fury = buff(context, 10, "activation=\"ACTIVE\" skillsubtype=\"BUFF\" tslot=\"BUFF\" cooldownId=\"2026\"");
		check(PlayerBotBuffs.canAdd(PlayerClass.RANGER, aim, List.of(focus)), "second Ranger preparation buff allowed");
		check(!PlayerBotBuffs.canAdd(PlayerClass.RANGER, fury, List.of(focus, aim)), "third Ranger preparation does not evict an existing buff");
		var conflict1 = buff(context, 11, "activation=\"ACTIVE\" skillsubtype=\"BUFF\" tslot=\"BUFF\" conflict_id=\"555\"");
		var conflict2 = buff(context, 12, "activation=\"ACTIVE\" skillsubtype=\"BUFF\" tslot=\"BUFF\" conflict_id=\"555\"");
		check(!PlayerBotBuffs.canAdd(PlayerClass.GLADIATOR, conflict2, List.of(conflict1)), "conflicting preparations do not alternate");
	}
	private static SkillTemplate buff(JAXBContext context, int id, String attributes) throws Exception {
		String xml = "<skill_data><skill_template skill_id=\"" + id + "\" name=\"fixture\" stack=\"BOT_BUFF_" + id
			+ "\" skilltype=\"MAGICAL\" " + attributes + "><effects><statup value=\"1\"/></effects></skill_template></skill_data>";
		return ((SkillData) context.createUnmarshaller().unmarshal(new StringReader(xml))).getSkillTemplate(id);
	}
}
