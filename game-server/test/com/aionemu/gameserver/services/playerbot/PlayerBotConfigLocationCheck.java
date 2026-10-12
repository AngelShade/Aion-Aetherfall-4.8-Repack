package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.aionemu.commons.configuration.ConfigurableProcessor;
import com.aionemu.gameserver.configs.Config;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;

/** Actual relocated loader and preserved native parsing; private files only. */
public final class PlayerBotConfigLocationCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static Properties load(Path root)throws Exception{var method=Config.class.getDeclaredMethod("loadProperties",Path.class);method.setAccessible(true);return (Properties)method.invoke(null,root);}
 public static void main(String[] args)throws Exception{
  var saved=new HashMap<Field,Object>();for(var f:PlayerBotConfig.class.getFields())if(Modifier.isStatic(f.getModifiers())&&!Modifier.isFinal(f.getModifiers()))saved.put(f,f.get(null));
  var root=Files.createTempDirectory(Path.of("."),"playerbot-config-location-").toAbsolutePath().normalize();
  try{
   for(String dir:List.of("main","administration","network","playerbots"))Files.createDirectories(root.resolve("config/"+dir));
   Files.writeString(root.resolve("config/mygs.properties"),"");
   var owner=root.resolve("config/playerbots/playerbots.properties");
   Files.writeString(owner,"gameserver.playerbots.population.max=1500\ngameserver.playerbots.population.min=1200\ngameserver.playerbots.max_per_owner=23\ngameserver.playerbots.quests.enable=false\ngameserver.playerbots.navigation.slice_ms=5\n");
   check(ConfigurableProcessor.process(load(root),PlayerBotConfig.class).isEmpty(),"Relocated owner settings reach the native parser");
   check(PlayerBotConfig.POPULATION_MAX==1500 && PlayerBotConfig.POPULATION_MIN==1200,"Owner population range remains intact");
   check(PlayerBotConfig.MAX_PER_OWNER==23,"Owner companion quota is loaded from new home");
   check(!PlayerBotConfig.QUESTS_ENABLED && PlayerBotConfig.TERRAIN_SLICE_MS==5,"Relocated file applies registered feature and navigation values");
   Files.writeString(root.resolve("config/playerbots/features.properties"),"gameserver.playerbots.quests.enable=true\n");
   Files.writeString(root.resolve("config/playerbots/navigation.properties"),"gameserver.playerbots.navigation.slice_ms=3\n");
   check(ConfigurableProcessor.process(load(root),PlayerBotConfig.class).isEmpty(),"Existing named files keep their normal loading order");
   check(PlayerBotConfig.QUESTS_ENABLED && PlayerBotConfig.TERRAIN_SLICE_MS==3,"Features/navigation still override their own named values");
   Files.writeString(root.resolve("config/mygs.properties"),"gameserver.playerbots.population.max=1400\n");
   check(ConfigurableProcessor.process(load(root),PlayerBotConfig.class).isEmpty() && PlayerBotConfig.POPULATION_MAX==1400,"Normal final server override is unchanged");
   Files.writeString(root.resolve("config/playerbots/other.properties"),"gameserver.playerbots.population.min=1\n");
   Files.writeString(root.resolve("config/playerbots/world-population-events.json"),"{\"min\":1}");
   check(load(root).getProperty("gameserver.playerbots.population.min").equals("1200"),"Other files and character metadata in the folder are not loaded");
   check(!Files.exists(root.resolve("config/main/playerbots.properties")),"Checks exercise a moved configuration, not a duplicate or fallback");
   var source=Path.of(args[0]);
   check(Files.isRegularFile(source.resolve("config/playerbots/playerbots.properties")) && !Files.exists(source.resolve("config/main/playerbots.properties")),"Maintained normal-builder template has only its new location");
  }finally{
   for(var e:saved.entrySet())e.getKey().set(null,e.getValue());
   try(var files=Files.walk(root)){for(var p:files.sorted(Comparator.reverseOrder()).toList()){if(!p.toAbsolutePath().normalize().startsWith(root))throw new AssertionError("Cleanup escaped fixture");Files.delete(p);}}
  }
  System.out.println("OK: "+checks+" relocated owner configuration/native loader/precedence checks; no World/DB/startup");
 }
}
