<?php

namespace App\Http\Controllers;

use App\Models\Appointment;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class AppointmentController extends Controller
{
    public function index(Request $request): JsonResponse { return response()->json($request->user()->appointments()->with('pet:id,name,species')->orderBy('appointment_date')->orderBy('appointment_time')->get()); }
    public function store(Request $request): JsonResponse { $data = $this->validated($request); $this->ownedPet($request, (int) $data['pet_id']); $data['user_id'] = $request->user()->id; return response()->json(Appointment::create($data)->load('pet:id,name'), 201); }
    public function show(Request $request, Appointment $appointment): JsonResponse { $this->owned($request, $appointment); return response()->json($appointment->load('pet:id,name')); }
    public function update(Request $request, Appointment $appointment): JsonResponse { $this->owned($request, $appointment); $data = $this->validated($request); $this->ownedPet($request, (int) $data['pet_id']); $appointment->update($data); return response()->json($appointment->fresh()->load('pet:id,name')); }
    public function destroy(Request $request, Appointment $appointment): JsonResponse { $this->owned($request, $appointment); $appointment->delete(); return response()->json(null, 204); }

    private function validated(Request $request): array { return $request->validate([
        'pet_id' => ['required', 'integer', 'exists:pets,id'], 'veterinarian_id' => ['nullable', 'exists:veterinarians,id'],
        'appointment_date' => ['required', 'date'], 'appointment_time' => ['required', 'date_format:H:i'],
        'clinic' => ['nullable', 'string', 'max:150'], 'reason' => ['required', 'string', 'max:500'],
        'status' => ['required', 'in:Scheduled,Completed,Cancelled'], 'notes' => ['nullable', 'string', 'max:2000'],
    ]); }
    private function ownedPet(Request $request, int $id): void { abort_unless($request->user()->pets()->whereKey($id)->exists(), 404); }
    private function owned(Request $request, Appointment $item): void { abort_unless($item->user_id === $request->user()->id, 404); }
}
