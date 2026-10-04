import java.lang.instrument.*;
import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.services.playerbot.PlayerBotService;
import com.aionemu.gameserver.utils.chathandlers.ChatProcessor;

/** Read-only effective bytecode capture and actual loaded geometry invocation. */
public final class PlayerBotCenteredFormationRuntimeCheckAgent {
    public static void agentmain(String report, Instrumentation instrumentation) throws Exception {
        List<String> lines = new ArrayList<>(); Map<Class<?>,byte[]> captured = new HashMap<>();
        Class<?> formation = Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotFormation");
        Class<?> layout = Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotFormationLayout");
        Object command = ChatProcessor.getInstance().getCommandList().stream().filter(c->c.getAliasWithPrefix().equals(".bot")).findFirst().orElseThrow();
        Class<?> botCommand = command.getClass();
        ClassFileTransformer probe = new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type, java.security.ProtectionDomain domain, byte[] bytes) {
                if (type==formation || type==botCommand) captured.put(type, bytes.clone());
                return null;
            }
        };
        try {
            instrumentation.addTransformer(probe,true);
            try { instrumentation.retransformClasses(formation,botCommand); }
            finally { instrumentation.removeTransformer(probe); }
            for (var test : Map.of(formation,"destination",botCommand,"execute").entrySet()) {
                var model=ClassFile.of().parse(captured.get(test.getKey()));
                var method=model.methods().stream().filter(m->m.methodName().equalsString(test.getValue())).findFirst().orElseThrow();
                boolean routed=false;
                for (var element : method.code().orElseThrow())
                    if (element instanceof InvokeInstruction invoke && invoke.owner().asInternalName().endsWith("/PlayerBotFormationLayout")
                            && invoke.name().equalsString(test.getKey()==formation ? "destination" : "command")) routed=true;
                if (!routed) throw new IllegalStateException("Loaded method did not use new formation helper: "+test.getValue());
                lines.add("OK: effective live "+test.getKey().getName()+"."+test.getValue()+" invokes the formation helper.");
            }
            Method point=layout.getDeclaredMethod("point",float.class,float.class,float.class,double.class,int.class,int.class,String.class);point.setAccessible(true);
            int count=0;
            for (String shape:List.of("circle","box","line","spread")) for(int bots=1;bots<=5;bots++) for(int slot=0;slot<bots;slot++) {
                Object value=point.invoke(null,100f,200f,7f,0d,slot,bots,shape);
                Method x=value.getClass().getDeclaredMethod("x"),y=value.getClass().getDeclaredMethod("y");x.setAccessible(true);y.setAccessible(true);
                double radius=Math.hypot(((Number)x.invoke(value)).doubleValue()-100,((Number)y.invoke(value)).doubleValue()-200);
                if(radius<3.39 || radius>6.81)throw new IllegalStateException("Loaded layout left owner center or range");count++;
            }
            lines.add("OK: "+count+" actual loaded formation destinations; no character, setting, order, inventory or world state changed.");
            Files.write(Path.of(report),lines,StandardOpenOption.CREATE_NEW);
        } catch(Throwable error) { Files.writeString(Path.of(report),"FAILED: "+error,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);throw new IllegalStateException(error); }
    }
    public static void main(String[] args)throws Exception {
        VirtualMachine vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}
    }
}
