@extends('layouts.app')
@section('title', 'Instance Atlas | Aetherfall Repack')
@section('meta_description', 'Find Aion 4.8 instances in the Aetherfall repack by name, faction, level and native member limit.')
@include('tools.assets')
@section('content')
<main class="container player-hub codex">
    <section class="page-intro"><div class="eyebrow">Chart your next expedition</div><h1>The instance atlas.</h1><p>Explore the instances listed in the included Aion 4.8 repack data. Search for a familiar destination or find one that fits your character and group.</p></section>
    @include('tools.navigation')
    <form class="hub-filters" method="GET" action="{{ route('tools.atlas') }}"><label>Name or map ID<input type="search" name="q" value="{{ $filters['q'] }}" maxlength="80" placeholder="Fire Temple, Steel Rake…"></label><label>Faction<select name="race"><option value="">Either faction</option><option value="ELYOS" @selected($filters['race']==='ELYOS')>Elyos</option><option value="ASMODIANS" @selected($filters['race']==='ASMODIANS')>Asmodians</option></select></label><label>Your level<input type="number" name="level" min="1" max="65" value="{{ $filters['level'] }}" placeholder="Any"></label><label>Member limit<select name="size"><option value="">Any size</option>@foreach(['solo'=>'One member','party'=>'2–6 members','alliance'=>'More than six'] as $key=>$label)<option value="{{ $key }}" @selected($filters['size']===$key)>{{ $label }}</option>@endforeach</select></label><button type="submit" class="btn">EXPLORE</button><a class="hub-text-link" href="{{ route('tools.atlas') }}">Reset</a></form>
    @if(!$available)<div class="hub-empty"><h2>Atlas unavailable</h2><p>The instance catalogue is not included in this website build.</p></div>
    @else
    <div class="hub-result-heading"><h2>Your next destination</h2><span>{{ $instances->total() }} matches · {{ $total }} included instances</span></div>
    <p class="hub-note">Included repack defaults, refreshed with the website build. These are listed entry levels and member limits, rather than recommended difficulty. Quest prerequisites, schedules and your own settings still apply. Dungeon entry-count limits are disabled in the default repack.</p>
    <section class="codex-atlas-grid" aria-label="Instance results">
    @forelse($instances as $instance)<article class="codex-instance"><span class="eyebrow">Map {{ $instance['id'] }}</span><h3>{{ $instance['name'] }}</h3><dl>@foreach($instance['factions'] as $race=>$entry)<div><dt>{{ $race==='ELYOS'?'Elyos':'Asmodians' }}</dt><dd>Level {{ $entry['min_level'] ?? '—' }}@if($entry['max_level'])–{{ $entry['max_level'] }}@elseif($entry['min_level'])+@endif <span>· {{ $entry['members'] ? $entry['members'].' member'.($entry['members']===1?'':'s') : 'Member limit unspecified' }}</span></dd></div>@endforeach</dl>@if(collect($instance['factions'])->contains(fn($entry)=>($entry['members']??0)>1))<a class="hub-text-link" href="{{ route('tools.planner') }}">Prepare a party →</a>@endif</article>
    @empty<div class="hub-empty"><h2>No matching destination</h2><p>Try another faction, level or group size.</p></div>@endforelse
    </section><div class="codex-pagination">{{ $instances->links() }}</div>
    @endif
</main>
@endsection
