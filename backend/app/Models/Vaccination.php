<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Vaccination extends Model
{
    protected $fillable = ['name', 'species', 'description', 'recommended_interval_months'];
    public function records() { return $this->hasMany(VaccinationRecord::class); }
}
