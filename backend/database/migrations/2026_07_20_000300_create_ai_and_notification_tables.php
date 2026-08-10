<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        Schema::create('notifications', function (Blueprint $table) { $table->id(); $table->foreignId('user_id')->constrained()->cascadeOnDelete(); $table->string('type', 50); $table->string('title'); $table->text('message'); $table->timestamp('scheduled_at')->nullable(); $table->timestamp('read_at')->nullable(); $table->timestamps(); $table->index(['user_id', 'scheduled_at']); });
        Schema::create('ai_predictions', function (Blueprint $table) {
            $table->id(); $table->foreignId('user_id')->constrained()->cascadeOnDelete(); $table->foreignId('pet_id')->nullable()->constrained()->nullOnDelete();
            $table->string('species', 20); $table->string('breed', 150); $table->decimal('confidence', 6, 5); $table->jsonb('top_predictions')->nullable(); $table->string('image_path'); $table->string('model_version', 100)->nullable(); $table->timestamps(); $table->index(['user_id', 'created_at']);
        });
    }
    public function down(): void { Schema::dropIfExists('ai_predictions'); Schema::dropIfExists('notifications'); }
};
