<?php

use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;

// Create the application and connect its routes
return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
        health: '/up',
    )
    // Enable Sanctum's stateful API middleware
    ->withMiddleware(function (Middleware $middleware): void
    {
        $middleware->statefulApi();
    })
    ->withExceptions(function (Exceptions $exceptions): void
    {
        // Laravel automatically renders validation and authentication errors as JSON for API requests.
    })
    ->create();
