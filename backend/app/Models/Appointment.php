<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Appointment extends Model
{
    protected $fillable = ['user_id', 'pet_id', 'veterinarian_id', 'appointment_date', 'appointment_time', 'clinic', 'reason', 'status', 'notes'];
    protected function casts(): array { return ['appointment_date' => 'date:Y-m-d']; }
    public function user() { return $this->belongsTo(User::class); }
    public function pet() { return $this->belongsTo(Pet::class); }
    public function veterinarian() { return $this->belongsTo(Veterinarian::class); }
}
