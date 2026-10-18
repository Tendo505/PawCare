<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class MedicalRecord extends Model
{
    //medical record fields that can be saved
    protected $fillable = [
        'pet_id',
        'veterinarian_id',
        'visit_date',
        'veterinarian',
        'diagnosis',
        'treatment',
        'notes'
    ];

    //format the visit date for the app
    protected function casts(): array
    {
        return ['visit_date' => 'date:Y-m-d'];
    }

    //get the pet whose health was recorded
    public function pet()
    {
        return $this->belongsTo(Pet::class);
    }

    //get the veterinarian profile using its saved identifier
    public function veterinarianProfile()
    {
        return $this->belongsTo(Veterinarian::class, 'veterinarian_id');
    }
}
