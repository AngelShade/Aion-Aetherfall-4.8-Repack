@extends('layouts.app')
@section('title', 'Repack Tools & Codex | Aetherfall')
@section('meta_description', 'Plan your Aetherfall adventure: compare classes, build a companion party, explore instances and find in-game commands.')
@include('tools.assets')
@section('content')
<main class="container player-hub codex">
    <section class="page-intro codex-intro"><div class="eyebrow">The Daeva’s field kit</div><h1>Prepare your next adventure.</h1><p>A few useful tools for your own Aion 4.8 world. Choose a class, plan your companions and find your way through Aetherfall.</p></section>
    @include('tools.navigation')
    <section class="codex-tool-grid" aria-label="Adventure tools">
        @foreach ([
            ['tools.classes','01 / Your calling','Class explorer','Compare Atreia’s eleven advanced classes, explore their playstyles and find a place in your party.','Choose your class','sorcerer'],
            ['tools.planner','02 / Your company','Companion party planner','Arrange a six-person party with friends, owned alts and temporary companions. Save it here or share your plan.','Build your party','templar'],
            ['tools.atlas','03 / Your destination','Instance atlas','Search the included instance catalogue by faction, level and native member limit. Find a destination for your next session.','Explore the atlas','ranger'],
            ['tools.commands','04 / Your orders','Command reference','Search companion orders, formation controls, quest options and supply commands, then copy the syntax you need.','Find a command','chanter']
        ] as [$route,$number,$title,$copy,$action,$icon])
        <article class="codex-tool"><div class="codex-tool-top"><span class="eyebrow">{{ $number }}</span><img src="{{ asset('images/icon/'.$icon.'.png') }}" alt="" width="48" height="48"></div><h2>{{ $title }}</h2><p>{{ $copy }}</p><a class="hub-text-link" href="{{ route($route) }}">{{ $action }} <span aria-hidden="true">↗</span></a></article>
        @endforeach
    </section>
    <div class="hub-callout"><div><span class="eyebrow">A world that belongs to you</span><h2>From plans to play.</h2><p>These tools help you prepare. Recruit and command your party in the game, and use your own installation’s settings when choosing an adventure.</p></div>@if ($aionFeatures['start'] ?? false)<a class="btn btn-outline" href="{{ route('start') }}">OPEN THE PLAY CHECKLIST →</a>@endif</div>
</main>
@endsection
