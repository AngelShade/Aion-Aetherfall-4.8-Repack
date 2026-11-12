package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Bot rarity and rank on native windows: the wire format, both values always named, and the owners that carry them. */
public final class PlayerBotIdentityCheck {
 static int checks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception {
  check(PlayerBotGrowth.identityLine("Renata",PlayerBotGrowth.GearRarity.EXCELLENT,PlayerBotGrowth.CombatRank.GOOD).equals("Renata|Excellent|Good"),"gear rarity and combat rank stay separate named values");
  check(PlayerBotGrowth.identityLine("Bo",PlayerBotGrowth.GearRarity.POOR,PlayerBotGrowth.CombatRank.EXCELLENT).equals("Bo|Poor|Excellent"),"great rank does not recolour poor gear");
  check(PlayerBotGrowth.identities("").isEmpty(),"no names, no answer");
  check(PlayerBotGrowth.identities(",, ,").isEmpty(),"blank names are ignored");
  check(PlayerBotHiring.prompt("Renata","Great gear, Good rank",2250,60).startsWith("Hire Renata (Great gear, Good rank)? "),"the hire question names both values");
  check(PlayerBotHiring.prompt("Renata","",2250,60).startsWith("Hire Renata? "),"a bot with no record reads as before");
  check(PlayerBotHiring.prompt("Renata","Great gear, Good rank",2250,60).endsWith("Kinah / 60"),"the question still ends before the native minutes");
  var root=Path.of(args[0]);var mods=root.resolveSibling("client-mods").resolve("aetherfall-core");
  // The interface is split into modules: the labels live in Identity, the HUD module drives its refresh.
  var labels=Files.readString(mods.resolve("modules/Identity.lua"));var hud=Files.readString(mods.resolve("modules/HUD.lua"));var engine=Files.readString(mods.resolve("core/Engine.lua"));
  var frames=Files.readString(mods.resolve("identity_frames.py"));
  for(var rarity:PlayerBotGrowth.GearRarity.values()) {
   String name=rarity.name().charAt(0)+rarity.name().substring(1).toLowerCase(java.util.Locale.ROOT);
   check(frames.contains("'"+name.toLowerCase(java.util.Locale.ROOT)+"': '") && labels.contains(name+" = "),"the native frames have a coloured label and the Identity module a tier for "+name);
  }
  check(labels.contains("('Equip: ' .. rarity)") && labels.contains("('Rank ' .. rankText(rank))"),"the target frame names both values");
  check(hud.contains("AF:Call('Identity', 'Update')") && engine.contains("local result=pack(pcall(f,m,...))"),"labels refresh with the HUD, not per frame, and cannot stop the rest of it");
  var http=Files.readString(root.resolve("src/com/aionemu/gameserver/services/PlayerBotHttpService.java"));
  int identity=http.indexOf("/market/companions/identity");
  check(identity>0 && http.indexOf("viewer.isPlayerBot()",identity)>identity,"only a logged-in human session can ask");
  var core=Files.readString(mods.resolve("companion_identity.h"));
  check(core.contains("std::thread(work,id,asked).detach()") && core.contains("WinHttpSetTimeouts"),"the UI thread never waits on the server");
  check(core.contains("MAX_ASKED=24") && core.contains("RETRY_MS=3000"),"requests stay small and paced");
  check(frames.contains("flag='transparent;not_focusable;") && frames.contains("def remove_overlay_xml"),"labels live inside the native frames, take no input, and the loose overlay window is removed");
  System.out.println("OK: "+checks+" bot rarity and rank identity checks");
 }
 private PlayerBotIdentityCheck(){}
}
