<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Veterinarian extends Model
{
    //veterinarian profile fields that can be saved
    protected $fillable = [
        'name',
        'clinic_name',
        'phone',
        'email',
        'address'
    ];

    //get the medical records linked to this veterinarian
    public function medicalRecords()
    {
        return $this->hasMany(MedicalRecord::class);
    }
}
