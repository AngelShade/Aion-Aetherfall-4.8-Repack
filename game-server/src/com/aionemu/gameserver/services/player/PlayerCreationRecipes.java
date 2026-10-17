package com.aionemu.gameserver.services.player;

import java.sql.*;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.RecipeList;

/** Starting skill callbacks precede the player INSERT. Defer their recipes until that row exists. */
public final class PlayerCreationRecipes extends RecipeList {
 private boolean stored;

 @Override public synchronized boolean addRecipe(Player player, int recipeId, int productionLimit) {
  return super.addRecipe(stored ? player : null, recipeId, productionLimit);
 }

 public static boolean persist(Player player) {
  if (!(player.getRecipeList() instanceof PlayerCreationRecipes recipes) || recipes.stored) return true;
  try (Connection connection = DatabaseFactory.getConnection()) {
   connection.setAutoCommit(false);
   try (PreparedStatement statement = connection.prepareStatement("INSERT INTO player_recipes (player_id,recipe_id,production_limit) VALUES (?,?,?)")) {
    for (var recipe : recipes.getRecipes().entrySet()) { statement.setInt(1, player.getObjectId()); statement.setInt(2, recipe.getKey()); statement.setInt(3, recipe.getValue()); statement.addBatch(); }
    statement.executeBatch(); connection.commit(); recipes.stored = true; return true;
   } catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
  } catch (SQLException error) {
   org.slf4j.LoggerFactory.getLogger(PlayerCreationRecipes.class).error("Cannot persist starting recipes for character {}", player.getObjectId(), error);
   return false;
  }
 }
}
