<?php

namespace App\Http\Controllers;

use App\Models\AiPrediction;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;

class AiPredictionController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        return response()->json($request->user()->predictions()->with('pet:id,name')->latest()->paginate(20));
    }

    public function store(Request $request): JsonResponse
    {
        $data = $request->validate(['image' => ['required', 'image', 'max:10240'], 'pet_id' => ['nullable', 'integer', 'exists:pets,id']]);
        if (! empty($data['pet_id'])) abort_unless($request->user()->pets()->whereKey($data['pet_id'])->exists(), 404);
        $image = $request->file('image');
        $response = Http::timeout(45)
            ->attach('image', file_get_contents($image->getRealPath()), $image->getClientOriginalName())
            ->post(rtrim(env('AI_SERVICE_URL', 'http://127.0.0.1:8001'), '/').'/predict');
        if ($response->failed()) return response()->json(['message' => 'Breed recognition service unavailable.', 'service_error' => $response->json()], 503);

        $result = $response->json();
        $path = $image->store('pet-predictions', 'public');
        $prediction = AiPrediction::create([
            'user_id' => $request->user()->id, 'pet_id' => $data['pet_id'] ?? null,
            'species' => $result['species'], 'breed' => $result['breed'], 'confidence' => $result['confidence'],
            'top_predictions' => $result['top_predictions'] ?? [], 'image_path' => $path, 'model_version' => $result['model_version'] ?? 'unknown',
        ]);
        return response()->json($prediction, 201);
    }
}
