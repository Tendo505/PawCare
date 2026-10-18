<?php

namespace App\Services;

use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class DemoPrediction
{
    //return an unsaved example, never a claim about the uploaded image
    public function respond(Request $request, ?int $petId): JsonResponse
    {
        $pet = $petId === null ? null : $request->user()->pets()->findOrFail($petId);
        $now = now()->toISOString();
        $message = 'DEMO ONLY: AI is unavailable. This is not an image prediction and has not been saved.';

        return response()->json([
            'id' => 0,
            'user_id' => $request->user()->id,
            'pet_id' => $pet?->id,
            'species' => $pet === null ? 'cat' : strtolower($pet->species),
            'breed' => 'DEMO ONLY - no prediction',
            'confidence' => 0.0,
            'top_predictions' => [
                ['breed' => 'DEMO ONLY - no prediction', 'confidence' => 0.0],
            ],
            'model_version' => 'DEMO-fallback-not-a-model',
            'created_at' => $now,
            'updated_at' => $now,
            'image_url' => null,
            'disclaimer' => $message,
            'pet' => $pet === null ? null : ['id' => $pet->id, 'name' => $pet->name],
            'demo' => true,
            'message' => $message,
        ], 201);
    }

    //only a missing model allows fallback after an upstream response
    public function isModelMissing(int $status, mixed $detail): bool
    {
        if ($status !== 503 || ! is_string($detail))
        {
            return false;
        }

        return preg_match('/model_missing|no trained model|model (file )?(is )?missing|model (file )?not found/i', $detail) === 1;
    }
}
