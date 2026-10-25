<?php

namespace App\Console\Commands;

use App\Models\User;
use Illuminate\Console\Command;
use Spatie\Permission\Models\Role;

class WebsiteAdmin extends Command
{
    protected $signature = 'aetherfall:website-admin';
    protected $description = 'Create or reset a local website administrator (game accounts are unchanged).';

    public function handle(): int
    {
        $email = $this->ask('Website administrator email');
        if (! filter_var($email, FILTER_VALIDATE_EMAIL)) { $this->error('Enter a valid email address.'); return 1; }
        $password = $this->secret('Website administrator password (at least 12 characters)');
        if (strlen((string) $password) < 12) { $this->error('Use at least 12 characters.'); return 1; }
        if ($password !== $this->secret('Confirm password')) { $this->error('Passwords do not match.'); return 1; }
        $user = User::updateOrCreate(['email'=>$email], ['name'=>'Website Administrator', 'password'=>$password]);
        Role::firstOrCreate(['name'=>'super_admin', 'guard_name'=>'web']);
        $user->assignRole('super_admin');
        $this->info('Website administrator is ready. Sign in at /admin.');
        return 0;
    }
}
