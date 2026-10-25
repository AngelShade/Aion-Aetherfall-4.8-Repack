<?php

/** Serve public assets before loading Laravel; private files never enter this path. */
function aetherfallServePublicFile(string $root): bool
{
    $method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
    $path = rawurldecode(parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH) ?: '/');
    if (str_contains($path, "\0") || str_contains($path, '\\') || preg_match('#(?:^|/)\.\.(?:/|$)#', $path)) {
        http_response_code(400);
        header('Cache-Control: no-store');
        return true;
    }
    if (! in_array($method, ['GET', 'HEAD'], true) || isset($_SERVER['HTTP_RANGE'])) return false;
    $media = str_starts_with($path, '/media/');
    $base = realpath($media ? $root.'/local/state/app/public' : __DIR__.'/public');
    if (! $base) return false;
    $file = realpath($base.'/'.($media ? substr($path, 7) : ltrim($path, '/')));
    if (! $file || ! str_starts_with($file, $base.DIRECTORY_SEPARATOR) || ! is_file($file)) return false;
    $types = ['png'=>'image/png', 'jpg'=>'image/jpeg', 'jpeg'=>'image/jpeg', 'gif'=>'image/gif',
        'webp'=>'image/webp', 'avif'=>'image/avif', 'svg'=>'image/svg+xml', 'ico'=>'image/x-icon',
        'css'=>'text/css; charset=UTF-8', 'js'=>'application/javascript; charset=UTF-8',
        'mjs'=>'application/javascript; charset=UTF-8', 'json'=>'application/json', 'map'=>'application/json',
        'txt'=>'text/plain; charset=UTF-8', 'woff'=>'font/woff', 'woff2'=>'font/woff2',
        'ttf'=>'font/ttf', 'otf'=>'font/otf', 'eot'=>'application/vnd.ms-fontobject'];
    $type = $types[strtolower(pathinfo($file, PATHINFO_EXTENSION))] ?? null;
    if (! $type) return false;
    $modified = filemtime($file);
    $etag = '"'.hash_file('sha256', $file).'"';
    // Revalidation keeps replacements at the same URL visible immediately. The
    // browser can reuse unchanged image bytes following a very small 304 response.
    header('Content-Type: '.$type);
    header('X-Content-Type-Options: nosniff');
    header('Cache-Control: public, max-age=0, must-revalidate');
    header('ETag: '.$etag);
    header('Last-Modified: '.gmdate('D, d M Y H:i:s', $modified).' GMT');
    $match = $_SERVER['HTTP_IF_NONE_MATCH'] ?? null;
    $notModified = $match !== null
        ? ($match === '*' || in_array($etag, array_map(
            static fn ($tag) => preg_replace('/^W\//', '', trim($tag)), explode(',', $match)), true))
        : (isset($_SERVER['HTTP_IF_MODIFIED_SINCE'])
            && ($since = strtotime($_SERVER['HTTP_IF_MODIFIED_SINCE'])) !== false && $since >= $modified);
    if ($notModified) {
        http_response_code(304);
        return true;
    }
    header('Content-Length: '.filesize($file));
    if ($method !== 'HEAD') readfile($file);
    return true;
}
