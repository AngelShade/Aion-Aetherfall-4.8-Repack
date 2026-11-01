package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Packaged native companion interface; no mutable settings directory required. */
public final class PlayerBotMedia {
 private static final ConcurrentHashMap<String,byte[]> ART_CACHE=new ConcurrentHashMap<>();
 private static final ConcurrentHashMap<String,String> TEXT_CACHE=new ConcurrentHashMap<>();
 private static final Set<String> CARD_ART=Set.of("summon-stage.jpg","castle-background.png","ornate-frame.png","class-portraits.jpg","window-frame.png",
  "skin-button-navy.png","skin-button-gold.png","skin-button-selected.png","skin-field.png","skin-panel.png","skin-icons.png","skin-chevron.png");
 public static boolean cardArt(String name){return CARD_ART.contains(name);}
 public static byte[] readCardArt(String name)throws IOException {
  if(!cardArt(name))throw new IOException("Invalid companion card art");
  var cached=ART_CACHE.get(name);if(cached!=null)return cached;
  try(var in=PlayerBotMedia.class.getResourceAsStream("/playerbots/media/cards/"+name)){
   if(in==null)throw new IOException("Companion card art missing");
   var bytes=in.readAllBytes();var prior=ART_CACHE.putIfAbsent(name,bytes);return prior==null?bytes:prior;
  }
 }
 public static String read(String name)throws IOException {
  if(!Set.of("bots.html","bots.css","bots.js").contains(name))throw new IOException("Invalid companion media");
  var cached=TEXT_CACHE.get(name);if(cached!=null)return cached;
  try(var in=PlayerBotMedia.class.getResourceAsStream("/playerbots/media/"+name)){
   if(in==null)throw new IOException("Companion interface resource missing");
   var text=new String(in.readAllBytes(),StandardCharsets.UTF_8);var prior=TEXT_CACHE.putIfAbsent(name,text);return prior==null?text:prior;
  }
 }
 private PlayerBotMedia(){}
}
