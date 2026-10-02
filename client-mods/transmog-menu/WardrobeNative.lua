-- Native Aion Wardrobe. Only bounded data crosses the native mailbox bridge.
WardrobeNative = {query={category='All',filter='unlocked',q='',page=1,target=0,skin=0},drafts={},skins={},cache={},view='collection',localPage=1,sequence=0,previewSequence=0}
local W=WardrobeNative
local categories={'All','Weapons','Armor','Costumes','Headwear','Shields','Wings'}
local filters={'unlocked','owned','all','locked'}
local slots={{1,0,0},{2,1,0},{4,0,1},{2048,1,1},{8,0,2},{16,1,2},{4096,0,3},{32768,1,3},{32,0,4}}
local weapons={SWORD=true,GREATSWORD=true,DAGGER=true,MACE=true,ORB=true,SPELLBOOK=true,POLEARM=true,STAFF=true,BOW=true,HARP=true,GUN=true,CANNON=true,KEYBLADE=true}
local function widget(name) return _G['Wardrobe'..name] end
local function text(name,value) widget(name):SetText(tostring(value or '')) end
local function show(name,value) if value then widget(name):Show() else widget(name):Hide() end end
local function enable(name,value) widget(name):Enable(value and not W.busy or false) end
-- SetWidgetRect applies the client's UI scale. Layout uses physical pixels,
-- matching the native preview and fullscreen host, so convert exactly once.
local function setrect(object,x,y,w,h)
    local scale=W.scale or 1
    object:SetRect(math.floor(x)/scale,math.floor(y)/scale,math.floor(w)/scale,math.floor(h)/scale)
end
local function rect(name,x,y,w,h) setrect(widget(name),x,y,w,h) end
local function esc(s) return tostring(s or ''):gsub('&','&amp;'):gsub('<','&lt;'):gsub('>','&gt;'):gsub('"','&quot;') end
local function url(s) return tostring(s):gsub('[^%w%-_%.~]',function(c) return string.format('%%%02X',c:byte()) end) end
-- The client's GetTime returns formatted calendar text, not elapsed seconds.
-- Use this addon's timer for debounce and short-lived browsing caches.
local function now() return W.clock or 0 end
local function status(message)
    text('Status',message)
    if not W.state then text('Empty',message);show('Empty',true) end
end
local function form(t) local fields={};for k,v in pairs(t) do fields[#fields+1]=url(k)..'='..url(v) end;return table.concat(fields,'&') end
local function target() if W.state then for _,i in ipairs(W.state.equipment) do if i.object==W.query.target then return i end end end end
local function compatible(item,skin)
    if not item or not skin then return false end
    if weapons[item.group] or weapons[skin.group] then return weapons[item.group] and weapons[skin.group] and item.group==skin.group end
    local a,b=item.slots,skin.slots
    while a>0 and b>0 do if a%2==1 and b%2==1 then return true end;a=math.floor(a/2);b=math.floor(b/2) end
    return false
end
local function icon(name,item)
    local path=WardrobeIcons and WardrobeIcons[item]
    show(name,path~=nil)
    if path then
        W.iconPaths=W.iconPaths or {}
        if W.iconPaths[name]~=path then
            widget(name):SetUIImage(0,path);widget(name):SetFlag('not_active');W.iconPaths[name]=path
        end
        W.iconRequests[#W.iconRequests+1]=name..':'..tostring(item)
    end
end
local function preview()
    if not W.state then return end
    local items,count,locked={},0,0;local wings=false
    for _,i in ipairs(W.state.equipment) do
        local d=W.drafts[i.object]
        if i.equipped and i.slot<131072 or d then items[#items+1]=tostring(d and (d.skin~=0 and d.skin or i.item) or i.skin) end
        if d then count=count+1;if not d.unlocked then locked=locked+1 end;if i.group=='WING' then wings=true end end
    end
    if W.previewWing then items[#items+1]=tostring(W.previewWing);wings=true end
    W.previewSequence=W.previewSequence+1
    widget('NativePreview'):SetText(W.previewSequence..'|'..table.concat(items,',')..'|'..(wings and '1' or '0'))
    text('Draft',count==0 and 'Current equipment' or count..' appearance changes'..(locked>0 and ' | '..locked..' locked' or ''))
    enable('Apply',count>0 and locked==0);show('RemoveLocked',locked>0)
end
local function control(name,value) widget('NativeControl'):SetText(name..'|'..tostring(value or 0)) end
function WardrobeNative_Layout(width,height)
    if width<640 or height<480 then return end
    W.scale=GetUIScale and GetUIScale() or 1
    if type(W.scale)~='number' or W.scale<=0 then W.scale=1 end
    setrect(PrivateWardrobe,0,0,width,height)
    W.width,W.height=width,height;W.left=math.max(300,math.min(620,math.floor(width*.38)))
    local left,right,rx=W.left,width-W.left-30,W.left+18
    rect('LeftPanel',12,78,left-12,height-112);rect('RightPanel',rx,78,right,height-112)
    rect('Character',24,35,left-36,28);rect('Counts',rx,35,right-92,28);rect('Refresh',width-104,35,88,28)
    rect('Target',24,81,left-40,25);rect('Draft',24,height-155,left-40,23)
    local top,bottom=112,height-175
    rect('PreviewBounds',84,top,left-160,math.max(100,bottom-top))
    local step=math.min(88,math.max(50,(height-315)/5))
    for _,s in ipairs(slots) do
        local mask,side,row=s[1],s[2],s[3];local x,y
        x=side==0 and 22 or left-66;y=112+row*step
        rect('Slot'..mask,x,y,44,44);rect('SlotIcon'..mask,x+2,y+2,40,40)
        rect('SlotSelected'..mask,x,y,44,44)
    end
    local controls={'Left','Right','Zoom','Helmet','Combat','Wings'}
    for i,n in ipairs(controls) do rect(n,22+(i-1)*(left-38)/6,height-127,(left-50)/6,25) end
    rect('Reset',24,height-94,110,26);rect('Apply',left-150,height-94,130,26)
    rect('RemoveLocked',24,height-62,left-44,25);rect('MouseHint',24,height-34,left-44,20)
    local tabWidth=math.min(145,(right-18)/3)
    for i,n in ipairs({'Collection','Outfits','Inventory'}) do rect(n,rx+10+(i-1)*tabWidth,87,tabWidth-3,28) end
    rect('Search',rx+10,123,right-250,28);rect('Clear',rx+right-233,123,68,28);rect('Filter',rx+right-158,123,148,28)
    for i in ipairs(categories) do rect('Category'..i,rx+10+(i-1)*(right-20)/7,159,(right-30)/7,27) end
    rect('Results',rx+10,193,right-150,22);rect('ClearTarget',rx+right-152,191,142,25)
    rect('Scroll',rx+10,222,right-20,math.max(65,height-451))
    W.columns=math.max(1,math.min(4,math.floor((right-38)/260)));W.cardWidth=math.floor((right-38)/W.columns)
    rect('Rows',0,0,right-40,math.ceil(36/W.columns)*66)
    for i=1,36 do
        local x,y=((i-1)%W.columns)*W.cardWidth,math.floor((i-1)/W.columns)*66
        rect('Row'..i,x,y,W.cardWidth-5,62);rect('Icon'..i,x+8,y+10,40,40)
        rect('Selected'..i,x+6,y+8,44,44)
        rect('Name'..i,x+58,y+8,W.cardWidth-70,27);rect('State'..i,x+58,y+36,W.cardWidth-70,20)
    end
    rect('Empty',rx+24,245,right-48,40)
    rect('Previous',rx+10,height-222,90,26);rect('Next',rx+right-100,height-222,90,26);rect('Page',rx+110,height-222,right-220,25)
    rect('Details',rx+10,height-186,right-20,95)
    for i,n in ipairs({'TryOn','Unlock','Restore'}) do rect(n,rx+10+(i-1)*(right-20)/3,height-81,(right-26)/3,29) end
    rect('SaveOutfit',rx+10,height-81,170,29);rect('DeleteOutfit',rx+190,height-81,130,29)
    rect('Status',rx+10,height-41,right-20,27)
    setrect(WardrobeConfirm,(width-460)/2,(height-300)/2,460,300)
    rect('ConfirmText',14,30,432,142);rect('ConfirmError',14,178,432,32)
    rect('OutfitName',18,139,422,28);rect('UnlockSource',18,140,422,28)
    rect('ConfirmCancel',234,237,95,30);rect('ConfirmAccept',337,237,105,30)
    if W.state then preview() end
end
local function receive(result,action)
    if result.error then status(result.error);text('ConfirmError',result.error);return end
    W.state=result;W.query.page=result.page;W.query.target=result.target
    if action then W.cache={} end
    for _,s in ipairs(result.skins) do W.skins[s.item]=s end
    for _,s in ipairs(result.outfitAppearances or {}) do W.skins[s.item]=s end
    if result.selected then W.skins[result.selected.item]=result.selected end
    if action then
        if action=='applyChanges' or action=='applyOutfit' or action=='restore' then W.drafts={};W.previewWing=nil end
        for _,d in pairs(W.drafts) do if W.skins[d.skin] then d.unlocked=W.skins[d.skin].unlocked end end
        WardrobeNative_CloseConfirm()
    end
    WardrobeNative_Render();status(result.notice~='' and result.notice or 'Wardrobe ready.')
    if action or not W.started then W.started=true;preview() end
end
function WardrobeNative_Fetch(extra)
    if W.busy then return end
    W.sequence=W.sequence+1;local params={};for k,v in pairs(W.query) do params[k]=v end
    for k,v in pairs(extra or {}) do params[k]=v end
    local key=form(W.query);local hit=W.cache[key]
    if not extra and hit and hit.expires>now() then W.pending=nil;widget('NativeTx'):SetText('');receive(hit.state);return end
    W.pending={sequence=W.sequence,key=key,action=extra and extra.action,since=now(),chunks={},bytes=0}
    if extra then if not W.state then return end;params.request=W.state.request;W.busy=true;enable('ConfirmAccept',false) end
    widget('NativeTx'):SetText(W.sequence..'|'..(extra and 'P' or 'G')..'|'..form(params))
    status(extra and 'Updating Wardrobe...' or 'Loading appearances...')
end
local function receiveChunk(response)
    local n,offset,final,chunk=response:match('^(%d+)|(%d+)|([01])\n(.*)$')
    local pending=W.pending
    if not n or not pending or tonumber(n)~=pending.sequence then return end
    if tonumber(offset)~=pending.bytes or pending.bytes+#chunk>1048576 then
        W.pending=nil;W.busy=false;status('Wardrobe item data was incomplete. Refresh to retry.');return
    end
    pending.chunks[#pending.chunks+1]=chunk;pending.bytes=pending.bytes+#chunk
    if final~='1' then return end
    W.pending=nil;W.busy=false;enable('ConfirmAccept',true)
    local ok,result=pcall(WardrobeJSON.decode,table.concat(pending.chunks))
    if ok then
        if not result.error and not pending.action then W.cache[pending.key]={state=result,expires=now()+2.5} end
        receive(result,pending.action)
    else status('Wardrobe could not read its item data. Refresh to retry.') end
end
function WardrobeNative_Render()
    if not W.state then return end
    local state=W.state;local t=target();W.rowData={};W.slotData={};W.hoverData={};W.iconRequests={}
    text('Character',state.character..' | Equipped appearances')
    text('Counts',state.unlocked..' / '..state.total..' appearances | '..state.tickets..' Appearance Unlock')
    text('Target',t and t.name or 'Select equipment')
    local nativeWorn={}
    if GetPlayerEquip then local ok,data=pcall(GetPlayerEquip);if ok and type(data)=='string' then for slot,item in data:gmatch('(%d+):(%d+)') do nativeWorn[2^tonumber(slot)]=tonumber(item) end end end
    for _,s in ipairs(slots) do
        local mask=s[1];local found
        for _,item in ipairs(state.equipment) do if item.equipped and (item.slot==mask or mask==1 and item.slot==3 or mask==131072 and item.slot==393216) then found=item;break end end
        W.slotData[mask]=found
        W.hoverData[mask]=found or nativeWorn[mask] and {item=nativeWorn[mask]}
        local d=found and W.drafts[found.object];icon('SlotIcon'..mask,d and (d.skin~=0 and d.skin or found.item) or found and found.skin or nativeWorn[mask])
        widget('Slot'..mask):SetTooltip(found and '' or 'No appearance equipment in this slot')
        show('SlotSelected'..mask,found and found.object==W.query.target)
        enable('Slot'..mask,W.hoverData[mask]~=nil)
    end
    local rows,pages,page
    if W.view=='collection' then rows=state.skins;pages=state.pages;page=state.page
    elseif W.view=='inventory' then
        rows={};for _,item in ipairs(state.equipment) do if not item.equipped and item.name:lower():find(W.query.q:lower(),1,true) then rows[#rows+1]=item end end
        pages=math.max(1,math.ceil(#rows/36));page=math.min(W.localPage,pages)
    else rows={};for _,outfit in ipairs(state.outfits) do if outfit.name:lower():find(W.query.q:lower(),1,true) then rows[#rows+1]=outfit end end;pages=math.max(1,math.ceil(#rows/36));page=math.min(W.localPage,pages) end
    W.localPage=page;show('Empty',#rows==0);text('Empty',W.view=='outfits' and 'No saved outfits' or W.view=='inventory' and 'No matching equipment' or 'No matching appearances')
    for i=1,36 do
        local row=rows[(W.view=='collection' and 0 or (page-1)*36)+i];W.rowData[i]=row
        show('Row'..i,row~=nil);show('Name'..i,row~=nil);show('State'..i,row~=nil)
        local selected=row and (W.view=='collection' and row.item==W.query.skin or W.view=='inventory' and row.object==W.query.target or W.view=='outfits' and W.selectedOutfit and row.name==W.selectedOutfit.name)
        show('Selected'..i,false)
        if row then WardrobeTheme_Button(widget('Row'..i),selected,false,true) end
        if row then
            icon('Icon'..i,row.skin or row.item);text('Name'..i,row.name)
            text('State'..i,W.view=='collection' and (row.unlocked and 'Unlocked' or #row.sources>0 and 'Ready to unlock' or 'Locked') or W.view=='inventory' and row.type or 'Saved Outfit')
            widget('Row'..i):SetTooltip('')
        else show('Icon'..i,false) end
    end
    rect('Rows',0,0,W.width-W.left-70,math.ceil(math.min(36,#rows)/W.columns)*66)
    text('Page',page..' / '..pages);enable('Previous',page>1);enable('Next',page<pages);W.pages=pages
    text('Results',W.view=='collection' and state.results..' appearances' or #rows..(W.view=='inventory' and ' equipment items' or ' saved outfits'))
    for i in ipairs(categories) do show('Category'..i,W.view=='collection') end
    for i,name in ipairs(categories) do WardrobeTheme_Button(widget('Category'..i),name==W.query.category,false) end
    for name,view in pairs({Collection='collection',Outfits='outfits',Inventory='inventory'}) do WardrobeTheme_Button(widget(name),view==W.view,false) end
    show('Filter',W.view=='collection');show('ClearTarget',W.view=='collection' and t~=nil)
    local skin=W.skins[W.query.skin];local outfit=W.selectedOutfit
    local description=skin and '<font color="#a6d9ff">'..esc(skin.name)..'</font><br>'..esc(skin.category)..' | '..esc(skin.type)..'<br>'..(skin.unlocked and 'Unlocked' or 'Locked appearance') or 'Select equipment, then choose an appearance.'
    if W.view=='inventory' and t then description=esc(t.name)..'<br>'..esc(t.type)..'<br>Select Collection to choose a skin.' end
    if W.view=='outfits' then description=outfit and esc(outfit.name)..'<br>Try on this outfit, then Apply Changes.' or 'Save your equipped appearances as an outfit.' end
    text('Details',description)
    show('TryOn',W.view~='inventory');show('Unlock',W.view=='collection');show('Restore',W.view~='outfits')
    show('SaveOutfit',W.view=='outfits');show('DeleteOutfit',W.view=='outfits')
    if W.view=='outfits' then
        local rx,right=W.left+18,W.width-W.left-30
        rect('SaveOutfit',rx+10,W.height-81,(right-26)/3,29)
        rect('DeleteOutfit',rx+10+(right-20)/3,W.height-81,(right-26)/3,29)
        rect('TryOn',rx+10+2*(right-20)/3,W.height-81,(right-26)/3,29)
    else
        local rx,right=W.left+18,W.width-W.left-30
        rect('TryOn',rx+10,W.height-81,(right-26)/3,29)
    end
    enable('TryOn',W.view=='outfits' and outfit~=nil or W.view=='collection' and skin~=nil)
    enable('Unlock',skin~=nil and not skin.unlocked and #skin.sources>0 and state.tickets>0)
    enable('Restore',t and t.skin~=t.item);enable('DeleteOutfit',outfit~=nil);enable('SaveOutfit',true)
    widget('NativeIcons'):SetText(table.concat(W.iconRequests,','))
end
function WardrobeNative_CloseConfirm()
    WardrobeConfirm:Hide();W.confirm=nil;W.overwrite=false;control('input',1)
end
local function confirm(title,body,action)
    if W.busy then return end
    W.confirm=action;text('ConfirmText','<font color="#a6d9ff">'..esc(title)..'</font><br><br>'..body);text('ConfirmError','')
    show('OutfitName',false);show('UnlockSource',false);enable('ConfirmAccept',true);WardrobeConfirm:Show();control('input',0)
end
local function stage(item,skin)
    if not compatible(item,skin) then status('Select compatible equipment for this appearance.');return false end
    if skin.item==item.skin then W.drafts[item.object]=nil else W.drafts[item.object]={object=item.object,skin=skin.item,expected=item.skin,name=skin.name,unlocked=skin.unlocked} end
    return true
end
function WardrobeNative_Click(name)
    g_AddonName='RelicCalc'
    if name=='WardrobeConfirmCancel' then WardrobeNative_CloseConfirm();return end
    if W.busy then return end
    if name=='WardrobeConfirmAccept' then if W.confirm then W.confirm() end;return end
    if W.confirm then return end
    if name=='WardrobeRefresh' then W.cache={};W.started=false;WardrobeNative_Fetch();return end
    local mask=tonumber(name:match('^WardrobeSlot(%d+)$'))
    if mask and W.slotData and W.slotData[mask] then W.query.target=W.slotData[mask].object;W.query.page=1;W.view='collection';WardrobeNative_Fetch();return end
    local category=tonumber(name:match('^WardrobeCategory(%d+)$'))
    if category then W.query.category=categories[category];W.query.target=0;W.query.page=1;WardrobeNative_Fetch();return end
    local row=tonumber(name:match('^WardrobeRow(%d+)$'))
    if row and W.rowData[row] then
        local item=W.rowData[row]
        if W.view=='collection' then W.query.skin=item.item
        elseif W.view=='inventory' then W.query.target=item.object;W.query.page=1;W.view='collection';WardrobeNative_Fetch();return
        else W.selectedOutfit=item end
        WardrobeNative_Render();return
    end
    local views={WardrobeCollection='collection',WardrobeInventory='inventory',WardrobeOutfits='outfits'}
    if views[name] then W.view=views[name];W.localPage=1;widget('Scroll'):SetVScrollValue(0);WardrobeNative_Render();return end
    if name=='WardrobeClear' then widget('Search'):SetText('');W.query.q='';W.query.page=1;WardrobeNative_Fetch();return end
    if name=='WardrobeClearTarget' then W.query.target=0;W.query.page=1;WardrobeNative_Fetch();return end
    if name=='WardrobePrevious' or name=='WardrobeNext' then
        local delta=name=='WardrobeNext' and 1 or -1
        if W.view=='collection' then W.query.page=math.max(1,math.min(W.pages,W.query.page+delta));WardrobeNative_Fetch()
        else W.localPage=math.max(1,math.min(W.pages,W.localPage+delta));WardrobeNative_Render() end
        widget('Scroll'):SetVScrollValue(0);return
    end
    local controls={WardrobeLeft='left',WardrobeRight='right',WardrobeZoom='zoom',WardrobeHelmet='helmet',WardrobeCombat='combat',WardrobeWings='wings'}
    if controls[name] then
        local c=controls[name];if c=='wings' then W.wings=not W.wings;control(c,W.wings and 1 or 0)
        elseif c=='left' or c=='right' then control(c,1);W.releaseControl=c;W.releaseAt=now()+.15 else control(c,1) end;return
    end
    if name=='WardrobeReset' then W.drafts={};W.previewWing=nil;control('camera-reset',1);preview();WardrobeNative_Render();return end
    if name=='WardrobeRemoveLocked' then for id,d in pairs(W.drafts) do if not d.unlocked then W.drafts[id]=nil end end;preview();WardrobeNative_Render();return end
    local skin=W.skins[W.query.skin];local t=target()
    if name=='WardrobeTryOn' and W.view=='collection' and skin then
        if not t then for _,i in ipairs(W.state.equipment) do if i.equipped and compatible(i,skin) then t=i;W.query.target=i.object;break end end end
        if t then if stage(t,skin) then preview();WardrobeNative_Render() end
        elseif skin.group=='WING' then W.previewWing=skin.item;preview();status('Wings previewed. Select equipped wings to apply an appearance.')
        else status('Select compatible equipment first.') end;return
    end
    if name=='WardrobeTryOn' and W.view=='outfits' and W.selectedOutfit then
        local ok,saved=pcall(WardrobeJSON.decode,W.selectedOutfit.skins_json);if not ok then status('This saved outfit could not load.');return end
        local changes={}
        for slot,appearance in pairs(saved) do
            local item;for _,i in ipairs(W.state.equipment) do if i.equipped and i.slot==tonumber(slot) then item=i;break end end
            local s=W.skins[appearance]
            if not item or appearance~=0 and not compatible(item,s) then status('Equip compatible items for every saved outfit slot.');return end
            if (appearance~=0 and appearance or item.item)~=item.skin then changes[item.object]={object=item.object,skin=appearance,expected=item.skin,name=s and s.name or item.name,unlocked=appearance==0 or s.unlocked} end
        end
        W.drafts=changes;preview();WardrobeNative_Render();return
    end
    if name=='WardrobeRestore' and t then W.drafts[t.object]={object=t.object,skin=0,expected=t.skin,name=t.name..' | Original appearance',unlocked=true};preview();WardrobeNative_Render();return end
    if name=='WardrobeUnlock' and skin and not skin.unlocked and #skin.sources>0 then
        confirm('Unlock Appearance','Unlock '..esc(skin.name)..' for your account?<br>Consumes 1 Appearance Unlock. Your equipment is kept.',function()
            local i=math.floor(widget('UnlockSource'):GetValue())+1;local source=skin.sources[i]
            if source then WardrobeNative_Fetch({action='unlock',source=source.object,skin=skin.item}) end
        end)
        widget('UnlockSource'):ClearItem();for _,source in ipairs(skin.sources) do widget('UnlockSource'):AddItem(source.name..(source.equipped and ' | Equipped' or ' | Inventory')) end
        widget('UnlockSource'):SetValue(0);show('UnlockSource',true);return
    end
    if name=='WardrobeApply' then
        local changes,names={},{}
        for _,d in pairs(W.drafts) do if not d.unlocked then status('Unlock or remove locked skins before applying changes.');return end
            changes[#changes+1]=string.format('{"object":%d,"skin":%d,"expected":%d}',d.object,d.skin,d.expected);names[#names+1]=esc(d.name)
        end
        if #changes==0 then return end
        confirm('Apply Changes',table.concat(names,'<br>')..'<br><br>Apply these appearances? Equipment stats are kept.',function() WardrobeNative_Fetch({action='applyChanges',changes='['..table.concat(changes,',')..']'}) end);return
    end
    if name=='WardrobeSaveOutfit' then
        confirm('Save Current Outfit','Save your equipped appearances. Remodeled skins must be unlocked.',function()
            local outfit=widget('OutfitName'):GetText():gsub('^%s+',''):gsub('%s+$','')
            if #outfit==0 then text('ConfirmError','Enter an outfit name.');return end
            if not W.overwrite then for _,o in ipairs(W.state.outfits) do if o.name:lower()==outfit:lower() then W.overwrite=true;text('ConfirmError','An outfit with this name exists. Confirm again to replace it.');return end end end
            WardrobeNative_Fetch({action='saveOutfit',name=outfit})
        end);widget('OutfitName'):SetText('');show('OutfitName',true);widget('OutfitName'):SetFocus();return
    end
    if name=='WardrobeDeleteOutfit' and W.selectedOutfit then local o=W.selectedOutfit;confirm('Delete Outfit','Delete '..esc(o.name)..'? Unlocked appearances are kept.',function() WardrobeNative_Fetch({action='deleteOutfit',name=o.name});W.selectedOutfit=nil end) end
end
local function hoverItem(name)
    local mask=tonumber(tostring(name):match('^WardrobeSlot(%d+)$'))
    local row=tonumber(tostring(name):match('^WardrobeRow(%d+)$'))
    local item=mask and W.hoverData and W.hoverData[mask] or row and W.rowData and W.rowData[row]
    local query=not W.confirm and item and (item.item or item.skin) and
        (item.tooltip or 'item='..tostring(item.item or item.skin)..'&count=1&enchant_count=0&authorize_count=0') or 'clear'
    if query~=W.hoverQuery then widget('NativeHover'):SetText(query);W.hoverQuery=query end
end
function WardrobeNative_OnEvent(event,...)
    g_AddonName='RelicCalc';local name=select(1,...)
    if event=='GAME_UI_COMBOBOX_SEL' and name=='WardrobeFilter' then W.lastFilter=widget('Filter'):GetValue();W.query.filter=filters[math.floor(W.lastFilter)+1] or 'unlocked';W.query.page=1;WardrobeNative_Fetch()
    elseif event=='GAME_UI_EDITBOX_CHANGED' and name=='WardrobeSearch' then W.searchAt=now()+.25
    elseif event=='GAME_UI_CANCEL_KEY_PRESSED' then if WardrobeConfirm:IsVisible()==1 then WardrobeNative_CloseConfirm() else PrivateWardrobe:Hide() end
    elseif event=='ADDON_TIMER' then
        W.clock=now()+.05
        if PrivateWardrobe:IsVisible()==1 then
            local meta=widget('NativeMeta'):GetText();local width,height,seq,ready=meta:match('^(%d+)|(%d+)|(%d+)|(%d+)')
            hoverItem(meta:match('^[^|]+|[^|]+|[^|]+|[^|]+|[^|]+|(.*)$') or '')
            local search=widget('Search'):GetText()
            if search~=W.lastSearch then W.lastSearch=search;W.searchAt=now()+.25 end
            local filter=widget('Filter'):GetValue()
            if filter~=W.lastFilter then W.lastFilter=filter;W.query.filter=filters[math.floor(filter)+1] or 'unlocked';W.query.page=1;WardrobeNative_Fetch() end
            if meta=='unsupported' then status('This client version does not support native Wardrobe.');end
            if width and (tonumber(width)~=W.width or tonumber(height)~=W.height or GetUIScale and GetUIScale()~=W.scale) then WardrobeNative_Layout(tonumber(width),tonumber(height)) end
            if seq and tonumber(seq)>0 and tonumber(seq)==W.previewSequence and ready=='0' then status('Character preview could not load. Refresh Wardrobe to retry.') end
            for index=0,7 do
                local channel=widget('NativeRx'..(index==0 and '' or index))
                local response=channel:GetText()
                if #response>0 then channel:SetText('');receiveChunk(response) end
            end
            if W.searchAt and now()>=W.searchAt then W.searchAt=nil;W.query.q=widget('Search'):GetText();W.query.page=1;W.localPage=1;if W.view=='collection' then WardrobeNative_Fetch() else WardrobeNative_Render() end end
            if W.releaseAt and now()>=W.releaseAt then control(W.releaseControl,0);W.releaseAt=nil end
        else
            if W.opened then W.opened=false;W.started=false;W.pending=nil;W.busy=false;WardrobeNative_CloseConfirm() end
        end
        PrivateWardrobe:SetTimer(.05)
    end
end
function WardrobeNative_OnLoad()
    g_AddonName='RelicCalc'
    for _,e in ipairs({'GAME_UI_COMBOBOX_SEL','GAME_UI_EDITBOX_CHANGED','GAME_UI_CANCEL_KEY_PRESSED','ADDON_TIMER'}) do PrivateWardrobe:RegisterEvent(e) end
    for _,name in ipairs({'NativeTx','NativeRx','NativeMeta','NativePreview','NativeControl','NativeHover','NativeIcons'}) do widget(name):SetMaxLength(1048576) end
    for index=1,7 do widget('NativeRx'..index):SetMaxLength(1023) end
    widget('Search'):SetMaxLength(60);widget('OutfitName'):SetMaxLength(32)
    for _,label in ipairs({'Unlocked','Ready to Unlock','All Appearances','Locked'}) do widget('Filter'):AddItem(label) end
    widget('Filter'):SetValue(0);W.lastFilter=0;W.lastSearch='';PrivateWardrobe:RegisterHandleCancelKey();PrivateWardrobe:Hide();WardrobeConfirm:Hide();PrivateWardrobe:SetTimer(.05)
    WardrobeNative_Layout(1280,960)
    WardrobeTheme_Apply()
    for i=1,36 do for _,name in ipairs({'Row','Icon','Selected','Name','State'}) do show(name..i,false) end end
    for _,s in ipairs(slots) do show('SlotIcon'..s[1],false);show('SlotSelected'..s[1],false);enable('Slot'..s[1],false) end
    for _,name in ipairs({'TryOn','Unlock','Restore','SaveOutfit','DeleteOutfit','RemoveLocked'}) do show(name,false) end
    enable('Apply',false)
    status('Loading appearances...')
end
function WardrobeNative_Open()
    g_AddonName='RelicCalc';W.opened=true;W.started=false;W.busy=false;W.pending=nil;W.cache={};W.drafts={};W.previewWing=nil;W.hoverQuery=nil
    widget('NativeTx'):SetText('');widget('NativeRx'):SetText('');widget('NativePreview'):SetText('');widget('NativeControl'):SetText('');widget('NativeHover'):SetText('clear')
    for index=1,7 do widget('NativeRx'..index):SetText('') end
    PrivateWardrobe:Show();WardrobeNative_Fetch()
end
