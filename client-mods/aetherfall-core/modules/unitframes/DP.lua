local K=AF.Skin
local function update(frame)
    local r,v,s=frame.bars.dp,frame.values,frame.settings
    local bg,fill=frame.widgets.DPBG,frame.widgets.DPFill
    local available=frame.unit=='player' and s.showDP==1 and AF.Native.Number(v.dp) and AF.Native.Number(v.dpmax) and v.dpmax>0
    local label=frame.widgets.DPTextCentre
    if not available then K.Hide(bg);K.Hide(fill);K.Hide(label);return end
    K.Place(bg,unpack(r));K.ImageColor(bg,frame.appearance.backdrop,1);K.Show(bg)
    if s.border==1 then K.Border(bg,r[3],r[4],frame.appearance.border) else K.HideEdges(bg) end
    local ratio=math.max(0,math.min(1,v.dp/v.dpmax))
    if ratio>0 then K.Place(fill,r[1]+1,r[2]+1,math.max(0,(r[3]-2)*ratio),math.max(1,r[4]-2));K.ImageColor(fill,'#d6b878',1);K.Show(fill)
    else K.Hide(fill) end
    -- The stock DP number is hidden with the native bar, so the frame writes its own.
    local text=AF.Tags:Format(s.dpText or '',v)
    if text~='' and r[4]>=11 then
        K.Place(label,r[1]+3,r[2],math.max(1,r[3]-6),r[4]);K.Font(label,frame.appearance.font)
        K.Text(label,frame.appearance.text);K.SetText(label,text);K.Show(label)
    else K.Hide(label) end
end
AF:Call('UnitFrames','RegisterElement','DP',{Update=update})
