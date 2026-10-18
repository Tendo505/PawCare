<?php

namespace App\Http\Controllers;

use App\Models\Pet;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class PetController extends Controller
{
    //1.read the owner's pets in name order
    public function index(Request $request): JsonResponse
    {
        return response()->json(
            $request->user()
                ->pets()
                ->with('petBreed')
                ->orderBy('name')
                ->get()
        );
    }

    //2.create a pet linked to the current account
    public function store(Request $request): JsonResponse
    {
        $pet = $request->user()->pets()->create($this->validated($request));

        return response()->json($pet, 201);
    }

    //3.read a pet and its related records
    public function show(Request $request, Pet $pet): JsonResponse
    {
        $this->owned($request, $pet);

        return response()->json($pet->load([
            'vaccinationRecords',
            'medicalRecords',
            'appointments',
            'images',
            'predictions',
        ]));
    }

    //4.update the selected pet's profile
    public function update(Request $request, Pet $pet): JsonResponse
    {
        $this->owned($request, $pet);
        $pet->update($this->validated($request));

        return response()->json($pet->fresh());
    }

    //5.delete the owner's selected pet
    public function destroy(Request $request, Pet $pet): JsonResponse
    {
        $this->owned($request, $pet);
        $pet->delete();

        return response()->json(null, 204);
    }

    //6.check ownership
    private function owned(Request $request, Pet $pet): void
    {
        abort_unless($pet->user_id === $request->user()->id, 404);
    }

    //7.validate pet details
    private function validated(Request $request): array
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
}
