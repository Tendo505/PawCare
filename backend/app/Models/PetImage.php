<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class PetImage extends Model
{
    //pet image fields that can be saved
    protected $fillable = [
        'pet_id',
        'path',
        'caption',
        'is_profile'
    ];

    //convert the profile image flag to a boolean
    protected function casts(): array
    {
        return ['is_profile' => 'boolean'];
    }

    //get the pet shown in this image
    public function pet()
    {
        return $this->belongsTo(Pet::class);
    }
}
