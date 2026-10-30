<?php

namespace App\Services;

use Illuminate\Database\Query\Builder;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Schema;
use RuntimeException;

/** Public, read-only native character data. Never return account or location data. */
class AionCommunity
{
    public const METRICS = ['ap' => 'Abyss Points', 'gp' => 'Glory Points', 'kills' => 'PvP kills', 'level' => 'Level'];

    public static function classes(): array
    {
        $labels = [];
        foreach (AetherfallGame::CLASSES as $name => $id) {
            $labels[$name] = ucwords(strtolower(str_replace('_', ' ', $name)));
        }
        return array_replace($labels, ['RIDER' => 'Aethertech', 'BARD' => 'Songweaver']);
    }

    private function players(): Builder
    {
        if (! AetherfallGame::enabled()) throw new RuntimeException('Native community data is unavailable.');
        $native = app(AetherfallGame::class);
        // Accounts and world may use separate connections. Read only restricted IDs.
        $restricted = Cache::remember('aion.community.restricted.v1', 60, fn () => $native->accounts()
            ->table('account_data')->where(fn ($q) => $q->where('access_level', '<>', 0)
                ->orWhere('activated', '<>', 1))->pluck('id')->all());
        $query = $native->world()->table('players as p')
            ->leftJoin('abyss_rank as a', 'a.player_id', '=', 'p.id')
            ->leftJoin('legion_members as m', 'm.player_id', '=', 'p.id')
            ->leftJoin('legions as l', 'l.id', '=', 'm.legion_id')
            ->whereNull('p.deletion_date')->whereNotIn('p.account_id', $restricted);
        // Generated roster characters are distinct from normal player-owned alts.
        foreach (['playerbot_roster' => 'player_id', 'playerbot_population_members' => 'player_id'] as $table => $column) {
            if (Cache::remember('aion.community.table.'.$table, 60,
                fn () => Schema::connection('aion_world')->hasTable($table))) {
                $query->whereNotExists(fn ($bot) => $bot->selectRaw('1')->from($table.' as bot')
                    ->whereColumn('bot.'.$column, 'p.id'));
            }
        }
        return $query;
    }

    private function columns(Builder $query): Builder
    {
        return $query->select(['p.id', 'p.name', 'p.exp', 'p.race', 'p.player_class',
            'l.id as legion_id', 'l.name as legion_name'])
            ->selectRaw('COALESCE(a.ap, 0) as ap, COALESCE(a.gp, 0) as gp, COALESCE(a.all_kill, 0) as kills');
    }

    private function present(object $row): object
    {
        $row->level = app(AetherfallGame::class)->level((int) $row->exp);
        $row->class_label = self::classes()[$row->player_class] ?? $row->player_class;
        $row->faction = $row->race === 'ELYOS' ? 'Elyos' : 'Asmodians';
        unset($row->exp, $row->player_class);
        return $row;
    }

    public function rankings(array $filters): Collection
    {
        $load = function () use ($filters) {
            $query = $this->columns($this->players());
            if ($filters['race'] !== '') $query->where('p.race', $filters['race']);
            if ($filters['class'] !== '') $query->where('p.player_class', $filters['class']);
            if ($filters['q'] !== '') {
                $query->whereRaw("p.name LIKE ? ESCAPE '!'", ['%'.str_replace(['!', '%', '_'], ['!!', '!%', '!_'], $filters['q']).'%']);
            }
            $column = ['ap' => 'a.ap', 'gp' => 'a.gp', 'kills' => 'a.all_kill', 'level' => 'p.exp'][$filters['sort']];
            return $query->orderByRaw('COALESCE('.$column.', 0) DESC')->orderBy('p.name')->orderBy('p.id')
                ->limit(100)->get()->map(fn ($row) => $this->present($row));
        };
        // Do not create unbounded persistent cache entries for visitor search strings.
        return $filters['q'] !== '' ? $load() : Cache::remember('aion.community.ranking.v1.'.
            implode('.', [$filters['sort'], $filters['race'], $filters['class']]), 60, $load);
    }

    public function character(int $id): ?object
    {
        $row = $this->columns($this->players())->where('p.id', $id)->first();
        return $row ? $this->present($row) : null;
    }

    public function legions(): Collection
    {
        return Cache::remember('aion.community.legions.v1', 60, fn () => $this->players()
            ->whereNotNull('l.id')->select(['l.id', 'l.name', 'l.level', 'l.contribution_points'])
            ->selectRaw('COUNT(*) as members')->groupBy('l.id', 'l.name', 'l.level', 'l.contribution_points')
            ->orderByDesc('l.contribution_points')->orderBy('l.name')->limit(100)->get());
    }

    public function legion(int $id): array
    {
        $legion = $this->legions()->firstWhere('id', $id);
        // Profiles also work for legions outside the first 100 results.
        if (! $legion) {
            $legion = $this->players()->where('l.id', $id)->select(['l.id', 'l.name', 'l.level', 'l.contribution_points'])
                ->selectRaw('COUNT(*) as members')->groupBy('l.id', 'l.name', 'l.level', 'l.contribution_points')->first();
        }
        $members = $legion ? $this->columns($this->players())->where('l.id', $id)
            ->orderByDesc('p.exp')->orderBy('p.name')->paginate(30)->through(fn ($row) => $this->present($row)) : null;
        return [$legion, $members];
    }
}
