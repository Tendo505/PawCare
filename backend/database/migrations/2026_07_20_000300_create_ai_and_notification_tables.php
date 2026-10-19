<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

//creates the reminder messages table and the saved breed scans table
return new class extends Migration
{
    //3.process

    //create the reminder and breed scan tables
    public function up(): void
    {
        //reminder messages for an owner, and whether they have been read
        Schema::create('notifications', function (Blueprint $table)
        {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('type', 50);
            $table->string('title');
            $table->text('message');
            $table->timestamp('scheduled_at')->nullable();
            $table->timestamp('read_at')->nullable();             //empty until the owner reads it
            $table->timestamps();
            $table->index(['user_id', 'scheduled_at']);
        });

        //one row per saved breed scan
        Schema::create('ai_predictions', function (Blueprint $table)
        {
            $table->id();
            //FK to users.id; deleting the owner deletes their scans
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            //optional FK to pets.id; deleting the pet keeps the scan but empties this column
            $table->foreignId('pet_id')->nullable()->constrained()->nullOnDelete();
            $table->string('species', 20);                        //"cat" or "dog"
            $table->string('breed', 150);
            $table->decimal('confidence', 6, 5);                  //0 to 1, for example 0.99123
            $table->jsonb('top_predictions')->nullable();         //the 3 best breeds, stored as json
            $table->string('image_path');                         //where the photo is saved on the server
            $table->string('model_version', 100)->nullable();
            $table->timestamps();
            $table->index(['user_id', 'created_at']);             //makes "newest scans first" fast
        });
    }

    //remove the breed scan and reminder tables
    public function down(): void
    {
        Schema::dropIfExists('ai_predictions');
        Schema::dropIfExists('notifications');
    }
};
