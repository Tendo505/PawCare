<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

//one pet profile belonging to an owner (table: pets)
class Pet extends Model
{
    use HasFactory;

    //1.setup

    //pet profile fields that can be saved
    protected $fillable = [
        'user_id',
        'pet_breed_id',
        'name',
        'species',
        'breed',
        'sex',
        'birth_date',
        'weight_kg',
        'microchip_number',
        'notes'
    ];

    //format the pet date and weight for the app
    protected function casts(): array
    {
        return [
            'birth_date' => 'date:Y-m-d',
            'weight_kg' => 'decimal:2'
        ];
    }

    //4.output

    //get the owner of this pet
    public function user()
    {
        return $this->belongsTo(User::class);
    }

    //get the breed catalogue entry linked to this pet
    public function petBreed()
    {
        return $this->belongsTo(PetBreed::class);
    }

    //get the pet vaccination history
    public function vaccinationRecords()
    {
        return $this->hasMany(VaccinationRecord::class);
    }

    //get the pet medical history
    public function medicalRecords()
    {
        return $this->hasMany(MedicalRecord::class);
    }

    //get the visits arranged for this pet
    public function appointments()
    {
        return $this->hasMany(Appointment::class);
    }

    //get the images saved for this pet
    public function images()
    {
        return $this->hasMany(PetImage::class);
    }

    //get the breed predictions saved for this pet
    public function predictions()
    {
        return $this->hasMany(AiPrediction::class);
    }
}
