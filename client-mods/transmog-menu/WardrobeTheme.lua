-- Opaque native Wardrobe surfaces. The client still draws its own controls.
WardrobeTheme = {images={},text='0.87,0.94,1,1',muted='0.64,0.77,0.86,1'}
local T=WardrobeTheme
local root='Textures/UI/WardrobeNative/'
function WardrobeTheme_Image(object,state,name)
    local key=object.name..':'..tostring(state)
    if T.images[key]~=name then object:SetUIImage(state,root..name);T.images[key]=name end
end
function WardrobeTheme_Button(object,selected,primary,card)
    local normal=card and (selected and 'card_selected' or 'card') or selected and 'button_selected' or primary and 'primary' or 'button'
    local over=card and 'card_over' or primary and 'primary_over' or 'button_over'
    local down=card and 'card_selected' or primary and 'primary_down' or 'button_down'
    for state,name in pairs({[0]=normal,[1]=over,[2]=down,[256]=normal,[1024]='button_disabled'}) do WardrobeTheme_Image(object,state,name) end
end
function WardrobeTheme_Apply()
    T.images={}
    for name,object in pairs(_G) do
        if type(name)=='string' and name:match('^Wardrobe') and not name:match('Icon') and not name:match('Selected') and not name:match('Native') and not name:match('^WardrobeRow%d+$') and type(object)=='table' and object.SetAttr and object.name==name then
            object:SetAttr('textColor',T.text)
            object:SetAttr('textColorOver','0.96,0.99,1,1')
            object:SetAttr('textColorSelected','0.96,0.99,1,1')
            object:SetAttr('textColorDisabled','0.51,0.62,0.70,1')
        end
    end
    PrivateWardrobe:SetAlpha(1,false);PrivateWardrobe:SetAttr('textColor',T.text)
    WardrobeConfirm:SetAlpha(1,false)
    WardrobeTheme_Image(PrivateWardrobe,0,'window');WardrobeTheme_Image(WardrobeConfirm,0,'window')
    for _,name in ipairs({'LeftPanel','RightPanel','Details','Scroll','ConfirmText'}) do WardrobeTheme_Image(_G['Wardrobe'..name],0,name=='LeftPanel' and 'preview' or 'panel') end
    for _,name in ipairs({'Search','Filter','OutfitName','UnlockSource'}) do WardrobeTheme_Image(_G['Wardrobe'..name],0,'field') end
    for _,name in ipairs({'Counts','MouseHint','Results','Page','State','Empty'}) do
        local object=_G['Wardrobe'..name];if object then object:SetAttr('textColor',T.muted) end
    end
    WardrobeConfirmError:SetAttr('textColor','1,0.64,0.58,1')
    for _,name in ipairs({'Refresh','Collection','Outfits','Inventory','Clear','Previous','Next','TryOn','Unlock','Restore','Reset','RemoveLocked','Apply','SaveOutfit','DeleteOutfit','ClearTarget','Left','Right','Zoom','Helmet','Combat','Wings','ConfirmCancel','ConfirmAccept'}) do
        WardrobeTheme_Button(_G['Wardrobe'..name],false,name=='Apply' or name=='ConfirmAccept' or name=='Unlock')
    end
    for i=1,7 do WardrobeTheme_Button(_G['WardrobeCategory'..i],false,false) end
    for i=1,36 do
        WardrobeTheme_Button(_G['WardrobeRow'..i],false,false,true)
        WardrobeTheme_Image(_G['WardrobeSelected'..i],0,'selection')
        _G['WardrobeState'..i]:SetAttr('textColor',T.muted)
    end
    for _,mask in ipairs({1,2,4,2048,8,16,4096,32768,32}) do
        WardrobeTheme_Button(_G['WardrobeSlot'..mask],false,false)
        WardrobeTheme_Image(_G['WardrobeSlotSelected'..mask],0,'selection')
    end
end
