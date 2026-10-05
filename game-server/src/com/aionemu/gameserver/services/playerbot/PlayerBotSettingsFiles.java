package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.*;

/** Short retries for Windows sharing violations; never truncate a committed setting file. */
final class PlayerBotSettingsFiles {
 @FunctionalInterface interface Move { void run(Path from,Path to,boolean atomic)throws IOException; }
 @FunctionalInterface interface Pause { void run(long millis)throws InterruptedException; }
 private static final Move NATIVE=(from,to,atomic)-> {
  if(atomic)Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  else Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);
 };
 static void replace(Path temporary,Path target)throws IOException { replace(temporary,target,NATIVE,Thread::sleep); }
 static void replace(Path temporary,Path target,Move move,Pause pause)throws IOException {
  boolean atomic=true;
  for(int attempt=0;;attempt++) {
   try {
    try { move.run(temporary,target,atomic); }
    catch(AtomicMoveNotSupportedException unsupported) { atomic=false;move.run(temporary,target,false); }
    return;
   } catch(AccessDeniedException denied) {
    // Readers/antivirus can briefly deny rename on Windows. Bound the total
    // delay to 175 ms so persistent permissions do not stall the AI scheduler.
    if(attempt>=3)throw denied;
    try { pause.run(25L<<attempt); }
    catch(InterruptedException interrupted) {
     Thread.currentThread().interrupt();var error=new InterruptedIOException("Interrupted while replacing companion settings");
     error.initCause(interrupted);error.addSuppressed(denied);throw error;
    }
   }
  }
 }
 private PlayerBotSettingsFiles() {}
}
