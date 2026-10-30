@extends('layouts.app')
@section('title', 'Companion Party Planner | Aetherfall Repack')
@section('meta_description', 'Build a six-person Aetherfall party with friends, owned alts and temporary companions. Save the plan on your device or share a link.')
@include('tools.assets')
@section('content')
<main class="container player-hub codex" data-party-planner>
    <section class="page-intro"><div class="eyebrow">Your company of Daevas</div><h1>One party. Your way.</h1><p>Plan a six-person group with friends, owned alts and temporary companions. Your plan is saved in this browser when you choose Save; sharing creates a link to the same composition.</p></section>
    @include('tools.navigation')
    <noscript><div class="hub-empty"><h2>Enable JavaScript to use the planner</h2><p>The class explorer and command reference work without it.</p></div></noscript>
    <section class="codex-planner" data-planner-controls hidden>
        <div class="codex-planner-bar"><label for="party-preset">Start with a formation<select id="party-preset" data-party-preset><option value="balanced">Balanced expedition</option><option value="ranged">Ranged expedition</option><option value="small">Two-person expedition</option><option value="empty">Only your Daeva</option></select></label><button class="btn btn-outline" type="button" data-party-apply>USE THIS STARTING POINT</button></div>
        <form data-party-form><div class="codex-party-grid">
        @for($slot=0;$slot<6;$slot++)
            <fieldset data-party-slot="{{ $slot }}"><legend>{{ $slot===0?'01 / Your Daeva':sprintf('%02d / Party member',$slot+1) }}</legend><label>Class<select data-party-class @if($slot===0) required @endif>@if($slot!==0)<option value="">Empty slot</option>@endif @foreach($classes as $entry)<option value="{{ $entry['key'] }}" data-default-role="{{ $entry['role'] }}">{{ $entry['name'] }}</option>@endforeach</select></label><label>Played by<select data-party-kind @if($slot===0) disabled @endif><option value="player">{{ $slot===0?'You':'Friend / player' }}</option>@if($slot!==0)<option value="alt">Owned alt</option><option value="temporary">Temporary companion</option>@endif</select></label><label>Planned role<select data-party-role>@foreach(['tank'=>'Tank','healer'=>'Healer','support'=>'Support','melee'=>'Melee damage','ranged'=>'Ranged damage'] as $key=>$label)<option value="{{ $key }}">{{ $label }}</option>@endforeach</select></label></fieldset>
        @endfor
        </div></form>
        <section class="codex-party-summary" aria-labelledby="party-summary-title"><h2 id="party-summary-title">Your party at a glance</h2><div class="codex-party-counts" data-party-counts></div><p data-party-advice></p></section>
        <div class="codex-party-actions"><button class="btn" type="button" data-party-save>SAVE ON THIS DEVICE</button><button class="btn btn-outline" type="button" data-party-share>CREATE SHARE LINK</button><button class="btn btn-outline" type="button" data-party-copy>COPY PARTY ROSTER</button></div>
        <label class="codex-share" data-party-share-output hidden>Share this plan<input type="text" readonly data-party-link><span class="hub-note">Copy this link to open the same class, role and member choices on another device.</span></label>
        <p class="codex-message" data-party-status role="status" aria-live="polite"></p>
    </section>
    <div class="hub-callout"><div><h2>Ready to gather your companions?</h2><p>This is a planning tool. Recruit your characters through the in-game Companions window and assign roles there. Plans do not create, change or equip characters. Existing alts keep their class and earned progress; recruitment still follows your installation’s rules.</p><p>A normal party has six places, including you and other players. Native alliances can be larger; this planner covers one party.</p></div><a class="hub-text-link" href="{{ route('tools.commands', ['category'=>'Party']) }}">Open recruitment commands →</a></div>
</main>
@endsection
