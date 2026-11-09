package com.aionemu.gameserver.services.playerbot;

/**
 * Notices a travelling bot that paces back and forth on one spot (owner, 9 Nov 2026: "moving in all directions
 * for short feet, bouncing back"). It keeps a few seconds of positions and reports when the bot covered real
 * distance, turned round again and again, and still ended up where it started without getting closer to its goal.
 * Pure: no World, geometry or tasks, so the check can replay any recorded track.
 */
final class PlayerBotMotionWatch {
 static final long WINDOW_MS=8000,SAMPLE_MS=250;
 static final double MIN_PATH=7,MAX_NET=2.5,MIN_GOAL_GAIN=2,SEGMENT=.6;
 static final int MIN_REVERSALS=3;
 private final long[] t=new long[(int)(WINDOW_MS/SAMPLE_MS)+2];
 private final float[] x=new float[t.length],y=new float[t.length];
 private final double[] goal=new double[t.length];
 private int head,size;
 private int world;

 void clear(){head=size=0;}

 /** Feed the bot's position each planner turn; true once when pacing is recognised, then the window starts again. */
 boolean observe(long now,int map,float px,float py,double goalDistance) {
  if(map!=world){world=map;clear();}
  if(size>0){int last=(head+size-1)%t.length;if(now-t[last]<SAMPLE_MS)return false;}
  if(size==t.length){head=(head+1)%t.length;size--;}
  int slot=(head+size)%t.length;t[slot]=now;x[slot]=px;y[slot]=py;goal[slot]=goalDistance;size++;
  while(size>1 && now-t[head]>WINDOW_MS){head=(head+1)%t.length;size--;}
  if(size<4 || now-t[head]<WINDOW_MS*3/4)return false;
  if(!pacing())return false;
  clear();return true;
 }

 private boolean pacing() {
  double path=0;int reversals=0;double lx=0,ly=0;boolean haveLeg=false;
  // Collapse samples into legs of at least SEGMENT metres so standing jitter is not a turn.
  float ax=x[head],ay=y[head];
  for(int i=1;i<size;i++) {
   int k=(head+i)%t.length;double dx=x[k]-ax,dy=y[k]-ay,len=Math.hypot(dx,dy);
   if(len<SEGMENT)continue;
   path+=len;
   if(haveLeg && (dx*lx+dy*ly)/(len*Math.hypot(lx,ly))<-.5)reversals++; // turned more than 120 degrees
   lx=dx;ly=dy;haveLeg=true;ax=x[k];ay=y[k];
  }
  int first=head,last=(head+size-1)%t.length;
  double net=Math.hypot(x[last]-x[first],y[last]-y[first]);
  double gained=goal[first]-goal[last];
  return path>=MIN_PATH && net<=MAX_NET && reversals>=MIN_REVERSALS && gained<MIN_GOAL_GAIN;
 }
}
