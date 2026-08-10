<?php

namespace App\Http\Controllers;

use App\Models\MedicalRecord;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class MedicalRecordController extends Controller
{
    public function index(Request $request): JsonResponse { return response()->json(MedicalRecord::whereHas('pet', fn ($q) => $q->where('user_id', $request->user()->id))->with('pet:id,name,species')->orderByDesc('visit_date')->get()); }
    public function store(Request $request): JsonResponse { $data = $this->validated($request); $this->ownedPet($request, (int) $data['pet_id']); return response()->json(MedicalRecord::create($data)->load('pet:id,name'), 201); }
    public function show(Request $request, MedicalRecord $medicalRecord): JsonResponse { $this->owned($request, $medicalRecord); return response()->json($medicalRecord->load('pet:id,name')); }
    public function update(Request $request, MedicalRecord $medicalRecord): JsonResponse { $this->owned($request, $medicalRecord); $data = $this->validated($request); $this->ownedPet($request, (int) $data['pet_id']); $medicalRecord->update($data); return response()->json($medicalRecord->fresh()->load('pet:id,name')); }
    public function destroy(Request $request, MedicalRecord $medicalRecord): JsonResponse { $this->owned($request, $medicalRecord); $medicalRecord->delete(); return response()->json(null, 204); }

    private function validated(Request $request): array { return $request->validate([
        'pet_id' => ['required', 'integer', 'exists:pets,id'], 'veterinarian_id' => ['nullable', 'exists:veterinarians,id'],
        'visit_date' => ['required', 'date', 'before_or_equal:today'], 'veterinarian' => ['nullable', 'string', 'max:150'],
        'diagnosis' => ['required', 'string', 'max:500'], 'treatment' => ['nullable', 'string', 'max:2000'], 'notes' => ['nullable', 'string', 'max:2000'],
    ]); }
    private function ownedPet(Request $request, int $id): void { abort_unless($request->user()->pets()->whereKey($id)->exists(), 404); }
    private function owned(Request $request, MedicalRecord $item): void { abort_unless($item->pet()->where('user_id', $request->user()->id)->exists(), 404); }
}
