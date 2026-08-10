<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class PetImage extends Model
{
    protected $fillable = ['pet_id', 'path', 'caption', 'is_profile'];
    protected function casts(): array { return ['is_profile' => 'boolean']; }
    public function pet() { return $this->belongsTo(Pet::class); }
}
