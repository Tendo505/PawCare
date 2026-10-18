<?php

use Illuminate\Http\Request;

// Start the request timer and load dependencies
define('LARAVEL_START', microtime(true));
require __DIR__.'/../vendor/autoload.php';

// Send the incoming HTTP request through Laravel
(require_once __DIR__.'/../bootstrap/app.php')
    ->handleRequest(Request::capture());
