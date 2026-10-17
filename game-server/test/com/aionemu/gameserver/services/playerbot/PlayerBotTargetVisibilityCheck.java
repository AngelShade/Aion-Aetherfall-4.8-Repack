package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import jakarta.xml.bind.JAXBContext;
import javax.xml.validation.SchemaFactory;

import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureVisualState;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.world.WorldPosition;
import com.aionemu.gameserver.world.knownlist.KnownList;
import com.aionemu.gameserver.world.knownlist.KnownObject;

/** Native JAXB/visibility and shared final rejection; no constructors, World, Cleaner IDs, DB or networking. */
public final class PlayerBotTargetVisibilityCheck {
 static int checks;
 static Object unsafe;
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 static <T> T allocate(Class<T> type)throws Exception{return type.cast(unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,type));}
 static void set(Object object,Class<?> owner,String field,Object value)throws Exception{
  Field f=owner.getDeclaredField(field);f.setAccessible(true);f.set(object,value);
 }
 static final class Known extends KnownList {
  Known(Player owner){super(owner);}
  void put(Npc npc,boolean visible)throws Exception{
   KnownObject object=new KnownObject(npc);set(object,KnownObject.class,"visible",visible);knownObjects.put(npc.getObjectId(),object);
  }
  void forget(Npc npc){knownObjects.remove(npc.getObjectId());}
 }
 static Player observer(int see,boolean world)throws Exception {
  Player player=allocate(Player.class);set(player,Creature.class,"seeState",see);set(player,Player.class,"worldBot",world);
  player.setKnownlist(new Known(player));return player;
 }
 static Npc npc(NpcTemplate template,int hide,boolean spawned)throws Exception {
  Npc npc=allocate(Npc.class);set(npc,VisibleObject.class,"objectTemplate",template);
  set(npc,Creature.class,"visualState",hide);
  WorldPosition position=new WorldPosition(300230000);set(position,WorldPosition.class,"isSpawned",spawned);
  set(npc,VisibleObject.class,"position",position);return npc;
 }
 public static void main(String[] args)throws Exception {
  Field f=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=f.get(null);
  Path source=args.length==0?Path.of("game-server"):Path.of(args[0]);
  var unmarshaller=JAXBContext.newInstance(NpcTemplate.class).createUnmarshaller();
  var schema=SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema").newSchema(source.resolve("data/static_data/npcs/npcs.xsd").toFile());
  String xml=Files.readString(source.resolve("data/static_data/npcs/npc_templates.xml"));
  Map<Integer,NpcTemplate> templates=new java.util.HashMap<>();
  for(int id:new int[]{282111,282112,282113,282114,282115,282116,282117,282118,282119,282093,282095,217005,217006}) {
   var match=Pattern.compile("<npc_template npc_id=\""+id+"\"[^>]*>.*?</npc_template>",Pattern.DOTALL).matcher(xml);
   check(match.find(),"Native source template exists "+id);
   schema.newValidator().validate(new javax.xml.transform.stream.StreamSource(new StringReader("<npc_templates>"+match.group()+"</npc_templates>")));
   NpcTemplate template=(NpcTemplate)unmarshaller.unmarshal(new StringReader(match.group()));templates.put(id,template);
   check(template.getTemplateId()==id,"Production schema/JAXB ID "+id);
   check(template.isClientVisible()==(id<282111 || id>282119),"Client controller/render metadata "+id);
  }
  check(new NpcTemplate().isClientVisible(),"Unannotated templates retain native default visibility");
  Player player=observer(0,true);
  var method=PlayerBotSession.class.getDeclaredMethod("validEnemy",Npc.class,List.class,boolean.class);method.setAccessible(true);
  PlayerBotSession session=allocate(PlayerBotSession.class);set(session,PlayerBotSession.class,"bot",player);
  for(int id=282111;id<=282119;id++) {
   Npc npc=npc(templates.get(id),0,true);((Known)player.getKnownList()).put(npc,true);
   check(player.canSee(npc) && player.getKnownList().sees(npc),"Prior native hide/known checks falsely see nonrendered controller "+id);
   check(!PlayerBotTargetEligibility.visible(player,npc),"Controller cannot enter shared target pool "+id);
   check(!(boolean)method.invoke(session,npc,List.of(),false),"Session acquisition rejects controller before threat "+id);
   check(!(boolean)method.invoke(session,npc,List.of(),true),"Explicit/quest target cannot bypass controller gate "+id);
   check(!PlayerBotService.allowsTarget(player,npc),"Final native damage/cast hook rejects controller "+id);
   set(player,Player.class,"worldBot",false);set(player,Player.class,"playerBotOwnerId",1);
   check(!PlayerBotService.allowsTarget(player,npc),"Controlled companion shares final controller gate "+id);
   set(player,Player.class,"playerBotOwnerId",0);
   check(PlayerBotService.allowsTarget(player,npc),"Human policy is unchanged "+id);
   set(player,Player.class,"worldBot",true);
  }
  for(int id:new int[]{282093,282095,217005,217006}) {
   for(int hide:new int[]{0,1,2,5,10,20,64,65,66}) {
    for(int see:new int[]{0,1,2,5,10,20}) {
     for(boolean spawned:new boolean[]{false,true}) {
      for(boolean known:new boolean[]{false,true}) {
       for(boolean cachedVisible:new boolean[]{false,true}) {
        Player observer=observer(see,true);Npc npc=npc(templates.get(id),hide,spawned);
        Known kl=(Known)observer.getKnownList();if(known)kl.put(npc,cachedVisible);
        boolean expected=spawned && known && cachedVisible && (hide&~CreatureVisualState.BLINKING.getId())<=see;
        check(PlayerBotTargetEligibility.visible(observer,npc)==expected,"Native detection/known/spawn admission "+id);
       }
      }
     }
    }
   }
   Npc real=npc(templates.get(id),0,true);Player detector=observer(0,true);((Known)detector.getKnownList()).put(real,true);
   check(PlayerBotTargetEligibility.visible(detector,real),"Visible damageable relic/boss remains eligible "+id);
   real.setVisualState(CreatureVisualState.HIDE2);
   check(!PlayerBotTargetEligibility.visible(detector,real),"Fresh native hide beats stale known visibility "+id);
   set(detector,Creature.class,"seeState",2);
   check(PlayerBotTargetEligibility.visible(detector,real),"Native detection restores actual hidden combat actor "+id);
  }
  check(!PlayerBotTargetEligibility.visible(null,null),"Absent actors rejected");
  check(!PlayerBotTargetEligibility.visible(player,null),"Absent target rejected");
  System.out.println("OK: "+checks+" shared bot native visibility/controller checks; no World/SQL/IDs/network");
 }
}
