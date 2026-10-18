<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class PetBreed extends Model
{
    //breed catalogue fields that can be saved
    protected $fillable = [
        'name',
        'species',
        'description'
    ];

    //get the pets linked to this breed
    public function pets()
    {
        return $this->hasMany(Pet::class);
    }
}
