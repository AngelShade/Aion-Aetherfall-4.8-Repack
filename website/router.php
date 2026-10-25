<?php

// The portable server exposes only Laravel public files; persistent uploads are
// served by Laravel's filesystem routes without a Windows junction or symlink.
$public = __DIR__.'/public';
$path = rawurldecode(parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH) ?: '/');
$file = realpath($public.$path);
if ($file && str_starts_with($file, realpath($public).DIRECTORY_SEPARATOR) && is_file($file)) {
    return false;
}
require $public.'/index.php';
