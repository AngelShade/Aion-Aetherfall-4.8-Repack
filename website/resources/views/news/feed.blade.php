{!! '<'.'?xml version="1.0" encoding="UTF-8"?'.'>' !!}
<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
<channel>
    <title>{{ $websiteSettings['site_name'] }} news</title>
    <link>{{ route('news.index') }}</link>
    <description>{{ $websiteSettings['meta_description'] }}</description>
    <language>en</language>
    <atom:link href="{{ route('news.feed') }}" rel="self" type="application/rss+xml"/>
    @foreach ($articles as $article)
    <item>
        <title>{{ preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F]/u', '', $article->title) }}</title>
        <link>{{ route('news.show', $article->slug) }}</link>
        <guid isPermaLink="true">{{ route('news.show', $article->slug) }}</guid>
        <description>{{ preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F]/u', '', $article->excerpt ?: \Illuminate\Support\Str::limit(strip_tags($article->body), 240)) }}</description>
        <pubDate>{{ ($article->published_at ?? $article->created_at)->toRssString() }}</pubDate>
    </item>
    @endforeach
</channel>
</rss>
