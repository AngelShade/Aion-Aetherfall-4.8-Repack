<?php

// Independent website installation: no repack root or game component is required.
$root = getenv('AETHERFALL_WEBSITE_ROOT');
if (! $root || ! is_file($root.'/component.json')) {
    throw new RuntimeException('Run this application from its standalone website folder.');
}
if (PHP_SAPI === 'cli-server') {
    $instance = getenv('AETHERFALL_WEBSITE_INSTANCE');
    if (is_string($instance) && preg_match('/^[a-f0-9-]{36}$/i', $instance)) {
        header('X-Aetherfall-Website: '.$instance);
    }
    require_once __DIR__.'/serve-public.php';
    if (aetherfallServePublicFile($root)) return;
}
$local = $root.'/local';
$state = $local.'/state';
foreach (['', '/app/private', '/app/public', '/framework/cache/data', '/framework/sessions', '/framework/views', '/logs'] as $directory) {
    if (! is_dir($state.$directory)) mkdir($state.$directory, 0700, true);
}
require_once __DIR__.'/vendor/autoload.php';
$settingsPath = $local.'/settings.env';
if (! is_file($settingsPath)) {
    $settings = 'APP_KEY=base64:'.base64_encode(random_bytes(32))."\n"
        ."APP_URL=http://127.0.0.1:8090\nAPP_TIMEZONE=UTC\n"
        ."AION_DB_HOST=127.0.0.1\nAION_DB_PORT=3306\nAION_DB_USERNAME=\nAION_DB_PASSWORD=\n"
        ."AION_ACCOUNT_DATABASE=aion_ls\nAION_WORLD_DATABASE=aion_gs\n"
        ."AION_STATUS_HOST=127.0.0.1\nAION_STATUS_PORT=7777\nGEMINI_API_KEY=\n";
    file_put_contents($settingsPath, $settings, LOCK_EX);
}
$settings = \Dotenv\Dotenv::parse(file_get_contents($settingsPath));
$database = $local.'/website.sqlite';
if (! is_file($database)) touch($database);
$defaults = [
    'APP_NAME'=>'Aetherfall', 'APP_ENV'=>'production', 'APP_DEBUG'=>'false',
    'APP_TIMEZONE'=>'UTC', 'DB_CONNECTION'=>'sqlite', 'DB_DATABASE'=>$database,
    'AION_BACKEND'=>'aetherfall', 'SESSION_DRIVER'=>'file', 'CACHE_STORE'=>'file',
    'QUEUE_CONNECTION'=>'sync', 'FILESYSTEM_DISK'=>'local',
    'SESSION_COOKIE'=>'aetherfall_'.substr(hash('sha256', realpath($root)),0,12),
    'LARAVEL_STORAGE_PATH'=>$state,
];
// Connection settings belong to this website. No game credentials are imported.
foreach (array_merge($defaults, $settings, [
    'DB_CONNECTION'=>'sqlite', 'DB_DATABASE'=>$database, 'LARAVEL_STORAGE_PATH'=>$state,
]) as $key => $value) {
    $_ENV[$key] = $_SERVER[$key] = (string) $value;
    putenv($key.'='.$value);
}
if (PHP_SAPI === 'cli-server') {
    return require __DIR__.'/router.php';
}
require __DIR__.'/artisan';
