<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    //keep database rules active outside Laravel on both supported connections
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

        //keep emails unique even when letter case differs
        DB::statement('CREATE UNIQUE INDEX users_email_lower_unique ON users (LOWER(email))');

        //index the reminder and status queries used by PawCare
        DB::statement('CREATE INDEX vaccination_records_status_due_index ON vaccination_records (status, due_date)');
        DB::statement('CREATE INDEX appointments_user_status_schedule_index ON appointments (user_id, status, appointment_date, appointment_time)');

        //prevent two active visits for one pet at the same time
        DB::statement("CREATE UNIQUE INDEX appointments_unique_scheduled_slot ON appointments (user_id, pet_id, appointment_date, appointment_time) WHERE status = 'Scheduled'");

        //reject invalid values inserted outside the API
        DB::statement("ALTER TABLE pets ADD CONSTRAINT pets_species_check CHECK (species IN ('Dog', 'Cat'))");
        DB::statement("ALTER TABLE pets ADD CONSTRAINT pets_sex_check CHECK (sex IN ('Male', 'Female', 'Unknown'))");
        DB::statement('ALTER TABLE pets ADD CONSTRAINT pets_weight_kg_check CHECK (weight_kg IS NULL OR weight_kg >= 0)');
        DB::statement("ALTER TABLE vaccination_records ADD CONSTRAINT vaccination_records_status_check CHECK (status IN ('Upcoming', 'Completed', 'Overdue'))");
        DB::statement('ALTER TABLE vaccination_records ADD CONSTRAINT vaccination_records_dates_check CHECK (administered_date IS NULL OR due_date >= administered_date)');
        DB::statement("ALTER TABLE appointments ADD CONSTRAINT appointments_status_check CHECK (status IN ('Scheduled', 'Completed', 'Cancelled'))");
        DB::statement('ALTER TABLE ai_predictions ADD CONSTRAINT ai_predictions_confidence_check CHECK (confidence >= 0 AND confidence <= 1)');
    }

    //remove only the Sprint 5 constraints and indexes
    public function down(): void
    {
        if (DB::getDriverName() === 'sqlite')
        {
            foreach ($this->sqliteChecks() as $name => [$table, $condition])
            {
                DB::statement("DROP TRIGGER IF EXISTS {$name}_insert");
                DB::statement("DROP TRIGGER IF EXISTS {$name}_update");
            }

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

    //SQLite supports expression indexes and partial unique indexes
    private function addSqliteRules(): void
    {
        DB::statement('CREATE UNIQUE INDEX users_email_lower_unique ON users (LOWER(email))');
        DB::statement('CREATE INDEX vaccination_records_status_due_index ON vaccination_records (status, due_date)');
        DB::statement('CREATE INDEX appointments_user_status_schedule_index ON appointments (user_id, status, appointment_date, appointment_time)');
        DB::statement("CREATE UNIQUE INDEX appointments_unique_scheduled_slot ON appointments (user_id, pet_id, appointment_date, appointment_time) WHERE status = 'Scheduled'");

        //SQLite cannot add named CHECK constraints without rebuilding existing tables
        foreach ($this->sqliteChecks() as $name => [$table, $condition])
        {
            foreach (['INSERT', 'UPDATE'] as $operation)
            {
                $trigger = $name.'_'.strtolower($operation);
                DB::unprepared("CREATE TRIGGER {$trigger} BEFORE {$operation} ON {$table} WHEN NOT ({$condition}) BEGIN SELECT RAISE(ABORT, '{$name}'); END");
            }
        }
    }

    //match the PostgreSQL checks without changing table or column names
    private function sqliteChecks(): array
    {
        return [
            'pets_species_check' => ['pets', "NEW.species IN ('Dog', 'Cat')"],
            'pets_sex_check' => ['pets', "NEW.sex IN ('Male', 'Female', 'Unknown')"],
            'pets_weight_kg_check' => ['pets', 'NEW.weight_kg IS NULL OR NEW.weight_kg >= 0'],
            'vaccination_records_status_check' => ['vaccination_records', "NEW.status IN ('Upcoming', 'Completed', 'Overdue')"],
            'vaccination_records_dates_check' => ['vaccination_records', 'NEW.administered_date IS NULL OR NEW.due_date >= NEW.administered_date'],
            'appointments_status_check' => ['appointments', "NEW.status IN ('Scheduled', 'Completed', 'Cancelled')"],
            'ai_predictions_confidence_check' => ['ai_predictions', 'NEW.confidence >= 0 AND NEW.confidence <= 1'],
        ];
    }

    //remove the shared indexes during rollback
    private function dropIndexes(): void
    {
        DB::statement('DROP INDEX IF EXISTS appointments_unique_scheduled_slot');
        DB::statement('DROP INDEX IF EXISTS appointments_user_status_schedule_index');
        DB::statement('DROP INDEX IF EXISTS vaccination_records_status_due_index');
        DB::statement('DROP INDEX IF EXISTS users_email_lower_unique');
    }
};
