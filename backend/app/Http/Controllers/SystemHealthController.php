<?php

namespace App\Http\Controllers;

use App\Services\SystemHealthService;
use Illuminate\Http\JsonResponse;

//GET /api/health - is laravel, the database and the ai service working? (no login needed)
class SystemHealthController extends Controller
{
    //laravel creates SystemHealthService and passes it in automatically
    public function __invoke(SystemHealthService $health): JsonResponse
    {
        //3.process: check each service; one offline service does not stop the others being checked
        $status = $health->check();

        //4.output
        return response()->json($status);
    }
}
