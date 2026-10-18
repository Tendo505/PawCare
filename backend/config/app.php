<?php

return [
    // Application identity and environment
    'name' => env('APP_NAME', 'PawCare API'),
    'env' => env('APP_ENV', 'production'),
    'debug' => (bool) env('APP_DEBUG', false),
    'url' => env('APP_URL', 'http://localhost'),

    // Display language and local time
    'timezone' => env('APP_TIMEZONE', 'Asia/Kuala_Lumpur'),
    'locale' => env('APP_LOCALE', 'en'),
    'fallback_locale' => 'en',
    'faker_locale' => 'en_MY',

    // Encryption keys remain in the environment
    'cipher' => 'AES-256-CBC',
    'key' => env('APP_KEY'),
    'previous_keys' => array_filter(explode(',', env('APP_PREVIOUS_KEYS', ''))),

    // Maintenance mode storage
    'maintenance' => [
        'driver' => env('APP_MAINTENANCE_DRIVER', 'file'),
    ],
];
