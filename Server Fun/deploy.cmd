@echo off
REM Modern deployment script for Dream Pediatrics Cloud Function
REM Uses the new Firebase params approach (not deprecated functions.config)

echo ========================================
echo Dream Pediatrics - Deploy Cloud Function
echo ========================================
echo.

set PROJECT_ID=dream-pedi
set BUCKET_NAME=dream-pedi-secrets
set KEY_PATH=aes_key.bin

echo Configuration:
echo   Project: %PROJECT_ID%
echo   Bucket:  %BUCKET_NAME%
echo   Key:     %KEY_PATH%
echo.

echo Checking bucket access...
gsutil ls gs://%BUCKET_NAME%/%KEY_PATH% >nul 2>&1
if errorlevel 1 (
    echo WARNING: Cannot verify bucket access
    echo Make sure you're logged in with the correct account
    echo.
    set /p CONTINUE="Continue anyway? (y/n): "
    if /i not "%CONTINUE%"=="y" exit /b 1
)
echo.

echo Deploying with environment variables...
echo.

firebase deploy --only functions:wrapAesKey --project %PROJECT_ID% --set-env-vars TEXTBOOK_KEY_BUCKET=%BUCKET_NAME%,TEXTBOOK_KEY_PATH=%KEY_PATH%

if errorlevel 1 (
    echo.
    echo ========================================
    echo Deployment FAILED
    echo ========================================
    echo.
    echo Common issues:
    echo 1. Not logged in: firebase login
    echo 2. Wrong project: firebase use %PROJECT_ID%
    echo 3. Billing not enabled: Upgrade to Blaze plan
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo Deployment SUCCESSFUL!
echo ========================================
echo.
echo Function URL:
echo https://us-central1-%PROJECT_ID%.cloudfunctions.net/wrapAesKey
echo.
echo Environment variables set:
echo   TEXTBOOK_KEY_BUCKET=%BUCKET_NAME%
echo   TEXTBOOK_KEY_PATH=%KEY_PATH%
echo.
pause
