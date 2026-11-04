package com.aionemu.gameserver.services.playerbot;

/** Admission decisions only: no server, world bots or real heap pressure. */
public final class PlayerBotHeapGuardCheck {
 private static int checks;
 private static void expect(boolean value,String what){checks++;if(!value)throw new AssertionError(what);}
 public static void main(String[] args) {
  expect(!PlayerBotHeapGuard.holding(false,69),"Admissions stay open below the hold line");
  expect(PlayerBotHeapGuard.holding(false,70),"Admissions pause at the hold line");
  expect(PlayerBotHeapGuard.holding(true,60),"A paused population does not resume at the release line");
  expect(!PlayerBotHeapGuard.holding(true,59),"Admissions resume once the heap has real room");
  expect(!PlayerBotHeapGuard.holding(false,65),"Between the lines an open population stays open");
  expect(PlayerBotHeapGuard.holding(true,65),"Between the lines a paused population stays paused");

  expect(PlayerBotHeapGuard.target(2733,1567,false)==2733,"An open guard leaves the requested target alone");
  expect(PlayerBotHeapGuard.target(2733,1567,true)==1567,"A paused guard allows no population growth");
  expect(PlayerBotHeapGuard.target(1500,1567,true)==1500,"A paused guard never raises a lower target");
  expect(PlayerBotHeapGuard.target(2733,1200,true)==1200,"Departures while paused lower the population for good");
  expect(PlayerBotHeapGuard.target(2733,-1,true)==0,"An invalid count cannot produce a negative target");

  expect(PlayerBotHeapGuard.percent(2747L<<20,3924L<<20)==70,"The reporter's 3.9 GB heap pauses at about 2.7 GB");
  expect(PlayerBotHeapGuard.percent(1,0)==0 && PlayerBotHeapGuard.percent(-1,100)==0,"An unknown heap never pauses admissions");
  expect(PlayerBotHeapGuard.percent(200,100)==100,"Usage above the maximum is clamped");
  System.out.println("PlayerBotHeapGuardCheck passed "+checks+" checks");
 }
}
