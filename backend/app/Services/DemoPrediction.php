<?php

namespace App\Services;

use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

//a clearly labelled "DEMO ONLY" answer used when AI_DEMO_FALLBACK is on and the ai service is down
//it is never a claim about the uploaded photo and it is never saved
class DemoPrediction
{
    //3.process

    //only a missing model may use the demo answer; any other fastapi error is shown to the user
    public function isModelMissing(int $status, mixed $detail): bool
    {
        if ($status !== 503 || ! is_string($detail))
        {
            return false;
        }

        $missingModelPattern = '/model_missing|no trained model|model (file )?(is )?missing|model (file )?not found/i';
        return preg_match($missingModelPattern, $detail) === 1;
    }

    //4.output

    //build the demo answer in the same json shape as a real saved prediction
    public function respond(Request $request, ?int $petId): JsonResponse
    {
        //findOrFail answers 404 when the pet is not one of the owner's pets
        $pet = null;
        if ($petId !== null)
        {
            $pet = $request->user()->pets()->findOrFail($petId);
        }

        $species = 'cat';
        $petIdOrNull = null;
        $petSummary = null;
        if ($pet !== null)
        {
            $species = strtolower($pet->species);
            $petIdOrNull = $pet->id;
            $petSummary = ['id' => $pet->id, 'name' => $pet->name];
        }

        $now = now()->toISOString();
        $message = 'DEMO ONLY: AI is unavailable. This is not an image prediction and has not been saved.';

        return response()->json([
            'id' => 0,
            'user_id' => $request->user()->id,
            'pet_id' => $petIdOrNull,
            'species' => $species,
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
            'pet' => $petSummary,
            'demo' => true,
            'message' => $message,
        ], 201);
    }
}
