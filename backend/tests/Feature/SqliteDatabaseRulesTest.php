<?php

namespace Tests\Feature;

use App\Models\AiPrediction;
use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Database\QueryException;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use PHPUnit\Framework\Attributes\DataProvider;
use Tests\TestCase;

class SqliteDatabaseRulesTest extends TestCase
{
    use RefreshDatabase;

    //check raw inserts and updates cannot bypass the same PostgreSQL rules
    #[DataProvider('invalidValues')]
    public function test_sqlite_checks_reject_invalid_values(string $table, array $values, string $constraint, string $operation): void
    {
        $this->seed(DatabaseSeeder::class);
        $this->createPrediction();
        $record = (array) DB::table($table)->first();

        $this->expectException(QueryException::class);
        $this->expectExceptionMessage($constraint);

        if ($operation === 'update')
        {
            DB::table($table)->where('id', $record['id'])->update($values);
            return;
        }

        unset($record['id']);
        DB::table($table)->insert(array_replace($record, $values));
    }

    public static function invalidValues(): array
    {
        $checks = [
            ['pets', ['species' => 'Human'], 'pets_species_check'],
            ['pets', ['sex' => 'Invalid'], 'pets_sex_check'],
            ['pets', ['weight_kg' => -1], 'pets_weight_kg_check'],
            ['vaccination_records', ['status' => 'Invalid'], 'vaccination_records_status_check'],
            ['vaccination_records', ['administered_date' => '2099-01-02', 'due_date' => '2099-01-01'], 'vaccination_records_dates_check'],
            ['appointments', ['status' => 'Invalid'], 'appointments_status_check'],
            ['ai_predictions', ['confidence' => -0.01], 'ai_predictions_confidence_check'],
            ['ai_predictions', ['confidence' => 1.01], 'ai_predictions_confidence_check'],
        ];
        $cases = [];

        foreach ($checks as $index => $check)
        {
            foreach (['insert', 'update'] as $operation)
            {
                $cases[$index.' '.$operation] = [...$check, $operation];
            }
        }

        return $cases;
    }

    //case differences must not create a second account outside the controller
    public function test_email_index_rejects_case_only_duplicates(): void
    {
        $this->seed(DatabaseSeeder::class);
        $this->expectException(QueryException::class);
        DB::table('users')->insert([
            'name' => 'Duplicate demo', 'email' => 'DEMO@PAWCARE.TEST', 'password' => 'not-a-real-password'
        ]);
    }

    //completed visits may share a time but cannot both become scheduled
    public function test_partial_index_rejects_a_second_active_appointment(): void
    {
        $this->seed(DatabaseSeeder::class);
        $appointment = (array) DB::table('appointments')->where('status', 'Scheduled')->first();
        unset($appointment['id']);
        $appointment['status'] = 'Completed';
        $duplicate = DB::table('appointments')->insertGetId($appointment);

        $this->assertDatabaseHas('appointments', ['id' => $duplicate, 'status' => 'Completed']);
        $this->expectException(QueryException::class);
        DB::table('appointments')->where('id', $duplicate)->update(['status' => 'Scheduled']);
    }

    //nullable catalogue links and child deletion keep their existing behaviour
    public function test_foreign_keys_preserve_null_and_cascade_actions(): void
    {
        $this->seed(DatabaseSeeder::class);
        $prediction = $this->createPrediction();
        $pet = $prediction->pet;
        $vaccination = $pet->vaccinationRecords()->firstOrFail();

        $this->assertSame(1, (int) DB::selectOne('PRAGMA foreign_keys')->foreign_keys);
        DB::table('pet_breeds')->where('id', $pet->pet_breed_id)->delete();
        DB::table('vaccinations')->where('id', $vaccination->vaccination_id)->delete();
        $this->assertNull($pet->fresh()->pet_breed_id);
        $this->assertNull($vaccination->fresh()->vaccination_id);

        $pet->delete();
        $this->assertDatabaseMissing('vaccination_records', ['pet_id' => $pet->id]);
        $this->assertDatabaseMissing('medical_records', ['pet_id' => $pet->id]);
        $this->assertDatabaseMissing('appointments', ['pet_id' => $pet->id]);
        $this->assertNull($prediction->fresh()->pet_id);
    }

    //SQLite text storage must return the same prediction array through the model
    public function test_prediction_json_and_nullable_values_round_trip(): void
    {
        $this->seed(DatabaseSeeder::class);
        $prediction = $this->createPrediction();
        $this->assertSame($prediction->top_predictions, $prediction->fresh()->top_predictions);

        $pet = $prediction->pet;
        $pet->update(['weight_kg' => null]);
        $this->assertNull($pet->fresh()->weight_kg);
        $record = $pet->vaccinationRecords()->firstOrFail();
        $record->update(['administered_date' => null]);
        $this->assertNull($record->fresh()->administered_date);
    }

    //use fictional structured predictions without reading or writing image files
    private function createPrediction(): AiPrediction
    {
        $owner = User::where('email', 'demo@pawcare.test')->firstOrFail();
        return AiPrediction::create([
            'user_id' => $owner->id,
            'pet_id' => $owner->pets()->firstOrFail()->id,
            'species' => 'Cat',
            'breed' => 'Siamese',
            'confidence' => 0.8,
            'top_predictions' => [['breed' => 'Siamese', 'confidence' => 0.8]],
            'image_path' => 'test-only/not-an-existing-image.jpg',
        ]);
    }
}
