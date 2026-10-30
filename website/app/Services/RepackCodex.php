<?php

namespace App\Services;

use Illuminate\Support\Collection;

/** Static repack reference data; no game database or live process access. */
class RepackCodex
{
    private ?array $reference = null;
    private ?array $atlas = null;

    public function reference(): array
    {
        return $this->reference ??= json_decode(file_get_contents(resource_path('content/repack-reference.json')), true, 32, JSON_THROW_ON_ERROR);
    }

    public function atlas(): ?array
    {
        $path = resource_path('content/repack-atlas.json');
        if (! is_file($path)) return null;
        return $this->atlas ??= json_decode(file_get_contents($path), true, 32, JSON_THROW_ON_ERROR);
    }

    public function instances(array $filters): Collection
    {
        return collect($this->atlas()['instances'] ?? [])->filter(function (array $instance) use ($filters): bool {
            if ($filters['q'] !== '' && ! str_contains(mb_strtolower($instance['name'].' '.$instance['id']), mb_strtolower($filters['q']))) return false;
            $factions = $filters['race'] !== '' ? array_intersect_key($instance['factions'], [$filters['race'] => true]) : $instance['factions'];
            if (! $factions) return false;
            return collect($factions)->contains(function (array $entry) use ($filters): bool {
                if ($filters['level'] && (($entry['min_level'] && $filters['level'] < $entry['min_level'])
                    || ($entry['max_level'] && $filters['level'] > $entry['max_level']))) return false;
                return match ($filters['size']) {
                    'solo' => $entry['members'] === 1,
                    'party' => $entry['members'] !== null && $entry['members'] > 1 && $entry['members'] <= 6,
                    'alliance' => $entry['members'] !== null && $entry['members'] > 6,
                    default => true,
                };
            });
        })->values();
    }
}
