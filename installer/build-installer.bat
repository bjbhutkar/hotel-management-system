@echo off
setlocal EnableDelayedExpansion

echo ============================================================
echo  Rasoi -- Build Installer
echo  Output: dist\RasoiSetup.exe
echo ============================================================
echo.

set "ROOT=%~dp0.."
set "INSTALLER_DIR=%~dp0"
set "JRE_DIR=%INSTALLER_DIR%jre"

:: ─── 1. Build fat JAR ────────────────────────────────────────────────────────
echo [1/4] Building application JAR...
cd /d "%ROOT%"
call mvn clean package -DskipTests
if errorlevel 1 (
    echo.
    echo [ERROR] Maven build failed. Fix compile errors and retry.
    pause & exit /b 1
)
echo.

:: ─── 2. Download JRE + generate icon ─────────────────────────────────────────
echo [2/4] Preparing JRE and icon...
powershell -NoProfile -ExecutionPolicy Bypass -File "%INSTALLER_DIR%prepare-installer.ps1" ^
    -JreDir  "%JRE_DIR%" ^
    -PngPath "%ROOT%\src\main\resources\images\rasoi.png" ^
    -IcoPath "%INSTALLER_DIR%rasoi.ico"
if errorlevel 1 (
    echo.
    echo [ERROR] Preparation step failed (JRE download or icon generation).
    pause & exit /b 1
)
if not exist "%JRE_DIR%\bin\java.exe" (
    echo [ERROR] JRE not found at: %JRE_DIR%\bin\java.exe
    pause & exit /b 1
)
echo.

:: ─── 3. Locate Inno Setup 6 ──────────────────────────────────────────────────
echo [3/4] Locating Inno Setup 6...
set "ISCC="
if exist "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" set "ISCC=C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if exist "C:\Program Files\Inno Setup 6\ISCC.exe"       set "ISCC=C:\Program Files\Inno Setup 6\ISCC.exe"
if not defined ISCC (
    echo.
    echo [ERROR] Inno Setup 6 not found.
    echo   1. Download from: https://jrsoftware.org/isdl.php
    echo   2. Install it, then re-run this script.
    pause & exit /b 1
)
echo Found: %ISCC%
echo.

:: ─── 4. Compile installer ────────────────────────────────────────────────────
echo [4/4] Compiling installer...
if not exist "%ROOT%\dist" mkdir "%ROOT%\dist"
"%ISCC%" "%INSTALLER_DIR%hotel-management.iss"
if errorlevel 1 (
    echo.
    echo [ERROR] Inno Setup compilation failed.
    pause & exit /b 1
)

echo.
echo ============================================================
echo  SUCCESS!  dist\RasoiSetup.exe is ready to ship.
echo ============================================================
echo.
pause
