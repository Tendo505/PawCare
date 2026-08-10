<?php

return [
    'name' => env('APP_NAME', 'PawCare API'),
    'env' => env('APP_ENV', 'production'),
    'debug' => (bool) env('APP_DEBUG', false),
    'url' => env('APP_URL', 'http://localhost'),
    'timezone' => env('APP_TIMEZONE', 'Asia/Kuala_Lumpur'),
    'locale' => env('APP_LOCALE', 'en'),
    'fallback_locale' => 'en',
    'faker_locale' => 'en_MY',
    'cipher' => 'AES-256-CBC',
    'key' => env('APP_KEY'),
    'previous_keys' => array_filter(explode(',', env('APP_PREVIOUS_KEYS', ''))),
    'maintenance' => ['driver' => env('APP_MAINTENANCE_DRIVER', 'file')],
];
