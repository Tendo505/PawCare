<?php

namespace App\Http\Controllers;

use App\Models\VaccinationRecord;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class VaccinationController extends Controller
{
    //read vaccinations for the owner's pets in due-date order
    public function index(Request $request): JsonResponse
    {
        return response()->json(
            VaccinationRecord::whereHas('pet', fn ($q) => $q->where('user_id', $request->user()->id))
                ->with('pet:id,name,species')
                ->orderBy('due_date')
                ->get()
        );
    }

    //create a vaccination record for an owned pet
    public function store(Request $request): JsonResponse
    {
        $data = $this->validated($request);
        $this->ownedPet($request, (int) $data['pet_id']);

        return response()->json(VaccinationRecord::create($data)->load('pet:id,name'), 201);
    }

    //read one vaccination record and its pet details
    public function show(Request $request, VaccinationRecord $vaccination): JsonResponse
    {
        $this->ownedRecord($request, $vaccination);

        return response()->json($vaccination->load('pet:id,name'));
    }

    //update vaccination details after checking ownership and dates
    public function update(Request $request, VaccinationRecord $vaccination): JsonResponse
    {
        $this->ownedRecord($request, $vaccination);
        $data = $this->validated($request);
        $this->ownedPet($request, (int) $data['pet_id']);
        $vaccination->update($data);

        return response()->json($vaccination->fresh()->load('pet:id,name'));
    }

    //delete the selected vaccination record
    public function destroy(Request $request, VaccinationRecord $vaccination): JsonResponse
    {
        $this->ownedRecord($request, $vaccination);
        $vaccination->delete();

        return response()->json(null, 204);
    }

    //validate vaccine fields and the order of administered and due dates
    private function validated(Request $request): array
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

    //hide records owned by another account
    private function ownedRecord(Request $request, VaccinationRecord $item): void
    {
        abort_unless($item->pet()->where('user_id', $request->user()->id)->exists(), 404);
    }
}
