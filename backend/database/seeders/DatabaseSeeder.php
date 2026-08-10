<?php

namespace Database\Seeders;

use App\Models\Appointment;
use App\Models\MedicalRecord;
use App\Models\PetBreed;
use App\Models\User;
use App\Models\Vaccination;
use App\Models\VaccinationRecord;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;

class DatabaseSeeder extends Seeder
{
    public function run(): void
    {
        $breeds = [['Golden Retriever', 'Dog'], ['Poodle', 'Dog'], ['German Shepherd', 'Dog'], ['British Shorthair', 'Cat'], ['Maine Coon', 'Cat'], ['Siamese', 'Cat']];
        foreach ($breeds as [$name, $species]) PetBreed::firstOrCreate(compact('name', 'species'));
        foreach ([['Rabies', 'Dog', 12], ['DHPP', 'Dog', 12], ['Rabies', 'Cat', 12], ['FVRCP', 'Cat', 12]] as [$name, $species, $months]) Vaccination::firstOrCreate(compact('name', 'species'), ['recommended_interval_months' => $months]);

        $user = User::firstOrCreate(['email' => 'demo@pawcare.my'], ['name' => 'Aina Rahman', 'password' => Hash::make('PawCare123')]);
        if ($user->pets()->exists()) return;
        $buddy = $user->pets()->create(['name' => 'Buddy', 'species' => 'Dog', 'breed' => 'Golden Retriever', 'sex' => 'Male', 'birth_date' => '2022-04-16', 'weight_kg' => 28.4, 'microchip_number' => 'MY-DOG-20481']);
        $luna = $user->pets()->create(['name' => 'Luna', 'species' => 'Cat', 'breed' => 'British Shorthair', 'sex' => 'Female', 'birth_date' => '2023-09-08', 'weight_kg' => 4.7]);
        VaccinationRecord::create(['pet_id' => $buddy->id, 'vaccine_name' => 'DHPP Booster', 'administered_date' => now()->subMonths(11), 'due_date' => now()->addDays(12), 'clinic' => 'Happy Tails Veterinary', 'status' => 'Upcoming']);
        VaccinationRecord::create(['pet_id' => $luna->id, 'vaccine_name' => 'FVRCP', 'administered_date' => now()->subMonths(11), 'due_date' => now()->addMonth(), 'clinic' => 'Paws & Claws Clinic', 'status' => 'Upcoming']);
        MedicalRecord::create(['pet_id' => $buddy->id, 'visit_date' => now()->subMonths(2), 'veterinarian' => 'Dr. Lim Wei', 'diagnosis' => 'Mild dermatitis', 'treatment' => 'Medicated shampoo for 14 days', 'notes' => 'Symptoms resolved.']);
        Appointment::create(['user_id' => $user->id, 'pet_id' => $buddy->id, 'appointment_date' => now()->addDays(5), 'appointment_time' => '10:30', 'clinic' => 'Happy Tails Veterinary', 'reason' => 'Annual wellness examination', 'status' => 'Scheduled']);
    }
}
