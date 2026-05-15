# How to Set a Default Firebase Storage Bucket

Guide to configure and set a default storage bucket for your Firebase project.

---

## 📋 Your Current Buckets

Based on your project, you have:

| Bucket Name | Type | Purpose |
|-------------|------|---------|
| `dream-pedi` | Firebase Storage (default) | Public files (textbook.enc, metadata.json) |
| `dream-pedi-secrets` | Cloud Storage | Private files (aes_key.bin) |

---

## 🎯 Understanding Default Buckets

### What is a Default Bucket?

The **default bucket** is the Firebase Storage bucket that:
- Is automatically used when you call `admin.storage().bucket()` without parameters
- Is used by Firebase Storage SDK in your app by default
- Is shown first in Firebase Console → Storage

### Current Default

Your current default bucket is: **`dream-pedi`**

This is correct for your use case because:
- ✅ Your app downloads public files from this bucket
- ✅ Your Cloud Function explicitly specifies the private bucket
- ✅ Firebase Console shows this bucket by default

---

## ⚙️ Method 1: Set Default in Firebase Admin SDK (Code)

### In Cloud Functions

Your Cloud Function already uses explicit bucket names, which is the **recommended approach**:

```javascript
// Explicit bucket (recommended for security)
const bucket = admin.storage().bucket('dream-pedi-secrets');

// Default bucket (uses project default)
const bucket = admin.storage().bucket();
```

**Current code (correct):**
```javascript
const keyBucket = KEY_BUCKET.value(); // 'dream-pedi-secrets'
const bucket = admin.storage().bucket(keyBucket); // Explicit
```

### In Android App

```java
// Use default bucket
FirebaseStorage storage = FirebaseStorage.getInstance();
StorageReference storageRef = storage.getReference();

// Use specific bucket
FirebaseStorage storage = FirebaseStorage.getInstance("gs://dream-pedi");
StorageReference storageRef = storage.getReference();
```

---

## ⚙️ Method 2: Configure Default in firebase.json

Add storage configuration to `firebase.json`:

```json
{
  "storage": {
    "rules": "storage.rules"
  },
  "functions": [
    {
      "codebase": "default",
      "source": "functions"
    }
  ]
}
```

Then create `storage.rules`:

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // Public read for textbooks folder
    match /textbooks/{allPaths=**} {
      allow read: if true;
      allow write: if false;
    }
    
    // Authenticated users only for other files
    match /{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

---

## ⚙️ Method 3: Set Default via Firebase Console

### Step 1: Go to Firebase Console

Visit: https://console.firebase.google.com/project/dream-pedi/storage

### Step 2: View Your Buckets

You should see:
- **dream-pedi** (default Firebase Storage bucket)
- Other buckets may not appear here (they're Cloud Storage buckets)

### Step 3: The Default is Already Set

Firebase automatically uses `dream-pedi` as the default because:
1. It's the Firebase Storage bucket (not just Cloud Storage)
2. It matches your project ID pattern
3. It was created when you initialized Firebase Storage

**You don't need to change anything!** ✅

---

## ⚙️ Method 4: Set Default via gcloud CLI

If you want to change which bucket is used by default in your code:

```bash
# Set default bucket for Firebase Admin SDK
gcloud config set storage/default_bucket dream-pedi

# Verify
gcloud config get-value storage/default_bucket
```

**Note:** This only affects gcloud commands, not Firebase SDK behavior.

---

## 🔍 How to Check Your Default Bucket

### Method 1: Firebase Console

```
https://console.firebase.google.com/project/dream-pedi/storage
```

The first bucket shown is your default.

### Method 2: Firebase Admin SDK

```javascript
// In Cloud Functions, log the default bucket
const defaultBucket = admin.storage().bucket();
console.log('Default bucket:', defaultBucket.name);
```

### Method 3: Check Project Settings

```bash
# Get project info
firebase projects:list

# Check storage configuration
cat firebase.json
```

---

## 📝 Recommended Configuration for Your Project

### Keep Current Setup (Recommended) ✅

Your current configuration is **optimal**:

1. **Default bucket: `dream-pedi`**
   - Used for public files (textbook.enc, metadata.json)
   - Accessible by app without authentication
   - Shown in Firebase Console

2. **Private bucket: `dream-pedi-secrets`**
   - Explicitly specified in Cloud Function
   - Not accessible publicly
   - Only Cloud Function can access

### Why This is Best

- ✅ **Security**: Private bucket is separate and explicit
- ✅ **Clarity**: Code clearly shows which bucket is used
- ✅ **Flexibility**: Easy to change bucket per operation
- ✅ **Best Practice**: Explicit is better than implicit

---

## 🔧 If You Want to Change Default Bucket

### Scenario: Make `dream-pedi-secrets` the Default

**Not recommended**, but here's how:

#### Step 1: Update Cloud Function Code

```javascript
// Change default in index.js
const KEY_BUCKET = defineString('TEXTBOOK_KEY_BUCKET', {
  description: 'Private bucket name where aes_key.bin is stored',
  default: 'dream-pedi-secrets'  // This is already your default!
});
```

#### Step 2: Update App Code

```java
// In Android app, specify the public bucket explicitly
FirebaseStorage publicStorage = FirebaseStorage.getInstance("gs://dream-pedi");
StorageReference textbookRef = publicStorage.getReference("textbooks/textbook.enc");

// Private bucket access via Cloud Function (no change needed)
```

---

## 🎯 Quick Reference

### Your Configuration

```
Default Firebase Storage Bucket: dream-pedi
├─ Used by: Android app (default)
├─ Contains: textbook.enc, metadata.json
└─ Access: Public read

Private Cloud Storage Bucket: dream-pedi-secrets
├─ Used by: Cloud Function (explicit)
├─ Contains: aes_key.bin
└─ Access: Service account only
```

### Commands

```bash
# List all buckets
gsutil ls

# Check default bucket in gcloud
gcloud config get-value storage/default_bucket

# Set default bucket in gcloud
gcloud config set storage/default_bucket dream-pedi

# View Firebase Storage in console
start https://console.firebase.google.com/project/dream-pedi/storage
```

---

## 🐛 Troubleshooting

### Issue: App can't access default bucket

**Solution:** Verify Firebase Storage is initialized:

```java
// In Android app
FirebaseStorage storage = FirebaseStorage.getInstance();
// This uses: gs://dream-pedi (your default)
```

### Issue: Cloud Function uses wrong bucket

**Solution:** Check environment variables:

```bash
# View current config
cat functions\.env.yaml

# Should show:
# TEXTBOOK_KEY_BUCKET: dream-pedi-secrets
```

### Issue: Want to use different bucket in app

**Solution:** Specify bucket explicitly:

```java
// Use specific bucket
FirebaseStorage storage = FirebaseStorage.getInstance("gs://dream-pedi-secrets");
```

---

## 📚 Related Documentation

- **Firebase Storage Docs**: https://firebase.google.com/docs/storage
- **Admin SDK Storage**: https://firebase.google.com/docs/admin/setup#initialize-sdk
- **Cloud Storage Docs**: https://cloud.google.com/storage/docs

---

## ✅ Summary

**Your current setup is already optimal!**

- ✅ Default bucket (`dream-pedi`) is correct for public files
- ✅ Private bucket (`dream-pedi-secrets`) is explicitly specified
- ✅ No changes needed

**If you want to verify:**

```bash
# Check what's in your default bucket
gsutil ls gs://dream-pedi/

# Check what's in your private bucket
gsutil ls gs://dream-pedi-secrets/
```

---

**Default Bucket:** `dream-pedi`  
**Private Bucket:** `dream-pedi-secrets`  
**Status:** ✅ Correctly configured  
**Action Required:** None
