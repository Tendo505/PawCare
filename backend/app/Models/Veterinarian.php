<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Veterinarian extends Model
{
    protected $fillable = ['name', 'clinic_name', 'phone', 'email', 'address'];
    public function medicalRecords() { return $this->hasMany(MedicalRecord::class); }
}
