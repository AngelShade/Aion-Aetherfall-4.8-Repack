import ai.instance.fireTemple.KromedeEncounter;

public class KromedeEncounterCheck {
 private static int checks;
 private static void check(boolean pass,String name){checks++;if(!pass)throw new AssertionError(name);}
 public static void main(String[] args){
  for(int boundary=0;boundary<2;boundary++)for(int hp:new int[]{100,50,25,1}) {
   final boolean upper=boundary==1;
   var fight=new KromedeEncounter((a,b)->upper?b:a);
   check(fight.next(0,hp)==17047,"opening Cry even on instant low HP");
   check(fight.next(10000,hp)==17047,"failed opening is retried");
   fight.complete(17047,10000,hp);
   check(fight.next(10299,hp)==0,"opening recovery");
   check(fight.next(10300,hp)==16847,"Cry then Impact");
   fight.complete(16847,11000,hp);
   if(hp<=25){
    check(fight.next(11300,hp)==16674,"pre-climax Verdict");fight.complete(16674,14000,hp);
    check(fight.next(16499,hp)==0,"climax pause");
    check(fight.next(16500,hp)==17056,"one lethal Struggle");fight.complete(17056,19000,hp);
    check(fight.next(19300,hp)==16674,"post-Struggle Verdict");fight.complete(16674,22000,hp);
    long due=22000+(upper?27000:23000);
    check(fight.next(due-1,hp)==0,"fast interval lower bound");
    check(fight.next(due,hp)==16674,"normal cycle resumes");fight.complete(16674,due+2500,hp);
    check(!fight.isSequence(),"no repeated Struggle phase");
   }else{
    long due=11000+(hp==100?(upper?35000:30000):(upper?27000:23000));
    check(fight.next(due-1,hp)==0,"Verdict interval boundary");
    check(fight.next(due,hp)==16674,"scheduled Verdict");
    // Damage crosses 25% during a cast; that cast completes before the combo starts.
    fight.complete(16674,due+2500,24);
    check(fight.next(due+2800,24)==16674,"threshold during cast queues combo");
   }
   fight.reset();check(fight.next(999999,100)==17047,"fresh opening after reset");
  }
  var fight=new KromedeEncounter((a,b)->a);fight.next(0,100);fight.complete(17047,2500,100);fight.next(2800,100);fight.complete(16847,3300,100);
  check(fight.next(4000,50)==0,"half-health does not cast instantly");
  check(fight.next(26999,50)==0,"half-health cadence boundary");
  check(fight.next(27000,50)==16674,"half-health shortens already pending timer");
  System.out.println("OK: "+checks+" encounter decisions: retries, opening, cadence, single climax, cast threshold and reset.");
 }
}
