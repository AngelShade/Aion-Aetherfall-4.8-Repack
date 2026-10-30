@php
    $exploreLinks = collect([
        ['feature'=>'news','route'=>'guides','label'=>'Find your way','title'=>'Player guides','copy'=>'Companions, classes and your first steps in Atreia.','image'=>'questing.jpg'],
        ['feature'=>'info','route'=>'gallery','label'=>'A glimpse of Atreia','title'=>'Explore the gallery','copy'=>'Parties, places and moments from the game.','image'=>'companions.jpg'],
        ['feature'=>'download','route'=>'download','label'=>'Your adventure awaits','title'=>'Get Aetherpack','copy'=>'Find the client, setup and the latest downloads.','image'=>'fortress.jpg'],
    ])->filter(fn ($link) => ($aionFeatures[$link['feature']] ?? false) && !request()->routeIs($link['route']))->take(2);
@endphp
@if ($exploreLinks->isNotEmpty())
<aside class="container af-continue" aria-labelledby="af-continue-title">
    <div class="af-continue-heading"><span class="eyebrow">There is more to discover</span><h2 id="af-continue-title">Where to next?</h2></div>
    <div class="af-continue-grid">
        @foreach ($exploreLinks as $link)
            <a class="af-continue-card" href="{{ route($link['route']) }}">
                <img src="{{ asset('images/aetherfall/screenshots/'.$link['image']) }}" alt="" loading="lazy" decoding="async" width="120" height="100">
                <span><small>{{ $link['label'] }}</small><strong>{{ $link['title'] }}</strong><span>{{ $link['copy'] }}</span></span>
                <b aria-hidden="true">↗</b>
            </a>
        @endforeach
    </div>
</aside>
@endif
