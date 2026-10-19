<?php

namespace App\Http\Controllers;

use App\Models\Pet;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

//pet profiles: GET, POST, PUT and DELETE /api/pets
class PetController extends Controller
{
    //GET /api/pets - the owner's pets in name order
    public function index(Request $request): JsonResponse
    {
        //3.process: read only this owner's pets, with their breed details
        $pets = $request->user()
            ->pets()
            ->with('petBreed')
            ->orderBy('name')
            ->get();

        //4.output
        return response()->json($pets);
    }

    //POST /api/pets - create a pet for the signed-in owner
    public function store(Request $request): JsonResponse
    {
        //2.input
        $data = $this->validatePet($request);

        //3.process: creating it through the owner's pets() fills in user_id
        $pet = $request->user()->pets()->create($data);

        //4.output: 201 means "created"
        return response()->json($pet, 201);
    }

    //GET /api/pets/{id} - one pet with all of its records
    public function show(Request $request, Pet $pet): JsonResponse
    {
        //3.process
        $this->checkOwner($request, $pet);
        $pet->load([
            'vaccinationRecords',
            'medicalRecords',
            'appointments',
            'images',
            'predictions',
        ]);

        //4.output
        return response()->json($pet);
    }

    //PUT /api/pets/{id} - update a pet's profile
    public function update(Request $request, Pet $pet): JsonResponse
    {
        //3.process: check the owner before reading the new details
        $this->checkOwner($request, $pet);

        //2.input
        $data = $this->validatePet($request);

        //3.process
        $pet->update($data);

        //4.output: read the saved row again so the answer matches the database
        return response()->json($pet->fresh());
    }

    //DELETE /api/pets/{id} - delete a pet; the database also deletes its health records
    public function destroy(Request $request, Pet $pet): JsonResponse
    {
        //3.process
        $this->checkOwner($request, $pet);
        $pet->delete();

        //4.output: 204 means "done, nothing to send back"
        return response()->json(null, 204);
    }

    //2.input

    //the rules for a pet form; laravel answers 422 with a message per field when a rule fails
    private function validatePet(Request $request): array
    {
        return $request->validate([
            'pet_breed_id' => ['nullable', 'exists:pet_breeds,id'],
            'name' => ['required', 'string', 'max:100'],
            'species' => ['required', 'in:Dog,Cat'],
            'breed' => ['nullable', 'string', 'max:100'],
            'sex' => ['required', 'in:Male,Female,Unknown'],
            'birth_date' => ['nullable', 'date', 'before_or_equal:today'],
            'weight_kg' => ['nullable', 'numeric', 'min:0', 'max:300'],
            'microchip_number' => ['nullable', 'string', 'max:100'],
            'notes' => ['nullable', 'string', 'max:2000'],
        ]);
    }

    //3.process

    //stop with 404 when the pet belongs to another owner
    private function checkOwner(Request $request, Pet $pet): void
    {
        if ($pet->user_id !== $request->user()->id)
        {
            abort(404);
        }
    }
}
