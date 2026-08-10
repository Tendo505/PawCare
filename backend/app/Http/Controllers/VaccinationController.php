<?php

namespace App\Http\Controllers;

use App\Models\VaccinationRecord;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class VaccinationController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        return response()->json(VaccinationRecord::whereHas('pet', fn ($q) => $q->where('user_id', $request->user()->id))->with('pet:id,name,species')->orderBy('due_date')->get());
    }
    public function store(Request $request): JsonResponse
    {
        $data = $this->validated($request);
        $this->ownedPet($request, (int) $data['pet_id']);
        return response()->json(VaccinationRecord::create($data)->load('pet:id,name'), 201);
    }
    public function show(Request $request, VaccinationRecord $vaccination): JsonResponse { $this->ownedRecord($request, $vaccination); return response()->json($vaccination->load('pet:id,name')); }
    public function update(Request $request, VaccinationRecord $vaccination): JsonResponse
    {
        $this->ownedRecord($request, $vaccination);
        $data = $this->validated($request);
        $this->ownedPet($request, (int) $data['pet_id']);
        $vaccination->update($data);
        return response()->json($vaccination->fresh()->load('pet:id,name'));
    }
    public function destroy(Request $request, VaccinationRecord $vaccination): JsonResponse { $this->ownedRecord($request, $vaccination); $vaccination->delete(); return response()->json(null, 204); }

    private function validated(Request $request): array { return $request->validate([
        'pet_id' => ['required', 'integer', 'exists:pets,id'], 'vaccination_id' => ['nullable', 'exists:vaccinations,id'],
        'vaccine_name' => ['required', 'string', 'max:150'], 'administered_date' => ['nullable', 'date'],
        'due_date' => ['required', 'date'], 'clinic' => ['nullable', 'string', 'max:150'],
        'status' => ['required', 'in:Upcoming,Completed,Overdue'], 'notes' => ['nullable', 'string', 'max:2000'],
    ]); }
    private function ownedPet(Request $request, int $id): void { abort_unless($request->user()->pets()->whereKey($id)->exists(), 404); }
    private function ownedRecord(Request $request, VaccinationRecord $item): void { abort_unless($item->pet()->where('user_id', $request->user()->id)->exists(), 404); }
}
