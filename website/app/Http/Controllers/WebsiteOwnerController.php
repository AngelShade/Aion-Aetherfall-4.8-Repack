<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;

class WebsiteOwnerController extends Controller
{
    public function __invoke(Request $request, string $token)
    {
        abort_unless(getenv('AETHERFALL_WEBSITE_ROOT')
            && in_array($request->server('REMOTE_ADDR'), ['127.0.0.1', '::1'], true), 403);
        $hash = hash('sha256', $token);
        $link = DB::table('website_owner_links')->where('token_hash', $hash)
            ->whereNull('consumed_at')->where('expires_at', '>', now())->first();
        $owner = $link ? User::find($link->user_id) : null;
        abort_unless($owner?->hasRole('super_admin'), 403);
        $consumed = DB::table('website_owner_links')->where('token_hash', $hash)
            ->whereNull('consumed_at')->where('expires_at', '>', now())->update(['consumed_at'=>now()]);
        abort_unless($consumed === 1, 403);
        Auth::guard('web')->login($owner);
        $request->session()->regenerate();
        return redirect('/admin')->header('Cache-Control', 'no-store, private')->header('Referrer-Policy', 'no-referrer');
    }
}
