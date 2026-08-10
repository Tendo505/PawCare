<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class AuthAndPetTest extends TestCase
{
    use RefreshDatabase;

    public function test_user_can_register_and_receive_token(): void
    {
        $this->postJson('/api/register', ['name' => 'Test Owner', 'email' => 'owner@example.com', 'password' => 'password123'])
            ->assertCreated()->assertJsonStructure(['token', 'user' => ['id', 'name', 'email']]);
    }

    public function test_user_can_create_and_list_own_pet(): void
    {
        $user = User::factory()->create(); Sanctum::actingAs($user);
        $this->postJson('/api/pets', ['name' => 'Milo', 'species' => 'Cat', 'breed' => 'Siamese', 'sex' => 'Male', 'birth_date' => '2024-01-15', 'weight_kg' => 4.2])->assertCreated();
        $this->getJson('/api/pets')->assertOk()->assertJsonCount(1)->assertJsonPath('0.name', 'Milo');
    }
}
