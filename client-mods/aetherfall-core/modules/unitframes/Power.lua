local K=AF.Skin
local function update(frame)
    local r,v=frame.bars.power,frame.values
    local available=v.mp~=nil and v.mpmax~=nil and v.mpmax>0
    local bg,fill=frame.widgets.PowerBG,frame.widgets.PowerFill
    if not available then K.Hide(bg);K.Hide(fill);return end
    K.Place(bg,unpack(r));K.ImageColor(bg,frame.appearance.backdrop,1);K.Show(bg)
    if frame.settings.border==1 then K.Border(bg,r[3],r[4],frame.appearance.border) else K.HideEdges(bg) end
    local ratio=math.max(0,math.min(1,v.mp/v.mpmax))
    local color=frame.settings.powerMode=='custom' and frame.settings.powerColor or frame.appearance.power
    if ratio>0 then K.Place(fill,r[1]+1,r[2]+1,math.max(0,(r[3]-2)*ratio),math.max(1,r[4]-2));K.ImageColor(fill,color,1);K.Show(fill)
    else K.Hide(fill) end
end
AF:Call('UnitFrames','RegisterElement','Power',{Update=update})
