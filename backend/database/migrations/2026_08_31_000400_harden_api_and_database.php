<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

//adds database rules that protect the data even if someone bypasses the laravel api
//(for example by typing sql into pgadmin), plus indexes that make common lists fast
//postgresql uses CHECK constraints; sqlite (the demo database) uses triggers that do the same job
return new class extends Migration
{
    //3.process

    //add the rules for the database in use
    public function up(): void
    {
        if (DB::getDriverName() === 'sqlite')
        {
            $this->addSqliteRules();
            return;
        }
        if (DB::getDriverName() !== 'pgsql')
        {
            return;
        }

        //no two accounts may share an email, even as "Demo@x.test" and "demo@x.test"
        DB::statement('CREATE UNIQUE INDEX users_email_lower_unique ON users (LOWER(email))');

        //indexes for the reminder lists and the appointment list
        DB::statement('CREATE INDEX vaccination_records_status_due_index ON vaccination_records (status, due_date)');
        DB::statement('CREATE INDEX appointments_user_status_schedule_index ON appointments (user_id, status, appointment_date, appointment_time)');

        //one pet cannot have two scheduled visits at the same date and time
        DB::statement("CREATE UNIQUE INDEX appointments_unique_scheduled_slot ON appointments (user_id, pet_id, appointment_date, appointment_time) WHERE status = 'Scheduled'");

        //only allowed values can be saved (the same lists as the app's dropdowns)
        DB::statement("ALTER TABLE pets ADD CONSTRAINT pets_species_check CHECK (species IN ('Dog', 'Cat'))");
        DB::statement("ALTER TABLE pets ADD CONSTRAINT pets_sex_check CHECK (sex IN ('Male', 'Female', 'Unknown'))");
        DB::statement('ALTER TABLE pets ADD CONSTRAINT pets_weight_kg_check CHECK (weight_kg IS NULL OR weight_kg >= 0)');
        DB::statement("ALTER TABLE vaccination_records ADD CONSTRAINT vaccination_records_status_check CHECK (status IN ('Upcoming', 'Completed', 'Overdue'))");
        //a vaccine cannot be due before the day it was given
        DB::statement('ALTER TABLE vaccination_records ADD CONSTRAINT vaccination_records_dates_check CHECK (administered_date IS NULL OR due_date >= administered_date)');
        DB::statement("ALTER TABLE appointments ADD CONSTRAINT appointments_status_check CHECK (status IN ('Scheduled', 'Completed', 'Cancelled'))");
        //a breed score must be between 0 and 1
        DB::statement('ALTER TABLE ai_predictions ADD CONSTRAINT ai_predictions_confidence_check CHECK (confidence >= 0 AND confidence <= 1)');
    }

    //remove only the rules and indexes added above
    public function down(): void
    {
        if (DB::getDriverName() === 'sqlite')
        {
            $this->removeSqliteCheck('pets_species_check');
            $this->removeSqliteCheck('pets_sex_check');
            $this->removeSqliteCheck('pets_weight_kg_check');
            $this->removeSqliteCheck('vaccination_records_status_check');
            $this->removeSqliteCheck('vaccination_records_dates_check');
            $this->removeSqliteCheck('appointments_status_check');
            $this->removeSqliteCheck('ai_predictions_confidence_check');
            $this->dropIndexes();
            return;
        }
        if (DB::getDriverName() !== 'pgsql')
        {
            return;
        }

        DB::statement('ALTER TABLE ai_predictions DROP CONSTRAINT IF EXISTS ai_predictions_confidence_check');
        DB::statement('ALTER TABLE appointments DROP CONSTRAINT IF EXISTS appointments_status_check');
        DB::statement('ALTER TABLE vaccination_records DROP CONSTRAINT IF EXISTS vaccination_records_dates_check');
        DB::statement('ALTER TABLE vaccination_records DROP CONSTRAINT IF EXISTS vaccination_records_status_check');
        DB::statement('ALTER TABLE pets DROP CONSTRAINT IF EXISTS pets_weight_kg_check');
        DB::statement('ALTER TABLE pets DROP CONSTRAINT IF EXISTS pets_sex_check');
        DB::statement('ALTER TABLE pets DROP CONSTRAINT IF EXISTS pets_species_check');
        $this->dropIndexes();
    }

    //the same rules for sqlite: the indexes work the same way, and each CHECK becomes a trigger
    private function addSqliteRules(): void
    {
        DB::statement('CREATE UNIQUE INDEX users_email_lower_unique ON users (LOWER(email))');
        DB::statement('CREATE INDEX vaccination_records_status_due_index ON vaccination_records (status, due_date)');
        DB::statement('CREATE INDEX appointments_user_status_schedule_index ON appointments (user_id, status, appointment_date, appointment_time)');
        DB::statement("CREATE UNIQUE INDEX appointments_unique_scheduled_slot ON appointments (user_id, pet_id, appointment_date, appointment_time) WHERE status = 'Scheduled'");

        //sqlite cannot add a named CHECK to an existing table, so a trigger stops a bad row instead
        //NEW means "the row being saved"
        $this->addSqliteCheck('pets_species_check', 'pets', "NEW.species IN ('Dog', 'Cat')");
        $this->addSqliteCheck('pets_sex_check', 'pets', "NEW.sex IN ('Male', 'Female', 'Unknown')");
        $this->addSqliteCheck('pets_weight_kg_check', 'pets', 'NEW.weight_kg IS NULL OR NEW.weight_kg >= 0');
        $this->addSqliteCheck('vaccination_records_status_check', 'vaccination_records', "NEW.status IN ('Upcoming', 'Completed', 'Overdue')");
        $this->addSqliteCheck('vaccination_records_dates_check', 'vaccination_records', 'NEW.administered_date IS NULL OR NEW.due_date >= NEW.administered_date');
        $this->addSqliteCheck('appointments_status_check', 'appointments', "NEW.status IN ('Scheduled', 'Completed', 'Cancelled')");
        $this->addSqliteCheck('ai_predictions_confidence_check', 'ai_predictions', 'NEW.confidence >= 0 AND NEW.confidence <= 1');
    }

    //add two triggers for one rule: one runs before a new row is added, one before a row is changed
    //when the rule is false, RAISE(ABORT) cancels the save with the rule's name as the error
    private function addSqliteCheck(string $name, string $table, string $condition): void
    {
        DB::unprepared("CREATE TRIGGER {$name}_insert BEFORE INSERT ON {$table} WHEN NOT ({$condition}) BEGIN SELECT RAISE(ABORT, '{$name}'); END");
        DB::unprepared("CREATE TRIGGER {$name}_update BEFORE UPDATE ON {$table} WHEN NOT ({$condition}) BEGIN SELECT RAISE(ABORT, '{$name}'); END");
    }

    //remove the two triggers of one rule
    private function removeSqliteCheck(string $name): void
    {
        DB::statement("DROP TRIGGER IF EXISTS {$name}_insert");
        DB::statement("DROP TRIGGER IF EXISTS {$name}_update");
    }

    //remove the four indexes added in up()
    private function dropIndexes(): void
    {
        DB::statement('DROP INDEX IF EXISTS appointments_unique_scheduled_slot');
        DB::statement('DROP INDEX IF EXISTS appointments_user_status_schedule_index');
        DB::statement('DROP INDEX IF EXISTS vaccination_records_status_due_index');
        DB::statement('DROP INDEX IF EXISTS users_email_lower_unique');
    }
};
