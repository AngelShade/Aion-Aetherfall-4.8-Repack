<?php

namespace App\Console\Commands;

use App\Models\User;
use App\Models\WebsiteSetting;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Spatie\Permission\Models\Role;

class WebsiteOwnerLink extends Command
{
    protected $signature = 'aetherfall:website-owner-link';
    protected $description = 'Create a short-lived, single-use local control-panel link.';

    public function handle(): int
    {
        if (! getenv('AETHERFALL_WEBSITE_ROOT')) {
            $this->error('Use Admin.cmd in the standalone website folder.');
            return 1;
        }
        Role::firstOrCreate(['name'=>'super_admin', 'guard_name'=>'web']);
        $owner = User::role('super_admin')->find((int) WebsiteSetting::get('local_owner_user_id', 0))
            ?? User::role('super_admin')->orderBy('id')->first();
        if (! $owner && User::query()->doesntExist()) {
            $owner = User::create(['name'=>'Website Owner', 'email'=>'owner@aetherfall.local', 'password'=>Str::random(64)]);
            $owner->assignRole('super_admin');
        }
        if (! $owner) {
            $this->error('Create a website administrator using Website Tools.cmd first.');
            return 1;
        }
        WebsiteSetting::putMany(['local_owner_user_id'=>(string) $owner->id]);
        $token = bin2hex(random_bytes(32));
        DB::transaction(function () use ($owner, $token): void {
            DB::table('website_owner_links')->where('expires_at', '<=', now())->delete();
            DB::table('website_owner_links')->where('user_id', $owner->id)->delete();
            DB::table('website_owner_links')->insert(['token_hash'=>hash('sha256', $token), 'user_id'=>$owner->id, 'expires_at'=>now()->addMinutes(5)]);
        });
        $this->line(url('/owner-access/'.$token));
        return 0;
    }
}
