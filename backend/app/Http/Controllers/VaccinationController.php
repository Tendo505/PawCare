<?php

namespace App\Http\Controllers;

use App\Models\VaccinationRecord;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

//vaccination records: GET, POST, PUT and DELETE /api/vaccinations
class VaccinationController extends Controller
{
    //GET /api/vaccinations - vaccinations for the owner's pets, nearest due date first
    public function index(Request $request): JsonResponse
    {
        //3.process: a vaccination has no user_id, so keep only records whose pet belongs to this owner
        $ownerId = $request->user()->id;
        $records = VaccinationRecord::whereHas('pet', function ($petQuery) use ($ownerId)
        {
            $petQuery->where('user_id', $ownerId);
        })
            ->with('pet:id,name,species')
            ->orderBy('due_date')
            ->get();

        //4.output
        return response()->json($records);
    }

    //POST /api/vaccinations - add a vaccination to one of the owner's pets
    public function store(Request $request): JsonResponse
    {
        //2.input
        $data = $this->validateVaccination($request);

        //3.process
        $this->ownedPet($request, (int) $data['pet_id']);
        $record = VaccinationRecord::create($data);
        $record->load('pet:id,name');

        //4.output
        return response()->json($record, 201);
    }

    //GET /api/vaccinations/{id} - one vaccination record
    public function show(Request $request, VaccinationRecord $vaccination): JsonResponse
    {
        //3.process
        $this->checkOwner($request, $vaccination);
        $vaccination->load('pet:id,name');

        //4.output
        return response()->json($vaccination);
    }

    //PUT /api/vaccinations/{id} - update a vaccination record
    public function update(Request $request, VaccinationRecord $vaccination): JsonResponse
    {
        //3.process: check the owner before reading the new details
        $this->checkOwner($request, $vaccination);

        //2.input
        $data = $this->validateVaccination($request);

        //3.process: the record may also be moved to another pet of the same owner
        $this->ownedPet($request, (int) $data['pet_id']);
        $vaccination->update($data);

        //4.output
        return response()->json($vaccination->fresh()->load('pet:id,name'));
    }

    //DELETE /api/vaccinations/{id} - delete a vaccination record
    public function destroy(Request $request, VaccinationRecord $vaccination): JsonResponse
    {
        //3.process
        $this->checkOwner($request, $vaccination);
        $vaccination->delete();

        //4.output
        return response()->json(null, 204);
    }

    //2.input

    //the rules for a vaccination form; the due date cannot be before the date it was given
    private function validateVaccination(Request $request): array
    {
        return $request->validate([
            'pet_id' => ['required', 'integer', 'exists:pets,id'],
            'vaccination_id' => ['nullable', 'exists:vaccinations,id'],
            'vaccine_name' => ['required', 'string', 'max:150'],
            'administered_date' => ['nullable', 'date', 'before_or_equal:today'],
            'due_date' => ['required', 'date', 'after_or_equal:administered_date'],
            'clinic' => ['nullable', 'string', 'max:150'],
            'status' => ['required', 'in:Upcoming,Completed,Overdue'],
            'notes' => ['nullable', 'string', 'max:2000'],
        ]);
    }

    //3.process

    //stop with 404 when the record's pet belongs to another owner
    private function checkOwner(Request $request, VaccinationRecord $record): void
    {
        $ownsPet = $record->pet()->where('user_id', $request->user()->id)->exists();
        if (! $ownsPet)
        {
            abort(404);
        }
    }
}
