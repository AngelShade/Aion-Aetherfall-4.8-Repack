import java.nio.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.account.*;
import com.aionemu.gameserver.network.aion.*;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.dao.PlayerSettingsDAO;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.configs.DatabaseConfig;
import sun.misc.Unsafe;

/** Real packet/DAO checks; fixture objects bypass world/controller initialization. */
public final class SpeechBubbleCheck {
 static int checks;
 static void check(boolean ok,String label) {checks++;if(!ok)throw new AssertionError(label);}
 static class Fixture extends Player {
  PlayerSettings settings;
  Fixture(){super(null,null);}
  public int getObjectId(){return 101;}
  public String getName(boolean tags){return "Fixture";}
  public boolean isStaff(){return false;}
  public PlayerSettings getPlayerSettings(){return settings;}
 }
 static class Client extends AionConnection {
  Player player;
  Client()throws Exception{super(null,null);}
  public Player getActivePlayer(){return player;}
 }
 static byte[] body(SM_MESSAGE p,Client c)throws Exception {
  ByteBuffer b=ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);p.setBuf(b);
  var write=SM_MESSAGE.class.getDeclaredMethod("writeImpl",AionConnection.class);write.setAccessible(true);write.invoke(p,c);
  return Arrays.copyOf(b.array(),b.position());
 }
 static String name(byte[] b) {
  ByteBuffer buf=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);buf.position(6);StringBuilder s=new StringBuilder();char c;
  while((c=buf.getChar())!=0)s.append(c);return s.toString();
 }
 public static void main(String[] args) {
  try { run(args); System.exit(0); } catch(Throwable e) { e.printStackTrace();System.exit(1); }
 }
 static void run(String[] args)throws Exception {
  com.aionemu.gameserver.configs.network.NetworkConfig.PACKET_PROCESSOR_MIN_THREADS=1;
  com.aionemu.gameserver.configs.network.NetworkConfig.PACKET_PROCESSOR_MAX_THREADS=1;
  com.aionemu.gameserver.configs.network.NetworkConfig.PACKET_PROCESSOR_THREAD_SPAWN_THRESHOLD=50;
  com.aionemu.gameserver.configs.network.NetworkConfig.PACKET_PROCESSOR_THREAD_KILL_THRESHOLD=3;
  var uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);Unsafe u=(Unsafe)uf.get(null);
  Fixture player=(Fixture)u.allocateInstance(Fixture.class);player.settings=new PlayerSettings();
  Client stock=(Client)u.allocateInstance(Client.class),patched=(Client)u.allocateInstance(Client.class);stock.player=patched.player=player;
  patched.enableSpeechBubbleClient();
  for(int style=0;style<5;style++) {
   player.settings.setSpeechBubbleStyle(style);var packet=SM_MESSAGE.speechBubbleAcknowledgement(player);
   byte[] normal=body(packet,stock),extension=body(packet,patched);
   check(name(normal).equals("Fixture"),"stock sender unaffected");
   check(name(extension).equals("~ASB1:"+style+":Fixture"),"negotiated metadata");
   check(Arrays.equals(Arrays.copyOf(normal,6),Arrays.copyOf(extension,6)),"type/race/object identity unchanged");
   check(Arrays.equals(Arrays.copyOfRange(normal,22,normal.length),Arrays.copyOfRange(extension,38,extension.length)),"message text unchanged");
  }
  check(name(body(new SM_MESSAGE(101,"Fixture","whisper",ChatType.WHISPER),patched)).equals("Fixture"),"whisper has no extension");
  boolean invalid=false;try{player.settings.setSpeechBubbleStyle(5);}catch(IllegalArgumentException e){invalid=true;}check(invalid,"invalid style refused");
  Properties p=new Properties();try(var in=Files.newInputStream(Path.of(args[0],"config/network/database.properties"))){p.load(in);}
  try(var in=Files.newInputStream(Path.of(args[0],"config/mygs.properties"))){p.load(in);}
  String url=p.getProperty("database.url").replace("${gameserver.timezone}","UTC"),user=p.getProperty("database.user"),password=p.getProperty("database.password");
  String schema="aion_speech_check_"+System.currentTimeMillis();
  try(Connection live=DriverManager.getConnection(url,user,password);Statement s=live.createStatement()) {
   String source=live.getCatalog();check(source.matches("[A-Za-z0-9_]+"),"safe source name");
   s.execute("CREATE DATABASE `"+schema+"`");
   try {
    s.execute("CREATE TABLE `"+schema+"`.player_settings LIKE `"+source+"`.player_settings");
    DatabaseConfig.DATABASE_URL=url.replace("/"+source,"/"+schema);DatabaseConfig.DATABASE_USER=user;DatabaseConfig.DATABASE_PASSWORD=password;
    DatabaseConfig.DATABASE_CONNECTIONS_MAX=2;DatabaseConfig.DATABASE_TIMEOUT=5000;DatabaseFactory.init();
    try(Connection c=DatabaseFactory.getConnection();Statement q=c.createStatement()){
     check(c.getCatalog().equals(schema),"DAO only uses disposable schema");
     check(PlayerSettingsDAO.loadSettings(101).getSpeechBubbleStyle()==0,"default for existing character");
     for(int style=0;style<5;style++) {
      player.settings.setSpeechBubbleStyle(style);PlayerSettingsDAO.saveSettings(player);
      check(PlayerSettingsDAO.loadSettings(101).getSpeechBubbleStyle()==style,"actual DAO persistence "+style);
     }
     q.execute("REPLACE INTO player_settings VALUES(101,-3,'99')");
     check(PlayerSettingsDAO.loadSettings(101).getSpeechBubbleStyle()==0,"invalid stored value defaults safely");
    }
   }finally{s.execute("DROP DATABASE `"+schema+"`");}
  }
  System.out.println(checks+" speech bubble packet and database checks passed.");
  System.exit(0); // Fixture-created packet processor has background workers.
 }
}
