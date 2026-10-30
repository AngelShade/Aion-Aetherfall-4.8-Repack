@extends('layouts.app')
@section('title', 'Game Data Unavailable | Aetherfall')
@section('content')
<main class="container player-hub"><section class="page-intro"><div class="eyebrow">Player hub</div><h1>Try again shortly</h1><p>The connected world could not provide this profile.</p></section>@include('community.navigation')@include('community.offline')</main>
@endsection
