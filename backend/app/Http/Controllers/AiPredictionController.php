<?php

namespace App\Http\Controllers;

use App\Models\AiPrediction;
use App\Services\DemoPrediction;
use Illuminate\Http\Client\ConnectionException;
use Illuminate\Http\Client\Response;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Facades\Validator;
use Illuminate\Validation\Rule;
use Throwable;

//breed recognition: the app sends a photo to laravel, laravel asks the fastapi ai service,
//then saves and returns the result (GET and POST /api/ai-predictions)
class AiPredictionController extends Controller
{
    //GET /api/ai-predictions - the owner's saved scans, newest first, 20 per page
    public function index(Request $request): JsonResponse
    {
        //3.process
        $predictions = $request->user()
            ->predictions()
            ->with('pet:id,name')
            ->latest()
            ->paginate(20);

        //4.output
        return response()->json($predictions);
    }

    //POST /api/ai-predictions - analyse a photo and save the result
    public function store(Request $request): JsonResponse
    {
        //2.input: a photo of at most 10 MB, and optionally one of the owner's pets (pet_id arrives as text, for example "12")
        $data = $this->validateUpload($request);
        $image = $request->file('image');
        $petId = null;
        if (isset($data['pet_id']))
        {
            $petId = $data['pet_id'];
        }

        //3.process: send the photo to fastapi (the app never talks to fastapi directly)
        try
        {
            $response = $this->sendToAiService($image);
        }
        catch (ConnectionException $error)
        {
            return $this->aiServiceOffline($request, $petId);
        }
        if (! $response->successful())
        {
            return $this->aiServiceError($request, $response, $petId);
        }

        //3.process: never save an incomplete answer from fastapi
        $result = $this->readAiResult($response);
        if ($result === null)
        {
            return response()->json(['message' => 'The AI service returned an invalid response.'], 502);
        }

        //3.process: keep the photo only after the prediction succeeded
        $path = $image->store('pet-predictions/'.$request->user()->id, 'public');
        if (! $path)
        {
            return response()->json(['message' => 'The prediction image could not be stored.'], 500);
        }
        try
        {
            $prediction = $this->savePrediction($request->user()->id, $petId, $result, $path);
        }
        catch (Throwable $error)
        {
            //the database save failed, so remove the photo again
            Storage::disk('public')->delete($path);
            report($error);
            return response()->json(['message' => 'The prediction could not be saved.'], 500);
        }

        //4.output
        return response()->json($prediction->load('pet:id,name'), 201);
    }

    //2.input

    //the photo must be jpg, png or webp up to 10 MB; pet_id must be one of the owner's pets
    private function validateUpload(Request $request): array
    {
        $ownerId = $request->user()->id;
        $ownersPet = Rule::exists('pets', 'id')->where(function ($petQuery) use ($ownerId)
        {
            $petQuery->where('user_id', $ownerId);
        });

        return $request->validate([
            'image' => ['required', 'file', 'mimes:jpg,jpeg,png,webp', 'mimetypes:image/jpeg,image/png,image/webp', 'max:10240'],
            'pet_id' => ['nullable', 'integer', $ownersPet],
        ]);
    }

    //3.process

    //post the photo to fastapi's /predict, with the time limits from config/services.php
    private function sendToAiService(UploadedFile $image): Response
    {
        $serviceUrl = rtrim((string) config('services.ai.url'), '/').'/predict';
        $photoBytes = file_get_contents($image->getRealPath());

        return Http::acceptJson()
            ->connectTimeout((int) config('services.ai.connect_timeout', 2))
            ->timeout((int) config('services.ai.timeout', 15))
            ->attach('image', $photoBytes, $image->getClientOriginalName(), ['Content-Type' => $image->getMimeType()])
            ->post($serviceUrl);
    }

    //fastapi is not running: answer 503, or a labelled DEMO result when AI_DEMO_FALLBACK is on
    private function aiServiceOffline(Request $request, mixed $petId): JsonResponse
    {
        if (config('services.ai.demo_fallback'))
        {
            $demo = app(DemoPrediction::class);
            return $demo->respond($request, $petId);
        }

        return response()->json([
            'message' => 'The AI service is not running. Start FastAPI on port 8001 and try again.',
        ], 503);
    }

    //fastapi answered with an error: pass on its message and a matching status code
    private function aiServiceError(Request $request, Response $response, mixed $petId): JsonResponse
    {
        $serviceMessage = $response->json('detail');
        $demo = app(DemoPrediction::class);
        if (config('services.ai.demo_fallback') && $demo->isModelMissing($response->status(), $serviceMessage))
        {
            return $demo->respond($request, $petId);
        }

        //413 too large, 415 wrong type, 422 no cat or dog, 503 model not ready; anything else becomes 502
        $status = 502;
        $knownStatuses = [413, 415, 422, 503];
        if (in_array($response->status(), $knownStatuses, true))
        {
            $status = $response->status();
        }

        $message = 'The AI service could not process this image.';
        if (is_string($serviceMessage))
        {
            $message = $serviceMessage;
        }

        return response()->json([
            'message' => $message,
            'ai_service_status' => $response->status(),
        ], $status);
    }

    //check every field fastapi must send; returns null when something is missing or out of range
    private function readAiResult(Response $response): ?array
    {
        $result = $response->json();
        if (! is_array($result))
        {
            $result = [];
        }

        $validator = Validator::make($result, [
            'species' => ['required', 'string', Rule::in(['cat', 'dog'])],
            'breed' => ['required', 'string', 'max:150'],
            'confidence' => ['required', 'numeric', 'between:0,1'],
            'top_predictions' => ['required', 'array', 'min:1', 'max:5'],
            'top_predictions.*.breed' => ['required', 'string', 'max:150'],
            'top_predictions.*.confidence' => ['required', 'numeric', 'between:0,1'],
            'model_version' => ['required', 'string', 'max:100'],
        ]);
        if ($validator->fails())
        {
            return null;
        }
        return $validator->validated();
    }

    //save the scan in one database transaction, so it is saved completely or not at all
    private function savePrediction(int $ownerId, mixed $petId, array $result, string $path): AiPrediction
    {
        return DB::transaction(function () use ($ownerId, $petId, $result, $path)
        {
            return AiPrediction::create([
                'user_id' => $ownerId,
                'pet_id' => $petId,
                'species' => $result['species'],
                'breed' => $result['breed'],
                'confidence' => $result['confidence'],
                'top_predictions' => $result['top_predictions'],
                'image_path' => $path,
                'model_version' => $result['model_version'],
            ]);
        });
    }
}
