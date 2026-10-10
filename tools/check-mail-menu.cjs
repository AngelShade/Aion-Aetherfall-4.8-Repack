// Real Chromium; all routes intercepted with simulated state. Never contacts GameServer.
const fs=require('fs'),path=require('path'),assert=require('assert');
const {chromium}=require(process.env.AION_PLAYWRIGHT_MODULE || 'playwright');
const media=path.join(__dirname,'../game-server/config/playerbots/media'),output=path.resolve(process.argv[2]);
(async()=>{
 fs.mkdirSync(output,{recursive:true});const browser=await chromium.launch({headless:true,...(process.env.AION_CHECK_BROWSER?{executablePath:process.env.AION_CHECK_BROWSER}:{})});
 const page=await browser.newPage({viewport:{width:1050,height:800}});let fixture,checks=0;const requests=[],errors=[];
 const check=(yes,why)=>{assert(yes,why);checks++;};page.on('pageerror',e=>errors.push(e.message));
 await page.route('**/*',async route=>{
  const url=new URL(route.request().url());assert.equal(url.hostname,'mail-check.invalid');
  if(url.pathname.endsWith('/state'))return route.fulfill({contentType:'application/json',body:JSON.stringify(fixture)});
  if(url.pathname.endsWith('/action')) {
   const request=Object.fromEntries(new URLSearchParams(route.request().postData()));requests.push(request);
   assert.equal(request.session_id,'offline-fixture');assert.equal(request.request,'offline-request');assert.equal(request.name,'MailBot');
   const bot=fixture.active[0];assert(bot.mailAvailable && !bot.closing && !bot.world);
   if(request.action==='maillist')bot.mailLoaded=true;
   else if(request.action==='mailread'){assert.equal(request.object,'888');bot.mail[0].unread=false;}
   else if(request.action==='mailtake'){assert.equal(request.object,'888');bot.mail[0].hasAttachments=false;bot.mail[0].item='';bot.mail[0].kinah=0;bot.mail[0].unread=false;bot.inventory.push({id:502,name:'Collected potions',count:17,slots:[],equipped:false,mailable:true});}
   else if(request.action==='maildelete'){assert.equal(request.object,'888');assert(!bot.mail[0].hasAttachments);bot.mail=[];bot.mailCount=0;}
   else if(request.action==='mailsenditem'){assert.equal(request.object,'501');bot.inventory=bot.inventory.filter(i=>i.id!==501);}
   else if(request.action==='mailsendkinah')assert.equal(request.kinah,'123');
   else assert.fail('Unexpected action '+request.action);
   fixture.notice='Native mail request completed.';return route.fulfill({contentType:'application/json',body:JSON.stringify(fixture)});
  }
  const file=url.pathname.split('/').pop();assert(['bots.html','bots.js','bots.css'].includes(file));
  return route.fulfill({contentType:file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'text/html',body:fs.readFileSync(path.join(media,file))});
 });
 const cases=[{available:true,world:false,closing:false,loaded:true},{available:false,world:false,closing:false,loaded:true},{available:true,world:false,closing:true,loaded:true},{available:true,world:true,closing:false,loaded:true},{available:true,world:false,closing:false,loaded:false},{available:true,world:false,closing:false,loaded:true,empty:true},{available:true,world:false,closing:false,loaded:true,sending:false}];
 for(const sample of cases) {
  fixture={owner:'Offline Owner',ownerLevel:40,enabled:true,generatedEnabled:true,generatedCount:1,generatedLimit:23,limit:23,recruitRemaining:22,teamKind:'Alliance',request:'offline-request',
   active:[{id:2,name:'MailBot',level:40,playerClass:'CLERIC',role:'HEALER',order:'FOLLOW',health:100,mana:100,status:'Idle',generated:true,temporary:true,questions:[],quests:[],world:sample.world,closing:sample.closing,
    mailAvailable:sample.available,mailReason:sample.available?'':'Bring both characters within talking distance of the same accessible regular mailbox.',mailSendEnabled:sample.sending!==false,mailLoaded:sample.loaded,mailCount:sample.empty?0:1,
    mail:sample.empty || !sample.loaded?[]:[{id:888,title:'<img src=x> Reward letter',sender:'Human player',message:'Your original mail body',unread:true,kinah:35,item:'Potions x17',hasAttachments:true}],
    inventory:[{id:501,name:'Earned potions',count:17,slots:[],equipped:false,mailable:true},{id:503,name:'Equipped armor',count:1,slots:['TORSO'],equipped:true,mailable:false},{id:504,name:'Bound quest item',count:1,slots:[],equipped:false,mailable:false}]}],
   roster:[{id:2,name:'MailBot',generated:true,ready:true,reserved:true}],classes:['CLERIC'],startingClasses:['PRIEST'],roles:['HEALER'],quests:[],presets:[],savedBots:[]};
  await page.goto('http://mail-check.invalid/market/companions/bots.html?session_id=offline-fixture');await page.locator('#owner-label').filter({hasText:fixture.owner}).waitFor();
  await page.locator('#active [data-bot-id="2"]').click();await page.locator('#bot-tab-mail').click();
  check((await page.locator('#bot-content').textContent()).includes(sample.world?'World bots retain':'Mailbox ('),'Mail tab communicates current ownership');
  if(sample.world){check(await page.locator('[data-action="mailtake"]').count()===0 && await page.locator('[data-action="mailsenditem"]').count()===0,'World mailbox and gifts hidden');continue;}
  const disabled=!sample.available || sample.closing;
  check(await page.locator('[data-action="maillist"]').isDisabled()===disabled,'Fresh mailbox access obeys postal/closing gates');
  check(await page.locator('[data-action="mailsenditem"]').count()===1,'Only eligible whole cube stack is selectable');
  check(await page.locator('[data-action="mailsenditem"]').isDisabled()===(disabled || sample.sending===false),'Outgoing access and enable gates applied');
  if(!sample.available)check((await page.locator('#bot-content').textContent()).includes('talking distance'),'Postal blocker visible');
  if(!sample.loaded){check((await page.locator('#bot-content').textContent()).includes('Refresh beside a mailbox'),'No background SQL load implied');await page.locator('[data-action="maillist"]').click();check(requests.at(-1).action==='maillist','Explicit refresh submitted');continue;}
  if(sample.empty){check((await page.locator('#bot-content').textContent()).includes('mailbox is empty'),'Empty mailbox understandable');continue;}
  check(await page.locator('[data-action="maildelete"]').isDisabled(),'Attachment-bearing letter cannot be deleted');
  check(await page.locator('[data-action="mailtake"]').isDisabled()===disabled,'Collect respects current native access');
  check(await page.locator('#bot-content img').count()===0,'Letter title renders as text, not HTML');
  if(!disabled && sample.sending!==false) {
   await page.locator('[data-action="mailread"]').click();await page.waitForFunction(()=>!document.querySelector('#bot-content').textContent.includes('Unread'));
   check(requests.at(-1).object==='888' && requests.at(-1).action==='mailread','Exact native letter ID submitted');
   await page.locator('[data-action="mailtake"]').click();await page.waitForFunction(()=>!document.querySelector('[data-action="maildelete"]').disabled);
   check(requests.at(-1).action==='mailtake' && requests.at(-1).object==='888','Both attachments requested using exact letter');
   check((await page.locator('#bot-content').textContent()).includes('Mailbox (1)'),'Collected letter retained for review');
   check(await page.locator('[data-action="mailtake"]').isDisabled(),'Collected attachments cannot be taken twice');
   await page.locator('[data-action="maildelete"]').click();await page.waitForFunction(()=>document.querySelector('#bot-content').textContent.includes('Mailbox (0)'));
   check(requests.at(-1).action==='maildelete','Only empty letter deletion submitted');
   await page.locator('[data-action="mailsenditem"]').first().click();await page.waitForFunction(()=>!document.querySelector('#bot-content').textContent.includes('Earned potions'));
   check(requests.at(-1).object==='501' && requests.at(-1).action==='mailsenditem','Exact whole item object selected; no caller-supplied receiver/count');
   const before=requests.length;await page.getByLabel('Kinah to request').fill('-1');await page.locator('[data-action="mailsendkinah"]').click();check(requests.length===before,'Negative money rejected locally');
   await page.getByLabel('Kinah to request').fill('9223372036854775808');await page.locator('[data-action="mailsendkinah"]').click();check(requests.length===before,'Native long overflow refused without rounding');
   await page.getByLabel('Kinah to request').fill('123');await page.locator('[data-action="mailsendkinah"]').click();await page.waitForFunction(()=>document.querySelector('[aria-label="Kinah to request"]').value==='');
   check(requests.at(-1).kinah==='123','Integer currency is sent as exact string');
   await page.screenshot({path:path.join(output,'companion-mail.png'),fullPage:true});
  }
 }
 check(errors.length===0,'No browser exceptions');fs.writeFileSync(path.join(output,'mail-checks.json'),JSON.stringify({checks,cases,requests,errors,nativeGameServerContact:false},null,2));console.log('OK: '+checks+' real-browser mail checks; simulated responses only');await browser.close();
})().catch(error=>{console.error(error);process.exit(1);});
