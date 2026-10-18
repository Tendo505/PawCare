<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;

//shared base for the PawCare API controllers
abstract class Controller
{
    //only allow the owner's pets before saving linked records
    protected function ownedPet(Request $request, int $id): void
    {
        abort_unless($request->user()->pets()->whereKey($id)->exists(), 404);
    }
}
