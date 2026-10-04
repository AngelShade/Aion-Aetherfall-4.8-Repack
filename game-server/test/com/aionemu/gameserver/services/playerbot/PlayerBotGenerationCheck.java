package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.services.player.PlayerCreationRecipes;

/** Regression for recipe callbacks before INSERT, plus trusted request-level selection. */
public final class PlayerBotGenerationCheck {
 public static void main(String[] args) {
  var recipes = new PlayerCreationRecipes();
  if (!recipes.addRecipe(null, 100001) || recipes.addRecipe(null, 100001) || !recipes.isRecipePresent(100001)) throw new AssertionError("Starting recipes must be buffered and deduplicated before a character exists");
  if (recipes.size() != 1) throw new AssertionError("Starting recipe lost");
  if (PlayerBotGenerationOptions.choose("matched",65)!=65 || PlayerBotGenerationOptions.choose("level1",65)!=1 || PlayerBotGenerationOptions.level(65)!=65) throw new AssertionError("Incorrect generated level policy/default");
  try { PlayerBotGenerationOptions.choose("999",65);throw new AssertionError("Arbitrary creation level accepted"); } catch(IllegalArgumentException expected) {}
  try { PlayerBotGenerationOptions.choose("level0",65);throw new AssertionError("Invalid creation option accepted"); } catch(IllegalArgumentException expected) {}
  System.out.println("OK: deferred recipe callbacks deduplicate without touching a nonexistent character/database; generated choices accept only owner level or level 1");
 }
}
