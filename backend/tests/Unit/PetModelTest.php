<?php

namespace Tests\Unit;

use App\Models\Pet;
use Tests\TestCase;

class PetModelTest extends TestCase
{
    public function test_pet_accepts_the_supported_profile_fields(): void
    {
        $pet = new Pet([
            'name' => 'Milo',
            'species' => 'Cat',
            'breed' => 'Domestic Shorthair',
            'sex' => 'Male',
            'birth_date' => '2024-05-10',
            'weight_kg' => 4.2,
            'microchip_number' => 'MY-CAT-1001',
            'notes' => 'Indoor cat',
        ]);

        $this->assertSame('Milo', $pet->name);
        $this->assertSame('2024-05-10', $pet->birth_date->format('Y-m-d'));
        $this->assertSame('4.20', $pet->weight_kg);
    }
}
