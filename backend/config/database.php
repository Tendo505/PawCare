<?php

use Illuminate\Support\Str;

return [
    //use the demo database unless an existing environment selects PostgreSQL
    'default' => env('DB_CONNECTION', 'sqlite'),
    'connections' => [
        // Permanent application records
        'pgsql' => [
            'driver' => 'pgsql',
            'url' => env('DB_URL'),
            'host' => env('DB_HOST', '127.0.0.1'),
            'port' => env('DB_PORT', '5432'),
            'database' => env('DB_DATABASE', 'pawcare'),
            'username' => env('DB_USERNAME', 'postgres'),
            'password' => env('DB_PASSWORD', ''),
            'charset' => 'utf8',
            'prefix' => '',
            'prefix_indexes' => true,
            'search_path' => 'public',
            'sslmode' => env('DB_SSLMODE', 'prefer'),
        ],
        //keep the demo and isolated test records separate from PostgreSQL
        'sqlite' => [
            'driver' => 'sqlite',
            'url' => env('DB_URL'),
            'database' => env('DB_DATABASE', database_path('database.sqlite')),
            'prefix' => '',
            'foreign_key_constraints' => true,
        ],
    ],

    // Track applied schema changes
    'migrations' => [
        'table' => 'migrations',
        'update_date_on_publish' => true,
    ],

    // Redis connection options
    'redis' => [
        'client' => env('REDIS_CLIENT', 'phpredis'),
        'options' => [
            'cluster' => env('REDIS_CLUSTER', 'redis'),
            'prefix' => env('REDIS_PREFIX', Str::slug(env('APP_NAME', 'pawcare'), '_').'_database_'),
        ],
    ],
];
