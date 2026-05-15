@echo off
REM Quick setup script for Dream Pediatrics Cloud Function
REM This configures the bucket name and deploys the function

echo ========================================
echo Dream Pediatrics - Quick Setup
echo ========================================
echo.

REM Configuration (EDIT THESE IF NEEDED)
set PROJECT_ID=dream-pedi
set BUCKET_NAME=dream-pedi-secrets
set KEY_PATH=aes_key.bin

echo Your Configuration:
echo   Project: %PROJECT_ID%
echo   Bucket:  %BUCKET_NAME%
echo   Key:     %KEY_PATH%
echo.

echo Step 1: Checking bucket access...
gsutil ls gs://%BUCKET_NAME%/%KEY_PATH%
if errorlevel 1 (
    echo.
    echo ERROR: Cannot access gs://%BUCKET_NAME%/%KEY_PATH%
    echo.
    echo Please check:
    echo 1. Is the bucket name correct?
    echo 2. Does the key file exist?
    echo 3. Are you logged in with the correct account?
    echo.
    echo Run: gcloud auth list
    echo.
    pause
    exit /b 1
)
echo OK - Bucket access verified
echo.

echo Step 2: Configuring Firebase Functions...
call firebase functions:config:set textbook.key_bucket="%BUCKET_NAME%" --project %PROJECT_ID%
call firebase functions:config:set textbook.key_path="%KEY_PATH%" --project %PROJECT_ID%
echo.

echo Step 3: Verifying configuration...
call firebase functions:config:get --project %PROJECT_ID%
echo.

echo Step 4: Ready to deploy!
echo.
set /p DEPLOY="Deploy now? (y/n): "
if /i "%DEPLOY%"=="y" (
    echo.
    echo Deploying Cloud Function...
    call firebase deploy --only functions:wrapAesKey --project %PROJECT_ID%
    echo.
    if errorlevel 1 (
        echo.
        echo Deployment FAILED!
        echo Check the error messages above.
        echo.
    ) else (
        echo.
        echo ========================================
        echo Deployment SUCCESSFUL!
        echo ========================================
        echo.
        echo Function URL:
        echo https://us-central1-%PROJECT_ID%.cloudfunctions.net/wrapAesKey
        echo.
    )
) else (
    echo.
    echo Skipped deployment.
    echo To deploy later, run:
    echo   firebase deploy --only functions:wrapAesKey --project %PROJECT_ID%
    echo.
)

pause
