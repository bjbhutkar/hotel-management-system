@echo off
:: ============================================================
::  PUSH_TO_GITHUB.bat
::  Run this ONCE from inside the hotel-management-system folder
::  to push all code to your GitHub repo.
::
::  Prerequisites: git must be installed and in your PATH.
:: ============================================================

echo.
echo  Hotel Management System — Push to GitHub
echo  =========================================
echo.

:: Check git is available
git --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo  [ERROR] git not found. Install from https://git-scm.com/
    pause
    exit /b 1
)

:: Initialize repo
git init
git branch -M main

:: Add remote (change URL if your repo name is different)
git remote remove origin >nul 2>&1
git remote add origin https://github.com/bjbhutkar/hotel-management-system.git

:: Stage and commit everything
git add .
git commit -m "feat: complete Hotel Management System - Java 21, JavaFX, Spring Boot, SQLite"

:: Push
echo.
echo  Pushing to GitHub...  (you may be asked to log in)
echo.
git push -u origin main

echo.
if %ERRORLEVEL% EQU 0 (
    echo  [OK] All code pushed to https://github.com/bjbhutkar/hotel-management-system
) else (
    echo  [ERROR] Push failed. Check your credentials and try again.
    echo  Tip: use a Personal Access Token as the password.
)
echo.
pause
