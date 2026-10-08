package ai.instance.rakes;

import java.util.List;

/** Native encounter phases, isolated from actors so resets and thresholds are checkable offline. */
final class SteelRakeCaptainPlan {
 enum Stage { IDLE, OPENING, ACTIVATING, COMBAT, RETREAT, WAVES, RETURN, COMBO_MOVE, PULL, BLAST }
 record Point(float x,float y,float z){}
 // First five points of native walker 055B73AA897B0E07D287848D3AD6EBCABB7DD93D.
 static final List<Point> STAIRS=List.of(new Point(427.45f,509.88f,1075.3801f),new Point(423.56f,509.89f,1074.3944f),new Point(417.51f,509.98f,1071.8457f),new Point(412.21f,510.09f,1071.8457f),new Point(403.88f,510.13f,1071.736f));
 static final int[] THRESHOLDS={80,55,30,5};
 private volatile Stage stage=Stage.IDLE;
 private int phase,waves;
 private long due;
 private boolean curse,rootPending,enhanced;
 private int comboSequence;
 private long nextCombo;
 Stage stage(){return stage;}
 boolean paused(){return stage!=Stage.IDLE && stage!=Stage.COMBAT;}
 int phase(){return phase;}
 boolean enhanced(){return enhanced;}
 boolean rootPending(){return rootPending;}
 void rooted(){rootPending=false;}
 int comboSequence(){return comboSequence;}
 int waveCount(){return phase==4?3:2;}
 void start(){stage=Stage.OPENING;}
 void arrived(long now){switch(stage){case OPENING,RETURN->stage=Stage.ACTIVATING;case RETREAT->{stage=Stage.WAVES;waves=0;due=now+9000;}case COMBO_MOVE->stage=Stage.PULL;default->{}}}
 void activated(){if(stage==Stage.ACTIVATING)stage=Stage.COMBAT;}
 boolean retreat(int hp){if(stage!=Stage.COMBAT || phase==THRESHOLDS.length || hp>THRESHOLDS[phase])return false;phase++;stage=Stage.RETREAT;return true;}
 int wave(long now){if(stage!=Stage.WAVES || now<due || waves>=waveCount())return 0;waves++;due=now+(waves==waveCount()?21000:35000);return waves;}
 boolean returning(long now){if(stage!=Stage.WAVES || waves!=waveCount() || now<due)return false;stage=Stage.RETURN;return true;}
 boolean curse(int hp){if(stage!=Stage.COMBAT || hp>50 || curse)return false;curse=rootPending=true;return true;}
 boolean enhance(int hp){if(stage!=Stage.COMBAT || hp>25 || enhanced)return false;enhanced=true;return true;}
 boolean combo(int hp,long now){if(stage!=Stage.COMBAT || rootPending || hp>25 || now<nextCombo)return false;nextCombo=now+30000;comboSequence++;stage=Stage.COMBO_MOVE;return true;}
 void pulled(){if(stage==Stage.PULL)stage=Stage.BLAST;}
 void blasted(){if(stage==Stage.BLAST)stage=Stage.COMBAT;}
 void reset(){stage=Stage.IDLE;phase=waves=comboSequence=0;due=nextCombo=0;curse=rootPending=enhanced=false;}
 static int[] waveNpcs(int phase,int wave){
  if(phase==4)return wave==1?new int[]{281185,281186,281188}:new int[]{281185,281186,281187,281188};
  if(phase==1)return wave==1?new int[]{281184,281181}:new int[]{281183,281184,281181};
  if(phase==2)return wave==1?new int[]{281184,281181}:new int[]{281182,281182,281181};
  if(phase==3)return wave==1?new int[]{281182,281182}:new int[]{281187,281181};
  throw new IllegalArgumentException("Unknown captain phase: "+phase);
 }
}
