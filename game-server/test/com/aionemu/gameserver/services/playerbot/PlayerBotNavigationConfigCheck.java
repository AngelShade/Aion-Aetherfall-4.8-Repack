package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.aionemu.commons.configuration.ConfigurableProcessor;
import com.aionemu.commons.configuration.Property;
import com.aionemu.gameserver.configs.Config;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;

/** Actual native named-file loader and property parser, with private inert files. */
public final class PlayerBotNavigationConfigCheck {
 private static int checks;
 private static final String PREFIX="gameserver.playerbots.navigation.";
 private static final String[] KEYS={"budget_ms_per_second","slice_ms","max_workers","segment_metres"};
 private static final String[] FIELDS={"TERRAIN_BUDGET_MS","TERRAIN_SLICE_MS","TERRAIN_WORKERS","TERRAIN_SEGMENT_METRES"};
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 private static Properties load(Path root)throws Exception{var m=Config.class.getDeclaredMethod("loadProperties",Path.class);m.setAccessible(true);return (Properties)m.invoke(null,root);}
 private static void values(int budget,int slice,int workers,float segment,String why){
  check(PlayerBotConfig.TERRAIN_BUDGET_MS==budget,why+" budget");check(PlayerBotConfig.TERRAIN_SLICE_MS==slice,why+" slice");
  check(PlayerBotConfig.TERRAIN_WORKERS==workers,why+" workers");check(PlayerBotConfig.TERRAIN_SEGMENT_METRES==segment,why+" segment");
 }
 public static void main(String[] args)throws Exception{
  var before=new LinkedHashMap<Field,Object>();for(Field f:PlayerBotConfig.class.getFields())if(Modifier.isStatic(f.getModifiers())&&!Modifier.isFinal(f.getModifiers()))before.put(f,f.get(null));
  Path root=Files.createTempDirectory(Path.of(".").toAbsolutePath().normalize(),"navigation-config-check-");
  try{
   for(String dir:List.of("administration","main","network","playerbots"))Files.createDirectories(root.resolve("config/"+dir));
   Files.writeString(root.resolve("config/mygs.properties"),"");
   Files.writeString(root.resolve("config/playerbots/playerbots.properties"),"");
   check(ConfigurableProcessor.process(load(root),PlayerBotConfig.class).isEmpty(),"optional navigation file may be absent without unknown properties");
   values(500,3,2,24,"compiled native defaults");
   Files.writeString(root.resolve("config/playerbots/not-server.properties"),PREFIX+"budget_ms_per_second=999\n");
   Files.writeString(root.resolve("config/playerbots/world-population-events.json"),"{\"budget_ms_per_second\":999}\n");
   check(ConfigurableProcessor.process(load(root),PlayerBotConfig.class).isEmpty(),"private metadata and other files are not server configuration");values(500,3,2,24,"named-file-only loading");
   Files.writeString(root.resolve("config/playerbots/playerbots.properties"),PREFIX+"budget_ms_per_second=777\n");
   Files.writeString(root.resolve("config/playerbots/features.properties"),"gameserver.playerbots.quests.custom=false\n");
   Files.writeString(root.resolve("config/playerbots/navigation.properties"),PREFIX+"budget_ms_per_second=350\n"+PREFIX+"slice_ms=2\n"+PREFIX+"max_workers=3\n"+PREFIX+"segment_metres=32\n");
   check(ConfigurableProcessor.process(load(root),PlayerBotConfig.class).isEmpty(),"four navigation keys are registered in the actual native parser");values(350,2,3,32,"named navigation settings override main");
   check(!PlayerBotConfig.QUEST_CUSTOM,"existing feature-file owner choice remains effective");
   Files.writeString(root.resolve("config/mygs.properties"),PREFIX+"budget_ms_per_second=450\n"+PREFIX+"segment_metres=48\n");
   check(ConfigurableProcessor.process(load(root),PlayerBotConfig.class).isEmpty(),"final mygs override remains registered");values(450,2,3,48,"mygs final native override");
   check(!PlayerBotConfig.QUEST_CUSTOM,"navigation and mygs loading preserve existing independent quest switches");
   for(int i=0;i<KEYS.length;i++){
    var annotation=PlayerBotConfig.class.getField(FIELDS[i]).getAnnotation(Property.class);
    check(annotation!=null&&annotation.key().equals(PREFIX+KEYS[i]),"owner key maps to actual compiled field"+FIELDS[i]);
   }
   var helper=new PlayerBotNavigationBudget(PlayerBotConfig.TERRAIN_BUDGET_MS,PlayerBotConfig.TERRAIN_SLICE_MS,PlayerBotConfig.TERRAIN_WORKERS);
   Object token=new Object();helper.request(token,PlayerBotNavigationBudget.Priority.CONTROLLED);
   check(helper.nominated(token),"explicit native values reach the real terrain budget constructor");try(var lease=helper.begin(token,PlayerBotNavigationBudget.Priority.CONTROLLED)){check(lease!=null,"owner-configured helper admits bounded work");}
  }finally{
   for(var entry:before.entrySet())entry.getKey().set(null,entry.getValue());
   try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList()){if(!p.toAbsolutePath().normalize().startsWith(root))throw new AssertionError("Fixture cleanup escaped its root");Files.delete(p);}}
  }
  System.out.println("OK: "+checks+" native optional navigation configuration, defaults, precedence and owner preservation checks");
 }
}
