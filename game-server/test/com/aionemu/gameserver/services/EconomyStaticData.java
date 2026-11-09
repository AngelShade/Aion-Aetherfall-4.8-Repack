package com.aionemu.gameserver.services;

import java.nio.file.*;
import java.util.*;
import com.aionemu.commons.configuration.ConfigurableProcessor;
import com.aionemu.gameserver.configs.main.*;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.utils.xml.JAXBUtil;

/** Only the real static tables used by appraisal; no server, SQL, world or runtime installation. */
public final class EconomyStaticData {
 public static void load()throws Exception {
  ConfigurableProcessor.process(new Properties(),PricesConfig.class,RatesConfig.class,GSConfig.class,PlayerBotEconomyConfig.class,CentralMarketSimulationConfig.class);
  Path data=Path.of("data/static_data");
  DataManager.ITEM_DATA=JAXBUtil.deserialize(data.resolve("items/item_templates.xml").toFile(),ItemData.class);
  DataManager.PET_DATA=JAXBUtil.deserialize(data.resolve("pets/pets.xml").toFile(),PetData.class);
  DataManager.TRADE_LIST_DATA=JAXBUtil.deserialize(data.resolve("npc_trade_list.xml").toFile(),TradeListData.class);
  DataManager.GOODSLIST_DATA=JAXBUtil.deserialize(data.resolve("goodslists/goodslists.xml").toFile(),GoodsListData.class);
  DataManager.TEMPERING_DATA=JAXBUtil.deserialize(data.resolve("enchants/tempering_templates.xml").toFile(),TemperingData.class);
  DataManager.RECIPE_DATA=JAXBUtil.deserialize(data.resolve("recipe/recipe_templates.xml").toFile(),RecipeData.class);
  // The server reads spawned NPCs from its spawn data; a plain ID scan gives the audit the same shop set.
  var spawned=new HashSet<Integer>();var npc=java.util.regex.Pattern.compile("npc_id=\"(\\d+)\"");
  for(var folder:List.of(data.resolve("spawns"),data.resolve("town_spawns"),data.resolve("events")))if(Files.isDirectory(folder))try(var files=Files.walk(folder)) {
   for(var file:files.filter(f->f.toString().endsWith(".xml")).toList()){var m=npc.matcher(new String(Files.readAllBytes(file),java.nio.charset.StandardCharsets.ISO_8859_1));while(m.find())spawned.add(Integer.parseInt(m.group(1)));}
  }
  com.aionemu.gameserver.services.trade.EconomyPrices.liveTraders(spawned);
  try(var files=Files.list(data.resolve("decomposable_items"))) {
   DataManager.DECOMPOSABLE_ITEMS_DATA=DecomposableItemsData.load(files.filter(p->p.toString().endsWith(".xml")).sorted().map(Path::toFile).toList(),data.resolve("decomposable_items/decomposable_items.xsd").toString());
  }
  var offers=MarketplaceService.class.getDeclaredMethod("loadOffers");offers.setAccessible(true);offers.invoke(null);
  CentralMarketHttpService.loadIcons();
 }
 private EconomyStaticData() {}
}
