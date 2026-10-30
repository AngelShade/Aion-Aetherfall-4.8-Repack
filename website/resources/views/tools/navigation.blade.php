<nav class="hub-navigation" aria-label="Repack tools">
    @foreach (['tools'=>'The toolkit', 'tools.classes'=>'Class explorer', 'tools.planner'=>'Party planner', 'tools.atlas'=>'Instance atlas', 'tools.commands'=>'Command reference'] as $name => $label)
        <a href="{{ route($name) }}" @if(request()->routeIs($name)) aria-current="page" @endif>{{ $label }}</a>
    @endforeach
</nav>
