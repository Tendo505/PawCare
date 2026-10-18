<?php

namespace App\Http\Controllers;

use App\Models\AiPrediction;
use App\Services\DemoPrediction;
use Illuminate\Http\Client\ConnectionException;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Facades\Validator;
use Illuminate\Validation\Rule;
use Throwable;

class AiPredictionController extends Controller
{
    //read the owner's saved prediction history
    public function index(Request $request): JsonResponse
    {
        return response()->json(
            $request->user()
                ->predictions()
                ->with('pet:id,name')
                ->latest()
                ->paginate(20)
        );
    }

    //create a prediction after checking the image and selected pet
    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'image' => [
                'required',
                'file',
                'mimes:jpg,jpeg,png,webp',
                'mimetypes:image/jpeg,image/png,image/webp',
                'max:10240',
            ],
            'pet_id' => [
                'nullable',
                'integer',
                Rule::exists('pets', 'id')->where(
                    fn ($query) => $query->where('user_id', $request->user()->id)
                ),
            ],
        ]);

        $image = $request->file('image');
        $serviceUrl = rtrim((string) config('services.ai.url'), '/').'/predict';

        //send images through laravel instead of calling fastapi from android
        try
        {
            $response = Http::acceptJson()
                ->connectTimeout((int) config('services.ai.connect_timeout', 2))
                ->timeout((int) config('services.ai.timeout', 15))
                ->attach(
                    'image',
                    file_get_contents($image->getRealPath()),
                    $image->getClientOriginalName(),
                    ['Content-Type' => $image->getMimeType()]
                )
                ->post($serviceUrl);
        }
        catch (ConnectionException)
        {
            if (config('services.ai.demo_fallback'))
            {
                return app(DemoPrediction::class)->respond($request, $data['pet_id'] ?? null);
            }

            return response()->json([
                'message' => 'The AI service is not running. Start FastAPI on port 8001 and try again.',
            ], 503);
        }

        //keep fastapi's validation message when available
        if (! $response->successful())
        {
            $serviceMessage = $response->json('detail');
            $demo = app(DemoPrediction::class);
            if (config('services.ai.demo_fallback') && $demo->isModelMissing($response->status(), $serviceMessage))
            {
                return $demo->respond($request, $data['pet_id'] ?? null);
            }

            $status = match ($response->status())
            {
                413, 415, 422, 503 => $response->status(),
                default => 502,
            };

            return response()->json([
                'message' => is_string($serviceMessage)
                    ? $serviceMessage
                    : 'The AI service could not process this image.',
                'ai_service_status' => $response->status(),
            ], $status);
        }

        //reject incomplete responses from fastapi
        $result = $response->json();
        $predictionValidator = Validator::make(is_array($result) ? $result : [], [
            'species' => ['required', 'string', Rule::in(['cat', 'dog'])],
            'breed' => ['required', 'string', 'max:150'],
            'confidence' => ['required', 'numeric', 'between:0,1'],
            'top_predictions' => ['required', 'array', 'min:1', 'max:5'],
            'top_predictions.*.breed' => ['required', 'string', 'max:150'],
            'top_predictions.*.confidence' => ['required', 'numeric', 'between:0,1'],
            'model_version' => ['required', 'string', 'max:100'],
        ]);
        if ($predictionValidator->fails())
        {
            return response()->json([
                'message' => 'The AI service returned an invalid response.',
            ], 502);
        }
        $predictionData = $predictionValidator->validated();

        //save the image only after prediction succeeds
        $path = $image->store('pet-predictions/'.$request->user()->id, 'public');
        if (! $path)
        {
            return response()->json(['message' => 'The prediction image could not be stored.'], 500);
        }

        try
        {
            $prediction = DB::transaction(fn () => AiPrediction::create([
                'user_id' => $request->user()->id,
                'pet_id' => $data['pet_id'] ?? null,
                'species' => $predictionData['species'],
                'breed' => $predictionData['breed'],
                'confidence' => $predictionData['confidence'],
                'top_predictions' => $predictionData['top_predictions'],
                'image_path' => $path,
                'model_version' => $predictionData['model_version'],
            ]));
        }
        catch (Throwable $error)
        {
            Storage::disk('public')->delete($path);
            report($error);

            return response()->json(['message' => 'The prediction could not be saved.'], 500);
        }

        return response()->json($prediction->load('pet:id,name'), 201);
    }
}
