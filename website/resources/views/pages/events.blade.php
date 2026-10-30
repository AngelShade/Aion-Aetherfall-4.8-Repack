@extends('layouts.app')
@section('title', 'Events & Siege Schedule | Aetherfall')
@section('meta_description', 'Plan your week in Atreia with fortress siege times and published Aetherfall events.')
@section('content')
<main class="container player-hub"><section class="page-intro"><div class="eyebrow">Plan your next adventure</div><h1>Events &amp; Sieges</h1><p>Gather your party and choose your next battle.</p></section>@include('community.navigation')
<div class="hub-result-heading"><h2>Weekly siege timetable</h2><span>{{ $schedule['custom'] ? 'Connected schedule file' : 'Aetherpack default schedule' }}</span></div>
<p class="hub-note">{{ $schedule['timezone'] ? 'Times use '.$schedule['timezone'].'.' : 'All times follow the game server’s clock. No browser timezone conversion is applied.' }} @if (! $schedule['custom'])You can customise the timetable in your own repack; the included default may differ from your installation.@endif These are scheduled starts, rather than confirmation of a battle in progress.</p>
@if ($schedule['next'])<div class="hub-callout"><span class="eyebrow">Next scheduled start</span><h3>{{ $schedule['next']['name'] }}</h3><time datetime="{{ $schedule['next']['at']->toAtomString() }}">{{ $schedule['next']['at']->format('l, d M · H:i T') }}</time></div>@endif
<nav class="hub-day-tabs" aria-label="Choose a weekday">@foreach (\App\Services\AionEventSchedule::DAYS as $key => $label)<a href="{{ route('events', ['day' => $key]) }}#timetable" @if ($selectedDay === $key) aria-current="page" @endif>{{ $label }}</a>@endforeach</nav>
<section id="timetable" class="hub-timetable" aria-label="{{ \App\Services\AionEventSchedule::DAYS[$selectedDay] }} schedule">
@if (! $schedule['available'])<div class="hub-empty"><h2>Schedule unavailable</h2><p>The siege timetable could not be read. Check your repack’s schedule settings or ask the person hosting your game.</p></div>
@else
@forelse ($schedule['days'][$selectedDay] as $entry)<article class="hub-event"><span class="hub-event-time">{{ $entry['time'] }}</span><div><small>{{ $entry['type'] }}</small><h3>{{ $entry['name'] }}</h3></div><span class="hub-muted">{{ \App\Services\AionEventSchedule::DAYS[$selectedDay] }}</span></article>
@empty<div class="hub-empty"><h2>A quiet day on the front</h2><p>No siege starts are listed for this day.</p></div>@endforelse
@endif
</section>
@if ($schedule['unsupported'])<p class="hub-note">{{ $schedule['unsupported'] }} custom scheduling rules could not be displayed. Check the custom timetable with the person hosting your game.</p>@endif
@if ($announcements->isNotEmpty())<section class="hub-section"><div class="hub-result-heading"><h2>Event announcements</h2></div><div class="hub-member-grid">@foreach ($announcements as $article)<a class="hub-member" href="{{ route('news.show', $article->slug) }}"><small>{{ $article->published_at?->format('d M Y') }}</small><strong>{{ $article->title }}</strong><span>{{ $article->excerpt }}</span></a>@endforeach</div></section>@endif
@if (filled($websiteSettings['discord_url']))<div class="hub-callout"><div><h3>Make plans with other Daevas</h3><p>Find a group or follow community announcements on Discord.</p></div><a class="btn btn-outline" href="{{ $websiteSettings['discord_url'] }}" target="_blank" rel="noopener noreferrer">JOIN DISCORD ↗</a></div>@endif
</main>
@endsection
