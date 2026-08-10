<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        Schema::create('vaccinations', function (Blueprint $table) { $table->id(); $table->string('name'); $table->string('species', 20); $table->text('description')->nullable(); $table->unsignedSmallInteger('recommended_interval_months')->nullable(); $table->timestamps(); $table->unique(['name', 'species']); });
        Schema::create('veterinarians', function (Blueprint $table) { $table->id(); $table->string('name'); $table->string('clinic_name')->nullable(); $table->string('phone', 40)->nullable(); $table->string('email')->nullable(); $table->text('address')->nullable(); $table->timestamps(); });
        Schema::create('vaccination_records', function (Blueprint $table) {
            $table->id(); $table->foreignId('pet_id')->constrained()->cascadeOnDelete(); $table->foreignId('vaccination_id')->nullable()->constrained()->nullOnDelete();
            $table->string('vaccine_name', 150); $table->date('administered_date')->nullable(); $table->date('due_date'); $table->string('clinic', 150)->nullable(); $table->string('status', 20)->default('Upcoming'); $table->text('notes')->nullable(); $table->timestamps(); $table->index(['pet_id', 'due_date']);
        });
        Schema::create('medical_records', function (Blueprint $table) {
            $table->id(); $table->foreignId('pet_id')->constrained()->cascadeOnDelete(); $table->foreignId('veterinarian_id')->nullable()->constrained()->nullOnDelete();
            $table->date('visit_date'); $table->string('veterinarian', 150)->nullable(); $table->string('diagnosis', 500); $table->text('treatment')->nullable(); $table->text('notes')->nullable(); $table->timestamps(); $table->index(['pet_id', 'visit_date']);
        });
        Schema::create('appointments', function (Blueprint $table) {
            $table->id(); $table->foreignId('user_id')->constrained()->cascadeOnDelete(); $table->foreignId('pet_id')->constrained()->cascadeOnDelete(); $table->foreignId('veterinarian_id')->nullable()->constrained()->nullOnDelete();
            $table->date('appointment_date'); $table->time('appointment_time'); $table->string('clinic', 150)->nullable(); $table->string('reason', 500); $table->string('status', 20)->default('Scheduled'); $table->text('notes')->nullable(); $table->timestamps(); $table->index(['user_id', 'appointment_date']);
        });
    }
    public function down(): void { Schema::dropIfExists('appointments'); Schema::dropIfExists('medical_records'); Schema::dropIfExists('vaccination_records'); Schema::dropIfExists('veterinarians'); Schema::dropIfExists('vaccinations'); }
};
