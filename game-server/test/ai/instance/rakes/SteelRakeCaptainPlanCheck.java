package ai.instance.rakes;

import java.util.*;

/** No world actors; actual production phase plan, boundaries and burst damage are exercised. */
public final class SteelRakeCaptainPlanCheck {
 private static int checks;
 private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
 public static void main(String[] args){
  var p=new SteelRakeCaptainPlan();check(!p.paused(),"idle");p.start();check(p.paused(),"opening movement blocks combat");p.arrived(0);check(p.stage()==SteelRakeCaptainPlan.Stage.ACTIVATING,"movement completes before activation");
  check(!p.retreat(1),"cannot overlap activation with retreat");p.activated();check(!p.paused(),"activation completion resumes combat");
  long now=0;Set<Integer> families=new HashSet<>();
  for(int phase=1;phase<=4;phase++){
   check(!p.retreat(SteelRakeCaptainPlan.THRESHOLDS[phase-1]+1),"above threshold "+phase);
   check(p.retreat(1),"burst damage queues next phase "+phase);check(p.phase()==phase,"phase never skipped");check(p.paused(),"retreat blocks attacks");
   check(p.wave(now+9000)==0,"waves wait for stair arrival");p.arrived(now);check(p.wave(now+8999)==0,"first wave boundary");
   int count=phase==4?3:2;
   for(int wave=1;wave<=count;wave++){
    now+=wave==1?9000:35000;check(p.wave(now)==wave,"wave "+wave);check(p.wave(now)==0,"wave cannot repeat");
    for(int id:SteelRakeCaptainPlan.waveNpcs(phase,wave))families.add(id);
   }
   check(!p.returning(now+20999),"return delay boundary");now+=21000;check(p.returning(now),"return phase");check(p.paused(),"returning boss remains paused");p.arrived(now);p.activated();check(!p.paused(),"completed return resumes");
  }
  check(!p.retreat(0),"no fifth phase");check(families.containsAll(Set.of(281181,281182,281183,281184,281185,281186,281187,281188)),"all native add families used");
  check(!p.curse(51),"root HP gate");check(p.curse(50),"root phase");check(!p.curse(1),"root does not repeat");check(p.enhance(25),"enhance phase");check(!p.enhance(1),"enhance once");
  check(p.rootPending(),"root remains pending until completion");check(!p.combo(25,now),"capture waits for root completion");p.start();p.arrived(now);p.activated();check(p.rootPending() && p.enhanced(),"reactivation preserves queued root and enhancement");p.rooted();check(!p.rootPending(),"completed root acknowledged");
  check(p.combo(25,now),"capture sequence starts");check(p.paused(),"capture movement pauses normal attacks");p.arrived(now);check(p.stage()==SteelRakeCaptainPlan.Stage.PULL,"arrival before pull");p.pulled();check(p.stage()==SteelRakeCaptainPlan.Stage.BLAST,"pull completion before blast");p.blasted();check(!p.paused(),"blast resumes combat");check(!p.combo(25,now+29999),"combo cooldown");check(p.combo(25,now+30000),"later combo supported");
  check(p.comboSequence()==2,"each capture has a distinct completion token");
  p.reset();check(p.phase()==0 && p.stage()==SteelRakeCaptainPlan.Stage.IDLE,"reset removes progress");check(!p.enhanced() && !p.rootPending() && p.comboSequence()==0,"reset clears enhancement root and combo tokens");check(p.wave(Long.MAX_VALUE)==0,"no stale wave after reset");p.start();p.arrived(0);p.activated();check(p.curse(50)&&p.enhance(25),"new fight resets one-time phases");
  System.out.println("OK: "+checks+" production captain phase checks, no live actors");
 }
}
