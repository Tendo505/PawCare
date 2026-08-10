<?php

namespace App\Http\Controllers;

use App\Models\MedicalRecord;
use App\Models\VaccinationRecord;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class DashboardController extends Controller
{
    public function __invoke(Request $request): JsonResponse
    {
        $user = $request->user();
        $petIds = $user->pets()->pluck('id');
        return response()->json([
            'pet_count' => $petIds->count(),
            'upcoming_vaccinations' => VaccinationRecord::whereIn('pet_id', $petIds)->where('status', '!=', 'Completed')->count(),
            'scheduled_appointments' => $user->appointments()->where('status', 'Scheduled')->count(),
            'medical_record_count' => MedicalRecord::whereIn('pet_id', $petIds)->count(),
            'next_appointments' => $user->appointments()->with('pet:id,name')->where('status', 'Scheduled')->whereDate('appointment_date', '>=', today())->orderBy('appointment_date')->limit(5)->get(),
            'vaccinations_due' => VaccinationRecord::with('pet:id,name')->whereIn('pet_id', $petIds)->where('status', '!=', 'Completed')->orderBy('due_date')->limit(5)->get(),
        ]);
    }
}
