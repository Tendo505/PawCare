<?php

namespace App\Http\Controllers;

use App\Models\MedicalRecord;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

//medical visit records: GET, POST, PUT and DELETE /api/medical-records
class MedicalRecordController extends Controller
{
    //GET /api/medical-records - medical records for the owner's pets, newest visit first
    public function index(Request $request): JsonResponse
    {
        //3.process: a medical record has no user_id, so keep only records whose pet belongs to this owner
        $ownerId = $request->user()->id;
        $records = MedicalRecord::whereHas('pet', function ($petQuery) use ($ownerId)
        {
            $petQuery->where('user_id', $ownerId);
        })
            ->with('pet:id,name,species')
            ->orderByDesc('visit_date')
            ->get();

        //4.output
        return response()->json($records);
    }

    //POST /api/medical-records - add a visit record to one of the owner's pets
    public function store(Request $request): JsonResponse
    {
        //2.input
        $data = $this->validateMedicalRecord($request);

        //3.process
        $this->ownedPet($request, (int) $data['pet_id']);
        $record = MedicalRecord::create($data);
        $record->load('pet:id,name');

        //4.output
        return response()->json($record, 201);
    }

    //GET /api/medical-records/{id} - one medical record
    public function show(Request $request, MedicalRecord $medicalRecord): JsonResponse
    {
        //3.process
        $this->checkOwner($request, $medicalRecord);
        $medicalRecord->load('pet:id,name');

        //4.output
        return response()->json($medicalRecord);
    }

    //PUT /api/medical-records/{id} - update a medical record
    public function update(Request $request, MedicalRecord $medicalRecord): JsonResponse
    {
        //3.process: check the owner before reading the new details
        $this->checkOwner($request, $medicalRecord);

        //2.input
        $data = $this->validateMedicalRecord($request);

        //3.process: the record may also be moved to another pet of the same owner
        $this->ownedPet($request, (int) $data['pet_id']);
        $medicalRecord->update($data);

        //4.output
        return response()->json($medicalRecord->fresh()->load('pet:id,name'));
    }

    //DELETE /api/medical-records/{id} - delete a medical record
    public function destroy(Request $request, MedicalRecord $medicalRecord): JsonResponse
    {
        //3.process
        $this->checkOwner($request, $medicalRecord);
        $medicalRecord->delete();

        //4.output
        return response()->json(null, 204);
    }

    //2.input

    //the rules for a medical record form; a visit cannot be in the future
    private function validateMedicalRecord(Request $request): array
    {
        return $request->validate([
            'pet_id' => ['required', 'integer', 'exists:pets,id'],
            'veterinarian_id' => ['nullable', 'exists:veterinarians,id'],
            'visit_date' => ['required', 'date', 'before_or_equal:today'],
            'veterinarian' => ['nullable', 'string', 'max:150'],
            'diagnosis' => ['required', 'string', 'max:500'],
            'treatment' => ['nullable', 'string', 'max:2000'],
            'notes' => ['nullable', 'string', 'max:2000'],
        ]);
    }

    //3.process

    //stop with 404 when the record's pet belongs to another owner
    private function checkOwner(Request $request, MedicalRecord $record): void
    {
        $ownsPet = $record->pet()->where('user_id', $request->user()->id)->exists();
        if (! $ownsPet)
        {
            abort(404);
        }
    }
}
