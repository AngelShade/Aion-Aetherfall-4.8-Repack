# Companion collection artwork

Authored with built-in image generation for Aetherfall's illustrated collection
cards. These are class illustrations, not captures of individual character faces
or equipment. Native identity, level, origin, status and actions remain live data.

- `castle-background.png`: fixed collection-window backdrop.
- `ornate-frame.png`: transparent ornamental overlay shared by front and reverse.
  Its violet/silver decoration does not represent a combat rank or rolled grade.
- `window-frame.png`: shared nine-slice silver/gold ornamental window chrome.
- `class-portraits.png`: square four-column, three-row atlas, read left to right:
  Templar, Gladiator, Assassin, Ranger; Sorcerer, Spiritmaster, Cleric, Chanter;
  Gunner, Aethertech, Bard, generic Daeva. Starting classes use their family art.

The client loads one shared atlas for the visible page. Card flipping is local
presentation; it does not recruit, purchase, remove or alter character state.

The complete window uses the authored raster skin:

- `skin-button-navy.png`, `skin-button-gold.png`: nine-slice action/control plates.
- `skin-button-selected.png`: blue/silver selection and enabled-toggle state.
  Gold is reserved for primary actions; tabs/navigation never use a yellow fill.
- `skin-field.png`: recessed field plate; `skin-chevron.png`: dropdown overlay.
- `skin-panel.png`: framed material surface shared by panels and window chrome.
- `skin-icons.png`: four-by-four painted action-icon atlas, left to right:
  summon, equipment, focus, progress; care, more, companions, save;
  transfer, mail, retire, collection; create, quests, refresh, dropdown.

Retain these authored materials when extending any Companions view. Flat CSS
gradients and line-drawn substitutes do not replace this skin. Fields retain
their real native select/input controls; image overlays do not own their state.
Asset URLs are versioned; bump the art URL if its bytes change.

The window renders in software, so art is sized close to how it is painted. Nine-slice
CSS offsets match these pixel sizes; scale them with the image. Keep class-portraits.png$(
)at 1254x1254, because the collection cards depend on that size.
