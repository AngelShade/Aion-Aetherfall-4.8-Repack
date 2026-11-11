local K=AF.Skin
-- Aion's stock UnitClass names. Unknown/localized values retain the fixed colour;
-- target/targettarget deliberately have no invented class mapping.
local CLASS={Warrior='#c69b6d',Gladiator='#c69b6d',Templar='#c69b6d',Scout='#abd473',Assassin='#fff569',Ranger='#abd473',
    Mage='#69ccf0',Sorcerer='#69ccf0',Spiritmaster='#9482c9',Priest='#ffffff',Cleric='#ffffff',Chanter='#f58cba',
    Engineer='#40c7b4',Gunner='#40c7b4',Aethertech='#0070de',Artist='#00ff96',Songweaver='#00ff96'}
local classColors={};for name,color in pairs(CLASS) do classColors[name:lower()]=color end
local function update(frame)
    local r,v,s=frame.bars.health,frame.values,frame.settings
    local color=s.healthMode=='custom' and s.healthColor or frame.appearance.fixed
    if s.healthMode=='class' then color=classColors[type(v.class)=='string' and v.class:lower() or ''] or color end
    if v.unavailable then color=s.stateColors=='custom' and s.offlineColor or frame.appearance.offline
    elseif v.dead then color=s.stateColors=='custom' and s.deadColor or frame.appearance.dead end
    local bg,fill=frame.widgets.HealthBG,frame.widgets.HealthFill
    K.Place(bg,unpack(r));K.ImageColor(bg,(v.dead or v.unavailable) and color or frame.appearance.backdrop,1);K.Show(bg)
    if s.border==1 then K.Border(bg,r[3],r[4],frame.appearance.border) else K.HideEdges(bg) end
    local ratio=v.hp and v.hpmax and v.hpmax>0 and math.max(0,math.min(1,v.hp/v.hpmax)) or 0
    if ratio>0 then K.Place(fill,r[1]+1,r[2]+1,math.max(0,(r[3]-2)*ratio),math.max(1,r[4]-2));K.ImageColor(fill,color,1);K.Show(fill)
    else K.Hide(fill) end
end
AF:Call('UnitFrames','RegisterElement','Health',{Update=update})
