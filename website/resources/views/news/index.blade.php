@extends('layouts.app')

@section('title', ($pageTitle ?? 'Latest News') . ' | Aetherfall')
@section('meta_description', 'Read the latest news, announcements, events, and updates from Aetherfall.')

@section('content')
<main class="container">
    <section class="page-intro">
        <div class="eyebrow">Aetherfall</div>
        <h1>{{ $pageTitle ?? 'Latest News' }}</h1>
        <p>{{ $pageIntro ?? 'Release news, changes and notes from the server.' }}</p>
        @isset($guideSearch)
            <form method="GET" action="{{ route('guides') }}" class="portal-search" role="search">
                <input type="search" name="q" value="{{ $guideSearch }}" placeholder="Find a guide" aria-label="Search player guides" maxlength="80">
                <button class="btn" type="submit">SEARCH</button>
            </form>
        @endisset
    </section>

    <div class="news-page-grid">
        @forelse ($news as $article)
            <article class="news-page-card">
                <a href="{{ route('news.show', $article->slug) }}">
                    @if ($article->cover_image)
                        @include('partials.news-cover', ['path'=>$article->cover_image, 'alt'=>$article->title, 'imageClass'=>'news-page-cover'])
                    @else
                        <div class="news-page-placeholder">AION</div>
                    @endif
                </a>

                <div class="news-page-card-content">
                    <div class="news-page-category">{{ $article->category ?: 'Announcement' }}</div>
                    <h2>
                        <a href="{{ route('news.show', $article->slug) }}">{{ $article->title }}</a>
                    </h2>
                    <div class="news-page-date">
                        {{ $article->published_at?->format('d M Y') ?: $article->created_at?->format('d M Y') }}
                    </div>
                    <p>
                        {{ $article->excerpt ?: \Illuminate\Support\Str::limit(strip_tags($article->body), 140) }}
                    </p>
                    <a class="news-page-button" href="{{ route('news.show', $article->slug) }}">READ ARTICLE &rarr;</a>
                </div>
            </article>
        @empty
            <div class="news-page-empty">
                <div class="eyebrow">No News Available</div>
                <h2>{{ isset($guideSearch) ? 'No matching guides.' : 'No published articles yet.' }}</h2>
                <p>{{ isset($guideSearch) ? 'Try another word or clear the search.' : 'New posts will appear here when published.' }}</p>
                @if ($aionFeatures['home'] ?? false)<a class="news-page-button" href="{{ route('home') }}">BACK TO HOME</a>@endif
            </div>
        @endforelse
    </div>

    @if ($news->hasPages())
        <div class="news-page-pagination">{{ $news->links() }}</div>
    @endif
</main>
@endsection
