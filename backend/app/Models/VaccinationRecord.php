<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

//one vaccine dose given or due for a pet (table: vaccination_records)
class VaccinationRecord extends Model
{
    //1.setup

    //vaccination record fields that can be saved
    protected $fillable = [
        'pet_id',
        'vaccination_id',
        'vaccine_name',
        'administered_date',
        'due_date',
        'clinic',
        'status',
        'notes'
    ];

    //format the vaccination dates for the app
    protected function casts(): array
    {
        return [
            'administered_date' => 'date:Y-m-d',
            'due_date' => 'date:Y-m-d'
        ];
    }

    //4.output

    //get the pet receiving this vaccination
    public function pet()
    {
        return $this->belongsTo(Pet::class);
    }

    //get the vaccine catalogue entry for this record
    public function vaccination()
    {
        return $this->belongsTo(Vaccination::class);
    }
}
