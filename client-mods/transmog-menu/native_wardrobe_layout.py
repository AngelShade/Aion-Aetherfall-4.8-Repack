"""Generate native addon widgets using the English client's existing presets."""
import xml.etree.ElementTree as E
from pathlib import Path

# Profile-style equipment columns, restricted to appearance equipment.
SLOTS = [(1,'Main Hand','v5_slot_rhand',0,0),(2,'Off Hand','v5_slot_lhand',1,0),
 (4,'Headwear','v5_slot_helmet',0,1),(2048,'Shoulder Armor','v5_slot_shoulder',1,1),
 (8,'Chest Armor','v5_slot_armor',0,2),(16,'Gloves','v5_slot_glove',1,2),
 (4096,'Leg Armor','v5_slot_pants',0,3),(32768,'Wings','v5_slot_wing',1,3),
 (32,'Boots','v5_slot_shoes',0,4)]

def build():
    root=E.Element('Screen')
    dialog=E.SubElement(root,'Dialog',name='PrivateWardrobe',frame='0,0,1280,960',text='Wardrobe',
      close_offset='4,1',close_preset='v5_close',flag='close;title;not_visible;not_movable;fittoscreenwidth;fittoscreenheight',
      preset='v5_dialog2',title_height='25',title_offset='0,5')
    scripts=E.SubElement(dialog,'Scripts')
    E.SubElement(scripts,'OnLoad').text='WardrobeNative_OnLoad();'
    E.SubElement(scripts,'OnEvent').text='WardrobeNative_OnEvent(event, ...);'
    def widget(kind,name,text='',preset=None,parent=dialog,flags=None):
        attr=dict(type=kind,name=name,frame='0,0,1,1')
        if kind in ('static','button','editbox','combobox'):attr['font']='ui_default'
        if text:attr['text']=text
        if preset:attr['preset']=preset
        if flags:attr['flag']=flags
        node=E.SubElement(parent,'Widget',attr)
        if kind=='button':
            node.set('style','pushbutton')
            E.SubElement(E.SubElement(node,'Scripts'),'OnClick').text='WardrobeNative_Click(this.name);'
        return node
    widget('container','WardrobeLeftPanel',preset='v5_staticbox',flags='not_active')
    widget('container','WardrobeRightPanel',preset='v5_staticbox',flags='not_active')
    widget('static','WardrobeCharacter')
    widget('static','WardrobeCounts')
    widget('static','WardrobeTarget','Select equipment')
    widget('static','WardrobeDraft','Current equipment')
    widget('static','WardrobeStatus','Loading Wardrobe...')
    widget('static','WardrobeMouseHint','Drag: rotate | Right-drag: move | Wheel: zoom')
    widget('static','WardrobeResults')
    widget('static','WardrobePage')
    widget('static','WardrobeEmpty','No matching appearances')
    widget('htmlview','WardrobeDetails',preset='v5_staticbox')
    for name in ('Tx','Rx','Meta','Preview','Control','Hover','Icons'):
        widget('editbox','WardrobeNative'+name,flags='not_visible')
    for index in range(1,8):
        widget('editbox','WardrobeNativeRx'+str(index),flags='not_visible')
    widget('editbox','WardrobePreviewBounds',flags='not_visible')
    for name,label in [('Refresh','Refresh'),('Collection','Collection'),('Outfits','Saved Outfits'),('Inventory','Inventory'),
      ('Clear','Clear'),('Previous','Previous'),('Next','Next'),('TryOn','Try On'),('Unlock','Unlock Appearance'),
      ('Restore','Restore Appearance'),('Reset','Reset Preview'),('RemoveLocked','Remove Locked Skins'),('Apply','Apply Changes'),
      ('SaveOutfit','Save Current Outfit'),('DeleteOutfit','Delete Outfit'),('ClearTarget','All Equipment Types'),
      ('Left','Left'),('Right','Right'),('Zoom','Zoom'),('Helmet','Helmet'),('Combat','Combat'),('Wings','Wings')]:
        widget('button','Wardrobe'+name,label,'v5_button')
    widget('editbox','WardrobeSearch',preset='v5_editbox')
    widget('combobox','WardrobeFilter',preset='v5_staticbox3')
    for i,name in enumerate(['All','Weapons','Armor','Costumes','Headwear','Shields','Wings'],1):
        widget('button','WardrobeCategory'+str(i),name,'v5_button')
    for mask,label,preset,side,row in SLOTS:
        slot=widget('button','WardrobeSlot'+str(mask),preset=preset)
        slot.set('tooltip',label)
        widget('image','WardrobeSlotIcon'+str(mask))
        widget('image','WardrobeSlotSelected'+str(mask),preset='v5_slot_over',flags='not_active;not_visible')
    scroll=widget('scrollable','WardrobeScroll',flags='vscroll',preset='v5_staticbox')
    scroll.set('scroll_type','auto')
    content=widget('container','WardrobeRows',parent=scroll)
    for i in range(1,37):
        widget('button','WardrobeRow'+str(i),preset='v5_staticbox',parent=content)
        widget('image','WardrobeIcon'+str(i),parent=content)
        widget('image','WardrobeSelected'+str(i),preset='v5_slot_over',parent=content,flags='not_active;not_visible')
        widget('static','WardrobeName'+str(i),parent=content,flags='not_active;truncate;not_multiline;not_wordwrap')
        widget('static','WardrobeState'+str(i),parent=content,flags='not_active;truncate;not_multiline;not_wordwrap')
    modal=E.SubElement(root,'Dialog',name='WardrobeConfirm',frame='420,260,440,280',text='Wardrobe',
      preset='v5_dialog',flag='title;not_visible',title_height='25',title_offset='0,5')
    widget('htmlview','WardrobeConfirmText',parent=modal,preset='v5_staticbox',flags='vscroll')
    widget('static','WardrobeConfirmError',parent=modal)
    widget('editbox','WardrobeOutfitName',parent=modal,preset='v5_editbox')
    widget('combobox','WardrobeUnlockSource',parent=modal,preset='v5_staticbox3')
    widget('button','WardrobeConfirmCancel','Cancel','v5_button',parent=modal)
    widget('button','WardrobeConfirmAccept','Confirm','v5_button',parent=modal)
    root.set('widget_cnt',str(sum(1 for n in root.iter() if n.tag in ('Dialog','Widget'))))
    E.indent(root)
    return E.tostring(root,encoding='unicode',xml_declaration=True)

if __name__=='__main__':Path(__file__).with_name('Wardrobe.xml').write_text(build(),encoding='utf-8')
