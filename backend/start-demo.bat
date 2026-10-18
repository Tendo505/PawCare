@echo off
setlocal
pushd "%~dp0"

rem 1.check the existing Herd tools
where php >nul 2>&1
if errorlevel 1 goto missing_php

rem 2.keep any existing environment unchanged
if not exist ".env" (
    copy ".env.example" ".env" >nul
    if errorlevel 1 goto failed
)

rem 3.install locked dependencies only when needed
if not exist "vendor\autoload.php" (
    where composer >nul 2>&1
    if errorlevel 1 goto missing_composer
    call composer install --no-interaction --prefer-dist
    if errorlevel 1 goto failed
)

rem 4.reset only the dedicated SQLite demo after the PHP guard passes
call php scripts\start-demo.php
if errorlevel 1 goto failed
popd
exit /b 0

:missing_php
echo PHP was not found. Start Laravel Herd and open a new terminal.
goto failed

:missing_composer
echo Composer was not found. Enable the existing Herd Composer command.
goto failed

:failed
echo Demo setup stopped. No unguarded database reset was attempted.
popd
exit /b 1
