package com.aionemu.gameserver.services.playerbot;

import static com.aionemu.gameserver.skillengine.effect.EffectType.*;
import java.lang.reflect.Field;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.skillengine.effect.AbstractDispelEffect;
import com.aionemu.gameserver.skillengine.effect.SpellAttackEffect;
import com.aionemu.gameserver.skillengine.model.Effect;
import com.aionemu.gameserver.skillengine.model.EffectReserved.ResourceType;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;

/** Prioritizes only effects that this learned cleanse can actually dispel. No effect initialization or power changes. */
final class PlayerBotDispel {
	private static final Field POWER, DELTA_POWER;
	static {
		try {
			POWER=AbstractDispelEffect.class.getDeclaredField("power");DELTA_POWER=AbstractDispelEffect.class.getDeclaredField("dpower");
			POWER.setAccessible(true);DELTA_POWER.setAccessible(true);
		} catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
	}
	/** An already calculated terminal damage tick, never a new damage roll or a fabricated removal. */
	record Urgent(Effect effect,long deadline,int contribution) {}
	static long fatalDeadline(Effect effect,Creature target) {
		if(target.isDead() || effect.getPower()<=0)return 0;
		for(var template:effect.getSuccessEffects())if(template instanceof SpellAttackEffect tick && tick.getChecktime()>0
			&& effect.getDuration()>0 && effect.getDuration()<=tick.getChecktime()+1000L) {
			var damage=effect.getReserveds(template.getPosition());
			if(damage!=null && damage.isDamage() && damage.getType()==ResourceType.HP && damage.getValue()>=target.getLifeStats().getCurrentHp()
				&& PlayerBotEnemyUtility.nativeDispellable(target,effect))
				// Native overtime lasts one extra second; do not budget that final tail as rescue time.
				return Math.max(0,effect.getRemainingTimeMillis()-1000);
		}
		return 0;
	}
	static boolean fatal(Creature target){return target.getEffectController().getAbnormalEffects().stream().anyMatch(e->fatalDeadline(e,target)>0);}
	static int contribution(PlayerBotSkills.Entry entry,Effect effect) {
		if(entry.template().getEffects()==null)return 0;
		long power=0;
		for(var template:entry.template().getEffects().getEffects())if(template instanceof AbstractDispelEffect dispel && dispel.canDispelDebuff(effect,entry.level())) {
			try {power+=Math.max(0,POWER.getInt(dispel)+(long)DELTA_POWER.getInt(dispel)*entry.level());}
			catch(IllegalAccessException failure){throw new IllegalStateException("Native cleanse power unavailable",failure);}
		}
		return (int)Math.min(Integer.MAX_VALUE,power);
	}
	static Urgent urgent(PlayerBotSkills.Entry entry,Creature target) {
		Urgent best=null;
		for(var effect:target.getEffectController().getAbnormalEffects()) {
			long deadline=fatalDeadline(effect,target);if(deadline<=Math.max(400,entry.template().getDuration())+500L)continue;
			int power=contribution(entry,effect);if(power<=0)continue;
			if(best==null || deadline<best.deadline())best=new Urgent(effect,deadline,power);
		}
		return best;
	}
	static double urgency(Urgent need) {
		if(need==null)return 0;
		return 70+Math.clamp((6000-need.deadline())/1000.0,0,6)
			+8*Math.min(1,need.contribution()/(double)Math.max(1,need.effect().getPower()));
	}
	static double score(PlayerBotSkills.Entry entry, Creature target) {
		double best = 0;
		for (var effect : target.getEffectController().getAbnormalEffects()) {
			if (effect.getRemainingTimeMillis() <= 500 || entry.template().getEffects() == null) continue;
			boolean removable = entry.template().getEffects().getEffects().stream().filter(e -> e instanceof AbstractDispelEffect)
				.map(e -> (AbstractDispelEffect) e).anyMatch(e -> e.canDispelDebuff(effect, entry.level()));
			if (removable) best = Math.max(best, severity(effect.getSkillTemplate(), target.getLifeStats().getHpPercentage()));
		}
		return Math.max(best,urgency(urgent(entry,target)));
	}
	static double severity(SkillTemplate debuff, double health) {
		if (debuff == null) return 0;
		double result = debuff.hasAnyEffect(PARALYZE, FEAR, SLEEP, STUN, STUMBLE, STAGGER) ? 30
			: debuff.hasAnyEffect(SILENCE) ? 25 : debuff.hasAnyEffect(ROOT, SNARE) ? 18
			: debuff.hasAnyEffect(BLEED, POISON, SPELLATTACK) ? 12 : 5;
		return result + Math.clamp((100 - health) / 10, 0, 10);
	}
	private PlayerBotDispel() {}
}
