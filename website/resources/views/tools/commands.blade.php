@extends('layouts.app')
@section('title', 'Companion Commands | Aetherfall Repack')
@section('meta_description', 'Search Aetherfall companion commands for party orders, combat, formations, supplies and quest sharing.')
@include('tools.assets')
@section('content')
<main class="container player-hub codex">
    <section class="page-intro"><div class="eyebrow">A captain’s field notes</div><h1>The right order, at the right moment.</h1><p>Find a command, copy it and enter it in the game chat. Replace angle-bracket placeholders with your own values; choose one option when several are separated by |.</p></section>
    @include('tools.navigation')
    <form class="hub-filters" method="GET" action="{{ route('tools.commands') }}"><label>Search commands<input type="search" name="q" value="{{ $q }}" maxlength="80" placeholder="Follow, loot, quest, formation…"></label><label>Category<select name="category"><option value="">All commands</option>@foreach($categories as $label)<option @selected($category===$label)>{{ $label }}</option>@endforeach</select></label><button class="btn" type="submit">FIND COMMANDS</button><a class="hub-text-link" href="{{ route('tools.commands') }}">Reset</a></form>
    <div class="hub-result-heading"><h2>Companion field reference</h2><span>{{ $commands->count() }} commands</span></div><p class="codex-message" data-copy-status role="status" aria-live="polite"></p>
    <section class="codex-command-list" aria-label="Commands">
    @forelse($commands as $entry)<article class="codex-command"><div><span class="eyebrow">{{ $entry['category'] }}</span><code>{{ $entry['syntax'] }}</code><p>{{ $entry['description'] }}</p></div><button type="button" class="btn btn-outline" data-copy-text="{{ $entry['syntax'] }}" hidden>Copy command</button></article>
    @empty<div class="hub-empty"><h2>No matching command</h2><p>Try a shorter search or another category.</p></div>@endforelse
    </section>
    <p class="hub-note">These commands control your own eligible companions. Character ownership, costs, skills, equipment and proximity requirements still apply. The in-game companion window remains available for everyday controls.</p>
</main>
@endsection
