@php
    $shot = str_starts_with((string) $path, 'aetherfall/screenshots/')
        ? collect(config('aetherfall_content.screenshots'))->firstWhere('file', basename($path)) : null;
@endphp
@if ($shot)
    @include('partials.game-shot', ['shot'=>$shot, 'src'=>asset('media/'.$path), 'alt'=>$alt])
@else
    <img class="{{ $imageClass ?? '' }}" src="{{ asset('media/'.$path) }}" alt="{{ $alt }}" loading="lazy" decoding="async">
@endif
