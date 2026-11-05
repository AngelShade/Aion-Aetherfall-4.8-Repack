package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import com.aionemu.gameserver.model.craft.Profession;

/** World trade rules: spread, styles, supply that ignores demand, market first, real rates. No World, actors or SQL. */
public final class PlayerBotProfessionCheck {
 static int checks;
 static void check(boolean result,String text){checks++;if(!result)throw new AssertionError(text);}
 static void refused(Runnable work,String text){try{work.run();}catch(RuntimeException expected){checks++;return;}throw new AssertionError(text);}
 static final String WEIGHTS="30002:20,30003:10,40001:15,40002:10,40003:10,40004:10,40007:15,40008:8,40010:2";
 static final String GATHERERS="small_seller:25:200:20:25,regular:40:500:75:50,stockpiler:25:1000:250:100,hoarder:10:2000:750:100";
 static final String CRAFTERS="own_gear:30,skill_grinder:25,market_crafter:30,bulk_intermediates:15";
 static Map<String,String> row(int primary,String style){var row=new HashMap<String,String>();row.put("version","1");row.put("account","1");row.put("character","5");row.put("primary",Integer.toString(primary));row.put("style",style);return row;}
 public static void main(String[] args)throws Exception {
  // Spread: every native trade gets its share, in any admission order.
  var weights=PlayerBotProfessionPolicy.weights(WEIGHTS);
  check(weights.size()==Profession.values().length,"every native trade has a weight");
  refused(()->PlayerBotProfessionPolicy.weights("30002:100"),"a missing trade is refused");
  refused(()->PlayerBotProfessionPolicy.weights(WEIGHTS.replace("30002:20","99999:20")),"an unknown trade is refused");
  var ids=new ArrayList<Integer>();for(int i=1;i<=5000;i++)ids.add(i*7);Collections.shuffle(ids,new Random(11));
  var counts=new HashMap<Integer,Integer>();for(int id:ids)counts.merge(PlayerBotProfessionPolicy.choose(weights,counts,id),1,Integer::sum);
  for(var e:weights.entrySet())check(Math.abs(counts.getOrDefault(e.getKey(),0)-5000*e.getValue()/100)<=1,"trade "+e.getKey()+" stays on its target share");
  // Styles: bots differ, and a saved style never moves.
  var styles=PlayerBotProfessionPolicy.styles(GATHERERS,true);
  check(styles.size()==4 && styles.stream().map(PlayerBotProfessionPolicy.Style::limit).distinct().count()==4,"gatherers hold different amounts");
  check(PlayerBotProfessionPolicy.styles(CRAFTERS,false).size()==4,"four crafter styles");
  refused(()->PlayerBotProfessionPolicy.styles("broken:10:100:200:50",true),"a reserve above the limit is refused");
  refused(()->PlayerBotProfessionPolicy.styles("broken:10:100:20:500",true),"a lot above the limit is refused");
  var picked=new HashMap<String,Integer>();
  for(long seed=1;seed<=20000;seed++){var style=PlayerBotProfessionPolicy.style(styles,seed);check(style==PlayerBotProfessionPolicy.style(styles,seed),"the same bot always gets the same style");picked.merge(style.name(),1,Integer::sum);}
  for(var style:styles)check(Math.abs(picked.get(style.name())-20000*style.weight()/100)<600,style.name()+" appears near its weight");
  // Supply: gathering depends only on the bot's own holding limit. There is no market input to this decision.
  for(var style:styles) {
   check(PlayerBotProfessionPolicy.harvest(0,style.limit(),1)==1,style.name()+" gathers from empty");
   check(PlayerBotProfessionPolicy.harvest(style.limit()-1,style.limit(),1)==1,style.name()+" gathers up to its limit");
   check(PlayerBotProfessionPolicy.harvest(style.limit(),style.limit(),1)==0,style.name()+" stops at its limit");
   check(PlayerBotProfessionPolicy.harvest(style.limit()-1,style.limit(),2)==0,style.name()+" never overfills");
   check(PlayerBotProfessionPolicy.sale(style.reserve(),style.reserve(),style.lot(),500,0)==0,style.name()+" keeps its working reserve");
   check(PlayerBotProfessionPolicy.sale(style.limit(),style.reserve(),style.lot(),500,0)==Math.min(style.lot(),style.limit()-style.reserve()),style.name()+" sells one lot at a time");
   check(PlayerBotProfessionPolicy.sale(style.limit(),style.reserve(),style.lot(),500,500)==0,style.name()+" stops at the daily offer limit");
  }
  check(styles.get(0).reserve()<200 && PlayerBotProfessionPolicy.sale(100,20,25,500,0)==25,"a small seller lists well below the old 200 material reserve");
  // Market first, whether or not an order could be posted.
  long now=10_000_000;
  check(!PlayerBotProfessionPolicy.fallback(0,now),"no wait started yet");
  check(!PlayerBotProfessionPolicy.fallback(now+1,now),"the market gets first chance");
  check(PlayerBotProfessionPolicy.fallback(now,now),"after the wait the bot fetches inputs itself");
  // Buy orders: the highest selectable price under the limit, never above it.
  var ladder=List.of(100L,110L,120L,130L);
  check(PlayerBotMarket.orderPrice(ladder,125)==120,"a buy order offers the best price within its limit");
  check(PlayerBotMarket.orderPrice(ladder,130)==130,"the limit itself is allowed");
  check(PlayerBotMarket.orderPrice(ladder,99)==0,"no order below the lowest selectable price");
  // Rate: real time, not ticks, and a real player's pace including travel.
  check(PlayerBotProfessionPolicy.rolls(59000,60000,10)==0,"under a minute earns nothing");
  check(PlayerBotProfessionPolicy.rolls(600000,60000,10)==10,"one turn catches up ten minutes");
  check(PlayerBotProfessionPolicy.rolls(Long.MAX_VALUE,60000,10)==10,"a long sleep cannot bank unlimited attempts");
  check(2000/(PlayerBotProfessionPolicy.rolls(600000,60000,10)*6)<=34,"a hoarder can reach 2000 inside two days of server time");
  // Practice only earns XP within budget; useful work never reaches this gate.
  check(PlayerBotProfessionPolicy.practice(100,90,0,12,500,1000),"XP practice within budget runs");
  check(!PlayerBotProfessionPolicy.practice(141,100,0,12,500,1000),"no XP above the 40 point window");
  check(!PlayerBotProfessionPolicy.practice(100,101,0,12,500,1000),"a recipe above the bot's skill is refused");
  check(!PlayerBotProfessionPolicy.practice(100,90,12,12,500,1000),"the hourly limit holds");
  check(!PlayerBotProfessionPolicy.practice(100,90,0,12,1001,1000),"the daily loss budget holds");
  check(!PlayerBotProfessionPolicy.practice(100,90,0,12,1,-5),"a spent budget stays spent");
  // Cost basis follows the stock it belongs to and survives until sold.
  check(PlayerBotProfessionPolicy.floor(0,10)==0,"gathered stock has no cost floor");
  check(PlayerBotProfessionPolicy.floor(650,10)==100,"a floor returns the unit cost after the 35 percent market share");
  check(PlayerBotProfessionPolicy.floor(650,0)==0,"no stock, no floor");
  check(PlayerBotProfessionPolicy.costAfterOffer(1000,10,4)==600,"offered units take their share of the cost");
  check(PlayerBotProfessionPolicy.costAfterOffer(1000,10,10)==0,"selling everything clears the cost");
  check(PlayerBotProfessionPolicy.costAfterOffer(5,10,4)>=0,"small costs never go negative");
  check(PlayerBotProfessionPolicy.profit(100,300,75,10),"a real margin counts as profit");
  check(!PlayerBotProfessionPolicy.profit(100,200,75,10),"the 35 percent share and failed attempts are counted");
  // Saved rows through the real metadata validator.
  PlayerBotMetadata.validate(1,5,"profession",row(30002,"hoarder"));checks++;
  PlayerBotMetadata.validate(1,5,"profession",row(40002,"a_style_later_renamed"));checks++;
  var ledger=row(40002,"own_gear");ledger.put("offered.152000201","25");ledger.put("cost.155000075","900");ledger.put("marketWait","5");PlayerBotMetadata.validate(1,5,"profession",ledger);checks++;
  for(var bad:List.of(row(99999,"hoarder"),row(30002,"BAD STYLE"))){try{PlayerBotMetadata.validate(1,5,"profession",bad);throw new AssertionError("bad row accepted");}catch(java.io.IOException expected){checks++;}}
  var unknown=row(30002,"hoarder");unknown.put("free_kinah","5");try{PlayerBotMetadata.validate(1,5,"profession",unknown);throw new AssertionError("unknown field accepted");}catch(java.io.IOException expected){checks++;}
  var negative=row(30002,"hoarder");negative.put("spent","-1");try{PlayerBotMetadata.validate(1,5,"profession",negative);throw new AssertionError("negative ledger accepted");}catch(java.io.IOException expected){checks++;}
  PlayerBotProfessions.validateUpdate(Map.of(),row(30002,"hoarder"));checks++;
  PlayerBotProfessions.validateUpdate(row(30002,"hoarder"),row(30002,"hoarder"));checks++;
  refused(()->PlayerBotProfessions.validateUpdate(row(30002,"hoarder"),row(30003,"hoarder")),"a saved trade cannot change");
  refused(()->PlayerBotProfessions.validateUpdate(row(30002,"hoarder"),row(30002,"regular")),"a saved style cannot change");
  // Source contract: sleeping bots never navigate, and the old starvation and tether gates stay fixed.
  var services=Path.of(args[0],"src/com/aionemu/gameserver/services/playerbot");
  for(String file:List.of("PlayerBotProfessions.java","PlayerBotProfessionPolicy.java","PlayerBotProfessionCatalog.java","PlayerBotProfessionMarket.java")) {
   var text=Files.readString(services.resolve(file));
   check(!text.contains("navigation") && !text.contains("PlayerBotNavigation") && !text.contains("PlayerBotWorldTravel.Decision"),file+" never moves a bot");
  }
  var session=Files.readString(services.resolve("PlayerBotSession.java"));var market=Files.readString(services.resolve("PlayerBotMarket.java"));
  check(session.contains("if(!worldActivity()){if(worldDormant)PlayerBotProfessions.dormantTurn(this,"),"dormant decisions reach the bounded worker");
  check(session.contains("PlayerBotProfessions.dormantDue(this,"),"sleeping bots get no decisions, so a due trade turn must ask for one");
  check(session.contains("(questJob==null || professionTurn)") && session.contains("!questJob.turnIn()"),"a new quest cannot starve a trade turn, and a turn-in still wins");
  check(market.contains("boolean post=resting<=0 && !need.equipment() && session.autonomous()"),"only a world crafter's real input need posts an order");
  check(market.contains("PlayerBotProfessions.inputs(bot)"),"world bot trade inputs reach market needs");
  // Independent review, 5 November: each of these was a real defect.
  check(PlayerBotProfessionPolicy.zeroXpStop(2,399,399,2),"practice that earned nothing twice stops at that skill");
  check(!PlayerBotProfessionPolicy.zeroXpStop(1,399,399,2),"one empty result is not enough");
  check(!PlayerBotProfessionPolicy.zeroXpStop(2,399,400,2),"a new skill level earns a new try");
  check(PlayerBotProfessionPolicy.FACTORY_ROOM>=10,"a reset always leaves room to re-equip");
  var professions=Files.readString(services.resolve("PlayerBotProfessions.java"));var catalog=Files.readString(services.resolve("PlayerBotProfessionCatalog.java"));var temporary=Files.readString(services.resolve("PlayerBotTemporary.java"));
  check(!professions.contains("static Plan craftingPlan"),"awake crafting has no second path around the ledgers");
  check(!Files.readString(services.resolve("PlayerBotFocus.java")).contains("PlayerBotProfessions.craftingPlan"),"world bots do not reach the quest driver through Focus");
  check(Files.readString(services.resolve("PlayerBotWarehouseStore.java")).contains("move.bot().isWorldBot() && !PlayerBotProfessions.backgroundAllowed(move.bot())"),"a world bot's trade warehouse moves are admitted, and only those");
  check(catalog.contains("if(recipe.isMorph() || recipe.getMaxProductionCount()!=null || WORK_ORDERS.contains(recipe.getId()))continue;") && professions.contains("PlayerBotProfessionCatalog.supplied"),"quest-supplied stock does not survive a reset");
  check(temporary.contains("if(PlayerBotProfessions.keep(item,managed) && room>0)room--;else player.getInventory().delete(item);"),"worn earned goods survive a reset, and room stays");
  check(market.contains("PlayerBotProfessions.allowance(session,false)") && market.contains("PlayerBotProfessions.marketSpent(bot,cost)"),"trade inputs stay inside the trade's budget");
  check(market.contains("PlayerBotProfessions.sold(bot,") && !professions.contains("costAfterOffer(s.number(\"cost.\"+item),held(bot,item),quantity)"),"cost stays with goods through a listing");
  check(professions.contains("r.getSkillpoint()<=points"),"a gear goal above the bot's skill cannot hold its plan");
  check(professions.contains("RESTORED.add("),"a reload does not count offers twice");
  System.out.println("OK: "+checks+" world trade rule checks");
 }
 private PlayerBotProfessionCheck(){}
}
