@extends('layouts.app')
@section('title', 'Legion Rankings | Aetherfall')
@section('meta_description', 'Explore Aetherfall legions, contribution points and their player rosters.')
@section('content')
<main class="container player-hub"><section class="page-intro"><div class="eyebrow">Stronger together</div><h1>Legions of Atreia</h1><p>Discover the legions in your world, ranked by contribution points.</p></section>@include('community.navigation')
@if (! $available) @include('community.offline')
@elseif ($legions->isEmpty())<div class="hub-empty"><h2>A new banner awaits</h2><p>No eligible legions have been found in this world yet.</p></div>
@else
<div class="hub-table-wrap"><table class="hub-table"><caption class="hub-sr-only">Top 100 legions by contribution points</caption><thead><tr><th scope="col">#</th><th scope="col">Legion</th><th scope="col">Level</th><th scope="col">Players</th><th scope="col">Contribution</th></tr></thead><tbody>
@foreach ($legions as $legion)<tr><td class="hub-rank">{{ $loop->iteration }}</td><td><a class="hub-player" href="{{ route('legions.show', $legion->id) }}">{{ $legion->name }} ↗</a></td><td>{{ $legion->level }}</td><td>{{ number_format($legion->members) }}</td><td class="hub-score">{{ number_format($legion->contribution_points) }}</td></tr>@endforeach
</tbody></table></div>
@endif
<p class="hub-note">Up to 100 legions. Player counts exclude generated bots, deleted characters and staff accounts. Contribution is the legion’s saved total; updates can take about a minute.</p></main>
@endsection
