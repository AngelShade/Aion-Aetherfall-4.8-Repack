package com.aionemu.gameserver.skillengine.effect;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import com.aionemu.gameserver.controllers.observer.AttackShieldObserver;
import com.aionemu.gameserver.skillengine.model.Effect;
import com.aionemu.gameserver.skillengine.model.ShieldType;

/**
 * @author ATracer, Wakizashi, Sippolo, kecimis
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ShieldEffect")
public class ShieldEffect extends EffectTemplate {

	@XmlAttribute
	protected int hitdelta;
	@XmlAttribute
	protected int hitvalue;
	@XmlAttribute
	protected boolean percent;
	@XmlAttribute
	protected int radius = 0;
	@XmlAttribute
	protected int minradius = 0;
	@XmlAttribute
	protected int hitcount = 0;

	@Override
	public void applyEffect(Effect effect) {
		// check for condition race, skillId: 10317,10318, implemented as RaceCondition
		effect.addToEffectedController();
	}

	@Override
	public void startEffect(Effect effect) {
		int valueWithDelta = calculateBaseValue(effect);
		int hitValueWithDelta = hitvalue + hitdelta * effect.getSkillLevel();
		int count = hitcount;
		if (count <= 0 && effect.getSkillTemplate() != null && effect.getSkillTemplate().getStack() != null) {
			String stack = effect.getSkillTemplate().getStack();
			int idx = stack.indexOf("COUNT");
			if (idx != -1) {
				int start = idx + 5;
				int end = start;
				while (end < stack.length() && Character.isDigit(stack.charAt(end))) {
					end++;
				}
				if (end > start) {
					try {
						count = Integer.parseInt(stack.substring(start, end));
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}

		AttackShieldObserver asObserver = new AttackShieldObserver(hitValueWithDelta, valueWithDelta, percent, effect, hitType, getType(), hitTypeProb, 0, count);
		effect.addObserver(effect.getEffected(), asObserver);
	}

	public ShieldType getType() {
		return ShieldType.NORMAL;
	}

}
