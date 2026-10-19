<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

//the list of known breeds (table: pet_breeds)
class PetBreed extends Model
{
    //1.setup

    //breed catalogue fields that can be saved
    protected $fillable = [
        'name',
        'species',
        'description'
    ];

    //4.output

    //get the pets linked to this breed
    public function pets()
    {
        return $this->hasMany(Pet::class);
    }
}
