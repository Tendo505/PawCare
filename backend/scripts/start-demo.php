<?php

use Illuminate\Contracts\Console\Kernel;
use Illuminate\Support\Facades\Artisan;

//2.input: load fresh settings without deleting the running application's cache
$root = dirname(__DIR__);
chdir($root);
$cachePath = sys_get_temp_dir().'/pawcare-demo-config-'.bin2hex(random_bytes(12)).'.php';
putenv('APP_CONFIG_CACHE='.$cachePath);
$_ENV['APP_CONFIG_CACHE'] = $cachePath;
$_SERVER['APP_CONFIG_CACHE'] = $cachePath;
require $root.'/vendor/autoload.php';
$app = require $root.'/bootstrap/app.php';
$app->make(Kernel::class)->bootstrap();

try
{
    //3.process: allow only this project's dedicated SQLite demo file
    if (config('database.default') !== 'sqlite' || env('DB_URL') || config('database.connections.sqlite.url'))
    {
        throw new RuntimeException('Refusing to reset: select DB_CONNECTION=sqlite, remove DB_URL, and use only backend/database/database.sqlite. Existing PostgreSQL data and .env were not changed.');
    }

    $connection = $app['db']->connection();
    $directory = realpath($root.'/database');
    $expected = str_replace('\\', '/', realpath($root)).'/database/database.sqlite';
    $configured = $connection->getConfig('database');
    $configuredDirectory = false;
    if (is_string($configured))
    {
        $configuredDirectory = realpath(dirname($configured));
    }
    $resolved = '';
    if ($configuredDirectory !== false)
    {
        $resolved = str_replace('\\', '/', $configuredDirectory).'/'.basename($configured);
    }

    if (config('database.default') !== 'sqlite'
        || $connection->getDriverName() !== 'sqlite'
        || $connection->getConfig('url')
        || env('DB_URL')
        || $resolved !== $expected
        || str_replace('\\', '/', (string) $directory) !== dirname($expected)
        || (file_exists($expected) && str_replace('\\', '/', realpath($expected)) !== $expected))
    {
        throw new RuntimeException('Refusing to reset: select DB_CONNECTION=sqlite, remove DB_URL, and use only backend/database/database.sqlite. Existing PostgreSQL data and .env were not changed.');
    }

    if (config('app.env') === 'production')
    {
        throw new RuntimeException('Refusing to reset a production environment. This command is for a local demonstration only.');
    }

    //3.process: create missing demo prerequisites without replacing existing files
    if (! file_exists($expected))
    {
        $file = fopen($expected, 'x');
        if ($file === false)
        {
            throw new RuntimeException('Could not create the dedicated SQLite demo database.');
        }
        fclose($file);
    }

    if (! config('app.key'))
    {
        if (Artisan::call('key:generate', ['--force' => true]) !== 0)
        {
            throw new RuntimeException('The application key could not be generated. Check .env permissions.');
        }
    }

    //3.process: reset and seed the guarded connection in the same application process
    echo "Resetting only backend/database/database.sqlite with fictional demo records.\n";
    $result = Artisan::call('migrate:fresh', ['--database' => 'sqlite', '--seed' => true, '--force' => true]);
    echo Artisan::output();
    if ($result !== 0)
    {
        throw new RuntimeException('The guarded SQLite migration or seeder failed.');
    }

    //4.output: show the local demonstration checklist
    echo "\nDemo ready. Existing cached settings were not cleared.\n";
    echo "[ ] If you intentionally changed .env, run php artisan config:clear before using Herd.\n";
    echo "[ ] Open http://pawcare-api.test/api/health\n";
    echo "[ ] Start the AI service on http://127.0.0.1:8001\n";
    echo "[ ] Start the Android emulator, then run PawCare\n";
    echo "[ ] Log in: demo@pawcare.test / password123\n";
}
catch (Throwable $error)
{
    fwrite(STDERR, $error->getMessage()."\n");
    exit(1);
}
