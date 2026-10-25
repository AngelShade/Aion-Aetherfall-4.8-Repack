<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class WebsiteSetting extends Model
{
    protected $fillable = ['key', 'value'];

    protected static function booted(): void
    {
        $clear = static function (WebsiteSetting $setting): void {
            cache()->forget('website-setting:'.$setting->key);
            cache()->forget('website-setting-values.v1');
            if (app()->resolved(\App\Services\WebsiteViewData::class)) {
                app(\App\Services\WebsiteViewData::class)->forget();
            }
        };
        static::saved($clear);
        static::deleted($clear);
    }

    public static function values(): array
    {
        return cache()->remember('website-setting-values.v1', 300,
            fn () => static::query()->pluck('value', 'key')->all());
    }

    public static function get(string $key, mixed $default = null): mixed
    {
        return cache()->remember('website-setting:'.$key, 300, fn () => static::query()->where('key', $key)->value('value') ?? $default);
    }

    public static function putMany(array $settings): void
    {
        foreach ($settings as $key => $value) {
            static::query()->updateOrCreate(['key' => $key], ['value' => is_bool($value) ? ($value ? '1' : '0') : $value]);
            cache()->forget('website-setting:'.$key);
        }
    }
}
