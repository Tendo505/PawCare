<?php

namespace Database\Seeders;

use App\Models\PetBreed;
use App\Models\Vaccination;
use Illuminate\Database\Seeder;

class DatabaseSeeder extends Seeder
{
    //add the shared breed and vaccine catalogue without duplicating entries
    public function run(): void
    {
        //prepare the dog and cat breed catalogue
        $breeds = [
            ['Golden Retriever', 'Dog'],
            ['Poodle', 'Dog'],
            ['German Shepherd', 'Dog'],
            ['British Shorthair', 'Cat'],
            ['Maine Coon', 'Cat'],
            ['Siamese', 'Cat']
        ];

        //create only breed entries that are not already stored
        foreach ($breeds as [$name, $species])
        {
            PetBreed::firstOrCreate(compact('name', 'species'));
        }

        //create only vaccine entries that are not already stored
        foreach ([
            ['Rabies', 'Dog', 12],
            ['DHPP', 'Dog', 12],
            ['Rabies', 'Cat', 12],
            ['FVRCP', 'Cat', 12]
        ] as [$name, $species, $months])
        {
            Vaccination::firstOrCreate(
                compact('name', 'species'),
                ['recommended_interval_months' => $months]
            );
        }

        //add the sample account and records after the shared catalogue
        $this->call(DemoSeeder::class);
    }
}
