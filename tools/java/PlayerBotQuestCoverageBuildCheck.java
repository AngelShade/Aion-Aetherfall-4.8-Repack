package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import com.aionemu.commons.utils.Json;
import com.aionemu.gameserver.dataholders.StaticData;

/** Exact production JAXB coverage inventory, not a gameplay or quest-completion assertion. */
public final class PlayerBotQuestCoverageBuildCheck {
 public static void check(StaticData data)throws Exception {
  var rows=new ArrayList<Map<String,Object>>();var counts=new TreeMap<String,Integer>();
  for(var quest:data.questData.getQuestTemplates().stream().sorted(Comparator.comparingInt(q->q.getId())).toList()) {
   var model=data.questsScriptData.getQuest(quest.getId());String coverage=PlayerBotQuestMetadata.coverage(model);
   counts.merge(coverage,1,Integer::sum);
   rows.add(Map.of("id",quest.getId(),"category",quest.getCategory(),"level",quest.getMinlevelPermitted(),"model",model==null?"custom-or-no-XML":model.getClass().getSimpleName(),"coverage",coverage,"acceptPages",PlayerBotQuestMetadata.startPages(model)));
  }
  if(rows.size()!=data.questData.size())throw new AssertionError("Incomplete quest coverage inventory");
  Files.writeString(Path.of("quest-coverage.json"),Json.write(Map.of("quests",rows,"families",counts,"boundary","Native Java handler availability and gameplay acceptance remain separate; no quest variables are changed")));
  System.out.println("OK: exact native quest catalog coverage: "+rows.size()+" quest templates; "+counts+"; no native quest completion claimed");
 }
}
