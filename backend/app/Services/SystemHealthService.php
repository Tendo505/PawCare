<?php

namespace App\Services;

use Illuminate\Foundation\Application;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Throwable;

//checks laravel, the database and the ai service for GET /api/health
//it never returns passwords, hosts or other connection details
class SystemHealthService
{
    //4.output

    //"ok" when the database and the ai model are both ready, otherwise "degraded"
    public function check(): array
    {
        $database = $this->checkDatabase();
        $ai = $this->checkAiService();

        $overall = 'degraded';
        if ($database['status'] === 'ok' && $ai['status'] === 'ready')
        {
            $overall = 'ok';
        }

        return [
            'status' => $overall,
            'services' => [
                'laravel' => [
                    'status' => 'ok',
                    'version' => Application::VERSION,
                ],
                'database' => $database,
                'ai' => $ai,
            ],
            'checked_at' => now()->toIso8601String(),
        ];
    }

    //3.process

    //run the smallest possible query to see whether the database answers
    private function checkDatabase(): array
    {
        $database = [
            'status' => 'unavailable',
            'driver' => (string) config('database.default'),
        ];

        try
        {
            DB::select('select 1');
            $database['status'] = 'ok';
        }
        catch (Throwable $error)
        {
            //the status stays "unavailable"; the error details are not sent to the app
        }

        return $database;
    }

    //ask fastapi's /health endpoint, waiting at most 3 seconds
    private function checkAiService(): array
    {
        $ai = [
            'status' => 'unavailable',
            'model_ready' => false,
            'model_version' => null,
            'label_count' => 0,
        ];

        try
        {
            $healthUrl = rtrim((string) config('services.ai.url'), '/').'/health';
            $response = Http::acceptJson()->timeout(3)->get($healthUrl);

            if ($response->successful())
            {
                $modelReady = (bool) $response->json('model_ready', false);

                //"ready" when the model can predict, otherwise fastapi's own status such as "model_missing"
                $status = 'ready';
                if (! $modelReady)
                {
                    $status = (string) $response->json('status', 'model_missing');
                }

                $ai = [
                    'status' => $status,
                    'model_ready' => $modelReady,
                    'model_version' => $response->json('model_version'),
                    'label_count' => (int) $response->json('label_count', 0),
                ];
            }
        }
        catch (Throwable $error)
        {
            //pet records still work when the ai service is offline
        }

        return $ai;
    }
}
