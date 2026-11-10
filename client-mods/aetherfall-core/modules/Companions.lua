-- Extracted from the 1.2.94 interface; client calls retain their original order.
local M=AF:NewModule('Companions',{needs={'Lifecycle'},defaults={botbar={1,'toggle'},botbarmode={-1,'int',-1,2,1}},tags={botbarmode='share-skip'}})
local S={}
local N=AF.Native
local D=AFCore and AFCore.DIALOG or {}
local call,n,scale,screen=N.Call,N.Number,N.Scale,N.Screen
local dialog,nativeMovable,identity=N.Dialog,N.Movable,N.Identity
local featureLayout,featureWidget=N.Layout,N.LayoutWidget
local function get(key) return M.db[key] end
function M:ShowCompanionBar()
    if not AF:Call('Lifecycle','IsReady') then return false end
    AF.DB:Set(M,'botbar',1,true); return AF.DB:Save()
end
function M:RememberCompanionMode(mode)
    if not AF:Call('Lifecycle','IsReady') or (mode~=0 and mode~=1 and mode~=2) then return end
    AF.DB:Set(M,'botbarmode',mode,true); return AF.DB:Save()
end
AF.Config:Register(M,{{name='General',order=1,rows={{order=4,label='Companion bar',k='botbar',kind='toggle'}}}})
