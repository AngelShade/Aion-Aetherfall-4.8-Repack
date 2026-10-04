package com.aionemu.gameserver.services.playerbot;
import java.util.List;
import com.aionemu.gameserver.services.playerbot.PlayerBotQuestRoutes.*;
public final class PlayerBotQuestRoutesCheck {
 static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static Goal g(int quest,Kind kind,double distance,boolean group,boolean visible){return new Goal(quest,quest+100,kind,new PlayerBotNavigation.Point((float)distance,0,0),group,visible,distance);}
 public static void main(String[] args) {
  var hunt=g(1,Kind.HUNT,3,false,true);var talk=g(2,Kind.CONVERSATION,10,false,true);var reward=g(3,Kind.REWARD,20,false,true);var object=g(4,Kind.OBJECT,4,false,true);
  check(PlayerBotQuestRoutes.select(List.of(hunt,reward))==reward,"Native ready reward destination precedes another hunt");
  check(PlayerBotQuestRoutes.select(List.of(hunt,talk))==talk,"Intermediate interaction is a separate objective before further hunting");
  check(PlayerBotQuestRoutes.select(List.of(hunt,object))==object,"Object collection is not treated as kill credit");
  var grouped=g(5,Kind.HUNT,15,true,true);
  check(PlayerBotQuestRoutes.select(List.of(reward,grouped))==grouped,"Original group destination coordination precedes independent travel");
  var staticPoint=g(6,Kind.REWARD,2,false,false);
  check(PlayerBotQuestRoutes.select(List.of(staticPoint,reward))==reward,"Actual visible instance actor precedes a static source hint");
  var near=g(7,Kind.CONVERSATION,2,false,true);
  check(PlayerBotQuestRoutes.select(List.of(talk,near))==near,"Nearest equivalent conversation is selected");
  check(PlayerBotQuestRoutes.select(List.of())==null,"Unsupported quest remains without invented destination");
  check(PlayerBotQuestRoutes.select(List.of(g(1,Kind.HUNT,Double.NaN,false,true)))==null,"Invalid world coordinates rejected");
  check(PlayerBotQuestRoutes.select(List.of(g(1,Kind.HUNT,-5,false,true)))==null,"Negative route distances rejected");
  check(PlayerBotQuestRoutes.leash(Kind.OBJECT)<25,"Quest object navigation stays inside native interaction owner radius");
  check(PlayerBotQuestRoutes.leash(Kind.CONVERSATION)<25,"Conversation route stays inside the native intermediate-interaction owner radius");
  check(PlayerBotQuestRoutes.leash(Kind.HUNT)<45,"Automatic hunt does not enlarge native pull leash");
  check(PlayerBotQuestRoutes.key(hunt).equals(PlayerBotQuestRoutes.key(hunt)),"Retries use stable native destination identity");
  check(!PlayerBotQuestRoutes.key(hunt).equals(PlayerBotQuestRoutes.key(talk)),"Different objectives do not poison each other's retries");
  check(!PlayerBotQuestRoutes.enabled(Kind.HUNT,false),"Switching off quest combat also invalidates a cached hunt route");
  check(PlayerBotQuestRoutes.enabled(Kind.HUNT,true),"Opt-in hunt remains enabled");
  check(PlayerBotQuestRoutes.enabled(Kind.CONVERSATION,false) && PlayerBotQuestRoutes.enabled(Kind.OBJECT,false)
   && PlayerBotQuestRoutes.enabled(Kind.REWARD,false),"Quest combat toggle does not block peaceful quest objectives");
  System.out.println("OK: "+checks+" upstream group destination and native quest-route priority/leash comparisons");
 }
}
