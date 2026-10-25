<?php

namespace App\Services;

use App\Exceptions\AionRegistrationException;
use Illuminate\Database\QueryException;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use RuntimeException;

/** Native Aetherfall data, translated into the original website's view contracts. */
class AetherfallGame
{
    public const CLASSES = [
        'WARRIOR'=>0, 'GLADIATOR'=>1, 'TEMPLAR'=>2, 'SCOUT'=>3, 'ASSASSIN'=>4,
        'RANGER'=>5, 'MAGE'=>6, 'SORCERER'=>7, 'SPIRIT_MASTER'=>8, 'PRIEST'=>9,
        'CLERIC'=>10, 'CHANTER'=>11, 'ENGINEER'=>12, 'RIDER'=>13, 'GUNNER'=>14,
        'ARTIST'=>15, 'BARD'=>16,
    ];

    public static function enabled(): bool
    {
        return config('aion_game.backend') === 'aetherfall';
    }

    public function accounts(): \Illuminate\Database\Connection
    {
        return DB::connection('aion_accounts');
    }

    public function world(): \Illuminate\Database\Connection
    {
        return DB::connection('aion_world');
    }

    public static function passwordHash(string $password): string
    {
        // Exact UTF-8 SHA-1/Base64 format used by native AccountUtils.encodePassword.
        return base64_encode(sha1($password, true));
    }

    public function attempt(string $username, string $password): ?object
    {
        $account = $this->accounts()->table('account_data')->where('name', $username)->first();
        if (! $account || ! hash_equals((string) $account->password, self::passwordHash($password))) {
            return null;
        }
        $metadata = $this->accounts()->table('website_account_profiles')->where('account_id', $account->id)->first();
        if ($metadata && (int) $metadata->login_flag_web !== 0) return null;
        return (object) ['uid'=>(int) $account->id, 'account'=>$account->name, 'email'=>$metadata->email ?? ''];
    }

    public function register(string $username, string $password, string $email): void
    {
        try {
            $this->accounts()->transaction(function () use ($username, $password, $email): void {
                if ($this->accounts()->table('account_data')->where('name', $username)->exists()) {
                    throw new AionRegistrationException('That username is already registered.');
                }
                $id = $this->accounts()->table('account_data')->insertGetId([
                    'name'=>$username, 'password'=>self::passwordHash($password),
                    'activated'=>1, 'access_level'=>0, 'membership'=>0,
                ]);
                $this->accounts()->table('website_account_profiles')->insert([
                    'account_id'=>$id, 'email'=>$email,
                ]);
            });
        } catch (QueryException $exception) {
            if (($exception->errorInfo[1] ?? null) === 1062) {
                throw new AionRegistrationException('That username is already registered.');
            }
            throw $exception;
        }
    }

    public function changePassword(string $username, string $password): void
    {
        $this->accounts()->transaction(function () use ($username, $password): void {
            $account = $this->accounts()->table('account_data')->where('name', $username)->lockForUpdate()->first();
            if (! $account) throw new RuntimeException('Game account does not exist.');
            $this->accounts()->table('account_data')->where('id', $account->id)
                ->update(['password'=>self::passwordHash($password)]);
        });
    }

    public function profile(int $id, string $username): ?object
    {
        $row = $this->accounts()->table('account_data as a')
            ->leftJoin('website_account_profiles as w', 'w.account_id', '=', 'a.id')
            ->leftJoin('account_time as t', 't.account_id', '=', 'a.id')
            ->where('a.id', $id)->where('a.name', $username)
            ->select(['a.id as account_uid','a.creation_date as account_created_at',
                'a.membership as subscription_flag','a.activated','a.last_server as last_world',
                't.penalty_end','t.expiration_time'])
            ->selectRaw('COALESCE(w.donate_coin, 0) AS donate_coin, COALESCE(w.login_flag_web, 0) AS web_login_flag')
            ->selectRaw('UNIX_TIMESTAMP(t.penalty_end) AS penalty_end_epoch, UNIX_TIMESTAMP(t.expiration_time) AS expiration_epoch')->first();
        if (! $row) return null;
        // AccountTimeController uses the timestamp at Unix second 1 as a permanent ban.
        $row->block_flag = (int) $row->activated !== 1 || ($row->penalty_end_epoch !== null
            && ((int) $row->penalty_end_epoch === 1 || (int) $row->penalty_end_epoch >= time()));
        $row->secondary_block_flag = $row->expiration_epoch !== null && (int) $row->expiration_epoch < time();
        $row->last_login = null;
        $row->last_logout = null;
        try {
            $activity = $this->world()->table('website_character_activity as s')
                ->join('players as p', 'p.id', '=', 's.player_id')->where('p.account_id', $id)
                ->selectRaw('MAX(s.last_login) AS last_login, MAX(s.last_logout) AS last_logout')->first();
            $row->last_login = $activity?->last_login;
            $row->last_logout = $activity?->last_logout;
        } catch (\Throwable $exception) {
            Log::warning('Native account activity is unavailable.', ['exception'=>$exception::class]);
        }
        return $row;
    }

    private function characterQuery(): \Illuminate\Database\Query\Builder
    {
        return $this->world()->table('players as p')
            ->leftJoin('abyss_rank as a', 'a.player_id', '=', 'p.id')
            ->leftJoin('legion_members as m', 'm.player_id', '=', 'p.id')
            ->leftJoin('legions as l', 'l.id', '=', 'm.legion_id')
            ->leftJoin('website_character_activity as s', 's.player_id', '=', 'p.id')
            ->whereNull('p.deletion_date')
            ->select(['p.id as char_id','p.name as user_id','p.account_id','p.account_name',
                'p.race','p.player_class','p.gender','p.world_id as world','p.exp','p.online',
                'p.creation_date as create_date','p.last_online','s.last_login as last_login_time',
                's.last_logout as last_logout_time','s.total_seconds','s.session_started',
                'a.ap as abyss_point','m.legion_id as guild_id','m.rank as guild_rank',
                'l.name as guild_nickname']);
    }

    public function level(int $exp): int
    {
        $levels = config('aion_native.experience');
        foreach (array_reverse($levels, true) as $index => $threshold) {
            if ($exp >= $threshold) return min($index + 1, count($levels) - 1);
        }
        return 0;
    }

    private function presentCharacter(object $row): object
    {
        $row->class = self::CLASSES[$row->player_class] ?? -1;
        $row->race = $row->race === 'ELYOS' ? 0 : ($row->race === 'ASMODIANS' ? 1 : -1);
        $row->gender = $row->gender === 'FEMALE' ? 1 : 0;
        $row->lev = $this->level((int) $row->exp);
        $row->playtime = (int) ($row->total_seconds ?? 0);
        if ($row->online && $row->session_started) {
            $row->playtime += max(0, now()->timestamp - strtotime($row->session_started));
        }
        $row->abyss_point ??= 0;
        $row->guild_id ??= 0;
        $row->delete_date = 0;
        return $row;
    }

    public function characters(int $id, string $username): Collection
    {
        $profile = $this->profile($id, $username);
        if (! $profile) return collect();
        $rows = $this->characterQuery()->where('p.account_id', $id)->where('p.account_name', $username)
            ->orderByDesc('p.exp')->orderBy('p.name')->limit(100)->get();
        $bans = $this->world()->table('player_punishments')->whereIn('player_id', $rows->pluck('char_id'))
            ->where('punishment_type', 'CHARBAN')->get()->keyBy('player_id');
        return $rows->map(function ($row) use ($profile, $bans) {
            $ban = $bans->get($row->char_id);
            $row->is_banned = $profile->block_flag || $profile->secondary_block_flag
                || ($ban && ((int) $ban->duration === 0 || (int) $ban->start_time + (int) $ban->duration > time()));
            return $this->presentCharacter($row);
        });
    }

    public function metrics(): array
    {
        $players = $this->world()->table('players')->whereNull('deletion_date');
        $restrictedAccounts = $this->accounts()->table('account_data as a')
            ->leftJoin('account_time as t', 't.account_id', '=', 'a.id')
            ->where(fn ($q) => $q->where('a.activated', '<>', 1)
                ->orWhereRaw('UNIX_TIMESTAMP(t.penalty_end) = 1 OR UNIX_TIMESTAMP(t.penalty_end) >= UNIX_TIMESTAMP()')
                ->orWhereRaw('UNIX_TIMESTAMP(t.expiration_time) < UNIX_TIMESTAMP()'))->pluck('a.id');
        return [
            'accounts'=>$this->accounts()->table('account_data')->count(),
            'new_accounts'=>$this->accounts()->table('account_data')->where('creation_date','>=',now()->subDays(7))->count(),
            'characters'=>(clone $players)->count(),
            'new_characters'=>(clone $players)->where('creation_date','>=',now()->subDays(7))->count(),
            'elyos'=>(clone $players)->where('race','ELYOS')->count(),
            'asmodian'=>(clone $players)->where('race','ASMODIANS')->count(),
            'banned'=>(clone $players)->where(fn ($q) => $q->whereIn('account_id', $restrictedAccounts)
                ->orWhereIn('id', $this->world()->table('player_punishments')
                    ->select('player_id')->where('punishment_type','CHARBAN')
                    ->where(fn ($ban) => $ban->where('duration',0)->orWhereRaw('start_time + duration > UNIX_TIMESTAMP()'))))->count(),
            'guilds'=>$this->world()->table('legion_members as m')->join('players as p','p.id','=','m.player_id')
                ->whereNull('p.deletion_date')->distinct()->count('m.legion_id'),
        ];
    }

    public function activity(array $classMeta): array
    {
        $classes = $this->world()->table('players')->selectRaw('player_class, COUNT(*) AS total')
            ->whereNull('deletion_date')->groupBy('player_class')->orderByDesc('total')->get()
            ->map(function ($row) use ($classMeta) {
                $id = self::CLASSES[$row->player_class] ?? -1;
                return ['id'=>$id, 'label'=>$classMeta[$id][0] ?? $row->player_class,
                    'icon'=>$classMeta[$id][1] ?? 'warrior.png', 'total'=>(int) $row->total];
            })->all();
        $recent = $this->characterQuery()->orderByDesc('p.creation_date')->limit(8)->get()
            ->map(function ($row) use ($classMeta) {
                $row = $this->presentCharacter($row);
                $row->class_label = $classMeta[$row->class][0] ?? $row->player_class;
                $row->class_icon = $classMeta[$row->class][1] ?? 'warrior.png';
                $row->race_label = $row->race === 0 ? 'Elyos' : 'Asmodians';
                return (array) $row;
            })->all();
        return ['classes'=>$classes, 'class_meta'=>$classMeta, 'recent_characters'=>$recent,
            'recent_accounts'=>$this->accounts()->table('account_data')->select(['name as account','creation_date as create_date'])
                ->orderByDesc('creation_date')->limit(8)->get()->map(fn ($row)=>(array) $row)->all(),
            'online'=>app(AionServerStatus::class)->snapshot(), 'errors'=>[]];
    }
}
