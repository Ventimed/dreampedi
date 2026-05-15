# Encrypted Textbook Deployment Guide

## Overview
This guide explains how to deploy the encrypted Dream Pediatrics textbook to Firebase for secure distribution to the mobile app.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     SECURITY ARCHITECTURE                    │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  1. DOCX → Markdown → JSON (Plaintext)                      │
│  2. JSON → AES-256-GCM Encryption → textbook.enc            │
│  3. AES Key → Stored in Firebase Storage (Private Bucket)   │
│  4. Encrypted File → Firebase Storage (Public)               │
│  5. App → Cloud Function → Wrapped Key (RSA-OAEP)           │
│  6. App → Decrypts textbook locally                          │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

## Files Generated

### 1. Conversion Output (`converter/output/`)
- **output.json** (313,656 bytes) - Plaintext JSON with 37 chapters
- **output.md** - Markdown source

### 2. Encrypted Output (`converter/output/encrypted/`)
- **textbook.enc** (313,684 bytes) - AES-256-GCM encrypted textbook
- **metadata.json** (132 bytes) - Encryption metadata
- **aes_key.bin** (32 bytes) - ⚠️ **CRITICAL SECRET** - AES-256 encryption key

## Step-by-Step Deployment

### Step 1: Upload Encrypted Textbook to Firebase Storage

```bash
# Using Firebase CLI
firebase login
cd "c:\Users\NaB\Videos\Dream Pediatrics\converter"

# Upload encrypted textbook (PUBLIC - can be downloaded by app)
firebase storage:upload output/encrypted/textbook.enc textbooks/textbook.enc --project dreampedi

# Upload metadata (PUBLIC)
firebase storage:upload output/encrypted/metadata.json textbooks/metadata.json --project dreampedi
```

**Alternative: Using Firebase Console**
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project: `dreampedi`
3. Navigate to **Storage** → **Files**
4. Create folder: `textbooks/`
5. Upload:
   - `textbook.enc`
   - `metadata.json`
6. Set permissions: **Public read access** (app needs to download)

### Step 2: Upload AES Key to Secure Storage

⚠️ **CRITICAL**: The AES key must be stored in a **PRIVATE** bucket, accessible only by Cloud Functions.

```bash
# Create a private bucket for secrets (if not exists)
gsutil mb -p dreampedi -c STANDARD -l us-central1 gs://dream-pedi.firebasestorage.app

# Upload AES key to private bucket
gsutil cp output/encrypted/aes_key.bin gs://dream-pedi.firebasestorage.app/aes_key.bin

# Set strict permissions (Cloud Functions only)
gsutil iam ch allUsers:objectViewer gs://dream-pedi.firebasestorage.app/aes_key.bin -d
```

**Alternative: Using Firebase Console**
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select project: `dreampedi`
3. Navigate to **Cloud Storage** → **Buckets**
4. Create new bucket: `dream-pedi.firebasestorage.app` (or use existing)
   - Location: `us-central1`
   - Storage class: `Standard`
   - Access control: **Uniform**
5. Upload `aes_key.bin`
6. Set permissions:
   - **Remove** public access
   - **Add** service account: `dreampedi@appspot.gserviceaccount.com` with role `Storage Object Viewer`

### Step 3: Configure Cloud Function

The Cloud Function (`Server Fun/functions/index.js`) is already configured to:
- Verify user authentication (Firebase ID token)
- Fetch AES key from private storage
- Wrap the key with device's RSA public key
- Return wrapped key to authenticated app

**Environment Variables** (already set in code):
```javascript
KEY_BUCKET = 'dream-pedi.firebasestorage.app'  // Private bucket name
KEY_PATH = 'aes_key.bin'  // Key file path
```

**Deploy Cloud Function:**
```bash
cd "c:\Users\NaB\Videos\Dream Pediatrics\Server Fun"
firebase deploy --only functions:wrapAesKey --project dreampedi
```

### Step 4: Update App Configuration

The Android app (`DP App`) is already configured to:
1. Download `textbook.enc` from Firebase Storage
2. Request wrapped AES key from Cloud Function
3. Decrypt key using device's RSA private key
4. Decrypt textbook using AES-GCM
5. Parse JSON and store chapters in local SQLite database

**No code changes needed** - the app automatically handles the new encrypted format.

### Step 5: Verify Deployment

**Test the complete flow:**

1. **Check Storage URLs:**
   ```
   https://firebasestorage.googleapis.com/v0/b/dream-pedi/o/textbooks%2Ftextbook.enc?alt=media
   https://firebasestorage.googleapis.com/v0/b/dream-pedi/o/textbooks%2Fmetadata.json?alt=media
   ```

2. **Test Cloud Function:**
   ```bash
   # Get Firebase ID token from app logs
   curl -X POST https://us-central1-dreampedi.cloudfunctions.net/wrapAesKey \
     -H "Authorization: Bearer YOUR_ID_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"device_public_key_b64":"YOUR_PUBLIC_KEY_BASE64"}'
   ```

3. **Test in App:**
   - Install app on device
   - Login with verified account
   - Navigate to textbook section
   - Verify all 37 chapters load correctly

## Security Considerations

### ✅ What's Protected
- **Textbook content** is encrypted with AES-256-GCM
- **AES key** is stored in private bucket (not accessible to public)
- **Key distribution** requires authentication + RSA wrapping
- **Device-specific** decryption (each device has unique RSA key pair)

### ⚠️ Important Notes
1. **Never commit** `aes_key.bin` to git
2. **Never upload** `aes_key.bin` to public storage
3. **Always verify** Cloud Function authentication
4. **Rotate keys** periodically for enhanced security
5. **Monitor** Cloud Function logs for suspicious activity

## Updating Textbook Content

When you need to update the textbook:

1. **Update source** (DOCX or Markdown)
2. **Convert to JSON:**
   ```bash
   cd converter
   python md_to_json.py
   ```
3. **Encrypt:**
   ```bash
   python encrypt_dreampeditextbook_gcm.py
   ```
4. **Upload new files:**
   ```bash
   firebase storage:upload output/encrypted/textbook.enc textbooks/textbook.enc --project dreampedi
   firebase storage:upload output/encrypted/metadata.json textbooks/metadata.json --project dreampedi
   ```
5. **Update AES key** (if rotating):
   ```bash
   gsutil cp output/encrypted/aes_key.bin gs://dream-pedi.firebasestorage.app/aes_key.bin
   ```

## Troubleshooting

### Issue: App can't download textbook
- **Check**: Firebase Storage rules allow public read
- **Verify**: URLs are accessible in browser
- **Check**: App has internet permission

### Issue: Cloud Function returns 401
- **Check**: User is authenticated
- **Verify**: ID token is valid and not expired
- **Check**: Cloud Function has correct permissions

### Issue: Decryption fails in app
- **Check**: AES key is correct in private bucket
- **Verify**: Encryption used AES-256-GCM (not CBC)
- **Check**: Nonce is correctly prepended to ciphertext

### Issue: Missing chapters
- **Verify**: All 37 chapters in output.json
- **Check**: JSON structure matches app's ChapterEntity model
- **Run**: `python verify_chapter2.py` to test

## Monitoring

**Firebase Console:**
- Storage → Usage (monitor downloads)
- Functions → Logs (check for errors)
- Authentication → Users (verify access)

**Cloud Logging:**
```bash
gcloud logging read "resource.type=cloud_function AND resource.labels.function_name=wrapAesKey" --limit 50 --project dreampedi
```

## Cost Estimation

**Firebase Storage:**
- Encrypted file: ~314 KB
- Downloads: ~1 MB per user (first time)
- Cost: ~$0.026 per GB = **$0.000026 per user**

**Cloud Functions:**
- Invocations: 1 per user (first time)
- Cost: Free tier covers 2M invocations/month

**Total:** Essentially free for < 10,000 users

## Backup Strategy

**Backup critical files:**
```bash
# Backup encrypted files
cp output/encrypted/* ~/Backups/dreampediatrics/$(date +%Y%m%d)/

# Backup to cloud (optional)
gsutil cp output/encrypted/* gs://dream-pedi.firebasestorage.app/backups/$(date +%Y%m%d)/
```

**Version control:**
- Keep `output.json` in git (plaintext for reference)
- **Never commit** `aes_key.bin`
- Tag releases: `git tag v1.0-textbook`

## Support

For issues or questions:
- Check logs: Firebase Console → Functions → Logs
- Review code: `Server Fun/functions/index.js`
- Test locally: `firebase emulators:start`

---

**Last Updated:** 2026-05-14
**Version:** 1.0
**Chapters:** 37
**Encryption:** AES-256-GCM
