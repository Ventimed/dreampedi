# Firebase Storage Bucket Setup Guide

## Overview
You need two buckets for the Dream Pediatrics textbook system:
1. **Public Bucket** - For encrypted textbook files (app downloads)
2. **Private Bucket** - For the AES encryption key (Cloud Functions only)

---

## Method 1: Using Firebase Console (Easiest)

### Step 1: Create Public Bucket (Default Firebase Storage)

Your Firebase project already has a default public bucket!

1. **Go to Firebase Console**
   - Visit: https://console.firebase.google.com/
   - Select your project: **dreampedi**

2. **Navigate to Storage**
   - Click **Storage** in the left sidebar
   - Click **Get Started** (if first time)
   - Choose **Start in production mode** or **Test mode**

3. **Your Default Bucket**
   - Default bucket name: `dream-pedi`
   - This is your **PUBLIC BUCKET** ✅
   - Location: Usually `us-central1` or your selected region

4. **Create Textbooks Folder**
   - Click **Upload file** dropdown → **Create folder**
   - Name: `textbooks`
   - Click **Create**

5. **Upload Files**
   - Click on `textbooks` folder
   - Click **Upload file**
   - Select `textbook.enc` from `converter/output/encrypted/`
   - Click **Upload file** again
   - Select `metadata.json` from `converter/output/encrypted/`

6. **Set Public Access** (if needed)
   - Click on `textbook.enc`
   - Click **Access token** tab
   - Make sure there's a token (this makes it publicly accessible)
   - If no token, click **Create token**

**Your public bucket is ready!** ✅

---

### Step 2: Create Private Bucket (For Secrets)

#### Option A: Using Google Cloud Console (Recommended)

1. **Go to Google Cloud Console**
   - Visit: https://console.cloud.google.com/
   - Make sure you're in project: **dreampedi**

2. **Navigate to Cloud Storage**
   - Click the hamburger menu (☰) in top-left
   - Scroll down to **Cloud Storage** → **Buckets**
   - Or visit: https://console.cloud.google.com/storage/browser

3. **Create New Bucket**
   - Click **CREATE BUCKET** button at the top

4. **Configure Bucket Settings**

   **Step 1: Name your bucket**
   ```
   Bucket name: dream-pedi.firebasestorage.app
   ```
   - Must be globally unique
   - If taken, try: `dream-pedi-secrets` or `dream-pedi-keys`
   - Click **CONTINUE**

   **Step 2: Choose where to store your data**
   ```
   Location type: Region
   Location: us-central1 (or same as your Firebase)
   ```
   - Click **CONTINUE**

   **Step 3: Choose a storage class**
   ```
   Storage class: Standard
   ```
   - Click **CONTINUE**

   **Step 4: Choose how to control access**
   ```
   ✅ Access control: Uniform
   ```
   - **Note:** "Enforce public access prevention" option may not appear during creation
   - We'll set this after bucket creation using command line
   - Click **CONTINUE**

   **Step 5: Choose how to protect object data**
   ```
   Protection tools: None (or default)
   ```
   - Click **CREATE**

5. **Upload AES Key**
   - Click on your new bucket: `dream-pedi.firebasestorage.app`
   - Click **UPLOAD FILES**
   - Select `aes_key.bin` from `converter/output/encrypted/`
   - Click **Open**

6. **Verify Private Access**
   - Click on `aes_key.bin`
   - Try to access the public URL (should fail)
   - You should see: "Access denied" ✅

7. **Enforce Public Access Prevention (IMPORTANT!)**
   
   **Option A: Using Google Cloud Console (GUI)**
   
   After creating the bucket:
   
   1. Go to your bucket: `dream-pedi.firebasestorage.app`
   2. Click the **PERMISSIONS** tab at the top
   3. Scroll down to find the **Public access** section
   4. Click the **PREVENT PUBLIC ACCESS** button
   5. A dialog will appear asking: "Prevent public access to this bucket?"
   6. **Click "Confirm"** - This makes your bucket private ✅
   
   **What the dialog says:**
   - "You are about to revoke all public access to the bucket..."
   - "Overrides access granted to allUsers..."
   - "Restricts public sharing of existing and future resources"
   - "Does not impact individual user permissions" ← **This is key!**
   
   **This is exactly what you want!** Click **Confirm**.
   
   ⚠️ **Important:** This only blocks **public** access (allUsers, allAuthenticatedUsers). 
   It does NOT block access from specific service accounts that you grant permission to in Step 8.
   
   **Option B: Using Command Line (Alternative)**
   
   ```bash
   gsutil pap set enforced gs://dream-pedi.firebasestorage.app
   ```
   
   **Verify it worked:**
   ```bash
   gsutil pap get gs://dream-pedi.firebasestorage.app
   ```
   Should show: `Public access prevention is enforced`

8. **Grant Access to Cloud Functions (REQUIRED!)**
   
   ⚠️ **Do this step immediately after Step 7!**
   
   Now that public access is blocked, you need to explicitly grant access to your Cloud Function's service account:
   - Click **PERMISSIONS** tab at the top
   - Click **GRANT ACCESS**
   - In "New principals" field, enter:
     ```
     dreampedi@appspot.gserviceaccount.com
     ```
     (Replace `dreampedi` with your actual project ID)
   - In "Select a role" dropdown, choose:
     ```
     Cloud Storage → Storage Object Viewer
     ```
   - Click **SAVE**

**Your private bucket is ready!** ✅

---

## Method 2: Using Command Line (Advanced)

### Prerequisites

1. **Install Google Cloud SDK**
   - Download from: https://cloud.google.com/sdk/docs/install
   - Or use: `choco install gcloudsdk` (if you have Chocolatey)

2. **Install Firebase CLI**
   ```bash
   npm install -g firebase-tools
   ```

3. **Login to Google Cloud**
   ```bash
   gcloud auth login
   gcloud config set project dreampedi
   ```

### Create Public Bucket (Firebase Storage)

```bash
# Initialize Firebase (if not done)
firebase login
firebase init storage

# This creates the default bucket automatically
# Bucket name: dream-pedi
```

### Create Private Bucket

```bash
# Create private bucket
gsutil mb -p dreampedi -c STANDARD -l us-central1 gs://dream-pedi.firebasestorage.app

# Enforce public access prevention
gsutil pap set enforced gs://dream-pedi.firebasestorage.app

# Set uniform access control
gsutil uniformbucketlevelaccess set on gs://dream-pedi.firebasestorage.app

# Remove public access (if any)
gsutil iam ch -d allUsers:objectViewer gs://dream-pedi.firebasestorage.app
gsutil iam ch -d allAuthenticatedUsers:objectViewer gs://dream-pedi.firebasestorage.app

# Grant access to Cloud Functions service account
gsutil iam ch serviceAccount:dreampedi@appspot.gserviceaccount.com:objectViewer gs://dream-pedi.firebasestorage.app

# Upload AES key
gsutil cp "c:\Users\NaB\Videos\Dream Pediatrics\converter\output\encrypted\aes_key.bin" gs://dream-pedi.firebasestorage.app/aes_key.bin

# Verify it's private (should fail)
gsutil ls gs://dream-pedi.firebasestorage.app/
```

---

## Method 3: Using Firebase CLI

### Public Bucket

```bash
# Login
firebase login

# Initialize storage
cd "c:\Users\NaB\Videos\Dream Pediatrics"
firebase init storage

# Upload files
firebase storage:upload "converter/output/encrypted/textbook.enc" textbooks/textbook.enc --project dreampedi
firebase storage:upload "converter/output/encrypted/metadata.json" textbooks/metadata.json --project dreampedi
```

### Private Bucket

Firebase CLI doesn't directly create private buckets. Use Google Cloud Console or `gsutil` (Method 2).

---

## Verification Steps

### Verify Public Bucket

1. **Test Public Access**
   ```bash
   curl -I "https://firebasestorage.googleapis.com/v0/b/dream-pedi/o/textbooks%2Ftextbook.enc?alt=media"
   ```
   - Should return: `HTTP/2 200` ✅

2. **Check in Browser**
   - Open: https://firebasestorage.googleapis.com/v0/b/dream-pedi/o/textbooks%2Ftextbook.enc?alt=media
   - Should download the file ✅

### Verify Private Bucket

1. **Test Private Access (Should Fail)**
   ```bash
   curl -I "https://storage.googleapis.com/dream-pedi.firebasestorage.app/aes_key.bin"
   ```
   - Should return: `HTTP/2 403` (Forbidden) ✅

2. **Test with Service Account (Should Work)**
   ```bash
   gsutil cat gs://dream-pedi.firebasestorage.app/aes_key.bin | wc -c
   ```
   - Should return: `32` (bytes) ✅

3. **Check Permissions**
   ```bash
   gsutil iam get gs://dream-pedi.firebasestorage.app
   ```
   - Should show service account with `objectViewer` role ✅

---

## How Public Access Prevention Works

### What It Blocks:
- ❌ **allUsers** - Anyone on the internet
- ❌ **allAuthenticatedUsers** - Anyone with a Google account
- ❌ Public URLs without authentication

### What It Does NOT Block:
- ✅ **Specific service accounts** (like your Cloud Function)
- ✅ **Specific users** you grant access to
- ✅ **IAM permissions** you explicitly set

### Example:
```
Public Access Prevention: ENFORCED
├─ ❌ https://storage.googleapis.com/bucket/file.bin (BLOCKED - public URL)
├─ ✅ Cloud Function with service account (ALLOWED - has IAM permission)
└─ ✅ gsutil with your credentials (ALLOWED - you have IAM permission)
```

**Think of it like this:**
- **Public access prevention** = Locks the front door (no anonymous access)
- **IAM permissions** = Gives specific people keys (service accounts can still access)

---

## Bucket Configuration Summary

### Public Bucket: `dream-pedi`

| Setting | Value |
|---------|-------|
| **Name** | `dream-pedi` |
| **Type** | Firebase Storage (default) |
| **Location** | us-central1 (or your region) |
| **Access** | Public read |
| **Purpose** | Store encrypted textbook for app downloads |
| **Files** | `textbooks/textbook.enc`, `textbooks/metadata.json` |
| **URL** | `https://firebasestorage.googleapis.com/v0/b/dream-pedi/o/...` |

### Private Bucket: `dream-pedi.firebasestorage.app`

| Setting | Value |
|---------|-------|
| **Name** | `dream-pedi.firebasestorage.app` |
| **Type** | Google Cloud Storage |
| **Location** | us-central1 (same as Firebase) |
| **Access** | Private (service account only) |
| **Purpose** | Store AES encryption key securely |
| **Files** | `aes_key.bin` |
| **Access Control** | Uniform, public access prevented |
| **Permissions** | `dreampedi@appspot.gserviceaccount.com` (objectViewer) |

---

## Storage Rules (Firebase Storage)

If you need to customize Firebase Storage rules:

1. **Go to Firebase Console** → **Storage** → **Rules**

2. **Default Rules (Public Read):**
   ```javascript
   rules_version = '2';
   service firebase.storage {
     match /b/{bucket}/o {
       match /textbooks/{allPaths=**} {
         allow read: if true;  // Public read
         allow write: if false; // No public write
       }
       match /{allPaths=**} {
         allow read, write: if request.auth != null; // Authenticated users
       }
     }
   }
   ```

3. **Click Publish**

---

## Troubleshooting

### Issue: "Bucket name already exists"

**Solution:** Choose a different name
```bash
# Try these alternatives:
dream-pedi-textbook
dream-pedi-secrets
dream-pedi-keys-[random-number]
```

### Issue: "Permission denied" when uploading to private bucket

**Solution:** Check your authentication
```bash
gcloud auth login
gcloud config set project dreampedi
```

### Issue: Public bucket files not accessible

**Solution:** Check Firebase Storage rules
1. Go to Firebase Console → Storage → Rules
2. Make sure `allow read: if true;` for textbooks folder

### Issue: Cloud Function can't access private bucket

**Solution:** Grant service account access
```bash
gsutil iam ch serviceAccount:dreampedi@appspot.gserviceaccount.com:objectViewer gs://dream-pedi.firebasestorage.app
```

### Issue: Can't find service account email

**Solution:** Get it from Cloud Console
1. Go to: https://console.cloud.google.com/iam-admin/serviceaccounts
2. Find: `App Engine default service account`
3. Email format: `[project-id]@appspot.gserviceaccount.com`

---

## Cost Estimation

### Public Bucket (Firebase Storage)
- **Storage:** First 5 GB free, then $0.026/GB/month
- **Downloads:** First 1 GB free, then $0.12/GB
- **Your usage:** ~313 KB × 1000 users = ~313 MB
- **Cost:** **FREE** (under free tier)

### Private Bucket (Cloud Storage)
- **Storage:** First 5 GB free, then $0.020/GB/month
- **Operations:** First 5,000 free, then $0.05/10,000
- **Your usage:** 32 bytes (AES key)
- **Cost:** **FREE** (essentially zero)

---

## Security Best Practices

✅ **DO:**
- Keep private bucket truly private (no public access)
- Use uniform access control on private bucket
- Grant minimal permissions (objectViewer, not objectAdmin)
- Enable public access prevention on private bucket
- Regularly audit bucket permissions
- Use service accounts for Cloud Functions

❌ **DON'T:**
- Make private bucket public
- Share service account keys
- Commit bucket credentials to git
- Use overly permissive IAM roles
- Store unencrypted sensitive data in public bucket

---

## Quick Reference Commands

```bash
# List all buckets
gsutil ls

# Check bucket permissions
gsutil iam get gs://dream-pedi.firebasestorage.app

# Upload file to public bucket
firebase storage:upload local-file.txt remote-path.txt

# Upload file to private bucket
gsutil cp local-file.bin gs://dream-pedi.firebasestorage.app/file.bin

# Download from private bucket
gsutil cp gs://dream-pedi.firebasestorage.app/aes_key.bin ./aes_key.bin

# Check file size
gsutil du -h gs://dream-pedi.firebasestorage.app/aes_key.bin

# Make bucket private
gsutil pap set enforced gs://dream-pedi.firebasestorage.app

# Remove public access
gsutil iam ch -d allUsers:objectViewer gs://dream-pedi.firebasestorage.app
```

---

## Next Steps

After creating both buckets:

1. ✅ Verify public bucket is accessible
2. ✅ Verify private bucket is NOT accessible publicly
3. ✅ Upload `textbook.enc` and `metadata.json` to public bucket
4. ✅ Upload `aes_key.bin` to private bucket
5. ✅ Test Cloud Function can access private bucket
6. ✅ Test app can download from public bucket

**Then proceed to:** [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)

---

**Last Updated:** 2026-05-14
**Project:** Dream Pediatrics
**Public Bucket:** `dream-pedi`
**Private Bucket:** `dream-pedi.firebasestorage.app`
