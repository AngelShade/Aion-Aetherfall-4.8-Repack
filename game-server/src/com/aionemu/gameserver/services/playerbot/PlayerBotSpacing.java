package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;

/** Owner-controlled ranged spacing; one small retreat, then commit to combat. */
public final class PlayerBotSpacing {
 record Values(int account,float follow,float attack) {}
 static final class Engagement { long quiet; boolean retreated; }
 private static final Map<Integer,Values> SETTINGS=new ConcurrentHashMap<>();
 private static final Map<Integer,Engagement> ENGAGEMENTS=new ConcurrentHashMap<>();
 static Path path(int character){return Path.of("config","playerbots","spacing-character-"+character+".properties");}
 static void validate(float follow,float attack){
  if(!Float.isFinite(follow)||follow<2||follow>12||!Float.isFinite(attack)||attack<4||attack>18)
   throw new IllegalArgumentException("Owner spacing must be 2–12 m; attack spacing must be 4–18 m.");
 }
 static Values values(Player bot){
  int account=bot.getAccount().getId();
  var value=SETTINGS.computeIfAbsent(bot.getObjectId(),id->{
   try {
    var p=PlayerBotMetadata.load(account,id,"spacing",path(id));
    if(p.isEmpty())return new Values(account,4,10);
    if(Integer.parseInt(p.getProperty("account"))!=account||Integer.parseInt(p.getProperty("character"))!=id)throw new IllegalArgumentException("Spacing owner mismatch");
    float follow=Float.parseFloat(p.getProperty("follow")),attack=Float.parseFloat(p.getProperty("attack"));validate(follow,attack);
    return new Values(account,follow,attack);
   }catch(Exception e){throw new IllegalStateException("Cannot load companion spacing",e);}
  });
  if(value.account()!=account)throw new IllegalStateException("Spacing owner mismatch");return value;
 }
 public static void configure(PlayerBotSession session,float follow,float attack){
  validate(follow,attack);
  synchronized(session){
   if(session.closing())throw new IllegalArgumentException("Companion is saving.");
   var bot=session.bot();if(bot.getAccount().getId()!=session.owner().getAccount().getId())throw new IllegalArgumentException("Spacing owner mismatch");
   values(bot);var p=new Properties();p.setProperty("account",""+bot.getAccount().getId());p.setProperty("character",""+bot.getObjectId());
   p.setProperty("follow",""+follow);p.setProperty("attack",""+attack);Path file=path(bot.getObjectId());
   try{PlayerBotMetadata.save(bot.getAccount().getId(),bot.getObjectId(),"spacing",p);
   }catch(java.io.IOException e){throw new IllegalArgumentException("Companion spacing could not be saved",e);}
   SETTINGS.put(bot.getObjectId(),new Values(bot.getAccount().getId(),follow,attack));
  }
 }
 public static Map<String,Object> snapshot(Player bot,PlayerBotRules.Role role){
  var v=values(bot);return Map.of("rangedSpacing",PlayerBotCombatPosition.ranged(bot.getPlayerClass(),role),"ownerSpacing",v.follow(),"attackSpacing",v.attack());
 }
 static float attack(Player bot,float nativeDistance){return Math.min(values(bot).attack(),nativeDistance);}
 static float nativeDistance(com.aionemu.gameserver.model.PlayerClass pc,PlayerBotRules.Role role,float weapon,List<PlayerBotSkills.Entry> skills){
  weapon=Math.max(1.5f,weapon);if(!PlayerBotCombatPosition.ranged(pc,role))return weapon;
  var bands=new HashMap<Float,Integer>();
  for(var e:skills)if(e.kind()==PlayerBotRules.SkillKind.DAMAGE&&e.range()>=8)bands.merge(e.range(),1,Integer::sum);
  float common=0;int most=0;
  for(var band:bands.entrySet())if(band.getValue()>most||band.getValue()==most&&band.getKey()>common){common=band.getKey();most=band.getValue();}
  float reach=Math.max(weapon,common);
  if(reach<8)for(var e:skills)if(e.kind()==PlayerBotRules.SkillKind.HEAL&&e.range()>=8)reach=Math.max(reach,e.range());
  return reach>=8?Math.max(6,reach-1.5f):weapon;
 }
 static PlayerBotNavigation.Point formation(Player owner,Player bot,PlayerBotNavigation.Point p){
  if(!PlayerBotCombatPosition.ranged(bot.getPlayerClass(),PlayerBotService.getInstance().combatRole(bot)))return p;
  double dx=p.x()-owner.getX(),dy=p.y()-owner.getY(),length=Math.hypot(dx,dy);
  if(length<.01)return p;float radius=values(bot).follow();
  if(PlayerBotFormationLayout.selected(owner.getAccount().getId(),owner.getObjectId()).equals("line")) {
   int count=PlayerBotCombatPosition.formationOrder(owner).size();
   // Scale the wings together: normalizing both inner and outer line slots to
   // the same radius would stack two ranged companions on each side.
   double outer=3.4*Math.max(1,count/2);radius*=Math.min(1,length/outer);
  }
  return new PlayerBotNavigation.Point(owner.getX()+(float)(dx/length*radius),owner.getY()+(float)(dy/length*radius),p.z());
 }
 static void observe(Player bot,Creature target,long now){
  var state=ENGAGEMENTS.computeIfAbsent(bot.getObjectId(),id->new Engagement());
  synchronized(state){if(target==null){if(state.quiet==0)state.quiet=now;if(now-state.quiet>=5000)state.retreated=false;}else state.quiet=0;}
 }
 static boolean canRetreat(Player bot,Creature target,float desired){
  var state=ENGAGEMENTS.computeIfAbsent(bot.getObjectId(),id->new Engagement());
  synchronized(state){return !state.retreated&&desired>=4&&PositionUtil.isInRange(bot,target,2.5f,false);}
 }
 static boolean retreat(Player owner,Player bot,Creature target,PlayerBotNavigation navigation){
  var state=ENGAGEMENTS.computeIfAbsent(bot.getObjectId(),id->new Engagement());
  synchronized(state){
   if(state.retreated)return false;state.retreated=true;
   double dx=bot.getX()-target.getX(),dy=bot.getY()-target.getY(),length=Math.hypot(dx,dy);
   if(length<.01){dx=-Math.cos(bot.getHeading()*Math.PI/60);dy=-Math.sin(bot.getHeading()*Math.PI/60);length=1;}
   float x=bot.getX()+(float)(dx/length*2),y=bot.getY()+(float)(dy/length*2);
   // Do not stretch the party while a tank is trying to catch the pursuer.
   if(PositionUtil.getDistance(owner,x,y,bot.getZ())>values(bot).follow()+values(bot).attack())return false;
   return navigation.move(x,y,bot.getZ());
  }
 }
 static void close(Player bot){SETTINGS.remove(bot.getObjectId());ENGAGEMENTS.remove(bot.getObjectId());}
 private PlayerBotSpacing(){}
}
