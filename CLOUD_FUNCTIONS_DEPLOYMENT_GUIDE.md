# Cloud Functions Deployment Guide

Complete step-by-step guide for deploying the `wrapAesKey` Cloud Function to Firebase.

## 📋 Table of Contents

1. [Prerequisites](#prerequisites)
2. [Understanding the Cloud Function](#understanding-the-cloud-function)
3. [Pre-Deployment Setup](#pre-deployment-setup)
4. [Deployment Steps](#deployment-steps)
5. [Verification & Testing](#verification--testing)
6. [Troubleshooting](#troubleshooting)
7. [Monitoring & Maintenance](#monitoring--maintenance)

---

## Prerequisites

### Required Tools

1. **Node.js & npm**
   ```bash
   # Check if installed
   node --version  # Should be v16 or higher
   npm --version
   
   # Install from: https://nodejs.org/
   ```

2. **Firebase CLI**
   ```bash
   # Install globally
   npm install -g firebase-tools
   
   # Verify installation
   firebase --version
   ```

3. **Google Cloud SDK** (Optional, for advanced operations)
   ```bash
   # Download from: https://cloud.google.com/sdk/docs/install
   gcloud --version
   ```

### Required Access

- ✅ Firebase project admin access (`dreampedi`)
- ✅ Google Cloud project owner/editor role
- ✅ Firebase Storage bucket access (`dreamprod`)
- ✅ Billing enabled on Firebase project (required for Cloud Functions)

---

## Understanding the Cloud Function

### What Does `wrapAesKey` Do?

The `wrapAesKey` function is a secure key distribution service that:

1. **Authenticates** the user via Firebase ID token
2. **Receives** the device's RSA public key (base64 encoded)
3. **Fetches** the AES encryption key from private storage (`gs://dreamprod/aes_key.bin`)
4. **Wraps** the AES key using RSA-OAEP encryption with the device's public key
5. **Returns** the wrapped key to the app

### Security Features

- ✅ **Authentication Required**: Only authenticated users can call the function
- ✅ **Device-Specific**: Each device gets a uniquely wrapped key
- ✅ **No Key Caching**: AES key is fetched fresh on each request
- ✅ **Private Storage**: AES key stored in private bucket (not public)
- ✅ **Audit Logging**: All requests logged to Cloud Logging

### Architecture

```
┌─────────────┐
│  Android    │
│    App      │
└──────┬──────┘
       │ 1. POST /wrapAesKey
       │    Authorization: Bearer <ID_TOKEN>
       │    Body: { device_public_key_b64: "..." }
       ↓
┌─────────────────────────────────────────┐
│     Cloud Function: wrapAesKey          │
│     Region: us-central1                 │
├─────────────────────────────────────────┤
│  2. Verify ID Token                     │
│  3. Fetch aes_key.bin from dreamprod    │
│  4. Wrap key with RSA-OAEP (SHA-1)      │
│  5. Return { wrapped_key_b64: "..." }   │
└──────┬──────────────────────────────────┘
       │ 3. Fetch aes_key.bin
       ↓
┌─────────────────────┐
│  Firebase Storage   │
│  gs://dreamprod     │
│  └─ aes_key.bin     │
└─────────────────────┘
```

---

## Pre-Deployment Setup

### Step 1: Login to Firebase

```bash
# Login to Firebase
firebase login

# Verify you're logged in
firebase projects:list

# You should see 'dreampedi' in the list
```

### Step 2: Verify Project Configuration

```bash
cd "c:\Users\NaB\Videos\Dream Pediatrics\Server Fun"

# Check Firebase configuration
type .firebaserc
```

**Expected output:**
```json
{
  "projects": {
    "default": "dreampedi"
  }
}
```

### Step 3: Check Firebase Configuration

```bash
# View firebase.json
type firebase.json
```

**Expected output:**
```json
{
  "functions": {
    "source": "functions",
    "predeploy": [],
    "runtime": "nodejs18"
  }
}
```

### Step 4: Install Dependencies

```bash
cd functions

# Install Node.js dependencies
npm install

# Verify package.json
type package.json
```

**Required dependencies:**
- `firebase-admin`: Firebase Admin SDK
- `firebase-functions`: Cloud Functions SDK
- `crypto`: Built-in Node.js crypto module

### Step 5: Verify AES Key Exists in Private Bucket

```bash
# Check if aes_key.bin exists in private bucket
gsutil ls gs://dreamprod/aes_key.bin

# Expected output: gs://dreamprod/aes_key.bin
```

**If the key doesn't exist:**
```bash
# Upload the AES key to private bucket
cd "c:\Users\NaB\Videos\Dream Pediatrics\converter"
gsutil cp output/encrypted/aes_key.bin gs://dreamprod/aes_key.bin

# Verify upload
gsutil ls -l gs://dreamprod/aes_key.bin
```

### Step 6: Configure Environment Variables (IMPORTANT - Customize for Your Setup)

The function uses environment variables to locate your AES key. Firebase now uses the modern **params** approach instead of the deprecated `functions.config()`.

#### Method 1: Using .env.yaml File (Recommended) ⭐

Edit `functions/.env.yaml` with your bucket configuration:

```yaml
# functions/.env.yaml
TEXTBOOK_KEY_BUCKET: dream-pedi-secrets
TEXTBOOK_KEY_PATH: aes_key.bin
```

**This file is already created for you!** Just verify the values match your setup.

#### Method 2: Set During Deployment

You can also set environment variables during deployment:

```bash
# Deploy with custom environment variables
firebase deploy --only functions:wrapAesKey ^
  --set-env-vars TEXTBOOK_KEY_BUCKET=dream-pedi-secrets,TEXTBOOK_KEY_PATH=aes_key.bin
```

#### Method 3: Edit Default Values in Code

Edit `functions/index.js` to change the default values:

```javascript
// Change the default values:
const KEY_BUCKET = defineString('TEXTBOOK_KEY_BUCKET', {
  description: 'Private bucket name where aes_key.bin is stored',
  default: 'dream-pedi-secrets'  // ← Change this
});

const KEY_PATH = defineString('TEXTBOOK_KEY_PATH', {
  description: 'Path to the AES key file within the bucket',
  default: 'aes_key.bin'  // ← Change this if needed
});
```

#### Common Bucket Names (Choose Yours)

Based on your BUCKET_SETUP_GUIDE.md, you might be using:
- `dream-pedi-secrets` ← **Most likely yours**
- `dream-pedi.firebasestorage.app`
- `dream-pedi-keys`
- `dreamprod`

**To check which bucket you're using:**
```bash
# List all your buckets
gsutil ls

# Check if your key exists in a specific bucket
gsutil ls gs://dream-pedi-secrets/aes_key.bin
```

#### For Local Development

Edit `functions/.env` for local testing:

```bash
# functions/.env
TEXTBOOK_KEY_BUCKET=dream-pedi-secrets
TEXTBOOK_KEY_PATH=aes_key.bin
```

---

## 🎯 Quick Setup for Your Configuration

Based on your `BUCKET_SETUP_GUIDE.md`, here's your specific setup:

### Your Bucket Configuration

```bash
# 1. Set the correct Google Cloud account
gcloud config set account dreampediatrics9@gmail.com
gcloud config set project dream-pedi

# 2. Verify your bucket exists
gsutil ls gs://dream-pedi-secrets/aes_key.bin

# 3. Edit the environment variables file
cd "c:\Users\NaB\Videos\Dream Pediatrics\Server Fun\functions"
notepad .env.yaml

# Make sure it contains:
# TEXTBOOK_KEY_BUCKET: dream-pedi-secrets
# TEXTBOOK_KEY_PATH: aes_key.bin

# 4. Deploy
cd ..
firebase deploy --only functions:wrapAesKey --project dream-pedi
```

### Alternative: Deploy with Inline Environment Variables

```bash
cd "c:\Users\NaB\Videos\Dream Pediatrics\Server Fun"
firebase deploy --only functions:wrapAesKey --project dream-pedi ^
  --set-env-vars TEXTBOOK_KEY_BUCKET=dream-pedi-secrets,TEXTBOOK_KEY_PATH=aes_key.bin
```

### Troubleshooting Account Issues

If you get "Access Denied" errors:

```bash
# Check which account is active
gcloud auth list

# Switch to the correct account (the one with * should match your Firebase account)
gcloud config set account dreampediatrics9@gmail.com

# Or login with a new account
gcloud auth login

# Verify you can access the bucket
gsutil ls gs://dream-pedi-secrets/
```

---

## Deployment Steps

### Step 1: Navigate to Functions Directory

```bash
cd "c:\Users\NaB\Videos\Dream Pediatrics\Server Fun"
```

### Step 2: Test Locally (Optional but Recommended)

```bash
# Start Firebase emulators
firebase emulators:start --only functions

# The function will be available at:
# http://localhost:5001/dreampedi/us-central1/wrapAesKey
```

**Test with curl:**
```bash
# Get a test ID token from your app or Firebase Console
curl -X POST http://localhost:5001/dreampedi/us-central1/wrapAesKey ^
  -H "Authorization: Bearer YOUR_TEST_ID_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"device_public_key_b64\":\"YOUR_TEST_PUBLIC_KEY\"}"
```

Press `Ctrl+C` to stop the emulator.

### Step 3: Deploy to Firebase

```bash
# Deploy only the wrapAesKey function
firebase deploy --only functions:wrapAesKey --project dreampedi
```

**Expected output:**
```
=== Deploying to 'dreampedi'...

i  deploying functions
i  functions: ensuring required API cloudfunctions.googleapis.com is enabled...
i  functions: ensuring required API cloudbuild.googleapis.com is enabled...
✔  functions: required API cloudfunctions.googleapis.com is enabled
✔  functions: required API cloudbuild.googleapis.com is enabled
i  functions: preparing functions directory for uploading...
i  functions: packaged functions (XX KB) for uploading
✔  functions: functions folder uploaded successfully
i  functions: updating Node.js 18 function wrapAesKey(us-central1)...
✔  functions[wrapAesKey(us-central1)]: Successful update operation.

✔  Deploy complete!

Function URL (wrapAesKey): https://us-central1-dreampedi.cloudfunctions.net/wrapAesKey
```

### Step 4: Note the Function URL

**Your function is now live at:**
```
https://us-central1-dreampedi.cloudfunctions.net/wrapAesKey
```

Save this URL - you'll need it in your Android app configuration.

### Step 5: Set IAM Permissions

Ensure the Cloud Function has access to the private bucket:

```bash
# Grant Storage Object Viewer role to the Cloud Functions service account
gcloud projects add-iam-policy-binding dreampedi ^
  --member="serviceAccount:dreampedi@appspot.gserviceaccount.com" ^
  --role="roles/storage.objectViewer"

# Verify permissions
gsutil iam get gs://dreamprod
```

---

## Verification & Testing

### Test 1: Check Function Deployment

```bash
# List deployed functions
firebase functions:list --project dreampedi

# Expected output:
# ┌────────────┬────────────┬─────────┐
# │ Name       │ Region     │ Runtime │
# ├────────────┼────────────┼─────────┤
# │ wrapAesKey │ us-central1│ nodejs18│
# └────────────┴────────────┴─────────┘
```

### Test 2: Check Function Logs

```bash
# View recent logs
firebase functions:log --project dreampedi

# Or use Google Cloud Console:
# https://console.cloud.google.com/logs/query?project=dreampedi
```

### Test 3: Test with Real Request

**Prerequisites:**
- Get a valid Firebase ID token from your Android app
- Get a test RSA public key (base64 encoded)

**Using curl (Windows CMD):**
```bash
curl -X POST https://us-central1-dreampedi.cloudfunctions.net/wrapAesKey ^
  -H "Authorization: Bearer YOUR_FIREBASE_ID_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"device_public_key_b64\":\"YOUR_RSA_PUBLIC_KEY_BASE64\"}"
```

**Expected response:**
```json
{
  "wrapped_key_b64": "BASE64_ENCODED_WRAPPED_KEY..."
}
```

**Error responses:**

| Status | Error | Cause |
|--------|-------|-------|
| 401 | `Missing Authorization bearer token` | No ID token provided |
| 401 | `Token verification failed` | Invalid or expired ID token |
| 400 | `missing device_public_key_b64` | No public key in request body |
| 500 | `AES key not found on server` | aes_key.bin missing from bucket |
| 405 | `POST only` | Used wrong HTTP method |

### Test 4: Test from Android App

Update your Android app configuration:

```java
// In your app's configuration
private static final String WRAP_KEY_URL = 
    "https://us-central1-dreampedi.cloudfunctions.net/wrapAesKey";
```

Then test the complete flow:
1. User logs in → Get ID token
2. Generate RSA key pair
3. Call `wrapAesKey` function
4. Unwrap AES key
5. Decrypt textbook

---

## Troubleshooting

### Issue 1: Deployment Fails with "Billing Required"

**Error:**
```
Error: HTTP Error: 400, Billing account not configured
```

**Solution:**
1. Go to [Firebase Console](https://console.firebase.google.com/project/dreampedi/overview)
2. Click **Upgrade** to Blaze (Pay as you go) plan
3. Add a billing account
4. Retry deployment

**Note:** Cloud Functions require the Blaze plan, but the free tier covers most usage.

### Issue 2: Function Returns 500 "AES key not found"

**Error:**
```json
{
  "error": "AES key not found on server"
}
```

**Solution:**
```bash
# Verify the key exists
gsutil ls gs://dreamprod/aes_key.bin

# If missing, upload it
cd "c:\Users\NaB\Videos\Dream Pediatrics\converter"
gsutil cp output/encrypted/aes_key.bin gs://dreamprod/aes_key.bin

# Verify bucket name in function code
# Check KEY_BUCKET variable in index.js
```

### Issue 3: Function Returns 401 "Token verification failed"

**Causes:**
- Expired ID token (tokens expire after 1 hour)
- Invalid token format
- Token from wrong Firebase project

**Solution:**
```java
// In Android app, refresh the token
FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
user.getIdToken(true).addOnCompleteListener(task -> {
    if (task.isSuccessful()) {
        String idToken = task.getResult().getToken();
        // Use fresh token
    }
});
```

### Issue 4: Permission Denied Accessing Private Bucket

**Error in logs:**
```
Error: Permission denied accessing gs://dreamprod/aes_key.bin
```

**Solution:**
```bash
# Grant Storage Object Viewer role
gsutil iam ch serviceAccount:dreampedi@appspot.gserviceaccount.com:objectViewer gs://dreamprod

# Or use gcloud
gcloud projects add-iam-policy-binding dreampedi ^
  --member="serviceAccount:dreampedi@appspot.gserviceaccount.com" ^
  --role="roles/storage.objectViewer"
```

### Issue 5: Function Timeout

**Error:**
```
Function execution took too long
```

**Solution:**
```bash
# Increase timeout (default is 60s)
firebase functions:config:set timeout=120

# Redeploy
firebase deploy --only functions:wrapAesKey
```

### Issue 6: CORS Errors (if calling from web)

**Error:**
```
Access to fetch at '...' from origin '...' has been blocked by CORS policy
```

**Solution:**
Add CORS headers to the function:

```javascript
// In index.js, add to handleWrapAesKey:
res.set('Access-Control-Allow-Origin', '*');
res.set('Access-Control-Allow-Methods', 'POST');
res.set('Access-Control-Allow-Headers', 'Authorization, Content-Type');

if (req.method === 'OPTIONS') {
  res.status(204).send('');
  return;
}
```

---

## Monitoring & Maintenance

### View Function Logs

**Using Firebase CLI:**
```bash
# View recent logs
firebase functions:log --project dreampedi

# Follow logs in real-time
firebase functions:log --project dreampedi --follow

# Filter by function
firebase functions:log --only wrapAesKey
```

**Using Google Cloud Console:**
1. Go to [Cloud Logging](https://console.cloud.google.com/logs/query?project=dreampedi)
2. Filter by: `resource.type="cloud_function" AND resource.labels.function_name="wrapAesKey"`

### Monitor Function Performance

**Firebase Console:**
1. Go to [Functions Dashboard](https://console.firebase.google.com/project/dreampedi/functions)
2. Click on `wrapAesKey`
3. View metrics:
   - Invocations per day
   - Execution time
   - Error rate
   - Memory usage

**Key Metrics to Monitor:**
- **Invocation count**: Should match app usage
- **Error rate**: Should be < 1%
- **Execution time**: Should be < 2 seconds
- **Memory usage**: Should be < 256 MB

### Set Up Alerts

**Using Google Cloud Monitoring:**
```bash
# Create alert for high error rate
gcloud alpha monitoring policies create ^
  --notification-channels=YOUR_CHANNEL_ID ^
  --display-name="Cloud Function Errors" ^
  --condition-display-name="Error rate > 5%" ^
  --condition-threshold-value=0.05 ^
  --condition-threshold-duration=300s
```

### Update the Function

**When you need to update the code:**

1. Edit `functions/index.js`
2. Test locally:
   ```bash
   firebase emulators:start --only functions
   ```
3. Deploy:
   ```bash
   firebase deploy --only functions:wrapAesKey
   ```

### Rollback to Previous Version

```bash
# List function versions
gcloud functions list --project dreampedi

# Rollback to previous version
gcloud functions deploy wrapAesKey ^
  --region=us-central1 ^
  --source=gs://gcf-sources-XXXXX/XXXXX ^
  --project=dreampedi
```

### Delete the Function (if needed)

```bash
# Delete function
firebase functions:delete wrapAesKey --project dreampedi

# Confirm deletion when prompted
```

---

## Cost Estimation

### Cloud Functions Pricing

**Free Tier (per month):**
- 2 million invocations
- 400,000 GB-seconds
- 200,000 GHz-seconds
- 5 GB network egress

**Estimated Usage:**
- 1,000 users × 1 invocation = 1,000 invocations/month
- Execution time: ~1 second
- Memory: 256 MB
- Cost: **$0.00** (within free tier)

**At Scale (10,000 users):**
- 10,000 invocations/month
- Cost: **$0.00** (still within free tier)

### Storage Pricing

**Firebase Storage:**
- AES key: 32 bytes
- Cost: **$0.00** (negligible)

**Total Monthly Cost:** ~$0.00 for typical usage

---

## Security Best Practices

### ✅ Do's

- ✅ Always verify ID tokens
- ✅ Use HTTPS only
- ✅ Store AES key in private bucket
- ✅ Use RSA-OAEP for key wrapping
- ✅ Enable audit logging
- ✅ Monitor for suspicious activity
- ✅ Rotate AES key periodically
- ✅ Use environment variables for configuration

### ❌ Don'ts

- ❌ Never commit `aes_key.bin` to git
- ❌ Never log the AES key or wrapped key
- ❌ Never disable authentication
- ❌ Never use HTTP (only HTTPS)
- ❌ Never cache the AES key in memory
- ❌ Never expose the function URL publicly without auth
- ❌ Never use weak encryption (always AES-256-GCM)

---

## Quick Reference

### Essential Commands

```bash
# Login
firebase login

# Deploy function
cd "c:\Users\NaB\Videos\Dream Pediatrics\Server Fun"
firebase deploy --only functions:wrapAesKey --project dreampedi

# View logs
firebase functions:log --project dreampedi

# Test locally
firebase emulators:start --only functions

# List functions
firebase functions:list --project dreampedi

# Delete function
firebase functions:delete wrapAesKey --project dreampedi
```

### Important URLs

| Resource | URL |
|----------|-----|
| **Function Endpoint** | `https://us-central1-dreampedi.cloudfunctions.net/wrapAesKey` |
| **Firebase Console** | `https://console.firebase.google.com/project/dreampedi/functions` |
| **Cloud Logging** | `https://console.cloud.google.com/logs/query?project=dreampedi` |
| **Storage Bucket** | `https://console.cloud.google.com/storage/browser/dreamprod` |

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `TEXTBOOK_KEY_BUCKET` | `dreamprod` | Private bucket name |
| `TEXTBOOK_KEY_PATH` | `aes_key.bin` | Key file path |

---

## Next Steps

After deploying the Cloud Function:

1. ✅ **Update Android App** - Configure the function URL
2. ✅ **Test End-to-End** - Verify complete flow works
3. ✅ **Monitor Logs** - Check for errors
4. ✅ **Set Up Alerts** - Get notified of issues
5. ✅ **Document** - Update app documentation

---

## Support & Resources

### Documentation
- [Firebase Cloud Functions Docs](https://firebase.google.com/docs/functions)
- [Firebase Admin SDK](https://firebase.google.com/docs/admin/setup)
- [Google Cloud Storage](https://cloud.google.com/storage/docs)

### Related Guides
- [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) - Complete deployment guide
- [ARCHITECTURE.md](ARCHITECTURE.md) - System architecture
- [BUCKET_SETUP_GUIDE.md](BUCKET_SETUP_GUIDE.md) - Storage bucket setup

### Troubleshooting
- Check logs: `firebase functions:log`
- Test locally: `firebase emulators:start`
- Review code: `Server Fun/functions/index.js`

---

**Version:** 1.0  
**Last Updated:** 2026-05-15  
**Status:** Production Ready  
**Function:** `wrapAesKey`  
**Region:** `us-central1`  
**Runtime:** Node.js 18
