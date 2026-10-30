@extends('layouts.app')
@section('title', 'Class Explorer | Aetherfall Repack')
@section('meta_description', 'Explore and compare the eleven Aion 4.8 advanced classes for your Daeva and companion party.')
@include('tools.assets')
@section('content')
<main class="container player-hub codex">
    <section class="page-intro"><div class="eyebrow">Find your calling</div><h1>Eleven paths through Atreia.</h1><p>Explore the advanced classes available after your starting class. The focus tags describe playstyles; your build, skills and equipment shape how you play.</p></section>
    @include('tools.navigation')
    <form class="hub-filters" method="GET" action="{{ route('tools.classes') }}"><label>Search classes<input type="search" name="q" value="{{ $q }}" maxlength="80" placeholder="Class, starting class or playstyle"></label><label>Focus<select name="focus"><option value="">All playstyles</option>@foreach($focuses as $key=>$label)<option value="{{ $key }}" @selected($focus===$key)>{{ $label }}</option>@endforeach</select></label><button class="btn" type="submit">FIND YOUR CLASS</button><a class="hub-text-link" href="{{ route('tools.classes') }}">Reset</a></form>
    @if($comparison->isNotEmpty())
    <section class="codex-comparison" aria-labelledby="comparison-title"><div class="hub-result-heading"><h2 id="comparison-title">Your class comparison</h2><span>{{ $comparison->count() }} selected · up to three</span></div><div class="codex-comparison-grid">@foreach($comparison as $entry)<article><h3>{{ $entry['name'] }}</h3><p class="eyebrow">{{ $entry['family'] }} → {{ $entry['name'] }}</p><p><strong>Focus:</strong> {{ $entry['strength'] }}</p><p>{{ $entry['tip'] }}</p><a class="hub-text-link" href="{{ route('tools.planner', ['class'=>$entry['key']]) }}">Plan a party with this class →</a></article>@endforeach</div></section>
    @endif
    <form method="GET" action="{{ route('tools.classes') }}">
        <input type="hidden" name="q" value="{{ $q }}"><input type="hidden" name="focus" value="{{ $focus }}">
        <div class="hub-result-heading"><h2>Choose your Daeva</h2><span>{{ $classes->count() }} classes · select up to three to compare</span></div>
        <div class="codex-class-grid">
        @forelse($classes as $entry)
            <article class="codex-class" id="class-{{ $entry['key'] }}"><header><img src="{{ asset('images/icon/'.$entry['icon'].'.png') }}" alt="" width="48" height="48"><div><span class="eyebrow">{{ $entry['family'] }}</span><h2>{{ $entry['name'] }}</h2></div></header><p class="codex-subtitle">{{ $entry['subtitle'] }}</p><p>{{ $entry['summary'] }}</p><div class="codex-tags">@foreach($entry['tags'] as $tag)<span>{{ $focuses[$tag] }}</span>@endforeach</div><label class="codex-compare-choice"><input type="checkbox" name="compare[]" value="{{ $entry['key'] }}" @checked(in_array($entry['key'],$chosen,true))> Compare {{ $entry['name'] }}</label></article>
        @empty<div class="hub-empty"><h2>No matching class</h2><p>Try another search or remove the focus filter.</p></div>@endforelse
        </div>
        @if($classes->isNotEmpty())<button type="submit" class="btn codex-compare-submit">COMPARE SELECTED CLASSES →</button>@endif
    </form>
</main>
@endsection
