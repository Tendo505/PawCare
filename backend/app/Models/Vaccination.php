<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Vaccination extends Model
{
    //vaccination catalogue fields that can be saved
    protected $fillable = [
        'name',
        'species',
        'description',
        'recommended_interval_months'
    ];

    //get the pet records linked to this vaccine
    public function records()
    {
        return $this->hasMany(VaccinationRecord::class);
    }
}
