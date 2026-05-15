# Dream Pediatrics Cloud Function Configuration & Deployment Script
# This script configures and deploys the wrapAesKey Cloud Function

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Dream Pediatrics Cloud Function Setup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Configuration
$PROJECT_ID = "dream-pedi"
$BUCKET_NAME = "dream-pedi-secrets"
$KEY_PATH = "aes_key.bin"
$FUNCTION_NAME = "wrapAesKey"

Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Project ID: $PROJECT_ID"
Write-Host "  Private Bucket: $BUCKET_NAME"
Write-Host "  Key Path: $KEY_PATH"
Write-Host "  Function: $FUNCTION_NAME"
Write-Host ""

# Step 1: Check authentication
Write-Host "[Step 1/6] Checking authentication..." -ForegroundColor Green
gcloud auth list
Write-Host ""

$continue = Read-Host "Is the correct account active (marked with *)? (y/n)"
if ($continue -ne "y") {
    Write-Host "Please run: gcloud auth login" -ForegroundColor Red
    Write-Host "Then run this script again." -ForegroundColor Red
    exit 1
}

# Step 2: Set project
Write-Host "[Step 2/6] Setting Google Cloud project..." -ForegroundColor Green
gcloud config set project $PROJECT_ID
Write-Host ""

# Step 3: Verify bucket access
Write-Host "[Step 3/6] Verifying bucket access..." -ForegroundColor Green
$bucketCheck = gsutil ls "gs://$BUCKET_NAME/$KEY_PATH" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Cannot access gs://$BUCKET_NAME/$KEY_PATH" -ForegroundColor Red
    Write-Host ""
    Write-Host "Possible solutions:" -ForegroundColor Yellow
    Write-Host "1. Check if the bucket name is correct"
    Write-Host "2. Verify the AES key file exists:"
    Write-Host "   gsutil ls gs://$BUCKET_NAME/"
    Write-Host "3. Upload the key if missing:"
    Write-Host "   gsutil cp 'c:\Users\NaB\Videos\Dream Pediatrics\converter\output\encrypted\aes_key.bin' gs://$BUCKET_NAME/$KEY_PATH"
    Write-Host "4. Check your account has access to the bucket"
    Write-Host ""
    exit 1
}
Write-Host "✓ Bucket access verified" -ForegroundColor Green
Write-Host ""

# Step 4: Configure Firebase Functions
Write-Host "[Step 4/6] Configuring Firebase Functions..." -ForegroundColor Green
firebase functions:config:set textbook.key_bucket="$BUCKET_NAME" --project $PROJECT_ID
firebase functions:config:set textbook.key_path="$KEY_PATH" --project $PROJECT_ID
Write-Host ""

# Step 5: Verify configuration
Write-Host "[Step 5/6] Verifying configuration..." -ForegroundColor Green
firebase functions:config:get --project $PROJECT_ID
Write-Host ""

# Step 6: Deploy
Write-Host "[Step 6/6] Deploying Cloud Function..." -ForegroundColor Green
Write-Host "This may take a few minutes..." -ForegroundColor Yellow
Write-Host ""

firebase deploy --only functions:$FUNCTION_NAME --project $PROJECT_ID

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "✓ Deployment Successful!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Function URL:" -ForegroundColor Cyan
    Write-Host "https://us-central1-$PROJECT_ID.cloudfunctions.net/$FUNCTION_NAME" -ForegroundColor White
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Yellow
    Write-Host "1. Test the function (see CLOUD_FUNCTIONS_DEPLOYMENT_GUIDE.md)"
    Write-Host "2. Update your Android app with the function URL"
    Write-Host "3. Test the complete flow in your app"
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "✗ Deployment Failed" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Check the error messages above." -ForegroundColor Yellow
    Write-Host "Common issues:" -ForegroundColor Yellow
    Write-Host "1. Billing not enabled (upgrade to Blaze plan)"
    Write-Host "2. Wrong project selected"
    Write-Host "3. Missing dependencies (run: cd functions && npm install)"
    Write-Host ""
    exit 1
}
