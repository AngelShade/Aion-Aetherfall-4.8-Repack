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
    protected $description = 'Refresh shipped Aetherfall content while preserving owner-edited articles and settings.';

    public function handle(): int
    {
        $baselines = array_map(fn ($revision) => require resource_path('content/aetherfall-'.$revision.'.php'), ['v1', 'v3', 'v4']);
        DB::transaction(function () use ($baselines): void {
            $starter = ['tagline'=>'Explore Atreia', 'meta_description'=>'Explore Atreia with the Aetherfall community.'];
            foreach (config('aetherfall_content.settings') as $key=>$value) {
                $setting = WebsiteSetting::firstOrCreate(['key'=>$key], ['value'=>$value]);
                // Replace only the earlier empty/starter copy, never customized settings.
                if (blank($setting->value) || (isset($starter[$key]) && $setting->value === $starter[$key])
                    || collect($baselines)->contains(fn ($baseline) => array_key_exists($key, $baseline['settings'])
                        && $setting->value === $baseline['settings'][$key])) {
                    $setting->update(['value'=>$value]);
                }
                cache()->forget('website-setting:'.$key);
            }
            foreach (config('aetherfall_content.articles') as $i=>$article) {
                $cover = $article['cover'];
                if (! Storage::disk('public')->exists($cover)) Storage::disk('public')->put($cover, file_get_contents(public_path('images/'.$cover)));
                unset($article['cover']);
                $record = News::firstOrCreate(['slug'=>$article['slug']], $article + ['cover_image'=>$cover,
                    'status'=>'published', 'published_at'=>now()->subMinutes($i), 'is_featured'=>$i===0]);
                foreach ($baselines as $previous) {
                    $baseline = collect($previous['articles'])->firstWhere('slug', $article['slug']);
                    if ($baseline) {
                        $baseline['cover_image'] = $baseline['cover'];
                        unset($baseline['cover']);
                        // An edited article is owner content. Keep the whole article, including its publication choices.
                        $this->refreshUnedited($record, $baseline, $article + ['cover_image'=>$cover], ['status'=>'published']);
                    }
                }
            }
            foreach (config('aetherfall_content.downloads') as $i=>$item) {
                $record = DownloadItem::firstOrCreate(['slug'=>$item['slug']], $item + ['is_active'=>true, 'sort_order'=>$i, 'published_at'=>now()]);
                foreach ($baselines as $previous) {
                    $baseline = collect($previous['downloads'])->firstWhere('slug', $item['slug']);
                    // Keep a customized download as one unit; never relabel a private URL as a newer release.
                    if ($baseline) $this->refreshUnedited($record, $baseline, $item);
                }
            }
            foreach (config('aetherfall_content.faqs') as $i=>$faq) {
                $record = SupportFaq::firstOrCreate(['question'=>$faq['question']], $faq + ['is_active'=>true, 'sort_order'=>$i]);
                foreach ($baselines as $previous) {
                    $baseline = collect($previous['faqs'])->firstWhere('question', $faq['question']);
                    if ($baseline) $this->refreshUnedited($record, $baseline, $faq);
                }
            }
            $hero = 'aetherfall/hero-v1.png';
            if (! Storage::disk('public')->exists($hero)) Storage::disk('public')->put($hero, file_get_contents(public_path('images/'.$hero)));
            $slide = HeroSlide::where('title', 'Aetherfall')->where('image_path', 'hero-slides/aion-hero.jpg')
                ->where('description', 'Discover the timeless world of Atreia. Two factions, one destiny. Begin your journey in Aetherfall.')->first();
            if ($slide) $slide->update(['eyebrow'=>'Aion 4.8 NA · Repack', 'highlight'=>'',
                'description'=>'Your own Aion 4.8 adventure. Install the complete repack, play solo or host friends, and build your companion party.',
                'image_path'=>$hero, 'primary_label'=>'DOWNLOAD & PLAY', 'primary_url'=>'/download',
                'secondary_label'=>'PLAYER GUIDES', 'secondary_url'=>'/guides']);
            // Recognize the complete earlier shipped slide, preserving owner edits to any field.
            HeroSlide::where('title', 'Aetherfall')->where('image_path', $hero)
                ->where('eyebrow', 'Aion · Solo and co-op')->where('highlight', '')
                ->where('description', 'Quest with friends, recruit your alts and build a companion party. Explore Atreia at your own pace.')
                ->where('primary_label', 'DOWNLOAD & PLAY')->where('primary_url', '/download')
                ->where('secondary_label', 'PLAYER GUIDES')->where('secondary_url', '/guides')
                ->update(['eyebrow'=>'Aion 4.8 NA · Repack',
                    'description'=>'Your own Aion 4.8 adventure. Install the complete repack, play solo or host friends, and build your companion party.']);
        });
        $this->info('Aetherfall news, guides, downloads, FAQs and artwork are ready. Existing custom content was kept.');
        return 0;
    }

    private function refreshUnedited(\Illuminate\Database\Eloquent\Model $record, array $baseline, array $desired, array $guards = []): void
    {
        if ($record->wasRecentlyCreated) return;
        foreach ($baseline + $guards as $key => $value) {
            if ((string) $record->getRawOriginal($key) !== (string) $value) return;
        }
        // Existing dates, draft status, feature choices, ordering and visibility belong to the owner.
        unset($desired['published_at'], $desired['is_featured'], $desired['status']);
        $record->fill($desired);
        if ($record->isDirty()) $record->save();
    }
}
