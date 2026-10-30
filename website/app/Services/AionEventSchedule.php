<?php

namespace App\Services;

use DOMDocument;
use DOMXPath;
use Illuminate\Support\Facades\Cache;
use RuntimeException;

class AionEventSchedule
{
    public const DAYS = ['MON' => 'Monday', 'TUE' => 'Tuesday', 'WED' => 'Wednesday',
        'THU' => 'Thursday', 'FRI' => 'Friday', 'SAT' => 'Saturday', 'SUN' => 'Sunday'];

    public function weekly(): array
    {
        return Cache::remember('aion.events.schedule.v1', 60, function () {
            $custom = trim((string) config('aion_game.siege_schedule', ''));
            $path = $custom !== '' ? $custom : resource_path('content/siege-schedule.xml');
            // A source checkout can also run without packaging it first.
            if ($custom === '' && ! is_file($path)) $path = base_path('../game-server/config/schedule/siege_schedule.xml');
            $days = array_fill_keys(array_keys(self::DAYS), []);
            $result = ['days' => $days, 'available' => false, 'custom' => $custom !== '', 'unsupported' => 0,
                'timezone' => null, 'next' => null];
            $timezone = (string) config('aion_game.server_timezone', '');
            if (in_array($timezone, timezone_identifiers_list(), true)) $result['timezone'] = $timezone;
            if (! is_file($path) || ! is_readable($path)) return $result;
            $xml = file_get_contents($path);
            if ($xml === false || strlen($xml) > 1048576 || stripos($xml, '<!DOCTYPE') !== false) return $result;
            $previous = libxml_use_internal_errors(true);
            try {
                $document = new DOMDocument();
                if (! $document->loadXML($xml, LIBXML_NONET) || $document->documentElement?->tagName !== 'siege_schedule') return $result;
                foreach ((new DOMXPath($document))->query('/siege_schedule/fortress | /siege_schedule/agent_fight') as $node) {
                    $label = $node->nodeName === 'agent_fight' ? 'Agent battle' : 'Fortress '.$node->getAttribute('id');
                    $sibling = $node->previousSibling;
                    while ($sibling && $sibling->nodeType === XML_TEXT_NODE && trim($sibling->textContent) === '') $sibling = $sibling->previousSibling;
                    if ($sibling?->nodeType === XML_COMMENT_NODE && mb_strlen(trim($sibling->textContent)) < 100) $label = trim($sibling->textContent);
                    foreach ($node->getElementsByTagName('siegeTime') as $time) {
                        // Native Quartz entries use a zero second, fixed time and named weekdays.
                        // Complex owner rules remain explicitly unsupported instead of being guessed.
                        if (! preg_match('/^0\s+(\d{1,2})\s+(\d{1,2})\s+\?\s+\*\s+(\*|[A-Z,]+)(?:\s+\*)?$/', trim($time->textContent), $match)
                            || (int) $match[1] > 59 || (int) $match[2] > 23) { $result['unsupported']++; continue; }
                        $weekdays = $match[3] === '*' ? array_keys(self::DAYS) : explode(',', $match[3]);
                        if (array_diff($weekdays, array_keys(self::DAYS)) !== []) { $result['unsupported']++; continue; }
                        foreach (array_unique($weekdays) as $day) $result['days'][$day][] = ['name' => $label,
                            'time' => sprintf('%02d:%02d', $match[2], $match[1]),
                            'type' => $node->nodeName === 'fortress' ? 'Fortress siege' : 'Agent battle'];
                    }
                }
                foreach ($result['days'] as &$entries) usort($entries, fn ($a, $b) => [$a['time'], $a['name']] <=> [$b['time'], $b['name']]);
                unset($entries);
                $result['available'] = true;
                if ($result['timezone']) {
                    $now = now($result['timezone']);
                    foreach ($result['days'] as $day => $entries) {
                        foreach ($entries as $entry) {
                            $weekday = array_search($day, array_keys(self::DAYS), true) + 1;
                            $start = $now->copy()->startOfWeek()->addDays($weekday - 1)->setTimeFromTimeString($entry['time']);
                            if ($start->lessThanOrEqualTo($now)) $start->addWeek();
                            if (! $result['next'] || $start->lessThan($result['next']['at'])) $result['next'] = $entry + ['at' => $start];
                        }
                    }
                }
                return $result;
            } finally { libxml_clear_errors(); libxml_use_internal_errors($previous); }
        });
    }
}
