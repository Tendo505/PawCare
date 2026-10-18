<?php

namespace App\Http\Controllers;

use App\Services\SystemHealthService;
use Illuminate\Http\JsonResponse;

class SystemHealthController extends Controller
{
    //read each service status even when one is offline
    public function __invoke(SystemHealthService $health): JsonResponse
    {
        return response()->json($health->check());
    }
}
