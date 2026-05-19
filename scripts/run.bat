@echo off
:: ============================================================
::  Hotel Management System — Windows Launcher
:: ============================================================

title Hotel Management System

cd /d "%~dp0"

set JAR=hotel-management-system-1.0.0.jar

:: ------------------------------------------------------------
:: JavaFX SDK Path
:: ------------------------------------------------------------
set JAVAFX_LIB=C:\openjfx-17.0.19_windows-x64_bin-sdk\javafx-sdk-17.0.19\lib

:: ------------------------------------------------------------
:: Detect JAR location
:: ------------------------------------------------------------

:: Installed mode
if exist "%~dp0%JAR%" (
    set JAR_PATH=%~dp0%JAR%
    goto RUN_APP
)

:: Development mode
if exist "%~dp0..\target\%JAR%" (
    set JAR_PATH=%~dp0..\target\%JAR%
    goto RUN_APP
)

echo.
echo [ERROR] JAR not found.
echo Checked:
echo %~dp0%JAR%
echo %~dp0..\target\%JAR%
echo.
pause
exit /b 1

:: ------------------------------------------------------------
:: Run Application
:: ------------------------------------------------------------

:RUN_APP

echo.
echo Starting Hotel Management System...
echo Using JAR:
echo %JAR_PATH%
echo.

java ^
  --module-path "%JAVAFX_LIB%" ^
  --add-modules javafx.controls,javafx.fxml ^
  --add-opens java.base/java.lang=ALL-UNNAMED ^
  --add-opens java.base/java.util=ALL-UNNAMED ^
  -jar "%JAR_PATH%"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Application exited with code %ERRORLEVEL%
    pause
)