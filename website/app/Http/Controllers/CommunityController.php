<?php

namespace App\Http\Controllers;

use App\Services\AionCommunity;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;
use Throwable;

class CommunityController extends Controller
{
    public function index(Request $request, AionCommunity $community)
    {
        $string = static fn ($key) => is_string($request->query($key)) ? trim($request->query($key)) : '';
        $filters = ['sort' => $string('sort'), 'race' => $string('race'), 'class' => $string('class'),
            'q' => mb_substr($string('q'), 0, 50)];
        if (! array_key_exists($filters['sort'], AionCommunity::METRICS)) $filters['sort'] = 'ap';
        if (! in_array($filters['race'], ['ELYOS', 'ASMODIANS'], true)) $filters['race'] = '';
        if (! array_key_exists($filters['class'], AionCommunity::classes())) $filters['class'] = '';
        $players = collect();
        $available = true;
        try { $players = $community->rankings($filters); }
        catch (Throwable $exception) { $available = false; $this->unavailable($exception); }
        return view('community.rankings', compact('players', 'available', 'filters'));
    }

    public function character(string $id, AionCommunity $community)
    {
        abort_unless(ctype_digit($id) && (int) $id > 0 && (int) $id <= 2147483647, 404);
        try { $character = $community->character((int) $id); }
        catch (Throwable $exception) {
            $this->unavailable($exception);
            return response()->view('community.unavailable', [], 503)->header('Retry-After', '60');
        }
        abort_unless($character, 404);
        return view('community.character', compact('character'));
    }

    public function legions(AionCommunity $community)
    {
        $legions = collect(); $available = true;
        try { $legions = $community->legions(); }
        catch (Throwable $exception) { $available = false; $this->unavailable($exception); }
        return view('community.legions', compact('legions', 'available'));
    }

    public function legion(string $id, AionCommunity $community)
    {
        abort_unless(ctype_digit($id) && (int) $id > 0 && (int) $id <= 2147483647, 404);
        try { [$legion, $members] = $community->legion((int) $id); }
        catch (Throwable $exception) {
            $this->unavailable($exception);
            return response()->view('community.unavailable', [], 503)->header('Retry-After', '60');
        }
        abort_unless($legion, 404);
        return view('community.legion', compact('legion', 'members'));
    }

    private function unavailable(Throwable $exception): void
    {
        Log::warning('Public community data unavailable.', ['exception' => $exception::class]);
    }
}
