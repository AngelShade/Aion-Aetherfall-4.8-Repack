<nav class="hub-navigation" aria-label="Player hub">
    @if ($aionFeatures['repack.tools'] ?? false)<a href="{{ route('tools') }}">Repack tools &amp; codex</a>@endif
    @if ($aionFeatures['rankings'] ?? false)<a href="{{ route('rankings') }}" @if(request()->routeIs('rankings', 'characters.show')) aria-current="page" @endif>Players &amp; rankings</a><a href="{{ route('legions') }}" @if(request()->routeIs('legions', 'legions.show')) aria-current="page" @endif>Legions</a>@endif
    @if ($aionFeatures['events'] ?? false)<a href="{{ route('events') }}" @if(request()->routeIs('events')) aria-current="page" @endif>Events &amp; sieges</a>@endif
    @if ($aionFeatures['server.status'] ?? false)<a href="{{ route('status') }}" @if(request()->routeIs('status')) aria-current="page" @endif>Server status</a>@endif
    @if ($aionFeatures['start'] ?? false)<a href="{{ route('start') }}" @if(request()->routeIs('start')) aria-current="page" @endif>Start playing</a>@endif
</nav>
