<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Client\ConnectionException;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Storage;
use Laravel\Sanctum\Sanctum;
use PHPUnit\Framework\Attributes\DataProvider;
use Tests\TestCase;

class DemoFallbackTest extends TestCase
{
    use RefreshDatabase;

    //keep every check isolated from real services, uploads and saved settings
    protected function setUp(): void
    {
        parent::setUp();
        config(['services.ai.demo_fallback' => true, 'services.ai.url' => 'http://127.0.0.1:8001']);
        Storage::fake('public');
        Http::preventStrayRequests();
    }

    //an outage or timeout returns a labelled example without saving a prediction
    #[DataProvider('connectionFailures')]
    public function test_connection_failure_returns_an_unsaved_demo(string $message): void
    {
        Http::fake(fn () => throw new ConnectionException($message));
        $owner = User::factory()->create();
        $pet = $owner->pets()->create(['name' => 'Buddy', 'species' => 'Dog', 'sex' => 'Unknown']);
        Sanctum::actingAs($owner);

        $response = $this->postImage(['pet_id' => $pet->id]);
        $response->assertCreated()
            ->assertJsonPath('demo', true)
            ->assertJsonPath('id', 0)
            ->assertJsonPath('user_id', $owner->id)
            ->assertJsonPath('pet_id', $pet->id)
            ->assertJsonPath('pet.name', 'Buddy')
            ->assertJsonPath('species', 'dog')
            ->assertJsonPath('breed', 'DEMO ONLY - no prediction')
            ->assertJsonPath('image_url', null)
            ->assertJsonMissingPath('image_path')
            ->assertJsonStructure([
                'created_at', 'updated_at', 'model_version', 'disclaimer', 'message',
                'top_predictions' => [['breed', 'confidence']],
            ]);
        $this->assertEquals(0, $response->json('confidence'));
        $this->assertStringContainsString('not an image prediction', $response->json('message'));
        $this->assertStringContainsString('not been saved', $response->json('disclaimer'));
        $this->assertNothingSaved();
        $this->getJson('/api/ai-predictions')->assertOk()->assertJsonCount(0, 'data');
    }

    //only explicit missing-model responses qualify for the fallback
    #[DataProvider('missingModelMessages')]
    public function test_missing_model_returns_an_unsaved_demo(string $detail): void
    {
        Http::fake(['*/predict' => Http::response(['detail' => $detail], 503)]);
        Sanctum::actingAs(User::factory()->create());

        $this->postImage()->assertCreated()->assertJsonPath('demo', true)
            ->assertJsonPath('pet_id', null)->assertJsonPath('pet', null);
        $this->assertNothingSaved();
    }

    //turning the option off must retain the original service-unavailable response
    #[DataProvider('disabledFailures')]
    public function test_disabled_fallback_preserves_service_failure(bool $connectionFailure, string $message): void
    {
        config(['services.ai.demo_fallback' => false]);
        Http::fake($connectionFailure
            ? fn () => throw new ConnectionException('Connection refused')
            : ['*/predict' => Http::response(['detail' => $message], 503)]);
        Sanctum::actingAs(User::factory()->create());

        $expected = ['message' => $message];
        if (! $connectionFailure)
        {
            $expected['ai_service_status'] = 503;
        }
        $this->postImage()->assertServiceUnavailable()->assertExactJson($expected);
        $this->assertNothingSaved();
    }

    //upstream validation and unexpected errors must not become successful demos
    #[DataProvider('nonFallbackErrors')]
    public function test_other_upstream_errors_preserve_status_and_message(int $upstream, int $expected, string $detail): void
    {
        Http::fake(['*/predict' => Http::response(['detail' => $detail], $upstream)]);
        Sanctum::actingAs(User::factory()->create());

        $this->postImage()->assertStatus($expected)->assertExactJson([
            'message' => $detail, 'ai_service_status' => $upstream,
        ]);
        $this->assertNothingSaved();
    }

    //authentication remains required even if the service cannot be reached
    public function test_fallback_does_not_bypass_authentication(): void
    {
        Http::fake(fn () => throw new ConnectionException('Connection refused'));
        $this->postImage()->assertUnauthorized();
        Http::assertNothingSent();
        $this->assertNothingSaved();
    }

    //invalid uploads are rejected before an upstream call or example response
    #[DataProvider('invalidUploads')]
    public function test_fallback_does_not_bypass_image_validation(string $kind): void
    {
        Http::fake(fn () => throw new ConnectionException('Connection refused'));
        Sanctum::actingAs(User::factory()->create());
        $image = match ($kind)
        {
            'text' => UploadedFile::fake()->create('notes.txt', 1, 'text/plain'),
            'oversized' => UploadedFile::fake()->image('large.jpg')->size(10241),
            default => null,
        };

        $this->post('/api/ai-predictions', ['image' => $image], ['Accept' => 'application/json'])
            ->assertUnprocessable()->assertJsonValidationErrors('image');
        Http::assertNothingSent();
        $this->assertNothingSaved();
    }

    //another owner's pet cannot be exposed through the demo response
    public function test_fallback_does_not_bypass_pet_ownership(): void
    {
        Http::fake(fn () => throw new ConnectionException('Connection refused'));
        $otherPet = User::factory()->create()->pets()->create([
            'name' => 'Private pet', 'species' => 'Cat', 'sex' => 'Unknown',
        ]);
        Sanctum::actingAs(User::factory()->create());

        $this->postImage(['pet_id' => $otherPet->id])->assertUnprocessable()
            ->assertJsonValidationErrors('pet_id');
        Http::assertNothingSent();
        $this->assertNothingSaved();
    }

    //a real response retains its existing fields and saves the result normally
    public function test_real_success_is_unchanged_when_fallback_is_enabled(): void
    {
        $prediction = [
            'species' => 'cat', 'breed' => 'Siamese', 'confidence' => 0.82,
            'top_predictions' => [
                ['breed' => 'Siamese', 'confidence' => 0.82],
                ['breed' => 'Birman', 'confidence' => 0.11],
                ['breed' => 'Ragdoll', 'confidence' => 0.07],
            ],
            'model_version' => 'test-real-model',
        ];
        Http::fake(['*/predict' => Http::response($prediction)]);
        $owner = User::factory()->create();
        Sanctum::actingAs($owner);

        $response = $this->postImage()->assertCreated()->assertJsonMissingPath('demo')
            ->assertJsonMissingPath('message')->assertJsonMissingPath('image_path')
            ->assertJsonPath('disclaimer', 'Breed recognition is an estimate and is not veterinary advice.');
        foreach ($prediction as $field => $value)
        {
            $response->assertJsonPath($field, $value);
        }
        $saved = $owner->predictions()->firstOrFail();
        $response->assertExactJson($saved->load('pet:id,name')->toArray());
        $this->assertDatabaseCount('ai_predictions', 1);
        Storage::disk('public')->assertExists($saved->image_path);
        $this->assertCount(1, Storage::disk('public')->allFiles());
        Http::assertSentCount(1);
    }

    //an incomplete successful response is still an error, not a demo
    public function test_invalid_success_payload_does_not_trigger_fallback(): void
    {
        Http::fake(['*/predict' => Http::response(['species' => 'cat'])]);
        Sanctum::actingAs(User::factory()->create());

        $this->postImage()->assertStatus(502)->assertExactJson([
            'message' => 'The AI service returned an invalid response.',
        ]);
        $this->assertNothingSaved();
    }

    //send a small valid file with the same multipart field used by android
    private function postImage(array $fields = [])
    {
        return $this->post('/api/ai-predictions', [
            'image' => UploadedFile::fake()->image('pet.jpg', 40, 40), ...$fields,
        ], ['Accept' => 'application/json']);
    }

    //no placeholder result may enter history or leave an uploaded file behind
    private function assertNothingSaved(): void
    {
        $this->assertDatabaseCount('ai_predictions', 0);
        $this->assertSame([], Storage::disk('public')->allFiles());
    }

    public static function connectionFailures(): array
    {
        return [['Connection refused'], ['cURL error 28: Operation timed out']];
    }

    public static function missingModelMessages(): array
    {
        return [['No trained model was found.'], ['model_missing'], ['The model file is missing.']];
    }

    public static function disabledFailures(): array
    {
        return [
            [true, 'The AI service is not running. Start FastAPI on port 8001 and try again.'],
            [false, 'No trained model was found.'],
        ];
    }

    public static function nonFallbackErrors(): array
    {
        return [
            [422, 422, 'No cat or dog was detected.'],
            [413, 413, 'The uploaded image is too large.'],
            [415, 415, 'Unsupported image format.'],
            [503, 503, 'The model is busy. Try again later.'],
            [500, 502, 'No trained model was found.'],
            [401, 502, 'Service authorization failed.'],
        ];
    }

    public static function invalidUploads(): array
    {
        return [['text'], ['oversized'], ['missing']];
    }
}
