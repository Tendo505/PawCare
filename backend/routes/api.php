<?php

use App\Http\Controllers\AiPredictionController;
use App\Http\Controllers\AppointmentController;
use App\Http\Controllers\AuthController;
use App\Http\Controllers\DashboardController;
use App\Http\Controllers\MedicalRecordController;
use App\Http\Controllers\PetController;
use App\Http\Controllers\SystemHealthController;
use App\Http\Controllers\VaccinationController;
use Illuminate\Support\Facades\Route;

//every address the android app calls, all starting with /api
//"throttle:10,1" means at most 10 requests per minute from one device

//read service availability without exposing owner records
Route::get('/health', SystemHealthController::class)->middleware('throttle:30,1');

//authenticate accounts and limit repeated login attempts
Route::middleware('throttle:10,1')->group(function ()
{
    Route::post('/register', [AuthController::class, 'register']);
    Route::post('/login', [AuthController::class, 'login']);
});

//protect the planned owner modules with Sanctum
Route::middleware(['auth:sanctum', 'throttle:120,1'])->group(function ()
{
    //account session and dashboard
    Route::get('/me', [AuthController::class, 'me']);
    Route::post('/logout', [AuthController::class, 'logout']);
    Route::get('/dashboard', DashboardController::class);

    //pet profiles, health records and appointments
    Route::apiResource('pets', PetController::class);
    Route::apiResource('vaccinations', VaccinationController::class);
    Route::apiResource('medical-records', MedicalRecordController::class);
    Route::apiResource('appointments', AppointmentController::class);
    //breed recognition and saved results
    Route::get('/ai-predictions', [AiPredictionController::class, 'index']);
    Route::post('/ai-predictions', [AiPredictionController::class, 'store']);
});

//return JSON for an unknown API address
Route::fallback(function ()
{
    return response()->json(['message' => 'API endpoint not found.'], 404);
});
