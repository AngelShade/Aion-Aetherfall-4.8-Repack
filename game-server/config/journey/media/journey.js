(function(){
'use strict';
var tr=window.AetherfallWeb.text,fmt=window.AetherfallWeb.format;

function journeyNotice(text){
 var m=/^Welcome to (Sanctum|Pandaemonium)! Collect your skipped (Poeta|Ishalgen) quest rewards from the mailbox\. Speak to (Leah|Heimdall) for A Ceremony in \1 and earn its rewards when you complete it, then see (Polyidus|Doman) for Dispatch to (Verteron|Altgard)\.$/.exec(text);
 if(m)return fmt('Welcome to {capital}! Collect your skipped {starter} quest rewards from the mailbox. Speak to {guide} for A Ceremony in {capital} and earn its rewards when you complete it, then see {travelGuide} for Dispatch to {onward}.',{capital:tr(m[1]),starter:tr(m[2]),guide:tr(m[3]),travelGuide:tr(m[4]),onward:tr(m[5])});
 m=/^Title earned: (.+)\.$/.exec(text);
 if(m)return fmt('Title earned: {title}.',{title:tr(m[1])});
 m=/^Collect existing mail first\. The full reward bundle needs (\d+) free letters\.$/.exec(text);
 if(m)return fmt('Collect existing mail first. The full reward bundle needs {count} free letters.',{count:m[1]});
 return tr(text);
}
var state=null,selected='',busy=false,loadStarted=new Date().getTime(),sessionId=queryToken();
function el(id){return document.getElementById(id);}
function hidden(id,value){if(value)el(id).setAttribute('hidden','hidden');else el(id).removeAttribute('hidden');}
function nativeControl(show){if(window.AionObject&&typeof window.AionObject.JourneyVisibility==='function')window.AionObject.JourneyVisibility(show?1:0);}
function notice(text){el('notice').textContent=el('next-notice').textContent=journeyNotice(text||'');}
function encode(fields){var parts=[],k;for(k in fields)if(fields.hasOwnProperty(k))parts.push(encodeURIComponent(k)+'='+encodeURIComponent(fields[k]));return parts.join('&');}
function queryToken(){var m=/(?:[?&])session_id=([^&]*)/.exec(location.search);return m?decodeURIComponent(m[1]):'';}
function token(){return sessionId;}
window.JourneySessionReady=function(value){if(typeof value==='string'&&/^[a-f0-9]{32}$/.test(value)&&!/^[0]+$/.test(value))sessionId=value;};
function refreshSession(clear){if(window.AionObject&&typeof window.AionObject.JourneySession==='function'){if(clear)sessionId='';window.AionObject.JourneySession();return true;}return false;}
function presentation(data){
 var j=data.journey;
 var places={starter:tr(j.starter),capital:tr(j.capital),onward:tr(j.onward)};
 el('starter-title').textContent=fmt('Begin in {starter}',places);el('play').textContent=fmt('Play {starter}',places);
 el('capital-title').textContent=el('confirm').textContent=fmt('Ascend to {capital}',places);
 el('starter-description').textContent=tr(j.description);
 el('quest-count').textContent=fmt('{count} {starter} quests completed',{count:data.quests,starter:places.starter});
 el('ceremony-line').textContent=fmt('Complete the {capital} ceremony to earn its rewards',places);
 el('review-description').textContent=fmt('You will become a level 10 Daeva in {capital}. Your skipped quests will be completed, their rewards sent to your mailbox, and your class-specific Dispatch to {onward} quest added. A Ceremony in {capital} remains playable and grants its rewards when completed.',places);
 el('story-warning').textContent=fmt('This completes the {starter} story for this character.',places);
 el('starter-art').style.backgroundImage="url('/journey/media/"+j.starterArt+".jpg')";
 el('capital-art').style.backgroundImage="url('/journey/media/"+j.capitalArt+".jpg')";
}
function finale(data){hidden('proceed',!data.continuationOpen);el('close').className=data.continuationOpen?'outline':'';}
var EMBLEMS={earn:0,recruit:1,manage:2,help:3,fight:4,promote:5};
function companionsReady(){return !!(window.AionObject&&typeof window.AionObject.JourneyCompanions==='function');}
function span(parent,name,text){var s=document.createElement('span');s.className=name;s.textContent=text;parent.appendChild(s);return s;}
function chapterTask(c){
 var template;
 if(c.id==='earn')template=c.goal===1?'Earn a Recruitment Ticket from challenging instances or PvP wins.':'Earn {count} Recruitment Tickets from challenging instances or PvP wins.';
 if(c.id==='help')template=c.goal===1?"Answer a bot's call for help and finish an objective together.":"Answer a bot's call for help and finish {count} objectives together.";
 if(c.id==='fight')template=c.goal===1?'Finish a challenging run and see the ending through.':'Finish {count} challenging runs and see the ending through.';
 return template?fmt(template,{count:c.goal}):tr(c.task);
}
function chapterRow(list,number,index,c){
 var row=document.createElement('div'),body=document.createElement('div'),emblem=span(row,'next-emblem',''),meter;
 row.className='next-row'+(c.done?' done':'');body.className='next-body';
 emblem.style.backgroundPosition=-(index%3*48)+'px '+-(Math.floor(index/3)*48)+'px';
 span(body,'next-title',number+'. '+tr(c.title));span(body,'next-task',chapterTask(c));row.appendChild(body);
 span(row,'next-state',c.done?tr('Complete'):c.value+' / '+c.goal);
 if(!c.done){meter=document.createElement('div');meter.className='next-meter';span(meter,'next-fill','').style.width=Math.floor(100*c.value/c.goal)+'%';row.appendChild(meter);}
 list.appendChild(row);
}
function rewardRow(list,r){
 var row=document.createElement('div'),b;row.className='next-reward';
 span(row,'next-title',tr(r.title));
 if(r.ready&&!r.claimed){b=document.createElement('button');b.className='primary';b.textContent=tr('Claim title');b.onclick=function(){selected=r.id;choose('claim');};row.appendChild(b);}
 else span(row,'next-state',tr(r.claimed?'Claimed':r.id==='final'?'Every chapter':'First three chapters'));
 list.appendChild(row);
}
function continuation(data){
 document.body.className='continued';hidden('intro',true);hidden('paths',true);hidden('classes',true);hidden('complete',true);hidden('choice-hint',true);hidden('continuation',false);notice('');
 var story=data.chapters||{chapters:[],rewards:[]},list=el('chapters'),rewards=el('rewards'),numbers=['I','II','III','IV','V','VI'],done=0,i;
 list.innerHTML='';rewards.innerHTML='';
 for(i=0;i<story.chapters.length;i++){chapterRow(list,numbers[i],EMBLEMS[story.chapters[i].id]||0,story.chapters[i]);if(story.chapters[i].done)done++;}
 for(i=0;i<story.rewards.length;i++)rewardRow(rewards,story.rewards[i]);
 el('chapter-count').textContent=fmt('{done} of {total} chapters complete',{done:done,total:story.chapters.length});
 if(story.chapters.length&&done===story.chapters.length)el('next-lead').textContent=tr('Your chapter is written. Your companions stand with you, and the road goes on.');
 hidden('open-companions',!companionsReady());hidden('next-hint',companionsReady());
}
function request(method,path,fields,done,timeout){
 var xhr=new XMLHttpRequest(),settled=false,timer;timeout=timeout||20000;
 function finish(status,data){if(settled)return;settled=true;clearTimeout(timer);done(status,data);}
 function expired(){if(settled)return;finish(0,{error:'The request timed out. Reopen the menu to check your saved choice.'});xhr.abort();}
 xhr.open(method,path,true);xhr.timeout=timeout;
 if(method==='POST')xhr.setRequestHeader('Content-Type','application/x-www-form-urlencoded');
 xhr.onreadystatechange=function(){if(xhr.readyState!==4||settled)return;var data;try{data=JSON.parse(xhr.responseText);}catch(e){data={error:'The journey menu could not connect. Reopen it to try again.'};}finish(xhr.status,data);};
 xhr.ontimeout=expired;
 // Older client WebKit builds can ignore XMLHttpRequest.timeout.
 timer=setTimeout(expired,timeout);
 xhr.send(method==='POST'?encode(fields):null);
}
// The painted class portraits the Companions cards use, cell by cell.
var PORTRAITS={TEMPLAR:0,GLADIATOR:1,ASSASSIN:2,RANGER:3,SORCERER:4,SPIRIT_MASTER:5,CLERIC:6,CHANTER:7,GUNNER:8,RIDER:9,BARD:10};
function classTile(button,c){
 var key=String(c.id).toUpperCase().replace(/ /g,'_'),cell=PORTRAITS.hasOwnProperty(key)?PORTRAITS[key]:11,art=document.createElement('i'),name=document.createElement('span');
 art.style.backgroundPosition=(cell%4)/3*100+'% '+Math.floor(cell/4)/2*100+'%';name.textContent=tr(c.name);button.appendChild(art);button.appendChild(name);
}
function reveal(){if(document.body.className==='loading')document.body.className='';}
function load(){
 var remaining=12000-(new Date().getTime()-loadStarted);
 if(remaining<=0){reveal();notice('The journey menu could not connect. Close it and reopen Choose Your Journey.');return;}
 if(!token()&&refreshSession()){setTimeout(load,100);return;}
 request('GET','/journey/state?session_id='+encodeURIComponent(token()),{},function(status,data){
  if(status!==200){if(status===403)refreshSession(true);if(new Date().getTime()-loadStarted<12000){setTimeout(load,300);return;}reveal();notice('The journey menu could not connect. Close it and reopen Choose Your Journey.');return;}
  if(data&&data.error){reveal();notice(data.error);return;}
  state=data;presentation(data);var j=data.journey;
  if(!(!data.eligible&&data.continuationUnlocked))reveal();
  if(!data.eligible&&data.continuationUnlocked){continuation(data);return;}
  if(!data.eligible){
   hidden('paths',true);hidden('complete',false);finale(data);
   el('complete-title').textContent=data.welcome?fmt('Welcome to {capital}',{capital:tr(j.capital)}):tr('Your journey is underway');
   var fields={starter:tr(j.starter),capital:tr(j.capital),guide:tr(j.guide),travelGuide:tr(j.travelGuide),onward:tr(j.onward),
    ceremony:tr(data.ceremonyRewardsMailed?'Its rewards were already included in your earlier mail bundle.':'Complete the ceremony to earn its rewards.')};
   el('receipt').textContent=data.decision==='SKIP'?fmt('Your {starter} skip has already been applied. Collect your skipped quest rewards from the mailbox. Speak to {guide} for A Ceremony in {capital}. {ceremony} Then see {travelGuide} for Dispatch to {onward}.',fields):
    data.continuationOpen?tr('Your Ascension is complete. Proceed to see what comes next.'):fmt('This choice is available to starting-class characters in {starter}, before Ascension.',fields);
   if(data.welcome)nativeControl(true);return;
  }
  el('greeting').textContent=fmt('{character}, how will your story begin?',{character:data.name});
  if(data.prompt)nativeControl(true);
 },Math.min(2500,remaining));
}
function choose(choice){
 if(busy||!state)return;busy=true;el('play').disabled=el('confirm').disabled=el('proceed').disabled=true;notice('Preparing your journey...');
 request('POST','/journey/action',{session_id:token(),request:state.request,choice:choice,'class':selected},function(status,data){
  busy=false;el('play').disabled=el('confirm').disabled=el('proceed').disabled=false;
  if(status!==200||data.error){notice(data.error||'The journey menu could not connect. Reopen it to try again.');return;}
  state=data;presentation(data);
  if(choice==='proceed'||choice==='claim'){continuation(data);notice(data.notice);return;}
  hidden('paths',true);hidden('classes',true);hidden('complete',false);finale(data);notice('');
  el('complete-title').textContent=choice==='skip'?fmt('Welcome to {capital}',{capital:tr(data.journey.capital)}):tr('Your story begins');el('receipt').textContent=journeyNotice(data.notice||'');
  if(choice==='play'||choice==='ack')nativeControl(false);else if(data.welcome)nativeControl(true);
 });
}
el('play').onclick=function(){choose('play');};
el('skip').onclick=function(){if(!state)return;hidden('paths',true);hidden('classes',false);notice('');var list=el('class-list');list.innerHTML='';
 for(var i=0;i<state.classes.length;i++)(function(c){var b=document.createElement('button');classTile(b,c);b.onclick=function(){selected=c.id;var buttons=list.getElementsByTagName('button');for(var j=0;j<buttons.length;j++)buttons[j].className='';b.className='selected';el('selected-name').textContent=tr(c.name);hidden('review',false);};list.appendChild(b);})(state.classes[i]);
};
el('back').onclick=function(){if(busy)return;hidden('classes',true);hidden('review',true);hidden('paths',false);selected='';};
el('confirm').onclick=function(){if(selected)choose('skip');};el('close').onclick=function(){if(state&&state.welcome)choose('ack');else nativeControl(false);};
el('proceed').onclick=function(){choose('proceed');};el('next-close').onclick=function(){nativeControl(false);};
el('open-companions').onclick=function(){if(companionsReady())window.AionObject.JourneyCompanions();};
load();
})();
