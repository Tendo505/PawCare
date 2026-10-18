<?php

return [
    // FastAPI address and request time limits
    'ai' => [
        'url' => env('AI_SERVICE_URL', 'http://127.0.0.1:8001'),
        'connect_timeout' => (int) env('AI_CONNECT_TIMEOUT', 2),
        'timeout' => (int) env('AI_REQUEST_TIMEOUT', 15),
        'demo_fallback' => (bool) env('AI_DEMO_FALLBACK', false),
    ],
];
