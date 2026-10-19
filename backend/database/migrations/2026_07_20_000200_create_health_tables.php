<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

//creates the health tables: vaccine list, veterinarians, vaccinations, medical records and appointments
//each health record belongs to one pet (pet_id); deleting the pet deletes its health records
return new class extends Migration
{
    //3.process

    //create the health tables
    public function up(): void
    {
        //the list of known vaccines, for example "Rabies" for "Dog", repeated every 12 months
        Schema::create('vaccinations', function (Blueprint $table)
        {
            $table->id();
            $table->string('name');
            $table->string('species', 20);
            $table->text('description')->nullable();
            $table->unsignedSmallInteger('recommended_interval_months')->nullable();
            $table->timestamps();
            $table->unique(['name', 'species']);                  //the same vaccine cannot be listed twice
        });

        //veterinarian contact details
        Schema::create('veterinarians', function (Blueprint $table)
        {
            $table->id();
            $table->string('name');
            $table->string('clinic_name')->nullable();
            $table->string('phone', 40)->nullable();
            $table->string('email')->nullable();
            $table->text('address')->nullable();
            $table->timestamps();
        });

        //one row per vaccine dose, given or due
        Schema::create('vaccination_records', function (Blueprint $table)
        {
            $table->id();
            //FK to pets.id; deleting the pet deletes this record
            $table->foreignId('pet_id')->constrained()->cascadeOnDelete();
            //optional FK to the vaccine list
            $table->foreignId('vaccination_id')->nullable()->constrained()->nullOnDelete();
            $table->string('vaccine_name', 150);
            $table->date('administered_date')->nullable();        //empty when not given yet
            $table->date('due_date');
            $table->string('clinic', 150)->nullable();
            $table->string('status', 20)->default('Upcoming');    //Upcoming, Completed or Overdue
            $table->text('notes')->nullable();
            $table->timestamps();
            $table->index(['pet_id', 'due_date']);                //makes "due soon" lists fast
        });

        //one row per clinic visit outcome
        Schema::create('medical_records', function (Blueprint $table)
        {
            $table->id();
            //FK to pets.id; deleting the pet deletes this record
            $table->foreignId('pet_id')->constrained()->cascadeOnDelete();
            $table->foreignId('veterinarian_id')->nullable()->constrained()->nullOnDelete();
            $table->date('visit_date');
            $table->string('veterinarian', 150)->nullable();
            $table->string('diagnosis', 500);
            $table->text('treatment')->nullable();
            $table->text('notes')->nullable();
            $table->timestamps();
            $table->index(['pet_id', 'visit_date']);
        });

        //one row per clinic visit booked by an owner for a pet
        Schema::create('appointments', function (Blueprint $table)
        {
            $table->id();
            //an appointment links to both the owner and the pet
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('pet_id')->constrained()->cascadeOnDelete();
            $table->foreignId('veterinarian_id')->nullable()->constrained()->nullOnDelete();
            $table->date('appointment_date');
            $table->time('appointment_time');
            $table->string('clinic', 150)->nullable();
            $table->string('reason', 500);
            $table->string('status', 20)->default('Scheduled');   //Scheduled, Completed or Cancelled
            $table->text('notes')->nullable();
            $table->timestamps();
            $table->index(['user_id', 'appointment_date']);
        });
    }

    //remove the health tables, last created first
    public function down(): void
    {
        Schema::dropIfExists('appointments');
        Schema::dropIfExists('medical_records');
        Schema::dropIfExists('vaccination_records');
        Schema::dropIfExists('veterinarians');
        Schema::dropIfExists('vaccinations');
    }
};
