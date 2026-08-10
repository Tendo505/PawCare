<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class PetBreed extends Model
{
    protected $fillable = ['name', 'species', 'description'];
    public function pets() { return $this->hasMany(Pet::class); }
}
