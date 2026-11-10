-- Native interface policy; no state is shared with other modules.
local M=AF:NewModule('WorldMap',{scope='profile',needs={'Lifecycle'},defaults={mapx={-1,'number',-1,1,0},mapy={-1,'number',-1,1,0}}})
local S={}
local N=AF.Native
local D=AFCore and AFCore.DIALOG or {}
local call,n,scale,screen=N.Call,N.Number,N.Scale,N.Screen
local dialog,nativeMovable,identity=N.Dialog,N.Movable,N.Identity
local featureLayout,featureWidget=N.Layout,N.LayoutWidget
local function get(key) return M.db[key] end
function M:Hold()
    local id = S.mapId; local d = id and dialog(id)
    if not d then S.mapShown = false; return end
    local sw, sh = screen()
    if d.shown and not S.mapShown then
        AFCore.Movable(id, true)
        local mx, my = get('mapx'), get('mapy')
        if mx >= 0 and my >= 0 then AFCore.Move(id, math.min(sw - d.w, mx * sw), math.min(sh - d.h, my * sh)) end
        S.mapAt = AF:Call('Lifecycle','Ticks')
    end
    S.mapShown = d.shown
end
function M:OnSetting() S.mapShown=false end
function M:Id() return S.mapId end
function M:Remember(x,y) AF.DB:Set(M,'mapx',x,true); AF.DB:Set(M,'mapy',y,true) end
function M:QueueVisible()
    local map=S.mapId and dialog(S.mapId)
    if map and not map.shown then S.mapShown=false end
end
function M:ResetShown() S.mapShown=false end
function M:Diagnostics() return AF.Copy(S) end
M:Listen('WorldMapFound',function(_,id) S.mapId=id end)
M:Listen('RecoverNativeWindows',function() S.mapShown=false end)
