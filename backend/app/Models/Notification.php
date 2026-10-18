<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Notification extends Model
{
    //notification fields that can be saved
    protected $fillable = [
        'user_id',
        'type',
        'title',
        'message',
        'scheduled_at',
        'read_at'
    ];

    //convert the schedule and read time to date-time values
    protected function casts(): array
    {
        return [
            'scheduled_at' => 'datetime',
            'read_at' => 'datetime'
        ];
    }

    //get the user receiving this notification
    public function user()
    {
        return $this->belongsTo(User::class);
    }
}
