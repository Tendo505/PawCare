<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

//a migration describes database tables in php; "php artisan migrate" runs up() to create them
//and "php artisan migrate:rollback" runs down() to remove them again
//this one creates the account tables: users, password resets, sessions and login tokens
return new class extends Migration
{
    //3.process

    //create the account tables
    public function up(): void
    {
        //one row per owner account
        Schema::create('users', function (Blueprint $table)
        {
            $table->id();                                         //id: the primary key (PK), 1, 2, 3...
            $table->string('name');
            $table->string('email')->unique();                    //no two accounts may share an email
            $table->timestamp('email_verified_at')->nullable();   //nullable: the value may be empty
            $table->string('password');                           //stored hashed, never as plain text
            $table->rememberToken();
            $table->timestamps();                                 //created_at and updated_at, filled in by laravel
        });

        //password reset codes (a standard laravel table)
        Schema::create('password_reset_tokens', function (Blueprint $table)
        {
            $table->string('email')->primary();
            $table->string('token');
            $table->timestamp('created_at')->nullable();
        });

        //browser sessions (a standard laravel table; the app uses tokens instead)
        Schema::create('sessions', function (Blueprint $table)
        {
            $table->string('id')->primary();
            $table->foreignId('user_id')->nullable()->index();
            $table->string('ip_address', 45)->nullable();
            $table->text('user_agent')->nullable();
            $table->longText('payload');
            $table->integer('last_activity')->index();
        });

        //sanctum login tokens: the app sends one with every signed-in request
        Schema::create('personal_access_tokens', function (Blueprint $table)
        {
            $table->id();
            $table->morphs('tokenable');                          //which account the token belongs to
            $table->string('name');
            $table->string('token', 64)->unique();                //only a hash of the token is stored
            $table->text('abilities')->nullable();
            $table->timestamp('last_used_at')->nullable();
            $table->timestamp('expires_at')->nullable()->index();
            $table->timestamps();
        });
    }

    //remove the account tables, last created first
    public function down(): void
    {
        Schema::dropIfExists('personal_access_tokens');
        Schema::dropIfExists('sessions');
        Schema::dropIfExists('password_reset_tokens');
        Schema::dropIfExists('users');
    }
};
