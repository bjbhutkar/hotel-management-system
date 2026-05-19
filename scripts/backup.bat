@echo off
:: ============================================================
::  Hotel Management System — Manual Backup Script
:: ============================================================
title Hotel Management System — Backup

set DB_PATH=%USERPROFILE%\hotel_management\hotel_management.db
set BACKUP_DIR=%USERPROFILE%\hotel_management\backups

if not exist "%DB_PATH%" (
    echo  [ERROR] Database not found at: %DB_PATH%
    pause
    exit /b 1
)

:: Create backup directory
if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

:: Generate timestamp
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set DT=%%I
set TIMESTAMP=%DT:~0,8%_%DT:~8,6%

set BACKUP_FILE=%BACKUP_DIR%\hotel_backup_%TIMESTAMP%.db

copy "%DB_PATH%" "%BACKUP_FILE%"

echo.
echo  [OK] Backup created:
echo       %BACKUP_FILE%
echo.
pause
