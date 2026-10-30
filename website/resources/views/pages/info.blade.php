@extends('layouts.app')

@section('title', 'Repack Features | Aetherfall')
@section('meta_description', 'Explore the Aion 4.8 NA repack: solo and co-op play, companions, world bots, trading, progression, expanded storage and client tools.')

@section('content')
<main class="container aion-info">
    <section class="page-intro info-intro">
        <div class="eyebrow">The World of Atreia</div>
        <h1>Inside Aetherpack</h1>
        <p>Aetherfall is a complete Aion 4.8 NA repack you install and run yourself. Play solo or host friends, recruit a companion party and explore the custom features included with the client and game services.</p>
        <div class="info-intro-actions">
            <a href="#repack-features" class="btn">EXPLORE THE FEATURES <span aria-hidden="true">↓</span></a>
            @if ($aionFeatures['repack.tools'] ?? false)<a href="{{ route('tools') }}" class="btn btn-outline">ADVENTURE TOOLS <span aria-hidden="true">→</span></a>@endif
            @if ($aionFeatures['download'] ?? false)<a href="{{ route('download') }}" class="btn">DOWNLOAD CENTER <span aria-hidden="true">→</span></a>@endif
            @if ($aionFeatures['news'] ?? false)<a href="{{ route('news.index') }}" class="btn btn-outline">LATEST ANNOUNCEMENTS</a>@endif
        </div>
    </section>

    <section class="section info-section info-repack" id="repack-features" aria-labelledby="repack-title">
        <div class="section-heading">
            <div class="eyebrow">More than the base game</div>
            <h2 id="repack-title">What is in the repack?</h2>
            <p>The base pack and current cumulative update include the features below. Set up your own world, choose your population and difficulty, and keep control of your settings.</p>
        </div>
        <div class="info-release">
            <div>
                <span class="eyebrow">Latest published release · {{ config('aetherfall_content.release.published_on') }}</span>
                <h3>Aetherpack {{ config('aetherfall_content.release.version') }}</h3>
                <p>Smoother windows and camera turning, clearer custom menus, better companion priorities and updates that preserve your customized settings. Includes earlier cumulative updates.</p>
            </div>
            <div class="info-release-actions">
                @if ($aionFeatures['news'] ?? false)<a class="btn" href="{{ route('news.show', 'aetherpack-0-98-4') }}">WHAT'S NEW <span aria-hidden="true">↗</span></a>@endif
                <a class="info-text-link" href="{{ config('aetherfall_content.release.url') }}" target="_blank" rel="noopener noreferrer">Official release notes ↗</a>
            </div>
        </div>
        <div class="info-feature-grid">
            @foreach (config('aetherfall_content.repack_features', []) as $feature)
                <article class="info-feature-card">
                    <span class="eyebrow">{{ $feature['label'] }}</span>
                    <h3>{{ $feature['title'] }}</h3>
                    <ul>
                        @foreach ($feature['items'] as $item)<li>{{ $item }}</li>@endforeach
                    </ul>
                    @if ($aionFeatures['news'] ?? false)<a class="info-text-link" href="{{ route('news.show', $feature['guide']) }}">Read the guide <span aria-hidden="true">↗</span></a>@endif
                </article>
            @endforeach
        </div>
        <p class="info-repack-note">Quest, navigation and encounter coverage continue to be developed. HD textures are optional. This companion website is installed separately; its live panels describe the game installation configured for it.</p>
    </section>

    @php
        $serverState = strtoupper((string) ($serverMetrics['server_status'] ?? 'UNKNOWN'));
        $statusClass = match ($serverState) {
            'ONLINE' => 'status-online',
            'OFFLINE' => 'status-offline',
            default => 'status-unknown',
        };
        $onlinePlayers = $serverMetrics['online_players'] ?? null;
        $onlineBots = $serverMetrics['online_bots'] ?? null;
    @endphp

    <section class="info-status-grid" aria-label="Linked game installation">
        <article class="info-stat-card info-stat-featured">
            <div class="info-stat-top"><span class="info-stat-label">Game Server</span><span class="status-pill {{ $statusClass }}" data-live-info-status><span class="status-indicator"></span><span data-live-info-status-label>{{ $serverState }}</span></span></div>
            <h2>{{ $serverState === 'ONLINE' ? 'Server Online' : ($serverState === 'OFFLINE' ? 'Server Offline' : 'Status Unavailable') }}</h2>
            <p data-live-info-status-detail>{{ $serverMetrics['server_status_detail'] ?? 'Server reachability has not been verified.' }}</p>
            <span class="info-stat-footnote">Use the Aetherfall launcher to enter the game.</span>
        </article>
        <article class="info-stat-card">
            <span class="info-stat-label">Online Now</span>
            <div class="aion-population-counts" aria-live="polite">
                <div><span class="info-stat-label">Players</span><div class="info-stat-value" data-live-info-players>{{ is_numeric($onlinePlayers) ? number_format((int) $onlinePlayers) : '—' }}</div></div>
                <div><span class="info-stat-label">Bots</span><div class="info-stat-value" data-live-info-bots>{{ is_numeric($onlineBots) ? number_format((int) $onlineBots) : '—' }}</div></div>
            </div>
            <p data-live-info-bots-detail>{{ $serverMetrics['bots_detail'] ?? 'Live bot statistics are not currently available.' }}</p>
        </article>
        <article class="info-stat-card">
            <span class="info-stat-label">Game Version</span>
            <div class="info-stat-value info-version">AION {{ $websiteSettings['server_version'] ?: '4.8' }} <span>NA</span></div>
            <p>The pack includes the matching game client.</p>
        </article>
    </section>

    <section class="section info-section">
        <div class="section-heading">
            <div class="eyebrow">Choose Your Allegiance</div>
            <h2>Two Factions. One Atreia.</h2>
            <p>Choose your faction when you create your first character.</p>
        </div>
        <div class="info-faction-grid">
            <article class="info-faction-card info-elyos">
                <div class="faction-mark" aria-hidden="true">E</div>
                <div class="eyebrow">The Light</div>
                <h3>Elyos</h3>
                <p>Begin in Poeta, ascend in Verteron and travel through the Elyos lands.</p>
                <span class="faction-note">{{ $websiteSettings['character_faction_rule'] ?: 'Elyos and Asmodians' }}</span>
            </article>
            <article class="info-faction-card info-asmodian">
                <div class="faction-mark" aria-hidden="true">A</div>
                <div class="eyebrow">The Shadow</div>
                <h3>Asmodians</h3>
                <p>Begin in Ishalgen, ascend in Altgard and travel through the Asmodian lands.</p>
                <span class="faction-note">{{ $websiteSettings['character_faction_rule'] ?: 'Elyos and Asmodians' }}</span>
            </article>
        </div>
    </section>

    <section class="section info-section info-configuration">
        <div class="section-heading">
            <div class="eyebrow">Your installation’s configuration</div>
            <h2>Rates &amp; character rules</h2>
            <p>These are the values entered in this website’s settings for its linked game installation. Each repack owner can configure their own rates and character rules. Account bonuses and specific game systems may use different rates.</p>
        </div>
        <div class="info-detail-list">
            <div class="info-detail-row"><span>Game version</span><strong>Aion {{ $websiteSettings['server_version'] }}</strong></div>
            <div class="info-detail-row"><span>Level cap</span><strong>{{ $websiteSettings['level_cap'] ?: '—' }}</strong></div>
            <div class="info-detail-row"><span>Combat XP / Quest XP</span><strong>×{{ $websiteSettings['xp_rate'] ?: '—' }} / ×{{ $websiteSettings['quest_xp_rate'] ?: '—' }}</strong></div>
            <div class="info-detail-row"><span>Item drops / Quest Kinah</span><strong>×{{ $websiteSettings['drop_rate'] ?: '—' }} / ×{{ $websiteSettings['quest_kinah_rate'] ?: '—' }}</strong></div>
            <div class="info-detail-row"><span>Gathering / Crafting XP</span><strong>×{{ $websiteSettings['gathering_rate'] ?: '—' }} / ×{{ $websiteSettings['crafting_rate'] ?: '—' }}</strong></div>
            <div class="info-detail-row"><span>Character slots</span><strong>{{ $websiteSettings['character_slots'] ?: '—' }}</strong></div>
            <div class="info-detail-row"><span>Factions</span><strong>Elyos and Asmodians · {{ $websiteSettings['character_faction_rule'] }}</strong></div>
            <div class="info-detail-row"><span>Play styles</span><strong>Solo, co-op, quests, dungeons and faction PvP</strong></div>
            <div class="info-detail-row"><span>Published Aetherpack</span><strong>{{ $websiteSettings['repack_version'] ?: 'See Downloads' }}</strong></div>
        </div>
    </section>

    <section class="info-next-step">
        <div class="info-next-copy">
            <div class="eyebrow">Prepare for Your Journey</div>
            <h2>Get started</h2>
            <p>Download the pack, read the player guides or ask in the Aetherfall Discord if you get stuck.</p>
        </div>
        <div class="info-next-actions">
            @if ($aionFeatures['news'] ?? false)<a href="{{ route('news.index') }}" class="btn">READ NEWS <span aria-hidden="true">→</span></a>@endif
            @if ($aionFeatures['download'] ?? false)<a href="{{ route('download') }}" class="btn btn-outline">DOWNLOAD CENTER</a>@endif
            @if ($aionFeatures['support'] ?? false)<a href="{{ route('support') }}" class="info-text-link">Need help? Visit Support ↗</a>@endif
        </div>
    </section>
</main>
@endsection

@push('head')
<style>
.aion-info .info-repack { padding-top:12px; padding-bottom:48px; scroll-margin-top:110px; }
.info-repack .section-heading { max-width:820px; text-align:left; }
.info-repack .section-heading h2::after { left:0; transform:none; }
.info-release { display:flex; align-items:center; justify-content:space-between; gap:35px; margin:28px 0; padding:30px; background:linear-gradient(120deg,#183142,#0d1b29); border:1px solid #4b6f7d; border-left:3px solid #8fe3ec; }
.info-release .eyebrow { color:#8fe3ec; font-size:9px; letter-spacing:1.4px; }
.info-release h3 { margin:11px 0; color:#f0e4c8; font:500 29px Cinzel,serif; }
.info-release p { max-width:670px; margin:0; color:#a9c0ce; font-size:13px; line-height:1.9; }
.info-release-actions { flex:0 0 auto; display:flex; flex-direction:column; align-items:flex-start; gap:13px; }
.info-release-actions .info-text-link { font-size:11px; }
.info-feature-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:18px; }
.info-feature-card { min-width:0; display:flex; flex-direction:column; padding:27px; border:1px solid #294050; border-top:2px solid #4d7b89; background:#0e1d2b; }
.info-feature-card .eyebrow { font-size:9px; letter-spacing:1.4px; color:#8fe3ec; }
.info-feature-card h3 { margin:14px 0 18px; font:500 24px/1.4 Cinzel,serif; color:#eee0c2; }
.info-feature-card ul { margin:0 0 23px; padding-left:17px; color:#a6bdcc; font-size:12px; line-height:1.9; }
.info-feature-card li+li { margin-top:10px; }
.info-feature-card li::marker { color:#6ab3c3; }
.info-feature-card .info-text-link { align-self:flex-start; margin-top:auto; font-size:11px; }
.info-repack-note { margin:24px 0 0; padding:18px 22px; border-left:2px solid #526b7a; background:#0b1723; color:#91aaba; font-size:12px; line-height:1.9; }
@media(max-width:1000px) { .info-feature-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } .info-release { align-items:flex-start; } }
@media(max-width:600px) {
 .aion-info .info-repack { padding-top:6px; padding-bottom:35px; scroll-margin-top:90px; }
 .info-release { flex-direction:column; gap:22px; padding:23px; }.info-release h3 { font-size:26px; }
 .info-feature-grid { grid-template-columns:1fr; gap:14px; }.info-feature-card { padding:24px; }
 .info-feature-card h3 { font-size:24px; }.info-feature-card ul { font-size:13px; }
}
.aion-info { padding-bottom:76px; }
.info-intro { position:relative; padding-top:66px; padding-bottom:42px; }
.info-intro::after { content:""; position:absolute; z-index:-1; top:12px; right:0; width:min(42%,420px); height:210px; background:radial-gradient(ellipse at center,rgba(200,170,112,.12),transparent 68%); pointer-events:none; }
.info-intro p { max-width:720px; line-height:1.9; }
.info-intro-actions { display:flex; flex-wrap:wrap; gap:11px; margin-top:23px; }
.info-intro-actions .btn, .info-next-actions .btn { gap:10px; }
.info-status-grid { display:grid; grid-template-columns:1.25fr 1fr 1fr; gap:15px; }
.info-stat-card { min-width:0; padding:23px; border:1px solid var(--line); background:linear-gradient(145deg,#171c26 0%,#101319 82%); }
.info-stat-featured { border-color:rgba(200,170,112,.32); background:radial-gradient(ellipse at 100% 0%,rgba(200,170,112,.09),transparent 55%),linear-gradient(145deg,#171c26,#101319); }
.info-stat-top { display:flex; flex-wrap:wrap; justify-content:space-between; align-items:center; gap:10px; }
.info-stat-label { color:var(--muted); font-size:10px; letter-spacing:1.8px; text-transform:uppercase; }
.status-pill { display:inline-flex; align-items:center; gap:7px; padding:5px 8px; border:1px solid var(--line); font-size:9px; font-weight:700; letter-spacing:1.2px; }
.status-indicator { width:6px; height:6px; border-radius:50%; background:#a0a5ae; }
.status-online { color:#a8d9b5; border-color:rgba(104,190,128,.3); }
.status-online .status-indicator { background:#68be80; box-shadow:0 0 10px rgba(104,190,128,.5); }
.status-offline { color:#e3a5a5; border-color:rgba(220,105,105,.3); }
.status-offline .status-indicator { background:#dc6969; }
.status-unknown { color:#d0c5a8; border-color:rgba(200,170,112,.3); }
.info-stat-card h2 { margin:18px 0 10px; font-size:clamp(19px,2.2vw,25px); }
.info-stat-card p { margin:12px 0 0; color:var(--muted); font-size:11px; line-height:1.8; overflow-wrap:anywhere; }
.info-stat-value { margin-top:18px; font-family:'Cinzel',serif; font-size:clamp(29px,4vw,42px); line-height:1.2; color:var(--text); }
.info-version { font-size:clamp(24px,3vw,34px); }
.info-version span { color:var(--gold); font-size:.55em; letter-spacing:2px; }
.info-stat-footnote { display:block; margin-top:17px; color:#828894; font-size:9px; line-height:1.6; }
.info-section { padding-top:70px; padding-bottom:0; }
.info-section .section-heading { margin-bottom:23px; }
.info-faction-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; }
.info-faction-card { position:relative; overflow:hidden; min-height:235px; padding:30px; border:1px solid var(--line); background:linear-gradient(135deg,rgba(50,67,91,.35),rgba(15,18,25,.97) 65%); }
.info-asmodian { background:linear-gradient(135deg,rgba(91,47,65,.28),rgba(15,18,25,.97) 65%); }
.info-faction-card::after { content:""; position:absolute; width:190px; height:190px; right:-70px; bottom:-90px; border:1px solid rgba(200,170,112,.13); border-radius:50%; box-shadow:0 0 0 20px rgba(200,170,112,.025),0 0 0 40px rgba(200,170,112,.02); pointer-events:none; }
.faction-mark { float:right; display:grid; place-items:center; width:52px; height:52px; border:1px solid rgba(200,170,112,.42); color:var(--gold); font:28px 'Cinzel',serif; transform:rotate(45deg); margin:10px 12px 0 0; }
.faction-mark::first-letter { transform:rotate(-45deg); }
.info-faction-card h3 { margin:9px 0 12px; font-size:30px; }
.info-faction-card p { max-width:440px; color:#b2b7c2; font-size:12px; line-height:1.8; }
.faction-note { display:block; margin-top:22px; color:#858b96; font-size:9px; letter-spacing:.4px; }
.info-configuration { padding-bottom:0; }
.info-detail-list { border:1px solid var(--line); background:linear-gradient(145deg,#141923,#0f1218); }
.info-detail-row { display:flex; justify-content:space-between; align-items:center; gap:18px; padding:16px 20px; border-bottom:1px solid var(--line); font-size:12px; }
.info-detail-row:last-child { border-bottom:0; }
.info-detail-row > span:first-child { color:#c8cbd2; }
.info-detail-row strong { color:var(--text); font-weight:600; text-align:right; }
.pending-value { color:#a6aab3; font-size:11px; text-align:right; }
.info-next-step { display:flex; align-items:center; justify-content:space-between; gap:30px; margin-top:70px; padding:clamp(25px,4vw,40px); border:1px solid rgba(200,170,112,.24); background:radial-gradient(ellipse at 0% 50%,rgba(200,170,112,.09),transparent 55%),#11151d; }
.info-next-copy { max-width:590px; }
.info-next-copy h2 { margin:10px 0; font-size:clamp(23px,3.2vw,32px); }
.info-next-copy p { margin:0; color:var(--muted); font-size:12px; line-height:1.8; }
.info-next-actions { display:flex; flex-direction:column; align-items:stretch; gap:10px; min-width:190px; }
.info-text-link { padding:7px 0; color:var(--muted); font-size:10px; text-align:center; }
.info-text-link:hover { color:var(--gold); }
@media(max-width:850px) { .info-status-grid { grid-template-columns:1fr 1fr; } .info-stat-featured { grid-column:1/-1; } .info-next-step { align-items:flex-start; flex-direction:column; } .info-next-actions { width:100%; max-width:320px; } }
@media(max-width:560px) { .info-intro { padding-top:40px; } .info-status-grid,.info-faction-grid { grid-template-columns:1fr; } .info-stat-featured { grid-column:auto; } .info-stat-card { padding:20px; } .info-faction-card { padding:24px; } .info-detail-row { align-items:flex-start; padding:14px; font-size:11px; } .pending-value { max-width:48%; } .info-next-step { margin-top:48px; } }
@media(prefers-reduced-motion:reduce) { .info-stat-card,.info-faction-card { transition:none; } }
</style>
@endpush

@push('scripts')
<script>
(() => {
    const status = document.querySelector('[data-live-info-status]');
    const label = document.querySelector('[data-live-info-status-label]');
    const statusDetail = document.querySelector('[data-live-info-status-detail]');
    const players = document.querySelector('[data-live-info-players]');
    const bots = document.querySelector('[data-live-info-bots]');
    const botsDetail = document.querySelector('[data-live-info-bots-detail]');
    if (!status && !players) return;
    const endpoint = @json(url('/api/server-status'));
    let active = true;
    const refresh = async () => {
        if (!active || document.hidden) return;
        try {
            const response = await fetch(endpoint, { headers: { Accept: 'application/json' }, cache: 'no-store', credentials: 'same-origin' });
            if (!response.ok) return;
            const data = await response.json();
            const value = typeof data.server_status === 'string' ? data.server_status.toUpperCase() : 'UNKNOWN';
            if (label) label.textContent = value;
            if (status) {
                status.classList.remove('status-online', 'status-offline', 'status-unknown');
                status.classList.add(value === 'ONLINE' ? 'status-online' : (value === 'OFFLINE' ? 'status-offline' : 'status-unknown'));
            }
            if (statusDetail && typeof data.server_status_detail === 'string') statusDetail.textContent = data.server_status_detail;
            if (players) players.textContent = Number.isFinite(Number(data.online_players)) && data.online_players !== null ? Number(data.online_players).toLocaleString() : '—';
            if (bots) bots.textContent = Number.isFinite(Number(data.online_bots)) && data.online_bots !== null ? Number(data.online_bots).toLocaleString() : '—';
            if (botsDetail && typeof data.bots_detail === 'string') botsDetail.textContent = data.bots_detail;
        } catch (_) { /* Keep the last server-rendered values when polling fails. */ }
    };
    refresh();
    const timer = window.setInterval(refresh, 30000);
    document.addEventListener('visibilitychange', refresh);
    window.addEventListener('pagehide', () => { active = false; window.clearInterval(timer); }, { once: true });
})();
</script>
@endpush
