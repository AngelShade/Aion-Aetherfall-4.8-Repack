<?php

// Public starter copy grounded in the maintained README, installed features and
// published v0.96.6 release. WebsiteContent only inserts missing entries.
return [
    'settings' => [
        'site_name'=>'Aetherfall', 'server_name'=>'Aetherfall', 'tagline'=>'Play solo or with friends',
        'meta_description'=>'Aion with party companions, world bots and Aetherfall’s custom features. Downloads, guides and server news.',
        'server_version'=>'4.8', 'server_rate'=>'XP x1 · Quest Kinah x5 · Gathering x3', 'level_cap'=>'65',
        'xp_rate'=>'1', 'quest_xp_rate'=>'1', 'drop_rate'=>'1', 'quest_kinah_rate'=>'5', 'gathering_rate'=>'3',
        'crafting_rate'=>'1', 'character_slots'=>'8', 'character_faction_rule'=>'One faction per account',
        'discord_url'=>'https://discord.com/invite/cpRYCRsSnR',
        'release_url'=>'https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.96.6',
        'repack_version'=>'0.96.6',
    ],
    'articles' => [
        [
            'slug'=>'aetherfall-play-your-way', 'title'=>'Welcome to Aetherfall', 'category'=>'Announcement',
            'excerpt'=>'Play Aion solo or with friends, build a companion party and explore alongside world bots.',
            'cover'=>'aetherfall/hero-v1.png',
            'body'=>'<p>Aetherfall is an Aion server built for solo and co-op play. You can quest with friends, recruit companions or bring your own offline characters into your party.</p><h2>What you can play</h2><p>Choose Elyos or Asmodians and level to 65. Explore the world, run dungeons, craft, gather and take part in faction PvP. Independent world bots travel and play around the world; your party companions follow your orders.</p><h2>What Aetherfall adds</h2><p>The pack includes companion controls, Central Market, Wardrobe, Season Pass and larger inventory and warehouse layouts. These are available inside the game.</p><p><a href="/guides">Read the player guides</a> or <a href="/download">download the installer</a>. The server is still being developed, so some quests, routes and encounters need more work.</p>',
        ],
        [
            'slug'=>'aetherpack-0-96-6', 'title'=>'Aetherpack 0.96.6 is available', 'category'=>'Update',
            'excerpt'=>'Get the current installer kit, or use Update Repack in Setup for an existing installation.',
            'cover'=>'aetherfall/screenshots/fortress.jpg',
            'body'=>'<p>Aetherpack 0.96.6 was published on 24 October. The release includes the current downloader, Setup and update archive.</p><h2>Already playing?</h2><p>Close the game and stop the game servers normally. Leave the database running, open Aetherfall Setup and choose <strong>Update Repack</strong>. Start the servers again after the update finishes.</p><h2>New installation</h2><p>Download the installer kit from the <a href="/download">download page</a>, extract it and run Aetherfall Setup.exe. Choose a folder and use Download &amp; Install.</p><p>The public repack and the development server may have different changes. Use the <a href="https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.96.6" target="_blank">release page</a> for the files and notes for this version.</p>',
        ],
        [
            'slug'=>'development-notes-october-24', 'title'=>'Companion and world-bot work', 'category'=>'Update',
            'excerpt'=>'Recent development focuses on looting, recovery, water safety and movement. Report anything that gets stuck.',
            'cover'=>'aetherfall/screenshots/questing.jpg',
            'body'=>'<p>Recent work on the connected development server covers corpse looting, quest loot, healing and recovery, water safety and bot movement. World-bot population and legion behavior have also received changes.</p><p>These systems still need play testing. If a companion gets stuck, stops fighting or misses a quest step, report the map, quest or NPC name and what happened. A screenshot helps.</p><p>Development changes are not automatically part of every public repack. Check the release notes before updating. You can send reports in the <a href="https://discord.com/invite/cpRYCRsSnR" target="_blank">Aetherfall Discord</a>.</p>',
        ],
        [
            'slug'=>'getting-started', 'title'=>'Install, update and start playing', 'category'=>'Guide',
            'excerpt'=>'A short setup guide for a new installation and an existing repack.', 'cover'=>'aetherfall/hero-v1.png',
            'body'=>'<h2>New installation</h2><ol><li>Download the installer kit from the <a href="/download">download page</a> and extract it.</li><li>Run Aetherfall Setup.exe, choose an installation folder and click Download &amp; Install. If interrupted, reopen Setup and choose Resume. Use Local .7z if you already have the base archive.</li><li>Click Update Repack to get the current update.</li><li>Open Server Control and start the database and servers. Wait for GameServer to finish loading.</li><li>Click PLAY. The default repack can create a game account on first login; you can also use this website’s registration page for the server connected here.</li></ol><h2>Before you install</h2><p>Use 64-bit Windows and leave room for both the downloaded archive and the extracted game. The client, Java and database are included in the pack. The downloader ZIP itself is only the installer kit.</p><h2>Updating and closing</h2><p>For an update, close the game, stop the game servers and leave the database running. Use Update Repack, wait for it to finish, then start the servers again. When you finish playing, stop the game servers normally and stop the database last.</p><p>Keep Client, Server and Control together. The default pack connects to the same computer. Playing across a network needs matching connection settings.</p>',
        ],
        [
            'slug'=>'party-companions', 'title'=>'Build and control your companion party', 'category'=>'Guide',
            'excerpt'=>'Recruit offline alts or temporary companions, choose roles and use the party controls.',
            'cover'=>'aetherfall/screenshots/companions.jpg',
            'body'=>'<p>Open Player Companions in the game to recruit an offline character from your account or a temporary companion. Owned alts keep their class, level, equipment and progress.</p><h2>Start with the controls</h2><p><strong>Follow</strong> brings the party with you. <strong>Stay</strong> holds their position. <strong>Attack</strong> sends them into the fight, while <strong>Passive</strong> stops attacks. Use the role, skill and equipment controls to set up tanks, healers and damage dealers.</p><h2>Spacing and targets</h2><p>Choose a formation and a 2, 4 or 8 metre distance. Use Pull to bring an enemy back to the party. Kill marks focus a target; CC marks help reserve a target for crowd control. Clear marks when the plan changes.</p><h2>Quests and supplies</h2><p>Owned alts can share eligible quest completions while grouped. Keep them supplied with the gear, consumables and resources their class needs. Trading, mail and storage follow the normal in-game access rules.</p><p>Companions follow party orders. World bots are separate characters that choose their own activities. If a route or quest step fails, include the location and quest name in your report.</p>',
        ],
        [
            'slug'=>'aetherfall-in-game-features', 'title'=>'Market, Wardrobe and other in-game features', 'category'=>'Guide',
            'excerpt'=>'Where Aetherfall’s custom features fit into normal play.', 'cover'=>'aetherfall/screenshots/fortress.jpg',
            'body'=>'<h2>Central Market</h2><p>Buy and sell through the in-game Central Market. Its history and storage pages help you keep track of trades and collect items.</p><h2>Wardrobe and Season Pass</h2><p>Use Wardrobe for appearance features and the Season Pass window for its tasks and rewards. The in-game Cash Shop is a separate window with its own prices and balance.</p><h2>Inventory and storage</h2><p>The pack includes larger inventory and warehouse layouts, search and filtering. Previously earned inventory expansions are kept.</p><h2>Your account</h2><p>The website lets you register, sign in, view your characters and change your game password. In-game account features such as Account Fame stay in the game. Website Donation Coins are not automatically converted into an in-game currency.</p>',
        ],
        [
            'slug'=>'factions-classes-and-rates', 'title'=>'Factions, classes and server rates', 'category'=>'Guide',
            'excerpt'=>'Choose a faction and class, and check the rates on the connected server.', 'cover'=>'aetherfall/screenshots/taloc.jpg',
            'body'=>'<p>The connected server has a level cap of 65 and eight character slots per account. Accounts use one faction: Elyos or Asmodians.</p><h2>Choose a class</h2><p>Warriors become Gladiators or Templars. Scouts become Assassins or Rangers. Mages become Sorcerers or Spiritmasters. Priests become Clerics or Chanters. Engineers can become Gunners or Aethertechs, and Artists become Songweavers.</p><h2>Regular-account rates</h2><p>Combat and quest experience are x1, item drops are x1, quest Kinah is x5, and gathering experience and gathered amounts are x3. Crafting experience is x1. See <a href="/info">Server Information</a> for the current published settings.</p><p>Premium membership and specific game systems can use different rates. The public repack can also be configured differently from this development server.</p>',
        ],
    ],
    'downloads' => [
        ['slug'=>'aetherfall-installer-kit', 'title'=>'Aetherfall installer kit', 'type'=>'installer', 'version'=>'0.96.6',
            'description'=>'Start here for a new installation. Extract the ZIP and run Aetherfall Setup.exe. It downloads the full pack and includes the download tools.',
            'file_size_bytes'=>8653422, 'download_url'=>'https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/download/v0.96.6/Aetherfall-Downloader.zip'],
        ['slug'=>'aetherfall-setup', 'title'=>'Setup only', 'type'=>'installer', 'version'=>'0.96.6',
            'description'=>'For an existing installer folder. Keep its settings and download tools. New players should use the installer kit above.',
            'file_size_bytes'=>5608448, 'download_url'=>'https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/download/v0.96.6/Aetherfall-Setup.exe'],
        ['slug'=>'aetherfall-manual-update', 'title'=>'Manual update archive', 'type'=>'manual_patch', 'version'=>'0.96.6',
            'description'=>'For an existing installation. Setup → Update Repack is the normal way to update. Check the release notes before applying files manually.',
            'file_size_bytes'=>641298466, 'download_url'=>'https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/download/v0.96.6/Aetherfall-Patch-0.96.6.zip'],
    ],
    'faqs' => [
        ['question'=>'Can I play solo?', 'answer'=>'Yes. You can recruit offline alts or temporary companions into your party. Some quests, routes and encounters are still being worked on.', 'category'=>'Playing'],
        ['question'=>'Can I play with friends?', 'answer'=>'Yes. The default repack connects to the same computer. Playing from other computers needs matching server and client connection settings.', 'category'=>'Playing'],
        ['question'=>'Are world bots the same as my companions?', 'answer'=>'No. Your companions follow party orders. Independent world bots choose their own activities and can appear in the world alongside players.', 'category'=>'Companions'],
        ['question'=>'What do I download first?', 'answer'=>'Use the Aetherfall installer kit on the Download page. Extract it and run Aetherfall Setup.exe. The small ZIP is not the full client; Setup downloads the full pack.', 'category'=>'Installation'],
        ['question'=>'How do I update without losing my characters?', 'answer'=>'Close the game and stop the game servers normally. Leave the database running, then use Setup → Update Repack. Start the servers after it finishes. Keep your existing settings and data folders.', 'category'=>'Installation'],
        ['question'=>'Can I use my existing game account on this site?', 'answer'=>'Yes. The player login uses accounts from the connected server. Website administrators have a separate control-panel login.', 'category'=>'Account'],
        ['question'=>'How do I open the admin panel on my own computer?', 'answer'=>'Double-click Admin.cmd in the standalone website folder. It opens a short-lived local administrator sign-in. Website Tools.cmd can also create a separate email-and-password administrator.', 'category'=>'Website'],
        ['question'=>'Why is playtime missing or lower than expected?', 'answer'=>'The website starts recording activity when its integration is installed. Older playtime and login history cannot be recovered from that record.', 'category'=>'Account'],
        ['question'=>'How do I report a bug?', 'answer'=>'Use the Aetherfall Discord linked below. Include your repack version, the map, quest or NPC, what you did and what went wrong. Attach a screenshot if it helps.', 'category'=>'Support'],
        ['question'=>'Does the website sell items or collect payments?', 'answer'=>'No payment method is configured here. The in-game Cash Shop and the website Donation Coin display are separate features. No payment is required to use this local website.', 'category'=>'Account'],
    ],
    'screenshots' => [
        ['file'=>'companions.jpg','title'=>'A companion party in Inggison','caption'=>'Party formation outside Inggison Illusion Fortress.','x'=>450,'y'=>90,'width'=>1180,'height'=>663.75],
        ['file'=>'questing.jpg','title'=>'Questing in Tolbas Forest','caption'=>'A small party exploring and fighting in Verteron.','x'=>470,'y'=>130,'width'=>1120,'height'=>630],
        ['file'=>'taloc.jpg','title'=>'Inside Taloc’s Hollow','caption'=>'Taloc’s Soul and the paths through the hollow.','x'=>410,'y'=>110,'width'=>1190,'height'=>669.375],
        ['file'=>'fortress.jpg','title'=>'At Eltnen Fortress','caption'=>'The fortress, flight routes and local services.','x'=>450,'y'=>90,'width'=>1170,'height'=>658.125],
    ],
];
