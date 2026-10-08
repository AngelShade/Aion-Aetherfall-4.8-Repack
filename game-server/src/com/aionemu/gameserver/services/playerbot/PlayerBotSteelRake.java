package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.utils.PositionUtil;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Visible native mechanics only. Does not pull unopened rooms or bypass NPC interaction channels. */
final class PlayerBotSteelRake {
 private record SimpleAction(String name,BooleanSupplier useful,BooleanSupplier execution) implements Action {
  public boolean isUseful(){return useful.getAsBoolean();}
  public boolean isPossible(){return true;}
  public boolean execute(){return execution.getAsBoolean();}
 }
 private static Trigger trigger(Action action,double priority){return new Trigger(()->true,action,()->priority);}
 private record Claim(int bot,long until){}
 private static final Map<Integer,Claim> CLAIMS=new ConcurrentHashMap<>();
 static int feeder(boolean hunger,boolean thirst){return hunger?701386:thirst?701387:0;}
 static boolean shielded(String ai,boolean captain,boolean armor,boolean master){return "brasseyegrogget".equals(ai)&&captain || "gunnerkoakoa".equals(ai)&&armor || "tamer_anikiki".equals(ai)&&master;}
 static boolean attackable(Npc npc){return !PlayerBotSteelRakeCaptainTactics.scenery(npc.getNpcId())&&!shielded(npc.getAi().getName(),npc.getEffectController().hasAbnormalEffect(18191),npc.getEffectController().hasAbnormalEffect(18552),npc.getEffectController().hasAbnormalEffect(18189));}
 static Integer emitter(Npc npc){
  if("bomb".equals(npc.getAi().getName())){
   var ai=com.aionemu.gameserver.dataholders.DataManager.AI_DATA.getAiTemplate(npc.getNpcId());
   if(ai!=null && ai.getBombs()!=null && ai.getBombs().getBombTemplate()!=null)return ai.getBombs().getBombTemplate().getSkillId();
  }
  if("walkingtalkingbomb".equals(npc.getAi().getName()))return 19416;
  return null;
 }
 static boolean channeling(Player bot){
  final boolean[] valid={false};long now=System.currentTimeMillis();
  bot.getKnownList().forEachNpc(n->{Claim c=CLAIMS.get(n.getObjectId());if(c!=null&&c.bot()==bot.getObjectId()&&c.until()>now&&n.isSpawned()&&!n.isDead()&&"feeding_mantutu".equals(n.getAi().getName())&&n.getWorldId()==bot.getWorldId()&&n.getInstanceId()==bot.getInstanceId())valid[0]=true;});
  return valid[0];
 }
 static void close(Player bot){CLAIMS.entrySet().removeIf(e->e.getValue().bot()==bot.getObjectId());}
 static Trigger feeding(PlayerBotSession session,PlayerBotNavigation navigation,List<Npc> engaged){
  var bot=session.bot();var owner=session.owner();
  if(session.closing() || session.combatRole()==Role.TANK || bot.isDead())return null;
  Npc boss=engaged.stream().filter(n->"golden_eye_mantutu".equals(n.getAi().getName())).findFirst().orElse(null);
  if(boss==null)return null;
  int device=feeder(boss.getEffectController().hasAbnormalEffect(20489),boss.getEffectController().hasAbnormalEffect(20490));
  if(device==0)return null;
  List<Npc> candidates=new ArrayList<>();
  bot.getKnownList().forEachNpc(n->{if(n.getNpcId()==device && n.isSpawned() && !n.isDead() && n.getWorldId()==bot.getWorldId() && n.getInstanceId()==bot.getInstanceId() && PositionUtil.isInRange(owner,n,35))candidates.add(n);});
  Npc npc=candidates.stream().min(Comparator.comparingDouble(n->PositionUtil.getDistance(bot,n))).orElse(null);if(npc==null)return null;
  // Keep the healer available when another living non-tank can operate the device.
  var operators=PlayerBotService.getInstance().companions(owner).stream().filter(s->!s.closing()&&!s.bot().isDead()&&s.combatRole()!=Role.TANK&&PlayerBotPartyBehavior.state(s).order==Order.FOLLOW&&s.bot().getKnownList().sees(npc))
   .sorted(Comparator.comparingInt((PlayerBotSession s)->s.combatRole()==Role.HEALER?1:0).thenComparingDouble(s->PositionUtil.getDistance(s.bot(),npc)).thenComparingInt(s->s.bot().getObjectId())).toList();
  if(operators.isEmpty() || operators.getFirst()!=session)return null;
  return trigger(new SimpleAction("operate Mantutu supply device",()->npc.isSpawned()&&boss.isSpawned()&&!boss.isDead()&&feeder(boss.getEffectController().hasAbnormalEffect(20489),boss.getEffectController().hasAbnormalEffect(20490))==device,()->{
   CLAIMS.values().removeIf(c->c.until()<System.currentTimeMillis());
   Claim claim=CLAIMS.compute(npc.getObjectId(),(id,old)->old==null||old.bot()==bot.getObjectId()?new Claim(bot.getObjectId(),System.currentTimeMillis()+15000):old);
   if(claim.bot()!=bot.getObjectId())return false;
   if(!PositionUtil.isInTalkRange(bot,npc))return navigation.approach(npc,2);
   if(!DialogService.isInteractionAllowed(bot,npc)){CLAIMS.remove(npc.getObjectId(),claim);return false;}
   navigation.stop();npc.getController().onDialogRequest(bot);if(!bot.getController().hasScheduledTask(com.aionemu.gameserver.model.TaskId.ACTION_ITEM_NPC))CLAIMS.remove(npc.getObjectId(),claim);return true;
  }),ENCOUNTER+5);
 }
 static Npc adds(List<Npc> enemies){return PlayerBotSteelRakeCaptainTactics.adds(enemies);}
 private PlayerBotSteelRake(){}
}
