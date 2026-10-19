<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

//the list of known vaccines (table: vaccinations)
class Vaccination extends Model
{
    //1.setup

    //vaccination catalogue fields that can be saved
    protected $fillable = [
        'name',
        'species',
        'description',
        'recommended_interval_months'
    ];

    //4.output

    //get the pet records linked to this vaccine
    public function records()
    {
        return $this->hasMany(VaccinationRecord::class);
    }
}
