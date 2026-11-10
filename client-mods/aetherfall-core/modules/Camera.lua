local labels={camfov={'Native'},camdist={'Native'}}
for v=60,170,5 do labels.camfov[#labels.camfov+1]=tostring(v)..' deg' end
for v=5,50 do labels.camdist[#labels.camdist+1]=tostring(v) end
-- Extracted from the 1.2.94 interface; client calls retain their original order.
local M=AF:NewModule('Camera',{needs={},defaults={camfov={1,'choice',labels.camfov},camdist={1,'choice',labels.camdist}},tags={camfov='camera',camdist='camera'}})
local S={}
local N=AF.Native
local D=AFCore and AFCore.DIALOG or {}
local call,n,scale,screen=N.Call,N.Number,N.Scale,N.Screen
local dialog,nativeMovable,identity=N.Dialog,N.Movable,N.Identity
local featureLayout,featureWidget=N.Layout,N.LayoutWidget
local function get(key) return M.db[key] end
local choices={}
local CAMERA = {camfov = {name = 'fov', values = {-1}}, camdist = {name = 'distance', values = {-1}}}
for key, spec in pairs(CAMERA) do
    choices[key] = {'Native'}
    local first, last, step = key == 'camfov' and 60 or 5, key == 'camfov' and 170 or 50, key == 'camfov' and 5 or 1
    for v = first, last, step do
        spec.values[#spec.values + 1] = v
        choices[key][#choices[key] + 1] = tostring(v) .. (key == 'camfov' and ' deg' or '')
    end
end
function M:Apply()
    S.cameraError = nil
    for key, spec in pairs(CAMERA) do
        if not AFCore.SetCameraSetting or not AFCore.SetCameraSetting(spec.name, spec.values[get(key)]) then
            S.cameraError = 'Camera settings unavailable in this client.'
        end
    end
end
function M:BeforeSetting(key,value)
    local spec=CAMERA[key]
    if not AFCore.SetCameraSetting or not AFCore.SetCameraSetting(spec.name,spec.values[value]) then
        S.cameraError='Camera settings unavailable; your choice was kept.'; return false
    end
    S.cameraError=nil
end
function M:Status() return S.cameraError end
function M:FormatOption(spec)
    local camera=CAMERA[spec.k]; local current,original
    if AFCore.CameraSetting then current,original=AFCore.CameraSetting(camera.name) end
    if not current then return 'Unavailable' end
    if get(spec.k)==1 then return 'Native ('..string.format('%g',original or current)..')' end
    return choices[spec.k][get(spec.k)]
end
function M:Diagnostics() return AF.Copy(S) end
AF.Config:Register(M,{{name='Camera',order=9,status='Status',hint='Applies now. Scroll to zoom out. Native restores your original value.',rows={
 {order=1,label='Field of view',k='camfov',kind='choice',format='FormatOption'},
 {order=2,label='Maximum zoom distance',k='camdist',kind='choice',format='FormatOption'}}}})
M:Listen('ApplyCamera',M.Apply)
function M:Owns(key) return CAMERA[key]~=nil end
