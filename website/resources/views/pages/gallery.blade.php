@extends('layouts.app')
@section('title', 'Screenshots | Aetherfall')
@section('meta_description', 'In-game screenshots from Aetherfall: companion parties, questing and places around Atreia.')
@section('content')
<main class="container portal-gallery">
    <section class="page-intro">
        <div class="eyebrow">In the game</div><h1>Around Aetherfall</h1>
        <p>Screenshots from the Aetherfall client. Open an image to view the full screenshot.</p>
    </section>
    <div class="portal-gallery-grid">
        @foreach (config('aetherfall_content.screenshots') as $shot)
            <figure class="portal-gallery-card">
                <a href="{{ asset('images/aetherfall/screenshots/'.$shot['file']) }}" target="_blank" rel="noopener noreferrer">
                    @include('partials.game-shot', ['shot'=>$shot, 'src'=>asset('images/aetherfall/screenshots/'.$shot['file'])])
                </a>
                <figcaption><h2>{{ $shot['title'] }}</h2><p>{{ $shot['caption'] }}</p></figcaption>
            </figure>
        @endforeach
    </div>
</main>
@endsection
