@if ($aionFeatures['news'] ?? false)
<section class="section portal-features">
    <div class="section-heading">
        <div class="eyebrow">Playing Aetherfall</div>
        <h2>Your party. Your pace.</h2>
        <p>Bring friends, recruit your alts or build a companion party. The world has independent bots too.</p>
    </div>
    <div class="portal-feature-grid">
        @foreach ([
            ['file'=>'companions.jpg','title'=>'Party companions','body'=>'Recruit offline alts or temporary companions. Set roles, formations and combat orders.','guide'=>'party-companions'],
            ['file'=>'questing.jpg','title'=>'A world to explore','body'=>'Quest, gather, craft and run dungeons. World bots follow their own activities.','guide'=>'factions-classes-and-rates'],
            ['file'=>'fortress.jpg','title'=>'More in the game','body'=>'Central Market, Wardrobe, Season Pass and larger inventory and storage layouts.','guide'=>'aetherfall-in-game-features'],
        ] as $feature)
            @php($shot = collect(config('aetherfall_content.screenshots'))->firstWhere('file', $feature['file']))
            <article class="portal-feature-card">
                <a href="{{ route('news.show', $feature['guide']) }}" aria-label="{{ $feature['title'] }}">
                    @include('partials.game-shot', ['shot'=>$shot, 'src'=>asset('images/aetherfall/screenshots/'.$shot['file'])])
                </a>
                <div class="portal-feature-copy">
                    <h3>{{ $feature['title'] }}</h3><p>{{ $feature['body'] }}</p>
                    <a href="{{ route('news.show', $feature['guide']) }}">READ GUIDE →</a>
                </div>
            </article>
        @endforeach
    </div>
    <p class="portal-gallery-link"><a href="{{ route('gallery') }}">See more screenshots from the pack →</a></p>
</section>
@endif
