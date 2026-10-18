<?php

return [
    // Session-aware clients and account guard
    'stateful' => explode(',', env('SANCTUM_STATEFUL_DOMAINS', 'localhost,localhost:3000,127.0.0.1,127.0.0.1:8000')),
    'guard' => ['web'],

    // Expire Android bearer tokens after the configured number of minutes
    'expiration' => env('SANCTUM_EXPIRATION', 43200),
    'token_prefix' => env('SANCTUM_TOKEN_PREFIX', ''),

    // Apply session, cookie and CSRF protection
    'middleware' => [
        'authenticate_session' => Laravel\Sanctum\Http\Middleware\AuthenticateSession::class,
        'encrypt_cookies' => Illuminate\Cookie\Middleware\EncryptCookies::class,
        'validate_csrf_token' => Illuminate\Foundation\Http\Middleware\ValidateCsrfToken::class,
    ],
];
