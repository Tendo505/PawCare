<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;

class User extends Authenticatable
{
    use HasApiTokens, HasFactory, Notifiable;

    //account fields that can be saved
    protected $fillable = [
        'name',
        'email',
        'password'
    ];

    //keep private account values out of API responses
    protected $hidden = [
        'password',
        'remember_token'
    ];

    //hash passwords assigned through the model
    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'password' => 'hashed'
        ];
    }

    //get the pets belonging to this account
    public function pets()
    {
        return $this->hasMany(Pet::class);
    }

    //get the appointments arranged by this account
    public function appointments()
    {
        return $this->hasMany(Appointment::class);
    }

    //get the breed predictions saved by this account
    public function predictions()
    {
        return $this->hasMany(AiPrediction::class);
    }
}
