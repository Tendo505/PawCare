<?php

use Illuminate\Support\Facades\Route;

//opening http://pawcare-api.test in a browser shows the backend name and status
Route::get('/', function ()
{
    return response()->json([
        'name' => 'PawCare API',
        'version' => '1.0.0',
        'status' => 'ok',
    ]);
});
