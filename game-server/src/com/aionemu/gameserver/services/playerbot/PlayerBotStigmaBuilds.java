package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;

/** Selectable Temporary Bot Stigma builds from config/playerbots/stigma-builds.properties (static configuration). */
public final class PlayerBotStigmaBuilds {
 public static final String AUTO="AUTO";
 public record Build(String key,String name,String text,List<String> groups) {}
 private static final Path FILE=Path.of("config","playerbots","stigma-builds.properties");
 private static volatile Map<PlayerClass,List<Build>> builds;
 private static volatile Map<String,String> names;

 private static Map<PlayerClass,List<Build>> builds() {
  var loaded=builds;if(loaded!=null)return loaded;
  synchronized(PlayerBotStigmaBuilds.class) {
   if(builds!=null)return builds;
   var p=new Properties();var result=new EnumMap<PlayerClass,List<Build>>(PlayerClass.class);
   if(Files.isRegularFile(FILE))try(Reader in=Files.newBufferedReader(FILE,StandardCharsets.UTF_8)){p.load(in);}
   catch(IOException e){throw new IllegalStateException("Cannot read "+FILE,e);}
   if(Boolean.parseBoolean(p.getProperty("enabled","false"))) {
    var keys=new TreeSet<String>();for(String key:p.stringPropertyNames())if(key.endsWith(".stigmas"))keys.add(key.substring(0,key.length()-8));
    for(String id:keys) {
     int dot=id.indexOf('.');PlayerClass pc=PlayerClass.valueOf(id.substring(0,dot));String key=id.substring(dot+1);
     if(!key.matches("[a-z0-9_]{1,24}"))throw new IllegalStateException("Invalid Stigma build key "+id);
     var groups=Arrays.stream(p.getProperty(id+".stigmas").split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();
     result.computeIfAbsent(pc,c->new ArrayList<>()).add(new Build(key,p.getProperty(id+".name",key),p.getProperty(id+".text",""),groups));
    }
   }
   result.replaceAll((pc,list)->List.copyOf(list));
   return builds=Collections.unmodifiableMap(result);
  }
 }
 public static List<Build> forClass(PlayerClass pc){return builds().getOrDefault(pc,List.of());}
 public static Build find(PlayerClass pc,String key){for(var b:forClass(pc))if(b.key().equals(key))return b;return null;}
 /** Display name of a Stigma skill group: the name of its first normal (non-test) chargeable Stigma stone. */
 public static String groupName(String group) {
  var map=names;
  if(map==null){
   var built=new HashMap<String,String>();
   for(ItemTemplate t:DataManager.ITEM_DATA.getItemTemplates())if(t.isStigma() && !t.isTestItem() && t.getStigma().isChargeable())
    for(String g:t.getStigma().getGainSkillGroups())built.putIfAbsent(g,t.getName());
   names=map=Map.copyOf(built);
  }
  return map.getOrDefault(group,group);
 }
 /** Priority of a Stigma stone in the build (lower first), or MAX_VALUE when the build does not list it. */
 static int priority(Build build,ItemTemplate stone) {
  int best=Integer.MAX_VALUE;if(build==null)return best;
  for(String g:stone.getStigma().getGainSkillGroups()){int i=build.groups().indexOf(g);if(i>=0)best=Math.min(best,i);}
  return best;
 }
 private PlayerBotStigmaBuilds(){}
}
