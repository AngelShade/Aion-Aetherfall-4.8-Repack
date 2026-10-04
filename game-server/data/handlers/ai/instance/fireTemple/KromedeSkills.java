package ai.instance.fireTemple;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.skillengine.model.Skill;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.model.SkillType;

/** Preserve native effects/radii/levels, with a physical Verdict local to this encounter. */
public final class KromedeSkills {
	private static SkillTemplate verdictSource;
	private static SkillTemplate physicalVerdict;
	private KromedeSkills() { }

	public static synchronized Skill create(Npc owner, int id, Creature target) {
		SkillTemplate template = DataManager.SKILL_DATA.getSkillTemplate(id);
		if (template == null) return null;
		if (id == 16674) {
			if (template != verdictSource) {
				SkillTemplate copy = new SkillTemplate();
				try {
					for (Field field : SkillTemplate.class.getDeclaredFields()) {
						if (Modifier.isStatic(field.getModifiers())) continue;
						field.setAccessible(true);
						field.set(copy, field.get(template));
					}
					Field type = SkillTemplate.class.getDeclaredField("type");
					type.setAccessible(true);
					type.set(copy, SkillType.PHYSICAL);
				} catch (ReflectiveOperationException failure) {
					throw new IllegalStateException("Cannot create encounter-local Guilty Verdict", failure);
				}
				physicalVerdict = copy;
				verdictSource = template;
			}
			template = physicalVerdict;
		}
		return new Skill(template, owner, 28, target, null);
	}
}
