package com.aionemu.gameserver.services.playerbot;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Male and female companions of a class show different portraits, from two atlases cut the same way. */
public final class PlayerBotPortraitCheck {
 static int checks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception {
  for(String atlas:new String[]{"class-portraits.jpg","class-portraits-counterpart.jpg"}) {
   check(PlayerBotMedia.cardArt(atlas),atlas+" is served");
   var image=ImageIO.read(new ByteArrayInputStream(PlayerBotMedia.readCardArt(atlas)));
   check(image!=null && image.getWidth()==1254 && image.getHeight()==1254,atlas+" keeps the size the card offsets depend on");
  }
  var root=Path.of(args[0]);
  var script=Files.readString(root.resolve("config/playerbots/media/bots.js"));
  check(script.contains("var cardPortraitMale = [true,true,false,false,true,false,false,true,true,false,false];"),"the window knows who each atlas cell shows");
  check(!script.matches("(?s).*cardPortrait\\([^,()]+,\\s*'[a-z-]+'\\).*"),"every portrait in the window passes a gender");
  var src=root.resolve("src/com/aionemu/gameserver/services");
  check(Files.readString(src.resolve("playerbot/PlayerBotRecruitment.java")).contains("last.put(\"gender\",r.getString(3))"),"the recruit result names its gender");
  check(Files.readString(src.resolve("playerbot/PlayerBotSession.java")).contains("state.put(\"gender\", bot.getGender().name())"),"a live bot names its gender");
  var roster=Files.readString(src.resolve("playerbot/PlayerBotRoster.java"));
  check(roster.contains("row.put(\"gender\",r.getString(7))") && roster.contains("row.put(\"gender\",r.getString(5))"),"saved roster rows name their gender");
  System.out.println("OK: "+checks+" companion portrait gender checks");
 }
 private PlayerBotPortraitCheck(){}
}
