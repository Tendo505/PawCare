<?php

namespace App\Services;

use Illuminate\Foundation\Application;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Throwable;

class SystemHealthService
{
    //check each service without returning connection details
    public function check(): array
    {
        $database = $this->readDatabaseHealth();
        $ai = $this->readAiHealth();
        $allReady = $database['status'] === 'ok' && $ai['status'] === 'ready';

        return [
            'status' => $allReady ? 'ok' : 'degraded',
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

    //read database availability with a lightweight connection check
    private function readDatabaseHealth(): array
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
        catch (Throwable)
        {
            //do not return database connection details
        }

        return $database;
    }

    //read the AI service status without interrupting core pet records
    private function readAiHealth(): array
    {
        $ai = [
            'status' => 'unavailable',
            'model_ready' => false,
            'model_version' => null,
            'label_count' => 0,
        ];

        try
        {
            $response = Http::acceptJson()
                ->timeout(3)
                ->get(rtrim((string) config('services.ai.url'), '/').'/health');

            if ($response->successful())
            {
                $modelReady = (bool) $response->json('model_ready', false);
                $ai = [
                    'status' => $modelReady
                        ? 'ready'
                        : (string) $response->json('status', 'model_missing'),
                    'model_ready' => $modelReady,
                    'model_version' => $response->json('model_version'),
                    'label_count' => (int) $response->json('label_count', 0),
                ];
            }
        }
        catch (Throwable)
        {
            //pet records still work when the ai service is offline
        }

        return $ai;
    }
}
