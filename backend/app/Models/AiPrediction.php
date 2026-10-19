<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Facades\Storage;

//one saved breed scan (table: ai_predictions)
class AiPrediction extends Model
{
    //1.setup

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

    //4.output

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
        if (! $this->image_path)
        {
            return null;
        }
        return Storage::disk('public')->url($this->image_path);
    }

    //include the same guidance with every prediction
    public function getDisclaimerAttribute(): string
    {
        return 'Breed recognition is an estimate and is not veterinary advice.';
    }
}
