<?php

use Illuminate\Support\Facades\Route;

Route::get('/', fn () => response()->json([
    'name' => 'PawCare API',
    'version' => '1.0.0',
    'status' => 'ok',
]));
