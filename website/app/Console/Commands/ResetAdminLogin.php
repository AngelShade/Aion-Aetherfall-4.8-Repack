<?php

namespace App\Console\Commands;

use Illuminate\Console\Command;

class ResetAdminLogin extends Command
{
    /**
     * The name and signature of the console command.
     *
     * @var string
     */
    protected $signature = 'app:reset-admin-login';

    /**
     * The console command description.
     *
     * @var string
     */
    protected $description = 'Create or reset a website administrator using the standalone administration flow.';

    /**
     * Execute the console command.
     */
    public function handle()
    {
        return $this->call('aetherfall:website-admin');
    }
}
