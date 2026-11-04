package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;

import com.aionemu.commons.database.DatabaseFactory;

/** Dedicated generated characters share character persistence but never normal character slots. */
public final class PlayerBotRoster {
	public record Entry(int id, String name, boolean ready) {}
	public record Page(List<Map<String,Object>> rows,int total,int page,int generatedCount) {}
	/** Page only identities; no player/inventory loading and no collection-size ceiling. */
	public static Page page(int account,int owner,String search,String filter,String sort,int requested,Set<Integer> active,Set<Integer> saved) {
		if(search.length()>80 || filter.length()>200 || requested<0)throw new IllegalArgumentException("Invalid collection page");
		String[] facets=filter.split("\\|",-1);filter=facets[0];
		boolean generatedSchema=available();
		String base=generatedSchema?" FROM players p LEFT JOIN playerbot_roster b ON b.player_id=p.id LEFT JOIN playerbot_removed r ON r.player_id=p.id WHERE p.account_id=? AND p.id<>? AND (b.player_id IS NULL OR b.account_id=p.account_id) AND r.player_id IS NULL AND NOT EXISTS (SELECT 1 FROM playerbot_recycles consumed WHERE consumed.source_id=p.id)":" FROM players p WHERE p.account_id=? AND p.id<>?";
		String extra=switch(filter){
			case "all"->"";case "characters"->generatedSchema?" AND b.player_id IS NULL":"";case "generated"->generatedSchema?" AND b.player_id IS NOT NULL AND COALESCE("+trait("origin")+",'')<>'GACHA'":" AND FALSE";
			case "tickets"->generatedSchema?" AND "+trait("origin")+"='GACHA'":" AND FALSE";
			case "available"->" AND p.online=0"+(generatedSchema?" AND (b.player_id IS NULL OR b.ready=TRUE)":"")+ids("NOT IN",PlayerBotLease.reservedIds());
			case "active"->ids("IN",active);case "saved"->(generatedSchema?" AND b.player_id IS NOT NULL":" AND FALSE")+ids("IN",saved);
			default->throw new IllegalArgumentException("Invalid collection filter");};
		String order=switch(sort){case "name"->"p.name,p.id";case "class"->"p.player_class,p.name,p.id";case "level"->"p.exp DESC,p.name,p.id";case "rarity"->tier("autoEquipRarity",generatedSchema);case "rank"->tier("combatRank",generatedSchema);default->throw new IllegalArgumentException("Invalid collection sort");};
		String where=base+extra+facets(facets,generatedSchema)+" AND (p.name LIKE ? ESCAPE '!' OR REPLACE(p.player_class,'_',' ') LIKE ? ESCAPE '!')";
		String query="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
		try(var c=DatabaseFactory.getConnection()) {
			int total,count;
			try(var s=c.prepareStatement("SELECT COUNT(*)"+where)){parameters(s,account,owner,query);try(var r=s.executeQuery()){r.next();total=r.getInt(1);}}
			try(var s=c.prepareStatement("SELECT COUNT(*)"+base+(generatedSchema?" AND b.player_id IS NOT NULL":" AND FALSE"))){s.setInt(1,account);s.setInt(2,owner);try(var r=s.executeQuery()){r.next();count=r.getInt(1);}}
			int page=Math.min(requested,Math.max(0,(total-1)/6));var rows=new ArrayList<Map<String,Object>>();
			try(var s=c.prepareStatement("SELECT p.id,p.name,p.exp,p.player_class,"+(generatedSchema?"b.player_id,b.ready":"NULL,FALSE")+where+" ORDER BY "+order+" LIMIT 6 OFFSET ?")) {
				parameters(s,account,owner,query);s.setLong(5,(long)page*6);
				try(var r=s.executeQuery()){while(r.next()){
					int id=r.getInt(1);boolean generated=r.getObject(5)!=null;var row=new HashMap<String,Object>();
					row.put("id",id);row.put("name",r.getString(2));row.put("level",com.aionemu.gameserver.dataholders.DataManager.PLAYER_EXPERIENCE_TABLE.getLevelForExp(r.getLong(3)));row.put("playerClass",r.getString(4));
					row.put("generated",generated);row.put("ready",!generated || r.getBoolean(6));row.put("reserved",PlayerBotLease.isReserved(id));rows.add(row);
				}}
			}
			traits(c,account,rows);
			return new Page(List.copyOf(rows),total,page,count);
		}catch(SQLException e){throw new IllegalStateException("Cannot read collection page",e);}
	}
	/** Explicit enum facets share the paged count/query. Never filter only the six visible cards. */
	private static String facets(String[] facets,boolean schema) {
		if(facets.length>4)throw new IllegalArgumentException("Invalid collection filters");
		var seen=new HashSet<String>();var sql=new StringBuilder();
		for(int i=1;i<facets.length;i++) {
			String[] pair=facets[i].split(":",-1);
			if(pair.length!=2 || !seen.add(pair[0]))throw new IllegalArgumentException("Invalid collection filter");
			switch(pair[0]) {
				case "class" -> sql.append(" AND p.player_class='").append(com.aionemu.gameserver.model.PlayerClass.valueOf(pair[1]).name()).append("'");
				case "rank" -> {var rank=PlayerBotGrowth.CombatRank.valueOf(pair[1]);sql.append(schema?" AND "+trait("combatRank")+"='"+rank.name()+"'":" AND FALSE");}
				case "rarity" -> {var rarity=PlayerBotGrowth.GearRarity.valueOf(pair[1]);sql.append(schema?" AND "+trait("autoEquipRarity")+"='"+rarity.name()+"'":" AND FALSE");}
				default -> throw new IllegalArgumentException("Invalid collection filter");
			}
		}
		return sql.toString();
	}
	/** One stored growth field, for filters and sorting. The primary key makes this a single row read. */
	private static String trait(String field) {return "(SELECT JSON_UNQUOTE(JSON_EXTRACT(m.settings,'$."+field+"')) FROM playerbot_metadata m WHERE m.player_id=p.id AND m.section='growth')";}
	/** Highest tier first; bots with no traits follow, by name. */
	private static String tier(String field,boolean schema) {return (schema?"FIELD("+trait(field)+",'POOR','ORDINARY','GOOD','GREAT','EXCELLENT') DESC,":"")+"p.name,p.id";}
	/** Stored traits for the page's generated rows. A bot with no growth row stays a plain Temporary Bot. */
	private static void traits(Connection c,int account,List<Map<String,Object>> rows)throws SQLException {
		traits(c,account,rows,false);
	}
	private static void traits(Connection c,int account,List<Map<String,Object>> rows,boolean recycling)throws SQLException {
		var generated=new HashMap<Object,Map<String,Object>>();
		for(var row:rows)if(Boolean.TRUE.equals(row.get("generated")))generated.put(row.get("id"),row);
		if(generated.isEmpty())return;
		String field="JSON_UNQUOTE(JSON_EXTRACT(settings,'$.%s'))",fields=String.join(",",field.formatted("origin"),field.formatted("rolledAdvancedClass"),field.formatted("autoEquipRarity"),field.formatted("initialCombatRank"),field.formatted("combatRank"),field.formatted("earnedLearningXp"));
		try(var s=c.prepareStatement("SELECT player_id,"+fields+" FROM playerbot_metadata WHERE section='growth' AND account_id=? AND player_id IN ("+String.join(",",Collections.nCopies(generated.size(),"?"))+")")) {
			s.setInt(1,account);int index=1;for(var id:generated.keySet())s.setInt(++index,(Integer)id);
			try(var r=s.executeQuery()){while(r.next()){
				var values=new HashMap<>(Map.of("version","1","account",Integer.toString(account),"character",Integer.toString(r.getInt(1)),"origin",r.getString(2),"rolledAdvancedClass",r.getString(3),"autoEquipRarity",r.getString(4),"initialCombatRank",r.getString(5),"combatRank",r.getString(6),"earnedLearningXp",r.getString(7)));
				var profile=PlayerBotGrowth.decode(values);var identity=PlayerBotGrowth.identity(profile);
				if(identity!=null)generated.get(r.getInt(1)).put("growth",identity);
				if(recycling)generated.get(r.getInt(1)).put("needed",PlayerBotGrowth.remainingLearningXp(profile));
			}}
		}
	}
	/** Account-wide receiver identities; no player or inventory loading, independent of collection paging. */
	public static List<Map<String,Object>> recycleReceivers(int account,int owner,Collection<PlayerBotSession> companions) {
		if(!available())return List.of();
		var active=new HashMap<Integer,PlayerBotSession>();for(var session:companions)active.put(session.bot().getObjectId(),session);
		var rows=new ArrayList<Map<String,Object>>();
		try(var c=DatabaseFactory.getConnection();var s=c.prepareStatement("SELECT p.id,p.name,p.player_class,p.online FROM players p JOIN playerbot_roster b ON b.player_id=p.id AND b.account_id=p.account_id LEFT JOIN playerbot_removed r ON r.player_id=p.id WHERE p.account_id=? AND p.id<>? AND b.ready=TRUE AND r.player_id IS NULL AND "+trait("origin")+"='GACHA' AND NOT EXISTS (SELECT 1 FROM playerbot_recycles consumed WHERE consumed.source_id=p.id) ORDER BY p.name,p.id")) {
			s.setInt(1,account);s.setInt(2,owner);try(var r=s.executeQuery()){while(r.next()){
				int id=r.getInt(1);var session=active.get(id);
				if(session!=null ? session.closing() : r.getBoolean(4) || PlayerBotLease.isReserved(id))continue;
				var row=new HashMap<String,Object>();row.put("id",id);row.put("name",r.getString(2));row.put("playerClass",r.getString(3));row.put("generated",true);row.put("active",session!=null);rows.add(row);
			}}
			traits(c,account,rows,true);
			var result=new ArrayList<Map<String,Object>>();
			for(var row:rows) {
				var session=active.get(row.get("id"));var growth=(Map<?,?>)row.get("growth");
				if(session!=null){var profile=PlayerBotGrowth.profile(session.bot());growth=PlayerBotGrowth.identity(profile);row.put("needed",PlayerBotGrowth.remainingLearningXp(profile));}
				if(growth==null || !"GACHA".equals(growth.get("origin")) || "EXCELLENT".equals(growth.get("rank")) || ((Number)row.get("needed")).longValue()<=0)continue;
				row.put("rank",growth.get("rank"));row.remove("growth");row.remove("generated");result.add(row);
			}
			return List.copyOf(result);
		}catch(SQLException|java.io.IOException e){throw new IllegalStateException("Cannot read recycling receivers",e);}
	}
	private static void parameters(PreparedStatement s,int account,int owner,String query)throws SQLException {s.setInt(1,account);s.setInt(2,owner);s.setString(3,query);s.setString(4,query);}
	public static int freeGeneratedCount(int account) {
        if(!available())return 0;
		try(var c=DatabaseFactory.getConnection();var s=c.prepareStatement("SELECT COUNT(*) FROM playerbot_roster b LEFT JOIN playerbot_removed r ON r.player_id=b.player_id WHERE b.account_id=? AND r.player_id IS NULL AND NOT EXISTS (SELECT 1 FROM playerbot_metadata m WHERE m.player_id=b.player_id AND m.account_id=b.account_id AND m.section='growth' AND JSON_UNQUOTE(JSON_EXTRACT(m.settings,'$.origin'))='GACHA')")) {
			s.setInt(1,account);try(var r=s.executeQuery()){r.next();return r.getInt(1);}
		}catch(SQLException e){throw new IllegalStateException("Cannot read free-generation quota",e);}
	}
	private static String ids(String operation,Set<Integer> ids) {
		if(ids.isEmpty())return operation.equals("IN")?" AND FALSE":"";
		if(ids.stream().anyMatch(i->i<=0))throw new IllegalArgumentException("Invalid collection identity");
		return " AND p.id "+operation+" ("+ids.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))+")";
	}
	private static volatile Boolean schemaAvailable;
	private PlayerBotRoster() {}

	/** Optional schema: only a confirmed missing table permits the ordinary-character fallback. */
	public static boolean available() {
		Boolean known = schemaAvailable;
		if (known != null) return known;
		synchronized (PlayerBotRoster.class) {
			if (schemaAvailable != null) return schemaAvailable;
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("SELECT player_id FROM playerbot_roster LIMIT 0");
				ResultSet ignored = statement.executeQuery()) {
				schemaAvailable = true;
			} catch (SQLException e) {
				if (!"42S02".equals(e.getSQLState()) && e.getErrorCode() != 1146)
					throw new IllegalStateException("Cannot verify the companion roster; refusing to expose generated characters", e);
				schemaAvailable = false;
			}
			return schemaAvailable;
		}
	}

	public static String ordinaryCharactersClause() {
		return available() ? " AND NOT EXISTS (SELECT 1 FROM playerbot_roster b WHERE b.player_id=players.id)" : "";
	}

	public static List<Entry> list(int account) {
		return lists(List.of(account)).getOrDefault(account, List.of());
	}
	public static Entry findReady(int account,String name) {
		if(!available())return null;
		try(var c=DatabaseFactory.getConnection();var s=c.prepareStatement("SELECT b.player_id,p.name FROM players p JOIN playerbot_roster b ON b.player_id=p.id AND b.account_id=p.account_id LEFT JOIN playerbot_removed r ON r.player_id=p.id WHERE b.account_id=? AND b.ready=TRUE AND p.name=? AND r.player_id IS NULL AND NOT EXISTS (SELECT 1 FROM playerbot_recycles consumed WHERE consumed.source_id=p.id)")) {
			s.setInt(1,account);s.setString(2,name);try(var r=s.executeQuery()){return r.next()?new Entry(r.getInt(1),r.getString(2),true):null;}
		}catch(SQLException e){throw new IllegalStateException("Cannot look up companion",e);}
	}
	/** Saved level and class of roster characters, for the Companions roster cards (one bounded query). */
	public static Map<Integer, Map.Entry<Integer, String>> identities(Collection<Integer> characters) {
		if (characters.isEmpty()) return Map.of();
		var ids = new ArrayList<>(new TreeSet<>(characters));
		if (ids.size() > 1024 || ids.stream().anyMatch(id -> id <= 0)) throw new IllegalArgumentException("Invalid companion roster identities");
		var result = new HashMap<Integer, Map.Entry<Integer, String>>();
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT id,exp,player_class FROM players WHERE id IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")")) {
			for (int i = 0; i < ids.size(); i++) statement.setInt(i + 1, ids.get(i));
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) result.put(rows.getInt(1), Map.entry(com.aionemu.gameserver.dataholders.DataManager.PLAYER_EXPERIENCE_TABLE.getLevelForExp(rows.getLong(2)), rows.getString(3)));
			}
		} catch (SQLException e) { throw new IllegalStateException("Cannot read companion roster levels", e); }
		return result;
	}
	/** Bounded native XP snapshot only for identities already verified by the joined roster query. */
	static Map<Integer,Integer> populationLevels(Collection<Integer> characters) {
		if(characters.isEmpty())return Map.of();
		try(Connection connection=DatabaseFactory.getConnection()) {
			return populationLevels(connection,characters,exp->com.aionemu.gameserver.dataholders.DataManager.PLAYER_EXPERIENCE_TABLE.getLevelForExp(exp));
		}catch(SQLException e){throw new IllegalStateException("Cannot read saved population levels; no identities rerolled",e);}
	}
	static Map<Integer,Integer> populationLevels(Connection connection,Collection<Integer> characters,java.util.function.LongToIntFunction level)throws SQLException {
		var ids=new ArrayList<>(new TreeSet<>(characters));var result=new HashMap<Integer,Integer>();
		if(ids.stream().anyMatch(id->id<=0) || ids.size()>PlayerBotPopulationPolicy.MAX_RESERVE)throw new IllegalArgumentException("Invalid population identity snapshot");
		for(int start=0;start<ids.size();start+=1024) {
			var chunk=ids.subList(start,Math.min(ids.size(),start+1024));
			try(var statement=connection.prepareStatement("SELECT id,exp FROM players WHERE id IN ("+String.join(",",Collections.nCopies(chunk.size(),"?"))+")")) {
				for(int i=0;i<chunk.size();i++)statement.setInt(i+1,chunk.get(i));
				try(var rows=statement.executeQuery()){while(rows.next()) {
					int id=rows.getInt(1),value=level.applyAsInt(rows.getLong(2));
					if(!chunk.contains(id) || value<1 || value>65 || result.putIfAbsent(id,value)!=null)throw new SQLException("Invalid population XP snapshot");
				}}
			}
		}
		return Map.copyOf(result);
	}

	/** Fresh, account-scoped visibility snapshot. Archive checks share the same query as roster ownership. */
	public static Map<Integer, List<Entry>> lists(Collection<Integer> accounts) {
		if (accounts.isEmpty() || !available()) return Map.of();
		try (Connection connection = DatabaseFactory.getConnection()) {
			return lists(connection, accounts);
		} catch (SQLException e) { throw new IllegalStateException("Cannot read generated companion roster", e); }
	}

	static Map<Integer, List<Entry>> lists(Connection connection, Collection<Integer> accounts) throws SQLException {
		var requested = new TreeSet<>(accounts);
		if (requested.isEmpty()) return Map.of();
		if (requested.size() > 4096 || requested.stream().anyMatch(a -> !PlayerBotMetadata.validAccount(a)))
			throw new IllegalArgumentException("Invalid companion roster accounts");
		var result = new LinkedHashMap<Integer, List<Entry>>();
		requested.forEach(a -> result.put(a, new ArrayList<>()));
		var identities = new HashSet<Integer>();
		String placeholders = String.join(",", Collections.nCopies(requested.size(), "?"));
		try (PreparedStatement statement = connection.prepareStatement(
			"SELECT b.account_id,b.player_id,p.name,b.ready,r.account_id,r.document FROM playerbot_roster b "
			+ "LEFT JOIN players p ON p.id=b.player_id LEFT JOIN playerbot_removed r ON r.player_id=b.player_id "
			+ "WHERE b.account_id IN (" + placeholders + ") AND NOT EXISTS (SELECT 1 FROM playerbot_recycles consumed WHERE consumed.source_id=b.player_id) ORDER BY b.account_id,b.player_id")) {
			int index = 0;
			for (int account : requested) statement.setInt(++index, account);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					int account = rows.getInt(1), id = rows.getInt(2);
					if (!requested.contains(account) || id <= 0 || !identities.add(id))
						throw new SQLException("Invalid companion roster identity");
					String marker = rows.getString(6);
					if (rows.getObject(5) != null) {
						if (rows.getInt(5) != account || marker == null) throw new SQLException("Invalid archived companion owner");
						try { PlayerBotRepository.validateMarker(account, id, marker); }
						catch (java.io.IOException e) { throw new SQLException("Invalid archived companion marker", e); }
					} else {
						if (marker != null) throw new SQLException("Archived companion lacks an owner");
						result.get(account).add(new Entry(id, rows.getString(3), rows.getBoolean(4)));
					}
				}
			}
		}
		result.replaceAll((account, entries) -> List.copyOf(entries));
		return Collections.unmodifiableMap(result);
	}

	static boolean contains(int character) {
		if (!available()) return false;
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement(
			"SELECT player_id FROM playerbot_roster WHERE player_id=?")) {
			statement.setInt(1, character);
			try (ResultSet row = statement.executeQuery()) { return row.next(); }
		} catch (SQLException e) { throw new IllegalStateException("Cannot verify generated companion identity", e); }
	}

	/** Reserve visibility before creating the player row; failures stay quarantined as pending. */
	static void reserve(int account, int character) throws SQLException {
		if (!available()) throw new SQLException("Apply game-server/sql/playerbots.sql and restart before creating dedicated companions");
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO playerbot_roster (player_id,account_id,ready) VALUES (?,?,FALSE)")) {
			statement.setInt(1, character); statement.setInt(2, account);
			if (statement.executeUpdate() != 1) throw new SQLException("Cannot reserve generated companion");
		}
	}

	static void ready(int account, int character) throws SQLException {
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement(
			"UPDATE playerbot_roster SET ready=TRUE WHERE player_id=? AND account_id=? AND ready=FALSE AND NOT EXISTS (SELECT 1 FROM playerbot_removed r WHERE r.player_id=playerbot_roster.player_id) AND NOT EXISTS (SELECT 1 FROM playerbot_recycles consumed WHERE consumed.source_id=playerbot_roster.player_id)")) {
			statement.setInt(1, character); statement.setInt(2, account);
			if (statement.executeUpdate() != 1) throw new SQLException("Cannot finalize generated companion");
		}
	}
}
