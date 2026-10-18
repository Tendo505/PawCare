<?php

use Illuminate\Support\Facades\Route;

// Read the backend name and status
Route::get('/', fn () => response()->json([
    'name' => 'PawCare API',
    'version' => '1.0.0',
    'status' => 'ok',
]));
