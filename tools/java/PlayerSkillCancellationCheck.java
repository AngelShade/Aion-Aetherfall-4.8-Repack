package com.aionemu.gameserver.controllers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemUseAnimation;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.network.aion.AionServerPacket;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ITEM_USAGE_ANIMATION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SKILL_CANCEL;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.skillengine.model.Skill;
import com.aionemu.gameserver.skillengine.model.Skill.SkillMethod;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;

/** A deterministic clear between native cast reads; inert shells, no World or allocated native IDs. */
public final class PlayerSkillCancellationCheck {
 public record Packet(Player recipient, AionServerPacket packet, boolean broadcast) {}
 public static final List<Packet> packets = new ArrayList<>();
 private static final List<String> order = new ArrayList<>();
 private static int checks;
 private static Object unsafe;
 private static java.lang.reflect.Method allocate;
 private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
 private static <T> T shell(Class<T> type) throws Exception { return type.cast(allocate.invoke(unsafe, type)); }
 public static void packet(Player player, AionServerPacket packet, boolean broadcast) { packets.add(new Packet(player, packet, broadcast)); }
 static final class Actor extends Player {
  Skill current; int reads, clears, boosts; long boostUntil; float boostSpeed;
  Actor() { super(null, null); }
  @Override public Skill getCastingSkill() { reads++; Skill snapshot = current; current = null; return snapshot; }
  @Override public void setCasting(Skill skill) { clears++; current = skill; order.add("clear"); }
  @Override public void setHitTimeBoost(long until, float speed) { boosts++; boostUntil = until; boostSpeed = speed; order.add("boost"); }
 }
 static final class Cast extends Skill {
  SkillMethod method; boolean boost; int cancels; SkillTemplate template; ItemTemplate item; Creature target;
  Cast() { super(new SkillTemplate(), (Creature) null, 0, null, null); }
  @Override public void cancelCast() { cancels++; order.add("cancel"); }
  @Override public SkillMethod getSkillMethod() { return method; }
  @Override public boolean allowAnimationBoostByCastSpeed() { return boost; }
  @Override public float getCastSpeedForAnimationBoostAndChargeSkills() { return 0.75f; }
  @Override public SkillTemplate getSkillTemplate() { return template; }
  @Override public ItemTemplate getItemTemplate() { return item; }
  @Override public int getItemObjectId() { return 0; }
  @Override public Creature getFirstTarget() { return target; }
 }
 private static Object field(Object object, String name) throws Exception {
  Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
 }
 private static void scenario(SkillMethod method, boolean boost, boolean message, boolean otherAttacker) throws Exception {
  var actor = shell(Actor.class); var attacker = otherAttacker ? shell(Actor.class) : actor; var cast = shell(Cast.class);
  cast.method = method; cast.boost = boost; cast.template = new SkillTemplate(); cast.item = new ItemTemplate(); cast.target = actor;
  actor.current = cast; var controller = new PlayerController(); controller.setOwner(actor);
  var reason = message ? SM_SYSTEM_MESSAGE.STR_SKILL_TARGET_LOST() : null; packets.clear(); order.clear();
  controller.cancelCurrentSkill(attacker, reason);
  check(actor.reads == 1, "Only one native casting snapshot can observe the clearing race");
  check(cast.cancels == 1 && actor.clears == 1 && actor.current == null, "Snapshot cancellation and native clear each run once");
  check(order.equals(List.of("cancel", "clear", "boost")), "Native cancel/clear/animation-boost ordering stays intact");
  check(actor.boosts == 1 && actor.boostUntil == (boost ? Long.MAX_VALUE : 0) && actor.boostSpeed == (boost ? 0.75f : 0), "Native retail cast-speed boost behavior stays intact");
  int actorPackets = method == SkillMethod.CAST ? (message ? 2 : 1) : method == SkillMethod.ITEM ? 2 : 0;
  check(packets.size() == actorPackets + (otherAttacker ? 1 : 0), "Method-specific and attacker packet counts stay intact");
  if (method == SkillMethod.CAST) {
   check(packets.get(0).packet() instanceof SM_SKILL_CANCEL && packets.get(0).broadcast() && packets.get(0).recipient() == actor, "Cast cancellation broadcasts the native packet");
   check((int) field(packets.get(0).packet(), "skillId") == cast.template.getSkillId(), "Native cancellation identifies the captured skill");
   if (message) check(packets.get(1).packet() == reason && !packets.get(1).broadcast(), "Optional native cancellation reason is retained");
  } else if (method == SkillMethod.ITEM) {
   check(packets.get(0).packet() instanceof SM_SYSTEM_MESSAGE && !packets.get(0).broadcast(), "Item cancellation retains its native message");
   check(packets.get(1).packet() instanceof SM_ITEM_USAGE_ANIMATION && packets.get(1).broadcast(), "Item cancellation retains native broadcast animation");
   check(field(packets.get(1).packet(), "animation") == ItemUseAnimation.USE_CANCEL, "Item animation remains USE_CANCEL");
  }
  if (otherAttacker) check(packets.getLast().recipient() == attacker && packets.getLast().packet() instanceof SM_SYSTEM_MESSAGE && !packets.getLast().broadcast(), "Other player attacker receives native cancellation feedback");
 }
 public static void main(String[] args) throws Exception {
  var unsafeType = Class.forName("sun.misc.Unsafe"); var field = unsafeType.getDeclaredField("theUnsafe"); field.setAccessible(true); unsafe = field.get(null); allocate = unsafeType.getMethod("allocateInstance", Class.class);
  if (args.length > 0 && args[0].equals("--expect-race")) {
   try { scenario(SkillMethod.CAST, false, true, true); throw new AssertionError("Installed baseline no longer reproduces the reported double-read race"); }
   catch (NullPointerException reproduced) { check(reproduced.getStackTrace()[0].getClassName().equals(PlayerController.class.getName()), "Race is reproduced in native PlayerController"); }
   System.out.println("OK: " + checks + " installed native cancellation race reproduction; no World/SQL/native IDs"); return;
  }
  var empty = shell(Actor.class); var controller = new PlayerController(); controller.setOwner(empty); packets.clear(); order.clear();
  controller.cancelCurrentSkill(null, null);
  check(empty.reads == 1 && empty.clears == 0 && empty.boosts == 0 && packets.isEmpty(), "Empty casting snapshot safely preserves all state");
  scenario(SkillMethod.CAST, false, true, true);
  scenario(SkillMethod.CAST, true, false, false);
  scenario(SkillMethod.ITEM, false, true, true);
  scenario(SkillMethod.PASSIVE, false, false, false);
  scenario(SkillMethod.PROVOKED, true, false, false);
  scenario(SkillMethod.PENALTY, false, false, false);
  System.out.println("OK: " + checks + " native player cancellation race/animation/packet checks; no World/SQL/native IDs");
 }
}
