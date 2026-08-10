<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Notification extends Model
{
    protected $fillable = ['user_id', 'type', 'title', 'message', 'scheduled_at', 'read_at'];
    protected function casts(): array { return ['scheduled_at' => 'datetime', 'read_at' => 'datetime']; }
    public function user() { return $this->belongsTo(User::class); }
}
