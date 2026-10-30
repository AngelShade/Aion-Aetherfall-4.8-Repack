<?php

namespace App\Http\Controllers;

use App\Services\RepackCodex;
use Illuminate\Http\Request;
use Illuminate\Pagination\LengthAwarePaginator;

class RepackToolsController extends Controller
{
    private function text(Request $request, string $key): string
    {
        $value = $request->query($key, '');
        return is_string($value) ? mb_substr(trim($value), 0, 80) : '';
    }

    public function index()
    {
        return view('tools.index');
    }

    public function classes(Request $request, RepackCodex $codex)
    {
        $all = collect($codex->reference()['classes']);
        $q = $this->text($request, 'q');
        $focus = $this->text($request, 'focus');
        $focuses = ['frontline' => 'Frontline', 'healing' => 'Healing', 'support' => 'Support', 'melee' => 'Melee', 'ranged' => 'Ranged', 'pets' => 'Pets'];
        if (! isset($focuses[$focus])) $focus = '';
        $chosen = $request->query('compare', []);
        $chosen = is_array($chosen) ? array_slice(array_values(array_unique(array_filter($chosen, fn ($key) => is_string($key) && $all->contains('key', $key)))), 0, 3) : [];
        $comparison = $all->whereIn('key', $chosen);
        $classes = $all->filter(fn ($entry) => ($focus === '' || in_array($focus, $entry['tags'], true))
            && ($q === '' || str_contains(mb_strtolower($entry['name'].' '.$entry['family'].' '.$entry['summary']), mb_strtolower($q))));
        return view('tools.classes', compact('classes', 'comparison', 'chosen', 'focuses', 'q', 'focus'));
    }

    public function planner(RepackCodex $codex)
    {
        return view('tools.planner', ['classes' => $codex->reference()['classes']]);
    }

    public function commands(Request $request, RepackCodex $codex)
    {
        $all = collect($codex->reference()['commands']);
        $categories = $all->pluck('category')->unique()->values();
        $q = $this->text($request, 'q');
        $category = $this->text($request, 'category');
        if (! $categories->contains($category)) $category = '';
        $commands = $all->filter(fn ($entry) => ($category === '' || $category === $entry['category'])
            && ($q === '' || str_contains(mb_strtolower($entry['syntax'].' '.$entry['description']), mb_strtolower($q))));
        return view('tools.commands', compact('commands', 'categories', 'q', 'category'));
    }

    public function atlas(Request $request, RepackCodex $codex)
    {
        $filters = ['q' => $this->text($request, 'q'), 'race' => $this->text($request, 'race'),
            'size' => $this->text($request, 'size'), 'level' => $this->text($request, 'level')];
        if (! in_array($filters['race'], ['ELYOS', 'ASMODIANS'], true)) $filters['race'] = '';
        if (! in_array($filters['size'], ['solo', 'party', 'alliance'], true)) $filters['size'] = '';
        $level = filter_var($filters['level'], FILTER_VALIDATE_INT);
        $filters['level'] = $level !== false && $level >= 1 && $level <= 65 ? $level : null;
        $all = $codex->instances($filters);
        $requested = $this->text($request, 'page');
        $page = ctype_digit($requested) ? min(10000, max(1, (int) $requested)) : 1;
        $instances = new LengthAwarePaginator($all->forPage($page, 18), $all->count(), 18, $page,
            ['path' => route('tools.atlas'), 'query' => $request->query()]);
        return view('tools.atlas', ['instances' => $instances, 'filters' => $filters,
            'available' => $codex->atlas() !== null, 'total' => count($codex->atlas()['instances'] ?? [])]);
    }
}
