import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Apply only the companion ownership table to the configured game DB; never edit character rows. */
public class PlayerBotSchemaInstaller {
 public static void main(String[] args) throws Exception {
  Path root=Path.of(args[0]); Properties config=new Properties();
  try(var input=Files.newInputStream(root.resolve("config/network/database.properties"))) { config.load(input); }
  try(var input=Files.newInputStream(root.resolve("config/mygs.properties"))) { config.load(input); }
  String url=config.getProperty("database.url").replace("${gameserver.timezone}",config.getProperty("gameserver.timezone","UTC"));
  String sql=Files.readString(Path.of(args[1]));
  if (!sql.contains("CREATE TABLE IF NOT EXISTS playerbot_roster") || sql.matches("(?is).*\\b(UPDATE|DELETE|DROP|ALTER|INSERT|TRUNCATE)\\b.*")) throw new IllegalArgumentException("Only the additive companion schema is authorized");
  try(Connection connection=DriverManager.getConnection(url,config.getProperty("database.user"),config.getProperty("database.password")); Statement statement=connection.createStatement()) {
   try(ResultSet rows=statement.executeQuery("SELECT COUNT(*) FROM players WHERE online=1")) { rows.next(); if(rows.getInt(1)!=0) throw new IllegalStateException("Wait for normal character logout before migration"); }
   for(String command:sql.split(";")) if(!command.isBlank()) statement.execute(command);
   try(ResultSet rows=statement.executeQuery("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='playerbot_roster'")) {
    if(!rows.next() || !rows.getString(1).equalsIgnoreCase("InnoDB")) throw new SQLException("Companion roster requires InnoDB");
   }
   try(ResultSet rows=statement.executeQuery("SELECT player_id,account_id,ready,created_at FROM playerbot_roster LIMIT 0")) { }
   System.out.println("OK: playerbot_roster schema applied and verified. No character/inventory rows changed.");
  }
 }
}
