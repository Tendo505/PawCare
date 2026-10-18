<?php

use App\Services\SystemHealthService;
use Illuminate\Support\Facades\Artisan;

// Display the backend information
Artisan::command('pawcare:about', function ()
{
    $this->info('PawCare AI backend is ready.');
})->purpose('Display the PawCare API status');

// Check service connections without changing records
Artisan::command('pawcare:integration-check', function ()
{
    $result = app(SystemHealthService::class)->check();
    $services = $result['services'];

    $this->info('PawCare end-to-end integration check');
    $this->table(
        ['Component', 'Status', 'Details'],
        [
            ['Laravel API', $services['laravel']['status'], 'Laravel '.$services['laravel']['version']],
            ['Database', $services['database']['status'], strtoupper($services['database']['driver'])],
            [
                'AI service',
                $services['ai']['status'],
                $services['ai']['model_version'] ?: 'No model version reported',
            ],
        ]
    );

    if ($result['status'] === 'ok')
    {
        $this->info('All four PawCare layers are connected.');
    }
    else
    {
        $this->warn('Core or optional services need attention. See the table above.');
    }
})->purpose('Check Laravel, database, and FastAPI connections without changing data');
