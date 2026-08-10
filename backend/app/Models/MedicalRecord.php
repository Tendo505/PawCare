<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class MedicalRecord extends Model
{
    protected $fillable = ['pet_id', 'veterinarian_id', 'visit_date', 'veterinarian', 'diagnosis', 'treatment', 'notes'];
    protected function casts(): array { return ['visit_date' => 'date:Y-m-d']; }
    public function pet() { return $this->belongsTo(Pet::class); }
    public function veterinarianProfile() { return $this->belongsTo(Veterinarian::class, 'veterinarian_id'); }
}
