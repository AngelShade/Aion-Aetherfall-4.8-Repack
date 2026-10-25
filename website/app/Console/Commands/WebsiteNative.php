<?php

namespace App\Console\Commands;

use App\Services\AetherfallGame;
use Illuminate\Console\Command;

class WebsiteNative extends Command
{
    protected $signature = 'aetherfall:website-native';
    protected $description = 'Apply the additive native website integration to the explicitly configured game databases.';

    public function handle(): int
    {
        $native = app(AetherfallGame::class);
        $accounts = $native->accounts();
        $world = $native->world();
        foreach ([$accounts, $world] as $connection) {
            if (! preg_match('/^[A-Za-z0-9_]+$/', $connection->getDatabaseName())) {
                $this->error('Database names must contain letters, digits and underscores only.');
                return 1;
            }
        }
        if (! $accounts->getSchemaBuilder()->hasColumns('account_data', ['id','name','password','activated'])
            || ! $world->getSchemaBuilder()->hasColumns('players', ['id','account_id','account_name','online','player_class'])) {
            $this->error('The configured databases do not have the native Aetherfall schema. No migration was applied.');
            return 1;
        }
        $this->info('Native accounts database: '.$accounts->getDatabaseName());
        $this->info('Native character database: '.$world->getDatabaseName());
        $sql = file_get_contents(base_path('native-website.sql'));
        [$accountSql, $worldSql] = explode('USE aetherfall_gs;', $sql, 2);
        $accountSql = str_replace('USE aetherfall_ls;', '', $accountSql);
        [$tableSql, $triggers] = explode('DELIMITER //', $worldSql, 2);
        $accounts->unprepared($accountSql);
        $world->unprepared($tableSql);
        foreach (explode('//', str_replace('DELIMITER ;', '', $triggers)) as $trigger) {
            if (trim($trigger) !== '') $world->unprepared($trigger);
        }
        $this->info('Native integration initialized. Existing accounts and characters were retained; new activity history starts now.');
        return 0;
    }
}
