package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import java.sql.*;
import com.aionemu.gameserver.dao.PlayerBotRepositoryDAO;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Pure repository contracts and recording JDBC: no GameServer, database writes or actor/ID allocation. */
public final class PlayerBotRepositoryCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static void invalid(String text)throws Exception{try{PlayerBotRepository.decode(7,text);throw new AssertionError("Invalid document accepted");}catch(IOException expected){check(true,"Invalid document refused");}}
 public static void main(String[] args)throws Exception {
  var member=new PlayerBotPresets.Member(101,"Owned Alt",false,Role.SUPPORT,Order.PASSIVE);
  var temporary=new PlayerBotPresets.Member(102,"Temporary Bot",true,Role.TANK,Order.FOLLOW);
  var preset=new PlayerBotPresets.Preset("0123456789abcdef0123456789abcdef","Mixed party",List.of(member,temporary));
  var document=new PlayerBotPresets.Document(7,Set.of(102),List.of(preset));String text=PlayerBotRepository.encode(document);
  check(PlayerBotRepository.decode(7,text).equals(document),"Native document roundtrip preserves mixed party, role/order/IDs");
  check(PlayerBotRepository.decode(7,PlayerBotRepository.encode(new PlayerBotPresets.Document(7,Set.of(),List.of()))).saved().isEmpty(),"Empty bookmarks supported");
  invalid(text.replace("\"account\":7","\"account\":8"));invalid(text.replace("\"version\":1","\"version\":2"));
  invalid(text.replace("SUPPORT","INVALID"));invalid(text.replace("PASSIVE","INVALID"));invalid(text.replace("\"temporary\":false","\"temporary\":\"yes\""));
  invalid(text.replace("0123456789abcdef0123456789abcdef","invalid"));invalid(" ".repeat(524289));
  String marker="{\"version\":1,\"account\":7,\"id\":102,\"name\":\"Temporary Bot\",\"removedAt\":123}";
  PlayerBotRepository.validateMarker(7,102,marker);check(true,"Valid archive marker retained");
  for(String bad:List.of(marker.replace("\"account\":7","\"account\":8"),marker.replace("\"id\":102","\"id\":103"),marker.replace("\"version\":1","\"version\":2"),marker.replace("\"removedAt\":123","\"removedAt\":0"))){
   try{PlayerBotRepository.validateMarker(7,102,bad);throw new AssertionError("Invalid marker accepted");}catch(IOException expected){check(true,"Archive owner/version/timestamp guard");}
  }
  var db=new PlayerBotMetadataCheck.Jdbc();db.account=7;db.stored=Map.of("fixture","identity row");PlayerBotRepositoryDAO.account(db.connection(),7);PlayerBotRepositoryDAO.roster(db.connection(),7,102);check(true,"Native account and dedicated roster identity queries");
  db.account=8;try{PlayerBotRepositoryDAO.roster(db.connection(),7,102);throw new AssertionError("Foreign roster allowed");}catch(SQLException expected){check(true,"Foreign Temporary Bot cannot be archived");}db.account=7;
  for(boolean removed:List.of(false,true)){
   db.autoCommit=true;try{if(removed)PlayerBotRepositoryDAO.removed(db.connection(),7,102,marker);else PlayerBotRepositoryDAO.parties(db.connection(),7,text);throw new AssertionError("Autocommit permitted");}catch(SQLException expected){check(true,"Repository requires explicit native commit");}
  }
  db.autoCommit=false;int writes=db.updates;PlayerBotRepositoryDAO.parties(db.connection(),7,text);PlayerBotRepositoryDAO.removed(db.connection(),7,102,marker);check(db.updates==writes+2,"Prepared private repository writes only");
  check(db.sql.stream().noneMatch(s->s.contains("DELETE")||s.contains("inventory")||s.contains("UPDATE players")),"No character/inventory deletion or replacement");
  for(String file:List.of("bots.html","bots.css","bots.js"))check(PlayerBotMedia.read(file).equals(Files.readString(Path.of(args[0],"config/playerbots/media",file))),"Packaged native UI bytes preserved: "+file);
  try{PlayerBotMedia.read("../database.properties");throw new AssertionError("Unexpected resource allowed");}catch(IOException expected){check(true,"Interface whitelist refuses traversal");}
  System.out.println("OK: "+checks+" native repository/document/ownership/transaction/media checks; no world or live DB");
 }
}
