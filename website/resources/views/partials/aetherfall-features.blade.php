@if ($aionFeatures['news'] ?? false)
@php
    $realmFeatures = [
        ['file'=>'companions.jpg','title'=>'Your party. Your pace.','tab'=>'Build your party','body'=>'Bring friends, recruit your offline alts or choose temporary companions. Give each a role, set your formation and head out together.','guide'=>'party-companions','label'=>'Together, on your terms'],
        ['file'=>'questing.jpg','title'=>'Follow your curiosity.','tab'=>'Explore Atreia','body'=>'Take the long way through Atreia. Quest, gather, craft and discover dungeons, with independent world bots pursuing their own adventures.','guide'=>'factions-classes-and-rates','label'=>'A world worth getting lost in'],
        ['file'=>'fortress.jpg','title'=>'Make it your adventure.','tab'=>'Discover the extras','body'=>'Trade through Central Market, collect Wardrobe looks and explore Aetherpass, Journey and Account Fame. Expanded bags and warehouses give your collection room to grow.','guide'=>'aetherfall-in-game-features','label'=>'Familiar world. More possibilities.'],
    ];
@endphp
<section class="section portal-features" data-feature-showcase aria-labelledby="af-discover-title">
    <div class="section-heading af-editorial-heading">
        <div class="eyebrow">Discover the repack <span aria-hidden="true">/ 01</span></div>
        <h2 id="af-discover-title">Atreia is better<br>with a party.</h2>
        <p>A complete Aion 4.8 NA repack for your own machine or a shared adventure. Bring friends, recruit companions and choose how you play.</p>
    </div>
    <div class="af-feature-tabs" data-feature-tabs role="tablist" aria-label="Discover ways to play" hidden>
        @foreach ($realmFeatures as $feature)
            <button type="button" role="tab" id="af-tab-{{ $feature['guide'] }}" aria-controls="af-panel-{{ $feature['guide'] }}" aria-selected="{{ $loop->first ? 'true' : 'false' }}" tabindex="{{ $loop->first ? '0' : '-1' }}" data-feature-tab="{{ $loop->index }}">
                <span aria-hidden="true">0{{ $loop->iteration }}</span>{{ $feature['tab'] }}<span aria-hidden="true">↗</span>
            </button>
        @endforeach
    </div>
    <div class="portal-feature-grid" data-feature-panels>
        @foreach ($realmFeatures as $feature)
            @php($shot = collect(config('aetherfall_content.screenshots'))->firstWhere('file', $feature['file']))
            <article class="portal-feature-card" id="af-panel-{{ $feature['guide'] }}" data-feature-panel>
                <a href="{{ route('news.show', $feature['guide']) }}" aria-label="Read the guide: {{ $feature['tab'] }}">
                    @include('partials.game-shot', ['shot'=>$shot, 'src'=>asset('images/aetherfall/screenshots/'.$shot['file'])])
                </a>
                <div class="portal-feature-copy">
                    <span class="eyebrow">{{ $feature['label'] }}</span>
                    <h3>{{ $feature['title'] }}</h3><p>{{ $feature['body'] }}</p>
                    <a href="{{ route('news.show', $feature['guide']) }}">EXPLORE THE GUIDE <span aria-hidden="true">↗</span></a>
                </div>
            </article>
        @endforeach
    </div>
    @if ($aionFeatures['info'] ?? false)
        <p class="portal-gallery-link"><span>Captured in the Aetherfall client</span><a href="{{ route('gallery') }}">Step into the gallery <span aria-hidden="true">↗</span></a></p>
    @endif
</section>
@endif
