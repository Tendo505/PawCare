<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

//one clinic visit booked by an owner for a pet (table: appointments)
class Appointment extends Model
{
    //1.setup

    //appointment fields that can be saved
    protected $fillable = [
        'user_id',
        'pet_id',
        'veterinarian_id',
        'appointment_date',
        'appointment_time',
        'clinic',
        'reason',
        'status',
        'notes'
    ];

    //format the appointment date for the app
    protected function casts(): array
    {
        return ['appointment_date' => 'date:Y-m-d'];
    }

    //4.output

    //get the owner who arranged the appointment
    public function user()
    {
        return $this->belongsTo(User::class);
    }

    //get the pet attending the appointment
    public function pet()
    {
        return $this->belongsTo(Pet::class);
    }

    //get the linked veterinarian profile
    public function veterinarian()
    {
        return $this->belongsTo(Veterinarian::class);
    }
}
