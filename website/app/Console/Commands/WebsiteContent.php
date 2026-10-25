<?php

namespace App\Console\Commands;

use App\Models\DownloadItem;
use App\Models\HeroSlide;
use App\Models\News;
use App\Models\SupportFaq;
use App\Models\WebsiteSetting;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;

class WebsiteContent extends Command
{
    protected $signature = 'aetherfall:website-content';
    protected $description = 'Add verified Aetherfall starter content, preserving existing articles and custom settings.';

    public function handle(): int
    {
        DB::transaction(function (): void {
            $starter = ['tagline'=>'Explore Atreia', 'meta_description'=>'Explore Atreia with the Aetherfall community.'];
            foreach (config('aetherfall_content.settings') as $key=>$value) {
                $setting = WebsiteSetting::firstOrCreate(['key'=>$key], ['value'=>$value]);
                // Replace only the earlier empty/starter copy, never customized settings.
                if (blank($setting->value) || (isset($starter[$key]) && $setting->value === $starter[$key])) {
                    $setting->update(['value'=>$value]);
                }
                cache()->forget('website-setting:'.$key);
            }
            foreach (config('aetherfall_content.articles') as $i=>$article) {
                $cover = $article['cover'];
                if (! Storage::disk('public')->exists($cover)) Storage::disk('public')->put($cover, file_get_contents(public_path('images/'.$cover)));
                unset($article['cover']);
                News::firstOrCreate(['slug'=>$article['slug']], $article + ['cover_image'=>$cover,
                    'status'=>'published', 'published_at'=>now()->subMinutes($i), 'is_featured'=>$i===0]);
            }
            foreach (config('aetherfall_content.downloads') as $i=>$item) {
                DownloadItem::firstOrCreate(['slug'=>$item['slug']], $item + ['is_active'=>true, 'sort_order'=>$i, 'published_at'=>now()]);
            }
            foreach (config('aetherfall_content.faqs') as $i=>$faq) {
                SupportFaq::firstOrCreate(['question'=>$faq['question']], $faq + ['is_active'=>true, 'sort_order'=>$i]);
            }
            $hero = 'aetherfall/hero-v1.png';
            if (! Storage::disk('public')->exists($hero)) Storage::disk('public')->put($hero, file_get_contents(public_path('images/'.$hero)));
            $slide = HeroSlide::where('title', 'Aetherfall')->where('image_path', 'hero-slides/aion-hero.jpg')
                ->where('description', 'Discover the timeless world of Atreia. Two factions, one destiny. Begin your journey in Aetherfall.')->first();
            if ($slide) $slide->update(['eyebrow'=>'Aion · Solo and co-op', 'highlight'=>'',
                'description'=>'Quest with friends, recruit your alts and build a companion party. Explore Atreia at your own pace.',
                'image_path'=>$hero, 'primary_label'=>'DOWNLOAD & PLAY', 'primary_url'=>'/download',
                'secondary_label'=>'PLAYER GUIDES', 'secondary_url'=>'/guides']);
        });
        $this->info('Aetherfall news, guides, downloads, FAQs and artwork are ready. Existing custom content was kept.');
        return 0;
    }
}
