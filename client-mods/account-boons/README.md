# Aetherfall Boons (Profile > Account)

The Profile window's Account tab is NCSoft's free-to-play pack panel. Aetherfall uses it for
account bonuses that are earned in game, never sold. The server
(`AetherfallBoonService`) works out each account's boons at login and after a Season Pass
purchase, applies their effects and sends the list in `S_BM_PACK_LIST`
(`SM_PACKAGE_INFO_NOTIFY`, opcode 266: count, then pack id byte + seconds left, 0 = no time limit).

| Pack | Boon | Earned by | Effect |
| --- | --- | --- | --- |
| 1 | Shugo Trade Charter | Season Pass Premium (Kinah) on any character, until season end | +30% hunting/group/quest/gathering/crafting XP and life-skill XP; 84.5% Central Market return instead of 65%; Wardrobe unlocks without a ticket |
| 2 | Grace of the Tower | Season Pass Advanced Premium, until season end | +20% item drops; Energy of Repose refills in 24 h offline instead of 48; +2,000 VT market warehouse |
| 3 | Aetherfall Renown | Combat: levels from 10 (55+ x2, 65 x5); Life: skills from 100 / 5; Legend: quests / 10 + titles x5 | Daily mailed stipend 100 Kinah/point (caps: Combat 500,000, Life 250,000, Legend 250,000); Combat AP, Life skill XP and Legend Repose ladders; 1,000 total: +5% market return; 2,500: +10% item drops |
| 4 | Chronicle of Atreia | Chapters: Ascension ceremony (1007/2009), Abyss entry (1922/2947), The Unfinished Entry (3999), 300 and 800 completed quests | Per chapter: +3% max HP/MP, +2% physical attack, +20 magic boost, +3% physical defence, +10 magic resist |
| 5 | Daeva Veteran's Coffer | A level 65 character | Existing monthly veteran mail (now sent by Aetherfall); timer shows the next month |

Account Type reads **Aetherfall Daeva** (all five NA account-type strings). The server keeps
account status 4, so no client starter restrictions change.

## Client package

`build.py` rewrites `strings/client_strings_bm.xml` in the English `data.pak` and
`client_bm_pack.xml` in `Data/USA/BM/BM.pak` and `Data/BM/BM.pak` (packs 1-5 lose NCSoft's
client-side unrestrict/bonus effects). Every other archive member stays byte-identical.

```
python client-mods/account-boons/build.py --client <client> --output <fresh external stage>
python client-mods/account-boons/install.py --stage <stage> --client <client> --backup <fresh external backup>
```

Install with every Aion client closed. It verifies the live archives still match the build,
keeps the Graphics menu's `data.pak` record and package copy in step, lists the mod in the
launcher's Mods window and records `lastAetherfallBoonsDelivery` in `docs/INSTALLED_MODS.json`.
Restore by copying the backup's files back. Rebuild after any other mod changes these archives.

## Unverified

The native Details renderer consumes retail effect fields, not the long descriptions.
Use `client-mods/account-window/prepare_details.py` and its installer for the localized,
scrollable benefits guide and updated Fame descriptions, including Graphics restore resources.
The list remains earned-only; Account Window includes locked benefits and live progress.
The panel's exact rendering is the in-game acceptance check. Boons refresh at
login and after a Season Pass purchase; new levels, skills and quests count at the next login.
