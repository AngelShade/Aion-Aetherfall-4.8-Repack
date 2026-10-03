import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.sun.tools.attach.VirtualMachine;

/** Change only verified shop asset version constants; no server restart/state reset. */
public final class MarketUiVersionsAgent {
    private static String page(Class<?> type) throws Exception {
        var method=type.getDeclaredMethod("page",String.class);method.setAccessible(true);
        return (String)method.invoke(null,"FIXTURE-ONLY");
    }
    private static void updated(String html) {
        if(!html.contains("marketplace.css?v=15")||!html.contains("marketplace.js?v=2")||!html.contains("FIXTURE-ONLY"))throw new IllegalStateException("Unexpected new asset versions");
    }
    public static void agentmain(String argument,Instrumentation instrumentation)throws Exception {
        String[] args=argument.split("\\|",-1);if(args.length!=3)throw new IllegalArgumentException("class|hash|receipt");
        Path receipt=Path.of(args[2]);Files.writeString(receipt,"START: shop UI asset versions\n",StandardOpenOption.CREATE_NEW);
        try {
            Class<?> type=null;boolean server=false;
            for(Class<?> loaded:instrumentation.getAllLoadedClasses()) {
                if(loaded.getName().equals("com.aionemu.gameserver.GameServer"))server=true;
                if(loaded.getName().equals("com.aionemu.gameserver.services.MarketplaceService"))type=loaded;
            }
            if(!server||type==null||!instrumentation.isModifiableClass(type))throw new IllegalStateException("Expected the running Aion GameServer/shop");
            if(args[0].equals("verify")) {
                updated(page(type));
                if(args[1].equals("cleanup")) {
                    Class<?> config=Class.forName("com.aionemu.gameserver.configs.Config",false,type.getClassLoader());
                    var configs=(java.util.List<?>)config.getMethod("getClasses").invoke(null);
                    if(configs.size()!=37||configs.stream().anyMatch(c->((Class<?>)c).getName().contains("Afk")))throw new IllegalStateException("AFK config remains");
                    for(String name:java.util.List.of("com.aionemu.gameserver.services.AfkKeepAliveService","com.aionemu.gameserver.configs.main.AfkKeepAliveConfig")) {
                        try{Class.forName(name,false,type.getClassLoader());throw new IllegalStateException("AFK class remains");}catch(ClassNotFoundException expected){}
                    }
                    Files.writeString(receipt,"OK: AFK classes absent; 37 config classes; no AFK registration.\n",StandardOpenOption.APPEND);
                }
                Files.writeString(receipt,"OK: read-only live verification emits CSS v15 / JS v2.\n",StandardOpenOption.APPEND);return;
            }
            String before=page(type);
            if(!before.contains("marketplace.css?v=14")||!before.contains("marketplace.js?v=1'"))throw new IllegalStateException("Live shop changed before deployment");
            byte[] bytes=Files.readAllBytes(Path.of(args[0]));
            if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(args[1]))throw new IllegalStateException("Staged class checksum mismatch");
            instrumentation.redefineClasses(new ClassDefinition(type,bytes));updated(page(type));
            Files.writeString(receipt,"OK: live shop emits CSS v15 / JS v2; existing class fields and service state retained.\n",StandardOpenOption.APPEND);
        } catch(Throwable error) {
            Files.writeString(receipt,"ERROR: "+error+"\n",StandardOpenOption.APPEND);throw error;
        }
    }
    public static void main(String[] args)throws Exception {
        if(args[0].equals("verify")) {updated(page(Class.forName("com.aionemu.gameserver.services.MarketplaceService")));System.out.println("PASS: JVM loads the patched class and renders the new CSS/JS asset versions");return;}
        VirtualMachine vm=VirtualMachine.attach(args[0]);
        try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),(args[2].equals("verify")?"verify":Path.of(args[2]).toAbsolutePath())+"|"+args[3]+"|"+Path.of(args[4]).toAbsolutePath());}finally{vm.detach();}
    }
}
