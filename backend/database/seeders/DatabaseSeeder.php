<?php

namespace Database\Seeders;

use App\Models\PetBreed;
use App\Models\Vaccination;
use Illuminate\Database\Seeder;

//fills the shared breed and vaccine lists, then adds the demo account (php artisan db:seed)
//firstOrCreate only adds a row when it is missing, so running this twice never makes duplicates
class DatabaseSeeder extends Seeder
{
    //3.process
    public function run(): void
    {
        //the dog and cat breed list
        $this->addBreed('Golden Retriever', 'Dog');
        $this->addBreed('Poodle', 'Dog');
        $this->addBreed('German Shepherd', 'Dog');
        $this->addBreed('British Shorthair', 'Cat');
        $this->addBreed('Maine Coon', 'Cat');
        $this->addBreed('Siamese', 'Cat');

        //the vaccine list, each repeated every 12 months
        $this->addVaccine('Rabies', 'Dog', 12);
        $this->addVaccine('DHPP', 'Dog', 12);
        $this->addVaccine('Rabies', 'Cat', 12);
        $this->addVaccine('FVRCP', 'Cat', 12);

        //the demo account and its records come after the lists they link to
        $this->call(DemoSeeder::class);
    }

    //add one breed when it is not stored yet
    private function addBreed(string $name, string $species): void
    {
        PetBreed::firstOrCreate(['name' => $name, 'species' => $species]);
    }

    //add one vaccine when it is not stored yet
    private function addVaccine(string $name, string $species, int $months): void
    {
        Vaccination::firstOrCreate(
            ['name' => $name, 'species' => $species],
            ['recommended_interval_months' => $months]
        );
    }
}
