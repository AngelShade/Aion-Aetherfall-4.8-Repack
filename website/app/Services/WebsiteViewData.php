<?php

namespace App\Services;

use App\Models\InstalledPlugin;
use App\Models\ModulePlugin;
use App\Models\WebsiteSetting;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Schema;

/** Shared view data is resolved once per request, never across player sessions. */
class WebsiteViewData
{
    private ?array $data = null;
    private ?bool $hasAccount = null;

    public function forget(): void
    {
        $this->data = null;
        $this->hasAccount = null;
    }

    public function get(): array
    {
        $hasAccount = session()->has('aion_account_username');
        if ($this->data !== null && $this->hasAccount === $hasAccount) {
            return $this->data;
        }
        $defaults = [
            'site_name'=>'Aetherfall', 'tagline'=>'Explore Atreia',
            'meta_description'=>'Aetherfall: a complete Aion 4.8 NA repack for solo and co-op play, with companions, world bots and custom features.',
            'logo_path'=>'', 'favicon_path'=>'', 'server_name'=>'Aetherfall',
            'server_version'=>'4.8', 'server_rate'=>'', 'level_cap'=>'',
            'xp_rate'=>'', 'quest_xp_rate'=>'', 'drop_rate'=>'', 'quest_kinah_rate'=>'',
            'gathering_rate'=>'', 'crafting_rate'=>'', 'character_slots'=>'',
            'character_faction_rule'=>'', 'release_url'=>'', 'repack_version'=>'',
            'contact_email'=>'', 'discord_url'=>'', 'facebook_url'=>'',
            'maintenance_mode'=>false, 'maintenance_message'=>'We will be back soon.',
        ];
        $values = WebsiteSetting::values();
        $settings = [];
        foreach ($defaults as $key=>$default) $settings[$key] = $values[$key] ?? $default;
        $settings['maintenance_mode'] = filter_var($settings['maintenance_mode'], FILTER_VALIDATE_BOOLEAN);
        $features = [];
        foreach (['home', 'news', 'info', 'download', 'donation', 'support',
            'account.registration', 'account.login', 'account.characters', 'account.password', 'server.status', 'rankings', 'events', 'start', 'repack.tools'] as $key) {
            $features[$key] = ModulePlugin::featureEnabled($key);
        }
        $hooks = $navigation = $accountNavigation = [];
        try {
            if (Schema::hasTable('installed_plugins')) {
                $plugins = Cache::remember('aion.enabled_plugin_manifests.v1', 30, fn () => InstalledPlugin::query()
                    ->where('is_enabled', true)->orderBy('name')->get(['plugin_id', 'manifest'])
                    ->map(fn (InstalledPlugin $plugin) => ['plugin_id'=>$plugin->plugin_id,
                        'manifest'=>is_array($plugin->manifest) ? $plugin->manifest : []])->all());
                foreach ($plugins as $plugin) {
                    $manifest = $plugin['manifest'];
                    foreach (['topbar', 'footer'] as $hook) {
                        if (filled($manifest['hooks'][$hook] ?? null)) $hooks[$hook] = $manifest['hooks'][$hook];
                    }
                    foreach (($manifest['navigation'] ?? []) as $item) {
                        if (! is_array($item) || ! filled($item['label'] ?? null)) continue;
                        if (($item['requires_login'] ?? false) && ! $hasAccount) continue;
                        if (! ($item['requires_login'] ?? false) && ($item['guest_only'] ?? false) && $hasAccount) continue;
                        $entry = ['label'=>(string) $item['label'], 'url'=>(string) ($item['url'] ?? '#')];
                        if (($item['placement'] ?? 'global') === 'account_sidebar') {
                            if ($hasAccount) $accountNavigation[] = $entry;
                        } else {
                            $navigation[] = $entry;
                        }
                    }
                }
            }
        } catch (\Throwable $exception) {
            // Match the existing first-install/offline plugin-registry behavior.
        }
        $this->hasAccount = $hasAccount;
        return $this->data = ['websiteSettings'=>$settings, 'aionFeatures'=>$features,
            'aionPluginHooks'=>$hooks, 'aionPluginNavigation'=>$navigation,
            'aionAccountPluginNavigation'=>$accountNavigation];
    }
}
