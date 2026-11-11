-- Exact paths from the installed ENU native layouts (ui/game and game_hud_s1).
-- UIStatic=0x2022, UIGauge=0x200e and UIStaticImage=0x2021 were verified
-- through the generated native catalogue, not inferred from widget names.
local STATIC,GAUGE,IMAGE=0x2022,0x200e,0x2021
local function add(list,path,kind,optional) list[#list+1]={path=path,kind=kind,optional=optional} end
local player={dialog='basic_status',layout='basic_status_dialog',backdrop='',visuals={}}
for _,path in ipairs({'hp_gauge','hp_gauge_bg','mp_gauge','mp_gauge_bg','dp_gauge_1','dp_gauge_2','dp_gauge_3','dp_gauge_text'}) do add(player.visuals,path,GAUGE) end
add(player.visuals,'level',STATIC);add(player.visuals,'dp_level',IMAGE)
-- The frame's art is the one shown container; the native dialog box can be larger (it was in game).
local target={dialog='target',layout='target_dialog',rects={},visuals={}}
for i=1,7 do
    local prefix='container'..i..'/'
    target.rects[i]='container'..i
    add(target.visuals,prefix..'bg',IMAGE)
    add(target.visuals,prefix..'name',STATIC)
    if i<=4 or i==7 then
        add(target.visuals,prefix..'level',STATIC)
        add(target.visuals,prefix..'hp_gauge',GAUGE)
        add(target.visuals,prefix..'hp_gauge_bg',GAUGE)
        add(target.visuals,prefix..'af_resource_hp',STATIC,true)
    elseif i==5 then add(target.visuals,prefix..'hp_gauge',GAUGE) end
end
for _,path in ipairs({'mp_gauge','mp_gauge_bg'}) do add(target.visuals,'container7/'..path,GAUGE) end
for _,path in ipairs({'af_resource_mp','af_resource_dp','af_identity_rank'}) do add(target.visuals,'container7/'..path,STATIC,true) end
for _,rarity in ipairs({'poor','ordinary','good','great','excellent'}) do add(target.visuals,'container7/af_identity_gear_'..rarity,STATIC,true) end
local assist={dialog='target_assist',layout='target_assist_dialog',rect='container',visuals={}}
add(assist.visuals,'container/bg',IMAGE)
for _,path in ipairs({'level','name'}) do add(assist.visuals,'container/'..path,STATIC) end
for _,path in ipairs({'hp_gauge','hp_gauge_bg'}) do add(assist.visuals,'container/'..path,GAUGE) end
add(assist.visuals,'container/af_resource_hp',STATIC,true)
local definitions={player=player,target=target,targettarget=assist}
for i=1,6 do
    local prefix='ctn_member'..i
    -- headerReserve: the stock flight gauge (144-162) and our level text stay clear on the right.
    local row={dialog='party',layout='party_dialog',rect=prefix,title=prefix..'/title',headerReserve=56,visuals={}}
    -- #1 is the voice-chat container and its live button; #2 is only artwork.
    add(row.visuals,prefix..'/#2',STATIC)
    add(row.visuals,prefix..'/emblem',IMAGE) -- class icon; the health bar carries the class colour
    row.offscreen={prefix..'/emblem',prefix..'/level',prefix..'/hp_bg',prefix..'/hp',prefix..'/mp_bg',prefix..'/mp'}
    for _,path in ipairs({'title','level'}) do add(row.visuals,prefix..'/'..path,STATIC) end
    for _,path in ipairs({'hp_bg','hp','mp_bg','mp'}) do add(row.visuals,prefix..'/'..path,GAUGE) end
    add(row.visuals,prefix..'/af_identity_rank',STATIC,true)
    for _,rarity in ipairs({'poor','ordinary','good','great','excellent'}) do add(row.visuals,prefix..'/af_identity_name_'..rarity,STATIC,true) end
    definitions['party'..i]=row
end
AF:Call('UnitFrames','RegisterNative',definitions)
