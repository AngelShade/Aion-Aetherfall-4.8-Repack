<?php

namespace App\Console\Commands;

use App\Models\WebsiteSetting;
use App\Models\HeroSlide;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Storage;

class WebsiteDefaults extends Command
{
    protected $signature = 'aetherfall:website-defaults';
    protected $description = 'Set initial Aetherfall labels without changing owner settings.';
    public function handle(): int
    {
        $hero = 'hero-slides/aion-hero.jpg';
        if (! Storage::disk('public')->exists($hero)) {
            Storage::disk('public')->put($hero, file_get_contents(public_path('images/aion-hero.jpg')));
        }
        $oldDefaults = ['site_name'=>'Aion Aetherfall', 'server_name'=>'Aion Aetherfall',
            'meta_description'=>'Aion Aetherfall 4.8 local player portal.', 'tagline'=>'Aion 4.8'];
        foreach (['site_name'=>'Aetherfall','server_name'=>'Aetherfall','server_version'=>'4.8',
            'meta_description'=>'Explore Atreia with the Aetherfall community.','tagline'=>'Explore Atreia', 'level_cap'=>'65'] as $key=>$value) {
            $setting = WebsiteSetting::firstOrCreate(['key'=>$key], ['value'=>$value]);
            if (isset($oldDefaults[$key]) && $setting->value === $oldDefaults[$key]) {
                $setting->value = $value;
                $setting->save();
                cache()->forget('website-setting:'.$key);
            }
        }
        // Adapt only the upstream starter copy; owner-authored slides are retained.
        foreach (HeroSlide::query()->get() as $slide) {
            if (in_array($slide->title, ['AION PTS', 'Aion Aetherfall'], true)) {
                $slide->title = 'Aetherfall';
                $slide->description = str_replace(['AION 4.6 PTS', 'Aion Aetherfall 4.8'], 'Aetherfall', (string) $slide->description);
                $slide->save();
            }
        }
        return 0;
    }
}
