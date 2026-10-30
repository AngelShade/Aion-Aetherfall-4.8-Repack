
@extends('layouts.app')

@section('title', ($article->meta_title ?: $article->title) . ' | ' . ($websiteSettings['site_name'] ?? 'Aetherfall'))
@section('og_title', $article->meta_title ?: $article->title)
@section('og_type', 'article')

@section('meta_description', $article->meta_description ?: ($article->excerpt ?: \Illuminate\Support\Str::limit(strip_tags($article->body), 155)))
@section('og_description', $article->meta_description ?: ($article->excerpt ?: \Illuminate\Support\Str::limit(strip_tags($article->body), 155)))
@if ($article->cover_image)
    @section('og_image', asset('media/' . $article->cover_image))
@endif

@if (!($isPreview ?? false))
    @section('canonical_url')
        <link rel="canonical" href="{{ route('news.show', $article->slug) }}">
        <meta property="og:url" content="{{ route('news.show', $article->slug) }}">
    @endsection
@endif

@section('meta_extra')
    @if ($isPreview ?? false)
        <meta name="robots" content="noindex, nofollow">
    @endif
@endsection

@section('content')
    @if ($isPreview ?? false)
        <div style="background:#382d12;color:#f4d58a;padding:12px 20px;text-align:center;border-bottom:1px solid #8d6b2d;">
            PREVIEW MODE — This article is not being published by this preview.
        </div>
    @endif

    <main class="container">
        <section class="page-intro">
            <div class="eyebrow">
                <a href="{{ $article->category === 'Guide' ? route('guides') : route('news.index') }}">{{ $article->category === 'Guide' ? 'Player Guides' : 'News & Announcements' }}</a>
            </div>

            <h1>{{ $article->category === 'Guide' ? 'Player Guide' : 'Repack News' }}</h1>
            <p>{{ $article->category === 'Guide' ? 'Help with playing Aetherfall.' : 'Repack releases, development notes and community updates.' }}</p>
        </section>

        <div class="news-detail-layout">
            <article class="news-detail-card">
                <header class="news-detail-header">
                    <div class="news-page-category">
                        {{ $article->category ?: 'Announcement' }}
                    </div>

                    <h1>{{ $article->title }}</h1>

                    <div class="news-detail-meta">
                        Published
                        {{ $article->published_at?->format('d M Y') ?: $article->created_at?->format('d M Y') }}
                        &nbsp; · &nbsp; Aetherfall Team

                        @if ($article->is_featured)
                            &nbsp; · &nbsp; Featured Article
                        @endif
                    </div>
                </header>

                @if ($article->cover_image)
                    @include('partials.news-cover', ['path'=>$article->cover_image, 'alt'=>$article->title, 'imageClass'=>'news-detail-cover'])
                @endif

                <div class="news-detail-body">
                    @if ($article->excerpt)
                        <div class="news-detail-excerpt">
                            {{ $article->excerpt }}
                        </div>
                    @endif

                    <div class="news-detail-content">
                        <div class="article-content">
                            {!! $article->body !!}
                        </div>
                    </div>
                </div>

                <footer class="news-detail-footer">
                    <a
                        class="news-page-button"
                        href="{{ route('news.index') }}"
                    >
                        &larr; BACK TO NEWS
                    </a>
                </footer>
            </article>

            <aside class="news-detail-sidebar">
                <section class="news-sidebar-panel">
                    <h2>Repack Features</h2>

                    <p class="muted">
                        Explore the companions, custom services, progression and client tools included in Aetherpack.
                    </p>

                    <a class="news-page-button" href="{{ url('/info') }}">
                        REPACK FEATURES &rarr;
                    </a>
                </section>

                <section class="news-sidebar-panel">
                    <h2>Latest News</h2>

                    @forelse ($sidebarNews as $newsItem)
                        <a
                            class="news-sidebar-item"
                            href="{{ route('news.show', $newsItem->slug) }}"
                        >
                            <strong>{{ $newsItem->title }}</strong>

                            <small>
                                {{ $newsItem->published_at?->format('d M Y') ?: $newsItem->created_at?->format('d M Y') }}
                            </small>
                        </a>
                    @empty
                        <p class="muted">
                            No other news articles are available yet.
                        </p>
                    @endforelse

                    <a class="news-page-button" href="{{ route('news.index') }}">
                        VIEW ALL NEWS &rarr;
                    </a>
                </section>
            </aside>
        </div>
    </main>
@endsection
