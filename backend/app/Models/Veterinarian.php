<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

//a veterinarian and their clinic (table: veterinarians)
class Veterinarian extends Model
{
    //1.setup

    //veterinarian profile fields that can be saved
    protected $fillable = [
        'name',
        'clinic_name',
        'phone',
        'email',
        'address'
    ];

    //4.output

    //get the medical records linked to this veterinarian
    public function medicalRecords()
    {
        return $this->hasMany(MedicalRecord::class);
    }
}
