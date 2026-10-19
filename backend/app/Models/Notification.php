<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

//a reminder message for an owner (table: notifications)
class Notification extends Model
{
    //1.setup

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

    //4.output

    //get the user receiving this notification
    public function user()
    {
        return $this->belongsTo(User::class);
    }
}
