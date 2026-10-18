<?php

namespace App\Providers;

use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    //register custom services when the application starts
    public function register(): void
    {
        //no custom bindings are required
    }

    //configure services after they have been registered
    public function boot(): void
    {
        //no extra startup configuration is required
    }
}
