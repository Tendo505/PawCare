<?php

namespace Tests\Feature;

use App\Models\Appointment;
use App\Models\MedicalRecord;
use App\Models\Pet;
use App\Models\User;
use App\Models\VaccinationRecord;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class DemoSeederTest extends TestCase
{
    use RefreshDatabase;

    //check the main seeder creates a complete fake account after the catalogue
    public function test_database_seeder_creates_the_expected_demo_records(): void
    {
        $this->travelTo(now()->setDate(2026, 10, 18)->startOfDay());
        $this->seed(DatabaseSeeder::class);

        $owner = User::where('email', 'demo@pawcare.test')->firstOrFail();
        $cat = $owner->pets()->where('species', 'Cat')->firstOrFail();
        $dog = $owner->pets()->where('species', 'Dog')->firstOrFail();

        $this->assertTrue(Hash::check('password123', $owner->password));
        $this->assertNotSame('password123', $owner->password);
        $this->assertDatabaseCount('users', 1);
        $this->assertDatabaseCount('pets', 2);
        $this->assertDatabaseCount('vaccination_records', 3);
        $this->assertDatabaseCount('appointments', 2);
        $this->assertDatabaseCount('medical_records', 2);
        $this->assertDatabaseCount('pet_breeds', 6);
        $this->assertDatabaseCount('vaccinations', 4);
        $this->assertSame('Milo', $cat->name);
        $this->assertSame('Buddy', $dog->name);
        $this->assertSame('Siamese', $cat->petBreed->name);
        $this->assertSame('Golden Retriever', $dog->petBreed->name);
        $this->assertSame(0, VaccinationRecord::whereNull('vaccination_id')->count());
        $this->assertDatabaseHas('vaccination_records', [
            'pet_id' => $cat->id,
            'vaccine_name' => 'FVRCP',
            'administered_date' => null,
            'due_date' => '2026-11-01',
            'status' => 'Upcoming'
        ]);
        $this->assertDatabaseHas('vaccination_records', [
            'pet_id' => $cat->id,
            'vaccine_name' => 'Rabies',
            'due_date' => '2026-10-11',
            'status' => 'Overdue'
        ]);
        $this->assertDatabaseHas('appointments', [
            'user_id' => $owner->id,
            'pet_id' => $cat->id,
            'appointment_date' => '2026-10-21',
            'status' => 'Scheduled'
        ]);
        $this->assertDatabaseHas('appointments', [
            'user_id' => $owner->id,
            'pet_id' => $dog->id,
            'appointment_date' => '2026-10-08',
            'status' => 'Completed'
        ]);
        $this->assertSame(1, $cat->medicalRecords()->count());
        $this->assertSame(1, $dog->medicalRecords()->count());
    }

    //rerunning on another day must preserve saved records and their identifiers
    public function test_repeated_seeding_does_not_duplicate_or_reset_demo_records(): void
    {
        $this->seed(DatabaseSeeder::class);
        $owner = User::where('email', 'demo@pawcare.test')->firstOrFail();
        $owner->update(['name' => 'Edited Demo Owner', 'password' => 'changed-password123']);
        $cat = $owner->pets()->where('species', 'Cat')->firstOrFail();
        $cat->update(['notes' => 'Keep this edited note.']);
        $models = [User::class, Pet::class, VaccinationRecord::class, Appointment::class, MedicalRecord::class];
        $before = [];

        foreach ($models as $model)
        {
            $before[$model] = $model::orderBy('id')->get()->toArray();
        }

        $this->travel(30)->days();
        $this->seed(DatabaseSeeder::class);

        foreach ($models as $model)
        {
            $this->assertSame($before[$model], $model::orderBy('id')->get()->toArray());
        }

        $this->assertTrue(Hash::check('changed-password123', $owner->fresh()->password));
        $this->assertDatabaseCount('pet_breeds', 6);
        $this->assertDatabaseCount('vaccinations', 4);
    }

    //matching pet names under another account must remain untouched
    public function test_seeding_preserves_unrelated_accounts_and_care_records(): void
    {
        $other = User::factory()->create();
        $pet = $other->pets()->create(['name' => 'Milo', 'species' => 'Cat', 'sex' => 'Unknown']);
        $vaccination = $pet->vaccinationRecords()->create([
            'vaccine_name' => 'FVRCP', 'due_date' => '2099-09-10', 'status' => 'Upcoming'
        ]);
        $appointment = $other->appointments()->create([
            'pet_id' => $pet->id, 'appointment_date' => '2099-09-10',
            'appointment_time' => '10:00', 'reason' => 'Demo wellness check', 'status' => 'Scheduled'
        ]);
        $medical = $pet->medicalRecords()->create([
            'visit_date' => '2020-01-01', 'diagnosis' => 'Demo routine health check'
        ]);
        $records = [$other, $pet, $vaccination, $appointment, $medical];
        $before = array_map(fn ($record) => $record->fresh()->getAttributes(), $records);

        $this->seed(DatabaseSeeder::class);
        $this->seed(DatabaseSeeder::class);

        foreach ($records as $index => $record)
        {
            $this->assertSame($before[$index], $record->fresh()->getAttributes());
        }

        $this->assertDatabaseCount('users', 2);
        $this->assertDatabaseCount('pets', 3);
        $this->assertDatabaseCount('vaccination_records', 4);
        $this->assertDatabaseCount('appointments', 3);
        $this->assertDatabaseCount('medical_records', 3);
    }
}
