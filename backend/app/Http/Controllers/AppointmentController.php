<?php

namespace App\Http\Controllers;

use App\Models\Appointment;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Validation\ValidationException;

class AppointmentController extends Controller
{
    //read the owner's appointments with optional status and date filters
    public function index(Request $request): JsonResponse
    {
        $filters = $request->validate([
            'status' => ['nullable', 'in:Scheduled,Completed,Cancelled'],
            'upcoming' => ['nullable', 'in:1,0,true,false'],
        ]);

        $appointments = $request->user()
            ->appointments()
            ->with('pet:id,name,species')
            ->when(
                isset($filters['status']),
                fn ($query) => $query->where('status', $filters['status'])
            )
            ->when(
                $request->boolean('upcoming'),
                fn ($query) => $query
                    ->where('status', 'Scheduled')
                    ->whereDate('appointment_date', '>=', today())
            )
            ->orderBy('appointment_date')
            ->orderBy('appointment_time')
            ->get();

        return response()->json($appointments);
    }

    //create an appointment when the pet's schedule is available
    public function store(Request $request): JsonResponse
    {
        $data = $this->validated($request);

        //only allow the owner's pets
        $this->ownedPet($request, (int) $data['pet_id']);
        $this->ensureScheduleIsAvailable($request, $data);

        $data['user_id'] = $request->user()->id;
        $appointment = Appointment::create($data);

        return response()->json($appointment->load('pet:id,name,species'), 201);
    }

    //read one appointment and its pet details
    public function show(Request $request, Appointment $appointment): JsonResponse
    {
        $this->owned($request, $appointment);

        return response()->json($appointment->load('pet:id,name,species'));
    }

    //update an appointment after checking ownership and schedule
    public function update(Request $request, Appointment $appointment): JsonResponse
    {
        $this->owned($request, $appointment);
        $data = $this->validated($request);
        $this->ownedPet($request, (int) $data['pet_id']);
        $this->ensureScheduleIsAvailable($request, $data, $appointment);

        $appointment->update($data);

        return response()->json($appointment->fresh()->load('pet:id,name,species'));
    }

    //delete the owner's appointment
    public function destroy(Request $request, Appointment $appointment): JsonResponse
    {
        $this->owned($request, $appointment);
        $appointment->delete();

        return response()->json(null, 204);
    }

    //validate appointment fields and reject past scheduled dates
    private function validated(Request $request): array
    {
        $data = $request->validate([
            'pet_id' => ['required', 'integer', 'exists:pets,id'],
            'veterinarian_id' => ['nullable', 'exists:veterinarians,id'],
            'appointment_date' => ['required', 'date'],
            'appointment_time' => ['required', 'date_format:H:i'],
            'clinic' => ['nullable', 'string', 'max:150'],
            'reason' => ['required', 'string', 'max:500'],
            'status' => ['required', 'in:Scheduled,Completed,Cancelled'],
            'notes' => ['nullable', 'string', 'max:2000'],
        ]);

        //compare the same calendar date even when input formats differ
        $data['appointment_date'] = Carbon::parse($data['appointment_date'])->toDateString();

        if (
            $data['status'] === 'Scheduled'
            && today()->isAfter($data['appointment_date'])
        )
        {
            throw ValidationException::withMessages([
                'appointment_date' => 'A scheduled appointment cannot be in the past.',
            ]);
        }

        return $data;
    }

    //avoid overlapping visits for the same pet
    private function ensureScheduleIsAvailable(
        Request $request,
        array $data,
        ?Appointment $current = null
    ): void
    {
        if ($data['status'] !== 'Scheduled')
        {
            return;
        }

        $scheduledVisits = $request->user()
            ->appointments()
            ->where('pet_id', $data['pet_id'])
            ->where('status', 'Scheduled')
            ->when($current, fn ($query) => $query->where('id', '!=', $current->id))
            ->get(['id', 'appointment_date', 'appointment_time']);

        //normalize time because sqlite and postgresql return different formats
        $duplicateExists = $scheduledVisits->contains(
            fn (Appointment $appointment) =>
                $appointment->appointment_date->format('Y-m-d') === $data['appointment_date']
                && substr((string) $appointment->appointment_time, 0, 5) === substr($data['appointment_time'], 0, 5)
        );

        if ($duplicateExists)
        {
            throw ValidationException::withMessages([
                'appointment_time' => 'This pet already has an appointment at that date and time.',
            ]);
        }
    }

    //hide visits owned by another account
    private function owned(Request $request, Appointment $appointment): void
    {
        abort_unless($appointment->user_id === $request->user()->id, 404);
    }
}
