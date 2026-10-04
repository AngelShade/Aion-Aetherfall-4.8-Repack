package com.aionemu.gameserver.ai;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.aionemu.commons.configs.CommonsConfig;
import com.aionemu.commons.scripting.ScriptManager;
import com.aionemu.gameserver.configs.main.AIConfig;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.gameobjects.*;

public class AIRegistryCheck {
 public static final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
 @AIName("portal") public static class Portal extends AITemplate<Npc>{public Portal(Npc owner){super(owner);}}
 public static void pause()throws Exception{entered.countDown();if(!release.await(20,TimeUnit.SECONDS))throw new AssertionError("Reader timed out");}
 @SuppressWarnings({"unchecked","rawtypes"})
 public static void main(String[] args)throws Exception {
  CommonsConfig.SCRIPT_COMPILER_CACHING=false;DataManager.NPC_DATA=new NpcData();
  Path dir=Path.of(args[0]);Files.createDirectories(dir);AIConfig.HANDLER_DIRECTORY=dir.toFile();
  var engine=AIEngine.getInstance();engine.registerAI((Class)Portal.class);
  var baseline=new HashMap<String,Class<? extends AbstractAI<? extends Creature>>>();baseline.put("portal",Portal.class);
  Files.writeString(dir.resolve("Next.java"),"import com.aionemu.gameserver.ai.*;import com.aionemu.gameserver.model.gameobjects.*;import com.aionemu.commons.scripting.metadata.OnClassLoad; @AIName(\"portal\") public class Next extends AITemplate<Npc>{public Next(Npc owner){super(owner);}@OnClassLoad public static void waitForReader()throws Exception{AIRegistryCheck.pause();}}");
  var executor=Executors.newSingleThreadExecutor();var future=executor.submit(engine::reload);
  if(!entered.await(20,TimeUnit.SECONDS))throw new AssertionError("Loader did not reach staging");
  int reads=0;for(int i=0;i<100000;i++){if(AIRegistryReload.handlers(baseline).get("portal")!=Portal.class)throw new AssertionError("Published incomplete registry");reads++;}
  release.countDown();future.get(20,TimeUnit.SECONDS);executor.shutdown();
  var active=AIRegistryReload.handlers(baseline);if(active.get("portal")==Portal.class)throw new AssertionError("Registry did not publish");
  Files.writeString(dir.resolve("Broken.java"),"this is not Java");
  try{engine.reload();throw new AssertionError("Invalid compilation accepted");}catch(RuntimeException expected){}
  if(AIRegistryReload.handlers(baseline)!=active)throw new AssertionError("Compilation failure lost active registry");
  Files.delete(dir.resolve("Broken.java"));
  Files.writeString(dir.resolve("Duplicate.java"),"import com.aionemu.gameserver.ai.*;import com.aionemu.gameserver.model.gameobjects.*;@AIName(\"portal\") public class Duplicate extends AITemplate<Npc>{public Duplicate(Npc owner){super(owner);}}");
  try{engine.reload();throw new AssertionError("Duplicate handler accepted");}catch(RuntimeException expected){}
  if(AIRegistryReload.handlers(baseline)!=active)throw new AssertionError("Duplicate failure lost registry");
  Files.delete(dir.resolve("Duplicate.java"));
  try{AIRegistryReload.load(new ScriptManager(),baseline,()->{throw new IllegalStateException("missing required AI fixture");});throw new AssertionError("Invalid validation accepted");}catch(IllegalStateException expected){}
  if(AIRegistryReload.handlers(baseline)!=active)throw new AssertionError("Validation failure lost registry");
  System.out.println("OK: "+reads+" concurrent registry reads; atomic successful reload; compile, duplicate and validation failure retain active handlers.");
 }
}
