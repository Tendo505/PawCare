<?php

namespace App\Http\Controllers;

use App\Models\Appointment;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Validation\ValidationException;

//clinic appointments: GET, POST, PUT and DELETE /api/appointments
class AppointmentController extends Controller
{
    //GET /api/appointments - the owner's appointments by date and time
    //optional filters: ?status=Scheduled and ?upcoming=1 (scheduled visits from today onwards)
    public function index(Request $request): JsonResponse
    {
        //2.input
        $filters = $request->validate([
            'status' => ['nullable', 'in:Scheduled,Completed,Cancelled'],
            'upcoming' => ['nullable', 'in:1,0,true,false'],
        ]);

        //3.process: build the query step by step, adding a filter only when it was asked for
        $query = $request->user()->appointments()->with('pet:id,name,species');
        if (isset($filters['status']))
        {
            $query->where('status', $filters['status']);
        }
        if ($request->boolean('upcoming'))
        {
            $query->where('status', 'Scheduled');
            $query->whereDate('appointment_date', '>=', today());
        }
        $appointments = $query->orderBy('appointment_date')->orderBy('appointment_time')->get();

        //4.output
        return response()->json($appointments);
    }

    //POST /api/appointments - book a visit for one of the owner's pets
    public function store(Request $request): JsonResponse
    {
        //2.input
        $data = $this->validateAppointment($request);

        //3.process: the pet must be the owner's, and it cannot have two visits at the same time
        $this->ownedPet($request, (int) $data['pet_id']);
        $this->checkTimeIsFree($request, $data, null);
        $data['user_id'] = $request->user()->id;
        $appointment = Appointment::create($data);
        $appointment->load('pet:id,name,species');

        //4.output
        return response()->json($appointment, 201);
    }

    //GET /api/appointments/{id} - one appointment
    public function show(Request $request, Appointment $appointment): JsonResponse
    {
        //3.process
        $this->checkOwner($request, $appointment);
        $appointment->load('pet:id,name,species');

        //4.output
        return response()->json($appointment);
    }

    //PUT /api/appointments/{id} - update an appointment
    public function update(Request $request, Appointment $appointment): JsonResponse
    {
        //3.process: check the owner before reading the new details
        $this->checkOwner($request, $appointment);

        //2.input
        $data = $this->validateAppointment($request);

        //3.process: the visit being edited does not count as a clash with itself
        $this->ownedPet($request, (int) $data['pet_id']);
        $this->checkTimeIsFree($request, $data, $appointment);
        $appointment->update($data);

        //4.output
        return response()->json($appointment->fresh()->load('pet:id,name,species'));
    }

    //DELETE /api/appointments/{id} - delete an appointment
    public function destroy(Request $request, Appointment $appointment): JsonResponse
    {
        //3.process
        $this->checkOwner($request, $appointment);
        $appointment->delete();

        //4.output
        return response()->json(null, 204);
    }

    //2.input

    //the rules for an appointment form; a scheduled visit cannot be in the past
    private function validateAppointment(Request $request): array
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

        //store the date as YYYY-MM-DD so every comparison uses the same format
        $data['appointment_date'] = Carbon::parse($data['appointment_date'])->toDateString();

        $isScheduled = $data['status'] === 'Scheduled';
        $isInThePast = today()->isAfter($data['appointment_date']);
        if ($isScheduled && $isInThePast)
        {
            throw ValidationException::withMessages([
                'appointment_date' => 'A scheduled appointment cannot be in the past.',
            ]);
        }

        return $data;
    }

    //3.process

    //stop when the same pet already has a scheduled visit at this date and time
    //$current is the appointment being edited, or null when booking a new one
    private function checkTimeIsFree(Request $request, array $data, ?Appointment $current): void
    {
        //only scheduled visits can clash
        if ($data['status'] !== 'Scheduled')
        {
            return;
        }

        $query = $request->user()
            ->appointments()
            ->where('pet_id', $data['pet_id'])
            ->where('status', 'Scheduled');
        if ($current !== null)
        {
            $query->where('id', '!=', $current->id);
        }
        $scheduledVisits = $query->get(['id', 'appointment_date', 'appointment_time']);

        foreach ($scheduledVisits as $visit)
        {
            //compare HH:MM only, because sqlite and postgresql return the time in different formats
            $sameDate = $visit->appointment_date->format('Y-m-d') === $data['appointment_date'];
            $sameTime = substr((string) $visit->appointment_time, 0, 5) === substr($data['appointment_time'], 0, 5);
            if ($sameDate && $sameTime)
            {
                throw ValidationException::withMessages([
                    'appointment_time' => 'This pet already has an appointment at that date and time.',
                ]);
            }
        }
    }

    //stop with 404 when the appointment belongs to another owner
    private function checkOwner(Request $request, Appointment $appointment): void
    {
        if ($appointment->user_id !== $request->user()->id)
        {
            abort(404);
        }
    }
}
