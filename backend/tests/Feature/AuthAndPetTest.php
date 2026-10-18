<?php

namespace Tests\Feature;

use App\Models\Pet;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Storage;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class AuthAndPetTest extends TestCase
{
    use RefreshDatabase;

    //keep shared ownership checks on all linked-record forms
    public function test_linked_records_reject_another_owners_pet(): void
    {
        $owner = User::factory()->create();
        $otherOwner = User::factory()->create();
        $pet = $otherOwner->pets()->create([
            'name' => 'Private pet',
            'species' => 'Cat',
            'sex' => 'Unknown',
        ]);
        Sanctum::actingAs($owner);

        $this->postJson('/api/medical-records', [
            'pet_id' => $pet->id,
            'visit_date' => today()->toDateString(),
            'diagnosis' => 'Checkup',
        ])->assertNotFound();

        $this->postJson('/api/vaccinations', [
            'pet_id' => $pet->id,
            'vaccine_name' => 'Vaccine',
            'administered_date' => today()->toDateString(),
            'due_date' => today()->addYear()->toDateString(),
            'status' => 'Upcoming',
        ])->assertNotFound();

        $this->postJson('/api/appointments', [
            'pet_id' => $pet->id,
            'appointment_date' => today()->toDateString(),
            'appointment_time' => '09:30',
            'reason' => 'Checkup',
            'status' => 'Scheduled',
        ])->assertNotFound();

        $this->assertDatabaseCount('medical_records', 0);
        $this->assertDatabaseCount('vaccination_records', 0);
        $this->assertDatabaseCount('appointments', 0);
    }

    //check account creation, normalized email and password hashing
    public function test_user_can_register_and_receive_token(): void
    {
        $this->postJson('/api/register', [
            'name' => 'Test Owner',
            'email' => 'Owner@Example.COM',
            'password' => 'password123',
        ])
            ->assertCreated()
            ->assertJsonStructure(['token', 'user' => ['id', 'name', 'email']]);

        $user = User::where('email', 'owner@example.com')->firstOrFail();
        $this->assertTrue(Hash::check('password123', $user->password));
    }

    //check real bearer tokens across login, account loading and logout
    public function test_owner_can_log_in_read_account_and_log_out(): void
    {
        $user = User::factory()->create([
            'email' => 'owner@example.com',
            'password' => 'password123',
        ]);
        $oldToken = $user->createToken('previous-session')->plainTextToken;

        $token = $this->postJson('/api/login', [
            'email' => ' OWNER@EXAMPLE.COM ',
            'password' => 'password123',
        ])->assertOk()->assertJsonPath('user.id', $user->id)->json('token');

        $this->assertSame(1, $user->tokens()->count());
        $this->app['auth']->forgetGuards();
        $this->getJson('/api/me', ['Authorization' => 'Bearer '.$oldToken])->assertUnauthorized();

        $this->app['auth']->forgetGuards();
        $this->getJson('/api/me', ['Authorization' => 'Bearer '.$token])
            ->assertOk()->assertJsonPath('email', 'owner@example.com');

        $this->app['auth']->forgetGuards();
        $this->postJson('/api/logout', [], ['Authorization' => 'Bearer '.$token])->assertOk();
        $this->assertSame(0, $user->tokens()->count());

        $this->app['auth']->forgetGuards();
        $this->getJson('/api/me', ['Authorization' => 'Bearer '.$token])->assertUnauthorized();
    }

    //check invalid email formats and duplicate account addresses
    public function test_invalid_and_duplicate_email_addresses_are_rejected(): void
    {
        User::factory()->create(['email' => 'owner@example.com']);

        $this->postJson('/api/register', [
            'name' => 'Invalid Owner',
            'email' => 'not-an-email',
            'password' => 'password123',
        ])
            ->assertUnprocessable()
            ->assertJsonValidationErrors(['email']);

        $this->postJson('/api/register', [
            'name' => 'Duplicate Owner',
            'email' => ' OWNER@EXAMPLE.COM ',
            'password' => 'password123',
        ])
            ->assertUnprocessable()
            ->assertJsonValidationErrors(['email']);
    }

    //check pet creation and the owner's pet list
    public function test_user_can_create_and_list_own_pet(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);

        $this->postJson('/api/pets', [
            'name' => 'Milo',
            'species' => 'Cat',
            'breed' => 'Siamese',
            'sex' => 'Male',
            'birth_date' => '2024-01-15',
            'weight_kg' => 4.2,
        ])->assertCreated();

        $this->getJson('/api/pets')
            ->assertOk()
            ->assertJsonCount(1)
            ->assertJsonPath('0.name', 'Milo');
    }

    //check that private records require authentication
    public function test_private_routes_require_a_bearer_token(): void
    {
        $this->getJson('/api/pets')->assertUnauthorized();
        $this->getJson('/api/appointments')->assertUnauthorized();
    }

    //check the combined status when all services are ready
    public function test_system_health_reports_all_integrated_services(): void
    {
        Http::fake([
            'http://127.0.0.1:8001/health' => Http::response([
                'status' => 'ready',
                'model_ready' => true,
                'model_version' => 'oxford-pets-test-model',
                'label_count' => 37,
            ]),
        ]);

        $this->getJson('/api/health')
            ->assertOk()
            ->assertJsonPath('status', 'ok')
            ->assertJsonPath('services.laravel.status', 'ok')
            ->assertJsonPath('services.database.status', 'ok')
            ->assertJsonPath('services.ai.status', 'ready')
            ->assertJsonPath('services.ai.label_count', 37);
    }

    //check that an offline AI service does not expose connection details
    public function test_system_health_is_degraded_without_exposing_connection_secrets(): void
    {
        Http::fake([
            'http://127.0.0.1:8001/health' => Http::response([], 503),
        ]);

        $response = $this->getJson('/api/health')
            ->assertOk()
            ->assertJsonPath('status', 'degraded')
            ->assertJsonPath('services.database.status', 'ok')
            ->assertJsonPath('services.ai.status', 'unavailable');

        $payload = $response->getContent();
        $this->assertStringNotContainsString('127.0.0.1', $payload);
        $this->assertStringNotContainsString('password', strtolower($payload));
    }

    //check read and update requests cannot access another owner's pet
    public function test_user_cannot_access_another_owners_pet(): void
    {
        $owner = User::factory()->create();
        $otherUser = User::factory()->create();
        $otherPet = Pet::create([
            'user_id' => $otherUser->id,
            'name' => 'Private Pet',
            'species' => 'Cat',
            'sex' => 'Unknown',
        ]);

        Sanctum::actingAs($owner);

        $this->getJson("/api/pets/{$otherPet->id}")->assertNotFound();
        $this->putJson("/api/pets/{$otherPet->id}", [
            'name' => 'Changed',
            'species' => 'Cat',
            'sex' => 'Unknown',
        ])->assertNotFound();
    }

    //check unsupported species and invalid profile fields
    public function test_invalid_pet_data_is_rejected(): void
    {
        Sanctum::actingAs(User::factory()->create());

        $this->postJson('/api/pets', [
            'name' => '',
            'species' => 'Bird',
            'sex' => 'Invalid',
            'weight_kg' => -3,
        ])
            ->assertUnprocessable()
            ->assertJsonValidationErrors(['name', 'species', 'sex', 'weight_kg']);
    }

    //check that a pet cannot have overlapping scheduled visits
    public function test_duplicate_scheduled_appointment_is_rejected(): void
    {
        $user = User::factory()->create();
        $pet = $user->pets()->create([
            'name' => 'Toby',
            'species' => 'Cat',
            'sex' => 'Male',
        ]);
        Sanctum::actingAs($user);

        $appointment = [
            'pet_id' => $pet->id,
            'appointment_date' => '2099-09-10',
            'appointment_time' => '09:30',
            'clinic' => 'PawCare Clinic',
            'reason' => 'Routine examination',
            'status' => 'Scheduled',
        ];

        $this->postJson('/api/appointments', $appointment)->assertCreated();
        $this->postJson('/api/appointments', $appointment)
            ->assertUnprocessable()
            ->assertJsonValidationErrors(['appointment_time']);
    }

    //check the vaccination date validation rule
    public function test_vaccination_due_date_cannot_precede_administered_date(): void
    {
        $user = User::factory()->create();
        $pet = $user->pets()->create([
            'name' => 'Toby',
            'species' => 'Cat',
            'sex' => 'Male',
        ]);
        Sanctum::actingAs($user);

        $this->postJson('/api/vaccinations', [
            'pet_id' => $pet->id,
            'vaccine_name' => 'Rabies',
            'administered_date' => '2025-08-30',
            'due_date' => '2025-08-29',
            'status' => 'Completed',
        ])
            ->assertUnprocessable()
            ->assertJsonValidationErrors(['due_date']);
    }

    //check repeated failed logins reach the request limit
    public function test_login_endpoint_is_rate_limited(): void
    {
        $request = $this->withServerVariables(['REMOTE_ADDR' => '198.51.100.31']);

        for ($attempt = 1; $attempt <= 10; $attempt++)
        {
            $request->postJson('/api/login', [
                'email' => 'missing@example.com',
                'password' => 'wrong-password',
            ])->assertUnprocessable();
        }

        $request->postJson('/api/login', [
            'email' => 'missing@example.com',
            'password' => 'wrong-password',
        ])->assertTooManyRequests();
    }

    //check AI forwarding, image storage and saved prediction ownership
    public function test_ai_prediction_is_forwarded_and_saved_for_the_owner(): void
    {
        Storage::fake('public');
        Http::fake([
            'http://127.0.0.1:8001/predict' => Http::response([
                'species' => 'cat',
                'breed' => 'British_Shorthair',
                'confidence' => 0.82,
                'top_predictions' => [
                    ['breed' => 'British_Shorthair', 'confidence' => 0.82],
                    ['breed' => 'Russian_Blue', 'confidence' => 0.11],
                    ['breed' => 'Bombay', 'confidence' => 0.07],
                ],
                'model_version' => 'sprint-6-model',
            ]),
        ]);

        $user = User::factory()->create();
        $pet = $user->pets()->create([
            'name' => 'Toby',
            'species' => 'Cat',
            'sex' => 'Male',
        ]);
        Sanctum::actingAs($user);

        $response = $this->post('/api/ai-predictions', [
            'image' => UploadedFile::fake()->image('toby.jpg', 400, 400),
            'pet_id' => $pet->id,
        ], ['Accept' => 'application/json']);

        $response->assertCreated()
            ->assertJsonPath('breed', 'British_Shorthair')
            ->assertJsonPath('pet.name', 'Toby')
            ->assertJsonMissingPath('image_path')
            ->assertJsonStructure(['id', 'image_url', 'disclaimer', 'top_predictions']);

        $prediction = $user->predictions()->firstOrFail();
        $this->assertSame($pet->id, $prediction->pet_id);
        $this->assertSame(0.82, $prediction->confidence);
        Storage::disk('public')->assertExists($prediction->image_path);

        Http::assertSent(fn ($request) => $request->url() === 'http://127.0.0.1:8001/predict');
    }

    //check prediction requests cannot link another owner's pet
    public function test_ai_prediction_cannot_link_another_owners_pet(): void
    {
        Http::fake();
        $owner = User::factory()->create();
        $otherPet = User::factory()->create()->pets()->create([
            'name' => 'Private Pet',
            'species' => 'Dog',
            'sex' => 'Unknown',
        ]);
        Sanctum::actingAs($owner);

        $this->post('/api/ai-predictions', [
            'image' => UploadedFile::fake()->image('pet.jpg', 400, 400),
            'pet_id' => $otherPet->id,
        ], ['Accept' => 'application/json'])
            ->assertUnprocessable()
            ->assertJsonValidationErrors(['pet_id']);

        Http::assertNothingSent();
    }

    //check an AI service failure does not create a saved prediction
    public function test_ai_service_failure_does_not_create_a_database_record(): void
    {
        Storage::fake('public');
        Http::fake([
            'http://127.0.0.1:8001/predict' => Http::response([
                'detail' => 'No trained model was found.',
            ], 503),
        ]);
        $user = User::factory()->create();
        Sanctum::actingAs($user);

        $this->post('/api/ai-predictions', [
            'image' => UploadedFile::fake()->image('pet.jpg', 400, 400),
        ], ['Accept' => 'application/json'])
            ->assertServiceUnavailable()
            ->assertJsonPath('message', 'No trained model was found.');

        $this->assertDatabaseCount('ai_predictions', 0);
    }

    //check non-pet photos return validation messages without saving data
    public function test_non_pet_photo_is_rejected_and_not_saved(): void
    {
        Storage::fake('public');
        Http::fake([
            'http://127.0.0.1:8001/predict' => Http::response([
                'detail' => 'No cat or dog was detected. Choose a clear photo containing one cat or dog.',
            ], 422),
        ]);
        $user = User::factory()->create();
        Sanctum::actingAs($user);

        $this->post('/api/ai-predictions', [
            'image' => UploadedFile::fake()->image('person.jpg', 400, 400),
        ], ['Accept' => 'application/json'])
            ->assertUnprocessable()
            ->assertJsonPath('message', 'No cat or dog was detected. Choose a clear photo containing one cat or dog.');

        $this->assertDatabaseCount('ai_predictions', 0);
        Storage::disk('public')->assertDirectoryEmpty('predictions');
    }
}
