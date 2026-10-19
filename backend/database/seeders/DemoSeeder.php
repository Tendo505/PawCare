<?php

namespace Database\Seeders;

use App\Models\Pet;
use App\Models\PetBreed;
use App\Models\User;
use App\Models\Vaccination;
use Illuminate\Database\Seeder;
use Illuminate\Support\Carbon;

//creates the fictional demo account demo@pawcare.test with two pets and their records
//it never replaces existing data: firstOrCreate only adds what is missing
class DemoSeeder extends Seeder
{
    //3.process
    public function run(): void
    {
        $today = today();

        //the User model hashes the password; an existing demo account keeps its own password
        $owner = User::firstOrCreate(
            ['email' => 'demo@pawcare.test'],
            ['name' => 'PawCare Demo', 'password' => 'password123']
        );

        $cat = $this->addPet($owner, 'Milo', 'Cat', 'Siamese', 'Male', $today->copy()->subYears(3), 4.2);
        $dog = $this->addPet($owner, 'Buddy', 'Dog', 'Golden Retriever', 'Female', $today->copy()->subYears(2), 24.5);

        //vaccinations: days are counted from today, so the demo always shows one due soon and one overdue
        $this->addVaccination($cat, 'FVRCP', null, 14, 'Upcoming', $today);
        $this->addVaccination($cat, 'Rabies', -365, -7, 'Overdue', $today);
        $this->addVaccination($dog, 'DHPP', -14, 351, 'Completed', $today);

        //one upcoming visit for the home reminders and one completed visit
        $this->addAppointment($owner, $cat, 'Demo wellness check', 3, '10:00', 'Scheduled', $today);
        $this->addAppointment($owner, $dog, 'Demo follow-up visit', -10, '14:30', 'Completed', $today);

        //fake medical records, not treatment advice
        $this->addMedicalRecord($cat, 'Demo routine health check', -30, $today);
        $this->addMedicalRecord($dog, 'Demo follow-up assessment', -10, $today);
    }

    //add a pet for the owner, found again by name and species on later runs
    private function addPet(User $owner, string $name, string $species, string $breed, string $sex, Carbon $birthDate, float $weightKg): Pet
    {
        $breedId = PetBreed::where('name', $breed)->where('species', $species)->value('id');

        return $owner->pets()->firstOrCreate(
            ['name' => $name, 'species' => $species],
            [
                'pet_breed_id' => $breedId,
                'breed' => $breed,
                'sex' => $sex,
                'birth_date' => $birthDate->toDateString(),
                'weight_kg' => $weightKg,
                'notes' => 'Fictional '.strtolower($species).' for the PawCare demonstration.'
            ]
        );
    }

    //add a vaccination; $givenDays is null when the dose has not been given yet
    private function addVaccination(Pet $pet, string $vaccine, ?int $givenDays, int $dueDays, string $status, Carbon $today): void
    {
        $givenDate = null;
        if ($givenDays !== null)
        {
            $givenDate = $today->copy()->addDays($givenDays)->toDateString();
        }
        $vaccineId = Vaccination::where('name', $vaccine)->where('species', $pet->species)->value('id');

        $pet->vaccinationRecords()->firstOrCreate(
            ['vaccine_name' => $vaccine],
            [
                'vaccination_id' => $vaccineId,
                'administered_date' => $givenDate,
                'due_date' => $today->copy()->addDays($dueDays)->toDateString(),
                'clinic' => 'PawCare Demo Clinic',
                'status' => $status,
                'notes' => 'Fictional vaccination record for demonstration only.'
            ]
        );
    }

    //add an appointment, found again by pet and reason on later runs
    private function addAppointment(User $owner, Pet $pet, string $reason, int $days, string $time, string $status, Carbon $today): void
    {
        $owner->appointments()->firstOrCreate(
            ['pet_id' => $pet->id, 'reason' => $reason],
            [
                'appointment_date' => $today->copy()->addDays($days)->toDateString(),
                'appointment_time' => $time,
                'clinic' => 'PawCare Demo Clinic',
                'status' => $status,
                'notes' => 'Fictional appointment; no clinic booking has been made.'
            ]
        );
    }

    //add a medical record, found again by diagnosis on later runs
    private function addMedicalRecord(Pet $pet, string $diagnosis, int $days, Carbon $today): void
    {
        $pet->medicalRecords()->firstOrCreate(
            ['diagnosis' => $diagnosis],
            [
                'visit_date' => $today->copy()->addDays($days)->toDateString(),
                'veterinarian' => 'Demo Veterinarian',
                'treatment' => 'Sample consultation record for the application demonstration.',
                'notes' => 'Fictional medical record; not veterinary advice.'
            ]
        );
    }
}
