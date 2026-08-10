<?php

use Illuminate\Support\Facades\Artisan;

Artisan::command('pawcare:about', function () {
    $this->info('PawCare AI backend is ready.');
})->purpose('Display the PawCare API status');
