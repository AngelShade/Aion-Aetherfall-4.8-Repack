import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.gameobjects.LetterType;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.services.mail.SystemMailService;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.utils.xml.JAXBUtil;

/** One explicitly authorized test delivery: original native box IDs, one each to Baby. */
public class SeasonPassBoxMailAgent {
 static final int[] IDS={188052187,188052555,188053068,188053975,188053976,188053979,188053980,188053981,188053982,188053983,188053984,188053985,188053986,188053987,188053988,188053989};
 static final String MARKER="[BOXTEST-20261003-16]";
 static final String OLD="734c2b878b7a95cc3e6ffb1cf094cedab480214919b528821706dfbf7a12d41d";
 static final String NEW="e31f0b80342f348bc7da0af9baedd6b33d3775c58f55f3dae683f3f4f2b62762";
 static String hash(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
 static int outcomeCount(Object value){return value==null?0:value instanceof List<?> list?list.size():((DecomposableItemInfo)value).getSets().size();}
 static String signature(Object value)throws Exception {
  if(value==null)return "null";
  if(value instanceof List<?> list){StringBuilder s=new StringBuilder("[");for(Object v:list)s.append(signature(v)).append(';');return s+"]";}
  if(value instanceof Number || value instanceof Enum<?> || value instanceof String)return value.toString();
  StringBuilder s=new StringBuilder(value.getClass().getName());
  for(Class<?> type=value.getClass();type!=Object.class;type=type.getSuperclass())for(Field f:type.getDeclaredFields()){
   if(java.lang.reflect.Modifier.isStatic(f.getModifiers()))continue;f.setAccessible(true);s.append('|').append(f.getName()).append('=').append(signature(f.get(value)));
  }
  return s.toString();
 }
 @SuppressWarnings("unchecked") static Map<Integer,?> map(DecomposableItemsData data,String name)throws Exception{Field f=DecomposableItemsData.class.getDeclaredField(name);f.setAccessible(true);return (Map<Integer,?>)f.get(data);}
 static List<String> attachments(int id)throws Exception {
  List<String> rows=new ArrayList<>();
  try(Connection c=DatabaseFactory.getConnection();PreparedStatement s=c.prepareStatement("SELECT m.mail_unique_id,m.attached_item_id,i.item_id,i.item_count,i.item_location,m.unread FROM mail m LEFT JOIN inventory i ON i.item_unique_id=m.attached_item_id AND i.item_owner=m.mail_recipient_id WHERE m.mail_recipient_id=9403 AND m.mail_title=? AND m.mail_message LIKE ?")){
   s.setString(1,"Box Test "+id);s.setString(2,MARKER+"%");
   try(ResultSet r=s.executeQuery()){while(r.next()){
    if(r.getInt(3)!=id || r.getLong(4)!=1 || r.getInt(5)!=127)throw new IllegalStateException("Existing test mail attachment needs inspection: "+id);
    rows.add("mail="+r.getInt(1)+" object="+r.getInt(2)+" item="+r.getInt(3)+" count="+r.getLong(4)+" location="+r.getInt(5)+" unread="+r.getInt(6));
   }}
  }
  if(rows.size()>1)throw new IllegalStateException("Duplicate test deliveries: "+id);
  return rows;
 }
 public static void agentmain(String argument,Instrumentation instrumentation)throws Exception {
  if(Arrays.stream(instrumentation.getAllLoadedClasses()).noneMatch(c->c.getName().equals("com.aionemu.gameserver.GameServer")))throw new IllegalStateException("Expected Aion GameServer.");
  String[] args=argument.split("\\|",-1);if(args.length!=3)throw new IllegalArgumentException("mode|repository|receipt");
  Path repo=Path.of(args[1]).toRealPath();Path receipt=Path.of(args[2]);
  Files.writeString(receipt,"START: "+args[0]+"; Baby; sixteen boxes x1\n",StandardOpenOption.CREATE_NEW);
  try{
   PlayerCommonData baby=PlayerService.getOrLoadPlayerCommonData("Baby");
   if(baby==null || baby.getPlayerObjId()!=9403 || !baby.getName().equals("Baby"))throw new IllegalStateException("Expected Baby 9403.");
   Files.writeString(receipt,"IDENTITY: Baby 9403; class="+baby.getPlayerClass()+"; level="+baby.getLevel()+"; online="+(baby.getPlayer()!=null)+"; mailbox="+baby.getMailboxLetters()+"\n",StandardOpenOption.APPEND);
   int missing=0;
   for(int id:IDS){
    if(DataManager.ITEM_DATA.getItemTemplate(id)==null)throw new IllegalStateException("Missing native item "+id);
    List<String> rows=attachments(id);if(rows.isEmpty())missing++;
    Files.writeString(receipt,"CHECK "+id+" liveOutcomes="+outcomeCount(DataManager.DECOMPOSABLE_ITEMS_DATA.getInfoByItemId(id))+" existing="+rows+"\n",StandardOpenOption.APPEND);
   }
   if(!args[0].equals("send")){Files.writeString(receipt,"OK: read-only preflight.\n",StandardOpenOption.APPEND);return;}
   if(baby.getMailboxLetters()+missing>200)throw new IllegalStateException("Not enough mailbox space.");
   Path source=repo.resolve("game-server/data/static_data/decomposable_items/decomposable_items.xml");
   Path deployed=repo.resolve("target-deploy/game-server/data/static_data/decomposable_items/decomposable_items.xml");
   if(!hash(source).equals(NEW) || !(hash(deployed).equals(OLD)||hash(deployed).equals(NEW)))throw new IllegalStateException("Source/deployed box hashes changed.");
   DecomposableItemsData previous=DataManager.DECOMPOSABLE_ITEMS_DATA;
   DecomposableItemsData restored=JAXBUtil.deserialize(source.toFile(),DecomposableItemsData.class,repo.resolve("target-deploy/game-server/data/static_data/decomposable_items/decomposable_items.xsd").toString());
   Set<Integer> allowed=new HashSet<>();for(int id:IDS)allowed.add(id);
   for(String field:List.of("decomposableItemsInfo","selectableDecomposables")){
    Map<Integer,?> old=map(previous,field),fresh=map(restored,field);Set<Integer> all=new HashSet<>(old.keySet());all.addAll(fresh.keySet());
    for(int id:all)if(!allowed.contains(id)&&!signature(old.get(id)).equals(signature(fresh.get(id))))throw new IllegalStateException("Unrelated live box differs: "+id);
   }
   for(int id:IDS)if(restored.getInfoByItemId(id)==null || restored.getInfoByItemId(id).getSets().isEmpty() || restored.getInfoByItemId(id).isSelectable())throw new IllegalStateException("Missing/random-opening mismatch "+id);
   if(hash(deployed).equals(OLD)){
    Path backup=repo.resolve("target-deploy/game-server/backups/box-test-"+System.currentTimeMillis()+"/decomposable_items.xml");Files.createDirectories(backup.getParent());Files.copy(deployed,backup);
    if(!hash(backup).equals(OLD))throw new IllegalStateException("Backup hash mismatch.");
    try{Files.copy(source,deployed,StandardCopyOption.REPLACE_EXISTING);if(!hash(deployed).equals(NEW))throw new IllegalStateException("Installed data hash mismatch.");}
    catch(Exception e){Files.copy(backup,deployed,StandardCopyOption.REPLACE_EXISTING);throw e;}
    Files.writeString(receipt,"BACKUP: "+backup+"\n",StandardOpenOption.APPEND);
   }
   DataManager.DECOMPOSABLE_ITEMS_DATA=restored;
   Files.writeString(receipt,"OK: native JAXB reload; only sixteen original box definitions changed; source/deployment SHA256="+NEW+"\n",StandardOpenOption.APPEND);
   for(int id:IDS){
    List<String> rows=attachments(id);
    if(rows.isEmpty()){
     boolean sent=SystemMailService.sendMail("$$CASH_ITEM_MAIL","Baby","Box Test "+id,MARKER+" One original Aion 4.8 box for opening tests. Collect the attachment and use normally. Class bundles retain their native class requirement.",id,1,0,LetterType.BLACKCLOUD);
     rows=attachments(id);
     if(!sent || rows.size()!=1)throw new IllegalStateException("Delivery/attachment verification failed: "+id+"; inspect before retry.");
    }
    Files.writeString(receipt,"VERIFIED "+rows.get(0)+"\n",StandardOpenOption.APPEND);
   }
   Files.writeString(receipt,"SUCCESS: sixteen original boxes x1 persisted in Baby's Black Cloud mailbox. No Kinah, access level or server JAR changed.\n",StandardOpenOption.APPEND);
  }catch(Throwable e){Files.writeString(receipt,"ERROR: "+e+"; inspect persisted receipts/mail before retry.\n",StandardOpenOption.APPEND);throw e;}
 }
 public static void main(String[] args)throws Exception {
  VirtualMachine vm=VirtualMachine.attach(args[0]);
  try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]+"|"+Path.of(".").toRealPath()+"|"+Path.of(args[3]).toAbsolutePath());}finally{vm.detach();}
 }
}
