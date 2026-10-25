/* Native PlayerbotFactory spell/recipe reset checkpoint adapter.
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c,
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.Map;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.RecipeList;
import com.aionemu.gameserver.world.World;

/** Native skill callbacks must not auto-commit recipes before the factory's character checkpoint. */
final class PlayerBotFactoryRecipes extends RecipeList {
 private volatile boolean committed;
 PlayerBotFactoryRecipes(Map<Integer,Integer> recipes) {
  for(var entry:recipes.entrySet()) {
   if(entry.getKey()<=0 || entry.getValue()<0)throw new IllegalArgumentException("Invalid native factory recipe");
   super.addRecipe(null,entry.getKey(),entry.getValue());
  }
 }
 static void prepare(Player bot,boolean full) {
  if(!PlayerBotPopulationManager.global(bot) || bot.isSpawned() || World.getInstance().isInWorld(bot.getObjectId()) || !PlayerBotLease.isReserved(bot.getObjectId()))
   throw new IllegalStateException("Recipe factory requires exclusive unpublished generated custody");
  if(bot.getRecipeList()==null)throw new IllegalStateException("Native factory recipe load incomplete");
  bot.setRecipeList(new PlayerBotFactoryRecipes(full?Map.of():bot.getRecipeList().getRecipes()));
 }
 @Override public synchronized boolean addRecipe(Player player,int recipe,int limit) {
  if(recipe<=0 || limit<0)throw new IllegalArgumentException("Invalid native factory recipe");
  return super.addRecipe(committed?player:null,recipe,limit);
 }
 @Override public synchronized boolean deleteRecipe(Player player,int recipe) {
  if(!committed)throw new IllegalStateException("Unpublished factory cannot consume or delete a recipe through live SQL");
  return super.deleteRecipe(player,recipe);
 }
 @Override public synchronized void decreaseProductionLimit(Player player,int recipe) {
  if(!committed)throw new IllegalStateException("Unpublished factory cannot craft before its checkpoint");
  super.decreaseProductionLimit(player,recipe);
 }
 synchronized Map<Integer,Integer> pending(){return committed?null:getRecipes();}
 static void store(Connection c,int character,Map<Integer,Integer> recipes)throws SQLException {
  if(c.getAutoCommit() || character<=0 || recipes.entrySet().stream().anyMatch(e->e.getKey()<=0 || e.getValue()<0))
   throw new SQLException("Factory recipes require a valid native checkpoint transaction");
  try(var s=c.prepareStatement("DELETE FROM player_recipes WHERE player_id=?")){s.setInt(1,character);s.executeUpdate();}
  try(var s=c.prepareStatement("INSERT INTO player_recipes (player_id,recipe_id,production_limit) VALUES (?,?,?)")) {
   for(var entry:recipes.entrySet()){s.setInt(1,character);s.setInt(2,entry.getKey());s.setInt(3,entry.getValue());s.addBatch();}s.executeBatch();
  }
 }
 synchronized void committed(Map<Integer,Integer> saved) {
  if(!getRecipes().equals(saved))throw new IllegalStateException("Unpublished factory recipes changed during checkpoint");
  committed=true;
 }
}
