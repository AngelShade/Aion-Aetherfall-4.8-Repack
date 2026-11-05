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
  var hud=Files.readString(mods.resolve("AetherfallHUD.lua"));
  for(var rarity:PlayerBotGrowth.GearRarity.values()) {
   String name=rarity.name().charAt(0)+rarity.name().substring(1).toLowerCase(java.util.Locale.ROOT);
   check(hud.contains(name+" = '"),"the HUD has a colour for "+name);
  }
  check(hud.contains("' gear | '") && hud.contains("' rank'"),"the label always names both values");
  check(hud.contains("pcall(AFHUD_Identity)"),"labels refresh with the HUD, not per frame, and cannot stop the rest of it");
  var http=Files.readString(root.resolve("src/com/aionemu/gameserver/services/PlayerBotHttpService.java"));
  int identity=http.indexOf("/market/companions/identity");
  check(identity>0 && http.indexOf("viewer.isPlayerBot()",identity)>identity,"only a logged-in human session can ask");
  var core=Files.readString(mods.resolve("companion_identity.h"));
  check(core.contains("std::thread(work,id,asked).detach()") && core.contains("WinHttpSetTimeouts"),"the UI thread never waits on the server");
  check(core.contains("MAX_ASKED=24") && core.contains("RETRY_MS=3000"),"requests stay small and paced");
  var prepare=Files.readString(mods.resolve("prepare.py"));
  check(prepare.contains("flag='transparent;not_visible;not_movable;not_active'") && prepare.contains("name='AFHIdentity'"),"labels take no mouse input");
  System.out.println("OK: "+checks+" bot rarity and rank identity checks");
 }
 private PlayerBotIdentityCheck(){}
}
