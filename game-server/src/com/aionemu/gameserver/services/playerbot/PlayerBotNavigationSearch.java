package com.aionemu.gameserver.services.playerbot;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.WeakHashMap;

/** Fair bounded search turns; the scheduler promptly polls the nominated actor. */
final class PlayerBotNavigationSearch {
 private static final class Request {
  final WeakReference<Object> token;
  long nominated=-1;
  boolean cancelled;
  Request(Object token){this.token=new WeakReference<>(token);}
 }
 private final ArrayDeque<Request> queue=new ArrayDeque<>();
 private final WeakHashMap<Object,Request> waiting=new WeakHashMap<>();
 private long nextPermit;
 private Request head(long now){
  while(!queue.isEmpty()){
   var request=queue.peekFirst();Object owner=request.token.get();
   if(!request.cancelled && owner!=null && (request.nominated<0 || now-request.nominated<=5000)){
    if(request.nominated<0)request.nominated=now;return request;
   }
   queue.removeFirst();if(owner!=null && waiting.get(owner)==request)waiting.remove(owner);
  }
  return null;
 }
 synchronized boolean acquire(Object token,long now){
  if(token==null)throw new IllegalArgumentException("Missing navigation search token");
  head(now);var request=waiting.get(token);
  if(request==null){request=new Request(token);waiting.put(token,request);queue.addLast(request);}
  if(head(now)!=request || now<nextPermit)return false;
  queue.removeFirst();waiting.remove(token);nextPermit=now+100;return true;
 }
 synchronized boolean isTurn(Object token,long now){var request=head(now);return request!=null && request.token.get()==token;}
 synchronized boolean waiting(Object token){return waiting.containsKey(token);}
 synchronized void cancel(Object token){var request=waiting.remove(token);if(request!=null)request.cancelled=true;}
 PlayerBotNavigationSearch(){}
}
