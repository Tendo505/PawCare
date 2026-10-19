<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;

//shared base for the PawCare API controllers
//every endpoint in the controllers follows the same three steps:
//2.input (read and validate the request), 3.process (check the owner, then read or save), 4.output (return json)
abstract class Controller
{
    //3.process

    //stop with "404 not found" when the pet does not belong to the signed-in owner
    //404 is used instead of 403 so another owner cannot even tell that the pet exists
    protected function ownedPet(Request $request, int $petId): void
    {
        $petBelongsToOwner = $request->user()->pets()->whereKey($petId)->exists();
        if (! $petBelongsToOwner)
        {
            abort(404);
        }
    }
}
