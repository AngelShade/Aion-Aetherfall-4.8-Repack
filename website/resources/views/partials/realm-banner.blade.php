@if ($aionFeatures['download'] ?? false)
<section class="realm-banner" aria-label="Aetherfall downloads">
    <img class="realm-banner-art" src="{{ asset('images/aetherfall/brand/launcher-banner.png') }}" alt="" loading="lazy" decoding="async" width="1586" height="992">
    <div class="realm-banner-shade" aria-hidden="true"></div>
    <img class="realm-banner-frame" src="{{ asset('images/aetherfall/brand/frame.png') }}" alt="" loading="lazy" decoding="async" width="1586" height="992">
    <div class="realm-banner-copy">
        <span class="eyebrow">Aion · Solo and co-op</span>
        @include('partials.brand-wordmark')
        <h2>Ready to play?</h2>
        <p>Install the repack, bring your world online and build your party.</p>
        <div class="realm-banner-actions">
            <a href="{{ route('download') }}" class="btn">GET AETHERPACK <span aria-hidden="true">↗</span></a>
            @if ($aionFeatures['news'] ?? false)<a href="{{ route('guides') }}" class="realm-banner-guide">PLAYER GUIDES →</a>@endif
        </div>
    </div>
</section>
@endif
