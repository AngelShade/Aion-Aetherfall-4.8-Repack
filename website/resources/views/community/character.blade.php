@extends('layouts.app')
@section('title', $character->name . ' | Aetherfall Characters')
@section('meta_description', $character->name . ' — Level ' . $character->level . ' ' . $character->class_label . ', ' . $character->faction . '.')
@section('content')
<main class="container player-hub">
    <section class="page-intro"><div class="eyebrow">Daeva profile / {{ $character->faction }}</div><h1>{{ $character->name }}</h1><p>Level {{ $character->level }} {{ $character->class_label }}@if ($character->legion_id) · <a class="hub-text-link" href="{{ route('legions.show', $character->legion_id) }}">{{ $character->legion_name }}</a>@endif</p></section>
    @include('community.navigation')
    <section class="hub-profile"><div class="hub-profile-art" aria-hidden="true"><img src="{{ asset('images/icon/'.($character->race === 'ELYOS' ? 'elyos-ico.png' : 'asmodian-ico.png')) }}" alt=""><span>{{ $character->faction }}</span></div><div class="hub-profile-details"><span class="eyebrow">{{ $character->class_label }}</span><h2>Battle record</h2><div class="hub-stats"><div><strong>{{ number_format($character->ap) }}</strong><span>Abyss Points</span></div><div><strong>{{ number_format($character->gp) }}</strong><span>Glory Points</span></div><div><strong>{{ number_format($character->kills) }}</strong><span>PvP kills</span></div></div><p class="hub-note">Saved character statistics from the connected world. Account details, inventory and current location stay private.</p></div></section>
    <a class="hub-text-link" href="{{ route('rankings', ['class' => array_search($character->class_label, \App\Services\AionCommunity::classes(), true) ?: '', 'race' => $character->race]) }}">Explore this class and faction →</a>
</main>
@endsection
