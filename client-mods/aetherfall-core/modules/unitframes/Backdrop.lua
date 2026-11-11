local K=AF.Skin
local function update(frame)
    local r=frame.body or frame.rect
    K.Place(frame.widgets.Backdrop,unpack(r))
    K.ImageColor(frame.widgets.Backdrop,frame.appearance.backdrop,frame.settings.backdropOpacity/100)
    K.Show(frame.widgets.Backdrop)
    if frame.settings.border==1 then
        K.Border(frame.widgets.Backdrop,r[3],r[4],frame.appearance.border)
    else K.HideEdges(frame.widgets.Backdrop) end
end
AF:Call('UnitFrames','RegisterElement','Backdrop',{Update=update})
