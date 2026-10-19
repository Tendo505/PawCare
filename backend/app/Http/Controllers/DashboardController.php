<?php

namespace App\Http\Controllers;

use App\Models\MedicalRecord;
use App\Models\VaccinationRecord;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

//GET /api/dashboard - totals and the next care reminders for the signed-in owner
class DashboardController extends Controller
{
    //a controller with one action uses __invoke
    public function __invoke(Request $request): JsonResponse
    {
        //2.input: the owner and the ids of their pets
        $user = $request->user();
        $petIds = $user->pets()->pluck('id');

        //3.process: count the records
        $upcomingVaccinations = VaccinationRecord::whereIn('pet_id', $petIds)
            ->where('status', '!=', 'Completed')
            ->count();
        $scheduledAppointments = $user->appointments()
            ->where('status', 'Scheduled')
            ->count();
        $medicalRecordCount = MedicalRecord::whereIn('pet_id', $petIds)->count();

        //3.process: the next five scheduled visits from today, and the next five vaccinations still due
        $nextAppointments = $user->appointments()
            ->with('pet:id,name')
            ->where('status', 'Scheduled')
            ->whereDate('appointment_date', '>=', today())
            ->orderBy('appointment_date')
            ->orderBy('appointment_time')
            ->limit(5)
            ->get();
        $vaccinationsDue = VaccinationRecord::with('pet:id,name')
            ->whereIn('pet_id', $petIds)
            ->where('status', '!=', 'Completed')
            ->orderBy('due_date')
            ->limit(5)
            ->get();

        //4.output
        return response()->json([
            'pet_count' => $petIds->count(),
            'upcoming_vaccinations' => $upcomingVaccinations,
            'scheduled_appointments' => $scheduledAppointments,
            'medical_record_count' => $medicalRecordCount,
            'next_appointments' => $nextAppointments,
            'vaccinations_due' => $vaccinationsDue,
        ]);
    }
}
