@extends('layouts.app')
@section('title', 'Player Rankings & Character Search | Aetherfall')
@section('meta_description', 'Find Aetherfall characters and compare Abyss Points, Glory Points, PvP kills and levels.')
@section('content')
<main class="container player-hub">
    <section class="page-intro"><div class="eyebrow">The Daevas of Atreia</div><h1>Player Rankings</h1><p>Find a character, explore their profile or see who leads your faction and class.</p></section>
    @include('community.navigation')
    <form method="GET" action="{{ route('rankings') }}" class="hub-filters" role="search">
        <label>Character name<input type="search" name="q" value="{{ $filters['q'] }}" maxlength="50" placeholder="Find a Daeva…"></label>
        <label>Rank by<select name="sort">@foreach (\App\Services\AionCommunity::METRICS as $key => $label)<option value="{{ $key }}" @selected($filters['sort'] === $key)>{{ $label }}</option>@endforeach</select></label>
        <label>Faction<select name="race"><option value="">Both factions</option><option value="ELYOS" @selected($filters['race'] === 'ELYOS')>Elyos</option><option value="ASMODIANS" @selected($filters['race'] === 'ASMODIANS')>Asmodians</option></select></label>
        <label>Class<select name="class"><option value="">All classes</option>@foreach (\App\Services\AionCommunity::classes() as $key => $label)<option value="{{ $key }}" @selected($filters['class'] === $key)>{{ $label }}</option>@endforeach</select></label>
        <button class="btn" type="submit">SHOW PLAYERS</button><a class="hub-text-link" href="{{ route('rankings') }}">Reset</a>
    </form>
    <div class="hub-result-heading"><h2>{{ \App\Services\AionCommunity::METRICS[$filters['sort']] }}</h2><span>Top 100 matching characters · saved game statistics</span></div>
    @if (! $available)
        @include('community.offline')
    @elseif ($players->isEmpty())
        <div class="hub-empty"><h2>No characters found</h2><p>Try another name, faction or class. Characters appear after they have been created in this world.</p></div>
    @else
        <div class="hub-table-wrap"><table class="hub-table"><caption class="hub-sr-only">Characters ordered by {{ \App\Services\AionCommunity::METRICS[$filters['sort']] }}</caption><thead><tr><th scope="col">#</th><th scope="col">Character</th><th scope="col">Faction / Class</th><th scope="col">Level</th><th scope="col">{{ \App\Services\AionCommunity::METRICS[$filters['sort']] }}</th><th scope="col">Legion</th></tr></thead><tbody>
        @foreach ($players as $player)
            <tr><td class="hub-rank">{{ $loop->iteration }}</td><td><a class="hub-player" href="{{ route('characters.show', $player->id) }}">{{ $player->name }} <span aria-hidden="true">↗</span></a></td><td><span class="hub-faction {{ $player->race === 'ELYOS' ? 'is-elyos' : 'is-asmodian' }}">{{ $player->faction }}</span><small>{{ $player->class_label }}</small></td><td>{{ $player->level }}</td><td class="hub-score">{{ number_format($player->{$filters['sort']}) }}</td><td>@if ($player->legion_id)<a href="{{ route('legions.show', $player->legion_id) }}">{{ $player->legion_name }}</a>@else<span class="hub-muted">—</span>@endif</td></tr>
        @endforeach
        </tbody></table></div>
    @endif
    <p class="hub-note">Positions apply to these results. Statistics refresh about once a minute and can trail in-game progress until it is saved. Generated bots, deleted characters and staff accounts are excluded; player-owned alts remain eligible.</p>
</main>
@endsection
