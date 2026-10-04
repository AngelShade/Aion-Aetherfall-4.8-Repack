# Saved Temporary Bots and party presets

Open Player Companions and refresh once to load the installed controls.

1. On an active Temporary Bot, use **Save Temporary Bot**, or use **Save bot**
   on its Roster card. Active progress is checkpointed through native persistence.
2. After dismissal/logout, choose the Roster's **Saved Temporary Bots** filter
   and **Summon saved bot**. This recruits the same character ID and progress.
3. Recruit any combination of your own eligible alts and Temporary Bots. In
   **Party**, enter a name and use **Save party preset**.
4. In **Roster → Saved party presets**, use **Summon preset** to bring its
   missing members online and restore their roles/orders. Each card identifies
   owned alts separately from Temporary Bots.

Saving the same party name updates that preset. Up to 20 presets are supported,
with one to five companions each. Presets are account-owned; the current player
character cannot also be recruited as its own companion. Native ownership,
faction, alt-level eligibility, available party slots and active-combat rules apply.
An unavailable or online alt fails the complete preflight before any recruitment.

Existing humans and companions stay in the party. If there is insufficient room,
dismiss companions outside the chosen preset first. Summoning an already-active
preset does not create duplicates. Dead active members remain dead awaiting normal
resurrection. Removing a preset removes its bookmark, retaining all characters
and saved Temporary Bot progress.

Temporary Bot progress remains in the game's normal character tables; presets do
not duplicate characters or inventories. Temporary level/build maintenance still
applies when they return. Owned alts retain their configured class, level, gear,
skills and Stigmas. Presets restore only companion AI role/order; existing per-bot
gear/care settings stay attached to the same character.

Saved metadata lives under the deployed server's
`config/playerbots/saved-parties/account-<account-id>.json`. Include that directory,
the per-character settings and the native database in backups. Files are validated
and replaced atomically; corrupt saved-party data is preserved and reported.

Installed and tested through native DB save/dismiss/recruit fixtures and 25 actions
in the actual embedded browser. A human client login/logout/preset playthrough
remains an acceptance check. See `PLAYERBOTS_VALIDATION_20261004.md`.
