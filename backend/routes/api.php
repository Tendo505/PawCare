<?php

use App\Http\Controllers\AiPredictionController;
use App\Http\Controllers\AppointmentController;
use App\Http\Controllers\AuthController;
use App\Http\Controllers\DashboardController;
use App\Http\Controllers\MedicalRecordController;
use App\Http\Controllers\PetController;
use App\Http\Controllers\VaccinationController;
use Illuminate\Support\Facades\Route;

Route::post('/register', [AuthController::class, 'register']);
Route::post('/login', [AuthController::class, 'login']);

Route::middleware('auth:sanctum')->group(function () {
    Route::get('/me', [AuthController::class, 'me']);
    Route::post('/logout', [AuthController::class, 'logout']);
    Route::get('/dashboard', DashboardController::class);

    Route::apiResource('pets', PetController::class);
    Route::apiResource('vaccinations', VaccinationController::class);
    Route::apiResource('medical-records', MedicalRecordController::class);
    Route::apiResource('appointments', AppointmentController::class);
    Route::get('/ai-predictions', [AiPredictionController::class, 'index']);
    Route::post('/ai-predictions', [AiPredictionController::class, 'store']);
});
