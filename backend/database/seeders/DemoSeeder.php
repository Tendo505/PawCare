<?php

namespace Database\Seeders;

use App\Models\PetBreed;
use App\Models\User;
use App\Models\Vaccination;
use Illuminate\Database\Seeder;

class DemoSeeder extends Seeder
{
    //create fake presentation records without replacing existing data
    public function run(): void
    {
        $today = today();
        $owner = User::firstOrCreate(
            ['email' => 'demo@pawcare.test'],
            ['name' => 'PawCare Demo', 'password' => 'password123']
        );

        //the user model hashes the password and existing accounts keep their password
        $cat = $owner->pets()->firstOrCreate(
            ['name' => 'Milo', 'species' => 'Cat'],
            [
                'pet_breed_id' => PetBreed::where('name', 'Siamese')->where('species', 'Cat')->value('id'),
                'breed' => 'Siamese',
                'sex' => 'Male',
                'birth_date' => $today->copy()->subYears(3)->toDateString(),
                'weight_kg' => 4.2,
                'notes' => 'Fictional cat for the PawCare demonstration.'
            ]
        );
        $dog = $owner->pets()->firstOrCreate(
            ['name' => 'Buddy', 'species' => 'Dog'],
            [
                'pet_breed_id' => PetBreed::where('name', 'Golden Retriever')->where('species', 'Dog')->value('id'),
                'breed' => 'Golden Retriever',
                'sex' => 'Female',
                'birth_date' => $today->copy()->subYears(2)->toDateString(),
                'weight_kg' => 24.5,
                'notes' => 'Fictional dog for the PawCare demonstration.'
            ]
        );

        //stable lookup fields prevent duplicates even when seeding on a later day
        foreach ([
            [$cat, 'FVRCP', null, 14, 'Upcoming'],
            [$cat, 'Rabies', -365, -7, 'Overdue'],
            [$dog, 'DHPP', -14, 351, 'Completed']
        ] as [$pet, $vaccine, $administeredDays, $dueDays, $status])
        {
            $pet->vaccinationRecords()->firstOrCreate(
                ['vaccine_name' => $vaccine],
                [
                    'vaccination_id' => Vaccination::where('name', $vaccine)->where('species', $pet->species)->value('id'),
                    'administered_date' => $administeredDays === null ? null : $today->copy()->addDays($administeredDays)->toDateString(),
                    'due_date' => $today->copy()->addDays($dueDays)->toDateString(),
                    'clinic' => 'PawCare Demo Clinic',
                    'status' => $status,
                    'notes' => 'Fictional vaccination record for demonstration only.'
                ]
            );
        }

        //include an upcoming visit and a completed visit for the dashboard demonstration
        foreach ([
            [$cat, 'Demo wellness check', 3, '10:00', 'Scheduled'],
            [$dog, 'Demo follow-up visit', -10, '14:30', 'Completed']
        ] as [$pet, $reason, $days, $time, $status])
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

        //medical examples are fake records rather than treatment advice
        foreach ([
            [$cat, 'Demo routine health check', -30],
            [$dog, 'Demo follow-up assessment', -10]
        ] as [$pet, $diagnosis, $days])
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
}
