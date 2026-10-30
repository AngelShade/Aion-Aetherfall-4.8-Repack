<?php

use App\Http\Controllers\NewsController;
use App\Http\Controllers\AionRegistrationController;
use App\Http\Controllers\AionLoginController;
use App\Http\Controllers\AionPasswordController;
use App\Http\Controllers\AionCharacterController;
use App\Models\DonationOption;
use App\Models\DownloadItem;
use App\Models\News;
use App\Models\HeroSlide;
use App\Services\AionServerStatus;
use App\Services\AionEventSchedule;
use App\Http\Controllers\CommunityController;
use App\Http\Controllers\RepackToolsController;
use Illuminate\Support\Facades\Route;

Route::get('/owner-access/{token}', \App\Http\Controllers\WebsiteOwnerController::class)
    ->where('token', '[a-f0-9]{64}')->name('website.owner');

Route::get('/guides', [NewsController::class, 'guides'])
    ->middleware(['website.available', 'feature.enabled:news'])->name('guides');

Route::view('/gallery', 'pages.gallery')->middleware(['website.available', 'feature.enabled:info'])->name('gallery');

Route::view('/start', 'pages.start')->middleware(['website.available', 'feature.enabled:start'])->name('start');
Route::get('/rankings', [CommunityController::class, 'index'])
    ->middleware(['website.available', 'feature.enabled:rankings', 'throttle:60,1'])->name('rankings');
Route::get('/characters/{id}', [CommunityController::class, 'character'])->whereNumber('id')
    ->middleware(['website.available', 'feature.enabled:rankings', 'throttle:60,1'])->name('characters.show');
Route::get('/legions', [CommunityController::class, 'legions'])
    ->middleware(['website.available', 'feature.enabled:rankings', 'throttle:60,1'])->name('legions');
Route::get('/legions/{id}', [CommunityController::class, 'legion'])->whereNumber('id')
    ->middleware(['website.available', 'feature.enabled:rankings', 'throttle:60,1'])->name('legions.show');
Route::get('/events', function (\Illuminate\Http\Request $request, AionEventSchedule $events) {
    $selectedDay = $request->query('day');
    if (! is_string($selectedDay) || ! array_key_exists($selectedDay, AionEventSchedule::DAYS)) $selectedDay = 'MON';
    $schedule = $events->weekly();
    $announcements = \App\Models\ModulePlugin::featureEnabled('news') ? News::query()
        ->where('category', 'Event')->where('status', 'published')
        ->where(fn ($q) => $q->whereNull('published_at')->orWhere('published_at', '<=', now()))
        ->orderByDesc('published_at')->limit(6)->get() : collect();
    return view('pages.events', compact('selectedDay', 'schedule', 'announcements'));
})->middleware(['website.available', 'feature.enabled:events'])->name('events');
Route::get('/status', function (AionServerStatus $status) {
    return view('pages.status', ['metrics' => $status->snapshot()]);
})->middleware(['website.available', 'feature.enabled:server.status'])->name('status');
Route::get('/feed.xml', [NewsController::class, 'feed'])
    ->middleware(['website.available', 'feature.enabled:news'])->name('news.feed');

Route::middleware(['website.available', 'feature.enabled:repack.tools'])->group(function () {
    Route::get('/tools', [RepackToolsController::class, 'index'])->name('tools');
    Route::get('/tools/classes', [RepackToolsController::class, 'classes'])->name('tools.classes');
    Route::get('/tools/party-planner', [RepackToolsController::class, 'planner'])->name('tools.planner');
    Route::get('/tools/instances', [RepackToolsController::class, 'atlas'])->name('tools.atlas');
    Route::get('/tools/commands', [RepackToolsController::class, 'commands'])->name('tools.commands');
});

Route::get('/', function (AionServerStatus $aionServerStatus) {
    $latestNews = News::query()->where('category', '<>', 'Guide')
        ->where('status', 'published')
        ->where(function ($query) {
            $query->whereNull('published_at')
                ->orWhere('published_at', '<=', now());
        })
        ->orderByRaw('CASE WHEN published_at IS NULL THEN 1 ELSE 0 END')
        ->orderByDesc('published_at')
        ->orderByDesc('id')
        ->take(3)
        ->get();

    $heroSlides = HeroSlide::query()
        ->where('is_active', true)
        ->orderBy('sort_order')
        ->orderBy('id')
        ->get();

    $serverMetrics = $aionServerStatus->snapshot();

    return view('home', compact('latestNews', 'heroSlides', 'serverMetrics'));
})->middleware(['website.available', 'feature.enabled:home'])->name('home');

Route::get('/news', [NewsController::class, 'index'])
    ->middleware(['website.available', 'feature.enabled:news'])
    ->name('news.index');

Route::get('/news/{slug}', [NewsController::class, 'show'])
    ->middleware(['website.available', 'feature.enabled:news'])
    ->name('news.show');

Route::get('/media/{path}', function (string $path) {
    $disk = \Illuminate\Support\Facades\Storage::disk('public');
    abort_if(str_contains($path, '..') || str_contains($path, '\\') || ! $disk->exists($path), 404);
    return $disk->response($path, null, ['X-Content-Type-Options'=>'nosniff']);
})->where('path', '.*');

// Informational pages.
Route::get('/info', function (AionServerStatus $aionServerStatus) {
    $serverMetrics = $aionServerStatus->snapshot();

    return view('pages.info', compact('serverMetrics'));
})->middleware(['website.available', 'feature.enabled:info'])->name('info');
Route::get('/download', function () {
    $downloadItems = DownloadItem::query()
        ->where('is_active', true)
        ->where(function ($query) {
            $query->whereNull('published_at')
                ->orWhere('published_at', '<=', now());
        })
        ->orderBy('sort_order')
        ->orderBy('id')
        ->get();

    return view('pages.download', compact('downloadItems'));
})->middleware(['website.available', 'feature.enabled:download'])->name('download');
Route::get('/donation', function () {
    $donationOptions = DonationOption::query()
        ->where('is_active', true)
        ->orderBy('sort_order')
        ->orderBy('id')
        ->get();

    return view('pages.donation', compact('donationOptions'));
})->middleware(['website.available', 'feature.enabled:donation'])->name('donation');
Route::get('/support', function () {
    $supportFaqs = \App\Models\SupportFaq::query()
        ->where('is_active', true)
        ->orderBy('sort_order')
        ->orderBy('id')
        ->get();

    return view('pages.support', compact('supportFaqs'));
})->middleware(['website.available', 'feature.enabled:support'])->name('support');

// AION game account registration and session-based player login.
Route::get('/register', [AionRegistrationController::class, 'create'])
    ->middleware(['website.available', 'guest.aion', 'feature.enabled:account.registration'])
    ->name('register');
Route::post('/register', [AionRegistrationController::class, 'store'])
    ->middleware(['website.available', 'guest.aion', 'feature.enabled:account.registration', 'throttle:aion-register'])
    ->name('register.store');
Route::get('/login', [AionLoginController::class, 'create'])
    ->middleware(['website.available', 'guest.aion', 'feature.enabled:account.login'])
    ->name('login');
Route::post('/login', [AionLoginController::class, 'store'])
    ->middleware(['website.available', 'guest.aion', 'feature.enabled:account.login', 'throttle:aion-login'])
    ->name('login.store');
Route::post('/logout', [AionLoginController::class, 'destroy'])
    ->middleware('auth.aion')
    ->name('logout');
Route::get('/account', [AionLoginController::class, 'account'])
    ->middleware(['website.available', 'auth.aion'])
    ->name('account');
Route::get('/account/characters', [AionCharacterController::class, 'index'])
    ->middleware(['website.available', 'auth.aion', 'feature.enabled:account.characters'])
    ->name('account.characters');
Route::get('/account/change-password', [AionPasswordController::class, 'edit'])
    ->middleware(['website.available', 'auth.aion', 'feature.enabled:account.password'])
    ->name('account.password.edit');
Route::post('/account/change-password', [AionPasswordController::class, 'update'])
    ->middleware(['website.available', 'auth.aion', 'feature.enabled:account.password', 'throttle:aion-password-change'])
    ->name('account.password.update');

// Read-only JSON endpoint used by the public server status widgets.
Route::get('/api/server-status', function (AionServerStatus $aionServerStatus) {
    return response()->json($aionServerStatus->snapshot())
        ->header('Cache-Control', 'no-store, private')
        ->header('X-Content-Type-Options', 'nosniff');
})->middleware(['website.available', 'feature.enabled:server.status'])->name('api.server-status');

// Public XML sitemap for search engines.
Route::get('/sitemap.xml', function () {
    $urls = collect();

    if (\App\Models\ModulePlugin::featureEnabled('home')) {
        $urls->push(['loc' => route('home'), 'lastmod' => now()->toAtomString()]);
    }

    foreach ([
        'news' => 'news.index', 'info' => 'info', 'download' => 'download',
        'donation' => 'donation', 'support' => 'support', 'rankings' => 'rankings',
        'events' => 'events', 'start' => 'start', 'server.status' => 'status',
        'repack.tools' => 'tools',
    ] as $feature => $routeName) {
        if (\App\Models\ModulePlugin::featureEnabled($feature)) {
            $urls->push(['loc' => route($routeName), 'lastmod' => now()->toAtomString()]);
        }
    }

    if (\App\Models\ModulePlugin::featureEnabled('repack.tools')) {
        foreach (['tools.classes', 'tools.planner', 'tools.atlas', 'tools.commands'] as $routeName) {
            $urls->push(['loc' => route($routeName), 'lastmod' => now()->toAtomString()]);
        }
    }

    if (\App\Models\ModulePlugin::featureEnabled('news')) $urls->push(['loc'=>route('guides'), 'lastmod'=>now()->toAtomString()]);
    if (\App\Models\ModulePlugin::featureEnabled('info')) $urls->push(['loc'=>route('gallery'), 'lastmod'=>now()->toAtomString()]);
    if (\App\Models\ModulePlugin::featureEnabled('rankings')) $urls->push(['loc'=>route('legions'), 'lastmod'=>now()->toAtomString()]);

    if (\App\Models\ModulePlugin::featureEnabled('news')) News::query()
        ->where('status', 'published')
        ->where(function ($query) {
            $query->whereNull('published_at')->orWhere('published_at', '<=', now());
        })
        ->orderByDesc('published_at')
        ->get(['slug', 'updated_at'])
        ->each(function (News $news) use ($urls) {
            $urls->push(['loc' => route('news.show', $news->slug), 'lastmod' => $news->updated_at?->toAtomString()]);
        });

    return response(view('seo.sitemap', ['urls' => $urls])->render(), 200, [
        'Content-Type' => 'application/xml; charset=UTF-8',
    ]);
})->name('sitemap');

// Temporary signed preview for saved news articles.
Route::get('/news-preview/{news}', [NewsController::class, 'preview'])
    ->middleware('signed')
    ->name('news.preview');


// Only load route files for packages explicitly enabled in the registry. A disabled
// plugin's PHP route file is never required during application boot. If the registry
// cannot be queried (for example, before migrations), the core site still boots and
// plugin routes are omitted until the database is available again.
try {
    $pluginRoot = storage_path('app/aion-plugins');
    if (is_dir($pluginRoot) && \Illuminate\Support\Facades\Schema::hasTable('installed_plugins')) {
        $enabledPlugins = \App\Models\InstalledPlugin::query()
            ->where('is_enabled', true)
            ->get(['plugin_id', 'manifest'])
            ->keyBy('plugin_id');

        foreach (\Illuminate\Support\Facades\File::directories($pluginRoot) as $pluginDirectory) {
            $pluginId = basename($pluginDirectory);
            $registered = $enabledPlugins->get($pluginId);
            if (! $registered) continue;

            $routeFile = $pluginDirectory.'/routes/web.php';
            if (! is_file($routeFile)) continue;
            $manifest = is_array($registered->manifest) ? $registered->manifest : [];
            $manifestPath = $pluginDirectory.'/plugin.json';
            if (! is_file($manifestPath)) continue;
            $diskManifest = json_decode((string) file_get_contents($manifestPath), true);
            if (! is_array($diskManifest)
                || ($diskManifest['id'] ?? null) !== $pluginId
                || ($diskManifest['routes'] ?? null) !== 'routes/web.php'
                || ($manifest['id'] ?? null) !== $pluginId
                || ($manifest['routes'] ?? null) !== 'routes/web.php') continue;

            $basePath = trim((string) ($manifest['base_path'] ?? $pluginId), '/');
            if ($basePath !== '' && ! preg_match('#^[a-z0-9][a-z0-9/-]*$#', $basePath)) continue;

            $routeGroup = Route::middleware(['plugin.enabled:'.$pluginId])
                ->name('plugins.'.$pluginId.'.');
            if ($basePath !== '') $routeGroup->prefix($basePath);
            $routeGroup->group(function () use ($routeFile): void {
                require $routeFile;
            });
        }
    }
} catch (\Throwable $exception) {
    report($exception);
}

// Keep unknown URLs inside Laravel's web middleware so the session is available
// to the shared layout (including the AION account navbar) on custom 404 pages.
Route::fallback(function () {
    return response()->view('errors.404', [], 404);
});
