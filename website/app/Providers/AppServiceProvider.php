<?php

namespace App\Providers;

use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Gate;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\Facades\View;
use App\Models\InstalledPlugin;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\ServiceProvider;
use Illuminate\Support\Str;
use Spatie\Permission\Models\Role;
use App\Policies\RolePolicy;

class AppServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->scoped(\App\Services\WebsiteViewData::class);
        // Build the plugin namespace map once per PHP process. The previous
        // autoloader rescanned every plugin directory and decoded every manifest
        // for each unresolved class, adding avoidable filesystem work to requests.
        spl_autoload_register(static function (string $class): void {
            static $namespaceMap = null;

            if ($namespaceMap === null) {
                $namespaceMap = [];
                $pluginRoot = storage_path('app/aion-plugins');

                if (is_dir($pluginRoot)) {
                    foreach (\Illuminate\Support\Facades\File::directories($pluginRoot) as $directory) {
                        $manifestPath = $directory.'/plugin.json';
                        if (! is_file($manifestPath)) continue;

                        $manifest = json_decode((string) file_get_contents($manifestPath), true);
                        $namespace = is_array($manifest) ? ($manifest['namespace'] ?? null) : null;

                        if (is_string($namespace) && $namespace !== '') {
                            $namespaceMap[$namespace] = $directory.'/src';
                        }
                    }

                    uksort($namespaceMap, static fn (string $a, string $b): int => strlen($b) <=> strlen($a));
                }
            }

            foreach ($namespaceMap as $namespace => $sourceRoot) {
                if (! str_starts_with($class, $namespace.'\\')) continue;

                $relative = substr($class, strlen($namespace) + 1);
                if ($relative === '' || str_contains($relative, '..')) return;

                $file = $sourceRoot.'/'.str_replace('\\', '/', $relative).'.php';
                if (is_file($file)) require_once $file;
                return;
            }
        }, true, false);
    }

    public function boot(): void
    {
        Gate::policy(Role::class, RolePolicy::class);

        // Register view namespaces for installed packages without changing the core layout.
        try {
            if (Schema::hasTable('installed_plugins')) {
                Cache::remember('aion.installed_plugin_view_namespaces.v1', 30, fn () => InstalledPlugin::query()->get(['plugin_id'])->map(fn (InstalledPlugin $plugin) => $plugin->plugin_id)->all());
                foreach (Cache::get('aion.installed_plugin_view_namespaces.v1', []) as $pluginId) {
                    $plugin = new InstalledPlugin(['plugin_id' => $pluginId]);
                    $views = storage_path('app/aion-plugins/'.$plugin->plugin_id.'/resources/views');
                    if (is_dir($views)) {
                        View::addNamespace('aion-plugin-'.$plugin->plugin_id, $views);
                    }
                }
            }
        } catch (\Throwable $exception) {
            // Keep Artisan commands and first-install boot working before migrations are applied.
        }

        // Keep each account action on its own limiter so normal registration,
        // login and password-change traffic do not consume one shared bucket.
        RateLimiter::for('aion-register', function (Request $request) {
            return Limit::perMinute(5)->by('aion-register:'.$request->ip());
        });

        RateLimiter::for('aion-login', function (Request $request) {
            // Apply both an IP-wide ceiling and a username-specific ceiling.
            // This prevents bypassing the login limit by rotating usernames or IPs.
            $username = Str::lower(trim((string) $request->input('username', '')));

            return [
                Limit::perMinute(30)->by('aion-login-ip:'.$request->ip()),
                Limit::perMinute(8)->by('aion-login-user:'.$username),
            ];
        });

        RateLimiter::for('aion-password-change', function (Request $request) {
            $uid = (string) $request->session()->get('aion_account_uid', 'unknown');
            return Limit::perMinutes(10, 5)->by('aion-password-change:'.$request->ip().'|'.$uid);
        });

        // All nested and plugin views keep the original contracts, without repeating
        // settings, feature and plugin-cache work for every included partial.
        View::composer('*', function ($view): void {
            $view->with(app(\App\Services\WebsiteViewData::class)->get());
        });
    }
}
