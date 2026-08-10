<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class Pet extends Model
{
    use HasFactory;
    protected $fillable = ['user_id', 'pet_breed_id', 'name', 'species', 'breed', 'sex', 'birth_date', 'weight_kg', 'microchip_number', 'notes'];
    protected function casts(): array { return ['birth_date' => 'date:Y-m-d', 'weight_kg' => 'decimal:2']; }

    public function user() { return $this->belongsTo(User::class); }
    public function petBreed() { return $this->belongsTo(PetBreed::class); }
    public function vaccinationRecords() { return $this->hasMany(VaccinationRecord::class); }
    public function medicalRecords() { return $this->hasMany(MedicalRecord::class); }
    public function appointments() { return $this->hasMany(Appointment::class); }
    public function images() { return $this->hasMany(PetImage::class); }
    public function predictions() { return $this->hasMany(AiPrediction::class); }
}
