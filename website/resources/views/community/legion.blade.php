@extends('layouts.app')
@section('title', $legion->name . ' | Aetherfall Legions')
@section('content')
<main class="container player-hub"><section class="page-intro"><div class="eyebrow">Legion profile</div><h1>{{ $legion->name }}</h1><p>Level {{ $legion->level }} · {{ number_format($legion->members) }} players · {{ number_format($legion->contribution_points) }} contribution points</p></section>@include('community.navigation')
<div class="hub-result-heading"><h2>The roster</h2><span>Sorted by level</span></div><div class="hub-member-grid">
@foreach ($members as $member)<a class="hub-member" href="{{ route('characters.show', $member->id) }}"><strong>{{ $member->name }} <span aria-hidden="true">↗</span></strong><span>Level {{ $member->level }} · {{ $member->class_label }}</span><small class="hub-faction {{ $member->race === 'ELYOS' ? 'is-elyos' : 'is-asmodian' }}">{{ $member->faction }}</small></a>@endforeach
</div><div class="news-page-pagination">{{ $members->links() }}</div><p class="hub-note">Only eligible player characters are shown. Saved game data can trail current in-game progress.</p></main>
@endsection
