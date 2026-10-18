<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    //create the pet catalogue, profiles and image tables
    public function up(): void
    {
        //store the available pet breeds
        Schema::create('pet_breeds', function (Blueprint $table)
        {
            $table->id();
            $table->string('name');
            $table->string('species', 20);
            $table->text('description')->nullable();
            $table->timestamps();
            $table->unique(['name', 'species']);
        });

        //store each pet profile and its owner
        Schema::create('pets', function (Blueprint $table)
        {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('pet_breed_id')->nullable()->constrained()->nullOnDelete();
            $table->string('name', 100);
            $table->string('species', 20);
            $table->string('breed', 100)->nullable();
            $table->string('sex', 20)->default('Unknown');
            $table->date('birth_date')->nullable();
            $table->decimal('weight_kg', 6, 2)->nullable();
            $table->string('microchip_number', 100)->nullable();
            $table->text('notes')->nullable();
            $table->timestamps();
            $table->index(['user_id', 'name']);
        });

        //store image references linked to pets
        Schema::create('pet_images', function (Blueprint $table)
        {
            $table->id();
            $table->foreignId('pet_id')->constrained()->cascadeOnDelete();
            $table->string('path');
            $table->string('caption')->nullable();
            $table->boolean('is_profile')->default(false);
            $table->timestamps();
        });
    }

    //remove the pet tables in reverse dependency order
    public function down(): void
    {
        Schema::dropIfExists('pet_images');
        Schema::dropIfExists('pets');
        Schema::dropIfExists('pet_breeds');
    }
};
