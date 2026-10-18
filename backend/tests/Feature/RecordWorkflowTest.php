<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use PHPUnit\Framework\Attributes\DataProvider;
use Tests\TestCase;

class RecordWorkflowTest extends TestCase
{
    use RefreshDatabase;

    public function test_vaccination_can_be_saved_before_it_is_administered(): void
    {
        $owner = User::factory()->create();
        $pet = $owner->pets()->create(['name' => 'Milo', 'species' => 'Cat', 'sex' => 'Unknown']);
        Sanctum::actingAs($owner);
        $data = [
            'pet_id' => $pet->id,
            'vaccine_name' => 'Rabies',
            'administered_date' => null,
            'due_date' => '2099-09-10',
            'status' => 'Upcoming',
        ];

        $id = $this->postJson('/api/vaccinations', $data)
            ->assertCreated()->assertJsonPath('administered_date', null)->json('id');
        $this->putJson("/api/vaccinations/{$id}", [...$data, 'due_date' => '2099-09-11'])
            ->assertOk()->assertJsonPath('due_date', '2099-09-11');
    }

    public function test_dashboard_orders_same_day_appointments_by_time_before_limiting(): void
    {
        $owner = User::factory()->create();
        $pet = $owner->pets()->create(['name' => 'Milo', 'species' => 'Cat', 'sex' => 'Unknown']);
        Sanctum::actingAs($owner);

        foreach (['15:00', '14:00', '13:00', '12:00', '11:00', '09:00'] as $time)
        {
            $owner->appointments()->create([
                'pet_id' => $pet->id,
                'appointment_date' => '2099-09-10',
                'appointment_time' => $time,
                'reason' => 'Checkup',
                'status' => 'Scheduled',
            ]);
        }

        $times = $this->getJson('/api/dashboard')->assertOk()
            ->assertJsonCount(5, 'next_appointments')->json('next_appointments');
        $this->assertSame(['09:00', '11:00', '12:00', '13:00', '14:00'],
            array_map(fn ($item) => substr($item['appointment_time'], 0, 5), $times));
    }

    public function test_equivalent_date_formats_cannot_create_duplicate_appointments(): void
    {
        $owner = User::factory()->create();
        $pet = $owner->pets()->create(['name' => 'Milo', 'species' => 'Cat', 'sex' => 'Unknown']);
        Sanctum::actingAs($owner);
        $data = [
            'pet_id' => $pet->id,
            'appointment_date' => '2099-09-10',
            'appointment_time' => '09:30',
            'reason' => 'Checkup',
            'status' => 'Scheduled',
        ];

        $this->postJson('/api/appointments', $data)->assertCreated();
        $this->postJson('/api/appointments', [...$data, 'appointment_date' => '10 September 2099'])
            ->assertUnprocessable()->assertJsonValidationErrors('appointment_time');
        $this->assertDatabaseCount('appointments', 1);
    }

    #[DataProvider('invalidAccountFields')]
    public function test_non_text_account_fields_are_validation_errors(string $endpoint, string $field): void
    {
        $this->postJson($endpoint, [
            'name' => 'Owner',
            'email' => 'owner@example.com',
            'password' => 'password123',
            $field => ['not text'],
        ])->assertUnprocessable()->assertJsonValidationErrors($field);
        $this->assertDatabaseCount('users', 0);
    }

    public static function invalidAccountFields(): array
    {
        return [
            'register email' => ['/api/register', 'email'],
            'register name' => ['/api/register', 'name'],
            'login email' => ['/api/login', 'email'],
        ];
    }

    #[DataProvider('recordEndpoints')]
    public function test_owner_can_manage_records_without_exposing_other_owners_data(string $endpoint, array $fields): void
    {
        $owner = User::factory()->create();
        $other = User::factory()->create();
        $pet = $owner->pets()->create(['name' => 'Milo', 'species' => 'Cat', 'sex' => 'Unknown']);
        $otherPet = $other->pets()->create(['name' => 'Toby', 'species' => 'Dog', 'sex' => 'Unknown']);
        Sanctum::actingAs($other);
        $otherId = $this->postJson($endpoint, [...$fields, 'pet_id' => $otherPet->id])
            ->assertCreated()->json('id');
        Sanctum::actingAs($owner);
        $id = $this->postJson($endpoint, [...$fields, 'pet_id' => $pet->id])
            ->assertCreated()->json('id');

        $this->getJson($endpoint)->assertOk()->assertJsonCount(1)->assertJsonPath('0.id', $id);
        $this->getJson("{$endpoint}/{$id}")->assertOk()->assertJsonPath('pet.name', 'Milo');
        $this->putJson("{$endpoint}/{$id}", [...$fields, 'pet_id' => $pet->id, 'notes' => 'Updated'])
            ->assertOk()->assertJsonPath('notes', 'Updated');
        $this->putJson("{$endpoint}/{$id}", [...$fields, 'pet_id' => $pet->id, 'notes' => null])
            ->assertOk()->assertJsonPath('notes', null);
        $this->getJson("{$endpoint}/{$otherId}")->assertNotFound();
        $this->putJson("{$endpoint}/{$otherId}", [...$fields, 'pet_id' => $pet->id])->assertNotFound();
        $this->deleteJson("{$endpoint}/{$otherId}")->assertNotFound();
        $this->deleteJson("{$endpoint}/{$id}")->assertNoContent();
        $this->getJson($endpoint)->assertOk()->assertJsonCount(0);
        Sanctum::actingAs($other);
        $this->getJson("{$endpoint}/{$otherId}")->assertOk();
    }

    //check blank optional fields remove their previously saved values
    public function test_pet_optional_fields_can_be_cleared(): void
    {
        $owner = User::factory()->create();
        Sanctum::actingAs($owner);
        $required = ['name' => 'Milo', 'species' => 'Cat', 'sex' => 'Unknown'];
        $optional = [
            'breed' => 'Siamese',
            'birth_date' => '2020-01-01',
            'weight_kg' => 4.2,
            'microchip_number' => 'TEST-001',
            'notes' => 'Indoor cat',
        ];
        $id = $this->postJson('/api/pets', [...$required, ...$optional])
            ->assertCreated()->json('id');

        $response = $this->putJson("/api/pets/{$id}", [
            ...$required, ...array_fill_keys(array_keys($optional), null),
        ])->assertOk();

        foreach (array_keys($optional) as $field)
        {
            $response->assertJsonPath($field, null);
        }

        $this->assertDatabaseHas('pets', [
            'id' => $id, ...array_fill_keys(array_keys($optional), null),
        ]);
    }

    //delete dependent care records but preserve the unlinked prediction history
    public function test_deleting_a_pet_clears_related_care_records(): void
    {
        $owner = User::factory()->create();
        $pet = $owner->pets()->create(['name' => 'Milo', 'species' => 'Cat', 'sex' => 'Unknown']);
        Sanctum::actingAs($owner);

        foreach (self::recordEndpoints() as [$endpoint, $fields])
        {
            $this->postJson($endpoint, [...$fields, 'pet_id' => $pet->id])->assertCreated();
        }

        $prediction = $owner->predictions()->create([
            'pet_id' => $pet->id,
            'species' => 'cat',
            'breed' => 'Siamese',
            'confidence' => 0.8,
            'image_path' => 'test-placeholder.jpg',
        ]);

        $this->deleteJson("/api/pets/{$pet->id}")->assertNoContent();
        $this->assertDatabaseMissing('pets', ['id' => $pet->id]);
        $this->assertDatabaseCount('vaccination_records', 0);
        $this->assertDatabaseCount('medical_records', 0);
        $this->assertDatabaseCount('appointments', 0);
        $this->assertDatabaseHas('ai_predictions', ['id' => $prediction->id, 'pet_id' => null]);
    }

    public static function recordEndpoints(): array
    {
        return [
            'vaccination' => ['/api/vaccinations', [
                'vaccine_name' => 'Rabies', 'administered_date' => '2020-01-01',
                'due_date' => '2099-09-10', 'status' => 'Upcoming',
            ]],
            'medical record' => ['/api/medical-records', [
                'visit_date' => '2020-01-01', 'diagnosis' => 'Routine checkup',
            ]],
            'appointment' => ['/api/appointments', [
                'appointment_date' => '2099-09-10', 'appointment_time' => '09:30',
                'reason' => 'Routine checkup', 'status' => 'Scheduled',
            ]],
        ];
    }
}
