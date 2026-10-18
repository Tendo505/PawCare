<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Facades\Storage;

class AiPrediction extends Model
{
    //prediction fields that can be saved
    protected $fillable = [
        'user_id',
        'pet_id',
        'species',
        'breed',
        'confidence',
        'top_predictions',
        'image_path',
        'model_version'
    ];

    //hide the storage path and include the public result details
    protected $hidden = ['image_path'];
    protected $appends = ['image_url', 'disclaimer'];

    //convert stored prediction values to their response types
    protected function casts(): array
    {
        return [
            'confidence' => 'float',
            'top_predictions' => 'array'
        ];
    }

    //get the owner of this prediction
    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    //get the pet linked to this prediction
    public function pet(): BelongsTo
    {
        return $this->belongsTo(Pet::class);
    }

    //return a public image url instead of its storage path
    public function getImageUrlAttribute(): ?string
    {
        return $this->image_path ? Storage::disk('public')->url($this->image_path) : null;
    }

    //include the same guidance with every prediction
    public function getDisclaimerAttribute(): string
    {
        return 'Breed recognition is an estimate and is not veterinary advice.';
    }
}
