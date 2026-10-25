<span class="game-shot" style="aspect-ratio:{{ $shot['width'] }}/{{ $shot['height'] }}">
    <img src="{{ $src }}" alt="{{ $alt ?? $shot['title'] }}" loading="lazy" decoding="async"
         style="width:{{ 1920 / $shot['width'] * 100 }}%;left:{{ -$shot['x'] / $shot['width'] * 100 }}%;top:{{ -$shot['y'] / $shot['height'] * 100 }}%;height:auto">
</span>
