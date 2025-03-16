@echo off
echo ===================================================
echo = Android Project Deep Clean Script                =
echo ===================================================
echo.

rem Проверка наличия gradlew
if not exist gradlew.bat (
    echo ERROR: gradlew.bat not found!
    echo Please run this script from the project root directory.
    goto :error
)

echo Select cleanup mode:
echo.
echo 1. Standard Clean (safe, with running Gradle)
echo 2. Generate Full Cleanup Scripts (to run when Android Studio is closed)
echo 3. Exit
echo.

set /p mode=Enter your choice (1-3): 

if "%mode%"=="1" (
    echo.
    echo Starting standard clean process...
    echo.
    
    call gradlew.bat -b clean.gradle cleanAll
    
    if %ERRORLEVEL% neq 0 (
        echo.
        echo WARNING: Some files could not be deleted because they are in use.
        echo For a complete cleanup, close Android Studio and run the Full Cleanup Script.
        echo.
        echo Would you like to generate Full Cleanup Scripts now?
        set /p generate=Generate scripts (y/N)? 
        
        if /i "%generate%"=="y" (
            echo.
            echo Generating Full Cleanup Scripts...
            call gradlew.bat -b clean.gradle generateFullCleanScript
        )
    ) else (
        echo.
        echo ===================================================
        echo = Clean completed successfully!                   =
        echo ===================================================
    )
) else if "%mode%"=="2" (
    echo.
    echo Generating Full Cleanup Scripts...
    echo.
    
    call gradlew.bat -b clean.gradle generateFullCleanScript
    
    echo.
    echo Scripts generated! Run them when Android Studio is closed.
) else if "%mode%"=="3" (
    echo Exiting...
    goto :end
) else (
    echo Invalid choice. Please enter 1, 2, or 3.
    goto :error
)

goto :end

:error
echo Clean process failed!
exit /b 1

:end
echo.
echo You can now rebuild your project with 'gradlew build'
pause 