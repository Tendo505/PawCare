<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class VaccinationRecord extends Model
{
    protected $fillable = ['pet_id', 'vaccination_id', 'vaccine_name', 'administered_date', 'due_date', 'clinic', 'status', 'notes'];
    protected function casts(): array { return ['administered_date' => 'date:Y-m-d', 'due_date' => 'date:Y-m-d']; }
    public function pet() { return $this->belongsTo(Pet::class); }
    public function vaccination() { return $this->belongsTo(Vaccination::class); }
}
