<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

//creates the pet tables: the breed list, pet profiles and pet photos
//1 owner (users) has many pets, so pets has a user_id column
return new class extends Migration
{
    //3.process

    //create the pet tables
    public function up(): void
    {
        //the list of known breeds, for example "Siamese" for "Cat"
        Schema::create('pet_breeds', function (Blueprint $table)
        {
            $table->id();
            $table->string('name');
            $table->string('species', 20);
            $table->text('description')->nullable();
            $table->timestamps();
            $table->unique(['name', 'species']);                  //the same breed cannot be listed twice
        });

        //one row per pet profile
        Schema::create('pets', function (Blueprint $table)
        {
            $table->id();
            //foreign key (FK) to users.id; deleting the owner also deletes their pets
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            //optional FK to pet_breeds.id; deleting a breed only empties this column
            $table->foreignId('pet_breed_id')->nullable()->constrained()->nullOnDelete();
            $table->string('name', 100);
            $table->string('species', 20);                        //"Dog" or "Cat"
            $table->string('breed', 100)->nullable();
            $table->string('sex', 20)->default('Unknown');
            $table->date('birth_date')->nullable();
            $table->decimal('weight_kg', 6, 2)->nullable();       //up to 6 digits, 2 after the dot
            $table->string('microchip_number', 100)->nullable();
            $table->text('notes')->nullable();
            $table->timestamps();
            $table->index(['user_id', 'name']);                   //makes "my pets by name" fast
        });

        //photos saved for a pet
        Schema::create('pet_images', function (Blueprint $table)
        {
            $table->id();
            //FK to pets.id; deleting the pet also deletes its photos
            $table->foreignId('pet_id')->constrained()->cascadeOnDelete();
            $table->string('path');
            $table->string('caption')->nullable();
            $table->boolean('is_profile')->default(false);
            $table->timestamps();
        });
    }

    //remove the pet tables, last created first (a table must go before the table it points to)
    public function down(): void
    {
        Schema::dropIfExists('pet_images');
        Schema::dropIfExists('pets');
        Schema::dropIfExists('pet_breeds');
    }
};
