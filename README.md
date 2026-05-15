# Dream Pediatrics - Complete Project Suite

A comprehensive Android-based medical education platform for pediatric content delivery with secure encryption, payment management, and administrative controls.

---

## 📦 Project Overview

This repository contains four main components:

1. **DP App** - Mobile application for medical professionals and students
2. **DP Admin** - Administrative console for payment verification and user management
3. **Server Fun** - Firebase Cloud Functions for secure key distribution
4. **Tools** - Python utilities for content processing and encryption

---

## 🏗️ Architecture

### High-Level System Architecture

```
┌─────────────────┐
│   DP App        │ ← Medical students/professionals
│  (Android App)  │
└────────┬────────┘
         │
         ├─── Authentication (Firebase Auth)
         ├─── Content Download (Firebase Storage)
         ├─── Payment Submission (Firebase Realtime DB)
         └─── Key Exchange (Cloud Functions)
              │
              ▼
┌─────────────────┐         ┌──────────────────┐
│  Server Fun     │◄────────┤   DP Admin       │
│ (Cloud Functions)│         │ (Admin Console)  │
└─────────────────┘         └──────────────────┘
         │                           │
         │                           ├─── Payment Verification
         │                           ├─── User Management
         │                           └─── Push Notifications
         │
         ▼
┌─────────────────────────────────────────┐
│         Firebase Services               │
│  • Realtime Database                    │
│  • Cloud Storage (Public & Private)     │
│  • Authentication                       │
│  • Cloud Messaging (FCM)                │
└─────────────────────────────────────────┘
```

### Detailed Content Security Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                    CONTENT PROCESSING PIPELINE                        │
└──────────────────────────────────────────────────────────────────────┘

┌─────────────────┐
│  DOCX Source    │  Document1.docx (Medical Textbook)
│  (Tools)        │  ↓
└────────┬────────┘  python docx_to_markdown_json.py
         │
         ↓
┌─────────────────┐
│  Markdown       │  output.md (7,240 lines)
│  (Intermediate) │  + images/ (58 images)
└────────┬────────┘  ↓
         │           python md_to_json.py
         ↓
┌─────────────────┐
│  JSON           │  output.json (313 KB, 37 chapters)
│  (Plaintext)    │  ↓
└────────┬────────┘  python encrypt_dreampeditextbook_gcm.py
         │
         ↓
┌─────────────────────────────────────────────────────────────────────┐
│                         ENCRYPTION LAYER                             │
├─────────────────────────────────────────────────────────────────────┤
│  AES-256-GCM Encryption                                              │
│  ├─ Algorithm: AES-GCM                                               │
│  ├─ Key Size: 256 bits (32 bytes)                                   │
│  ├─ Nonce: 12 bytes (random)                                        │
│  └─ Output: nonce || ciphertext || auth_tag                         │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                │                       │
                ↓                       ↓
    ┌──────────────────┐    ┌──────────────────┐
    │  textbook.enc    │    │  aes_key.bin     │
    │  (313,684 bytes) │    │  (32 bytes)      │
    │  PUBLIC          │    │  SECRET          │
    └────────┬─────────┘    └────────┬─────────┘
             │                       │
             ↓                       ↓
┌─────────────────────────────────────────────────────────────────────┐
│                        FIREBASE STORAGE                              │
├─────────────────────────────────────────────────────────────────────┤
│  PUBLIC BUCKET                    PRIVATE BUCKET                     │
│  gs://dream-pedi                  gs://dream-pedi-secrets            │
│  ├─ textbooks/                    ├─ aes_key.bin                    │
│  │  ├─ textbook.enc               │  (Cloud Functions only)          │
│  │  └─ metadata.json              └─ backups/                        │
│  └─ images/                                                          │
│     └─ image_*.jpg/png                                               │
│                                                                       │
│  Access: Public Read              Access: Private                    │
└───────────────────────┬───────────────────────────────────────────┬─┘
                        │                                           │
                        ↓                                           ↓
┌─────────────────────────────────────────────────────────────────────┐
│                      CLOUD FUNCTION (Node.js)                        │
├─────────────────────────────────────────────────────────────────────┤
│  wrapAesKey Function                                                 │
│  ├─ Endpoint: /wrapAesKey                                           │
│  ├─ Region: us-central1                                             │
│  ├─ Authentication: Firebase ID Token (required)                    │
│  └─ Process:                                                         │
│     1. Verify user authentication                                    │
│     2. Receive device RSA public key (base64)                       │
│     3. Fetch aes_key.bin from private bucket                        │
│     4. Wrap AES key with RSA-OAEP (SHA-1)                           │
│     5. Return wrapped key (base64)                                   │
│                                                                       │
│  Security:                                                           │
│  ✓ Authenticated users only                                         │
│  ✓ Device-specific key wrapping                                     │
│  ✓ No key caching                                                   │
│  ✓ Audit logging enabled                                            │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
                                ↓
┌─────────────────────────────────────────────────────────────────────┐
│                      ANDROID APP (DP App)                            │
├─────────────────────────────────────────────────────────────────────┤
│  App Flow:                                                           │
│  1. User Authentication (Firebase Auth)                             │
│  2. Generate RSA-2048 Key Pair (device-specific)                    │
│  3. Download textbook.enc from Firebase Storage                     │
│  4. Request wrapped key from Cloud Function                         │
│     ├─ Send: ID Token + RSA Public Key                             │
│     └─ Receive: Wrapped AES Key                                     │
│  5. Unwrap AES key using RSA Private Key                            │
│  6. Decrypt textbook.enc using AES-GCM                              │
│  7. Parse JSON and store in SQLite                                  │
│  8. Display chapters in UI                                          │
│                                                                       │
│  Local Storage:                                                      │
│  ├─ SQLite Database (chapters table)                                │
│  └─ Android Keystore (RSA private key)                              │
│                                                                       │
│  Security:                                                           │
│  ✓ RSA private key stored in Android Keystore                       │
│  ✓ AES key never persisted (memory only)                            │
│  ✓ Decrypted content in SQLite (device-encrypted)                   │
│  ✓ Offline access after first download                              │
└─────────────────────────────────────────────────────────────────────┘
```

### Security Layers

The system implements **5 layers of security**:

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: Content Encryption (AES-256-GCM)                  │
│  ├─ Protects: Textbook content                              │
│  ├─ Key: 256-bit random                                     │
│  └─ Threat: Prevents unauthorized reading                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  Layer 2: Key Storage (Private Bucket)                      │
│  ├─ Protects: AES encryption key                            │
│  ├─ Access: Cloud Functions only                            │
│  └─ Threat: Prevents key theft                              │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  Layer 3: Key Wrapping (RSA-OAEP)                           │
│  ├─ Protects: Key transmission                              │
│  ├─ Method: Device-specific RSA public key                  │
│  └─ Threat: Prevents man-in-the-middle attacks              │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  Layer 4: Authentication (Firebase Auth)                    │
│  ├─ Protects: API access                                    │
│  ├─ Method: ID token verification                           │
│  └─ Threat: Prevents unauthorized access                    │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  Layer 5: Device Security (Android Keystore)                │
│  ├─ Protects: RSA private key                               │
│  ├─ Storage: Hardware-backed (if available)                 │
│  └─ Threat: Prevents key extraction                         │
└─────────────────────────────────────────────────────────────┘
```

---

## 📱 1. DP App (Mobile Application)

### Overview
Android application for accessing encrypted pediatric medical textbook content with offline support, bookmarks, search, and payment integration.

### Key Features
- **Secure Content Delivery**: AES-256-GCM encryption with RSA key wrapping
- **Authentication**: Firebase email/password with device verification
- **Payment Integration**: Multiple payment methods (CBE, Telebirr, E-Birr)
- **Offline Access**: Local SQLite database with Room
- **Full-Text Search**: Fast content search using FTS
- **Reading Features**: Bookmarks, history tracking, progress monitoring
- **Push Notifications**: FCM integration for admin notifications

### Technology Stack
- **Language**: Java
- **Min SDK**: Android 24 (Android 7.0)
- **Target SDK**: 34
- **Database**: Room with SQLCipher encryption support
- **Key Libraries**: Firebase SDK, PhotoView, Material Design Components

### Quick Start
```bash
cd "DP App"
# Add google-services.json to app/ directory
# Update Cloud Function URL in MainActivity.java
# Build and run
```

### Documentation
See [DP App/README.md](DP%20App/README.md) for complete build instructions, Firebase setup, and deployment guide.

---

## 🔐 2. DP Admin (Admin Console)

### Overview
Android application for administrators to manage premium subscriptions, verify payments, and send push notifications to app users.

### Key Features
- **Payment Management**: View and verify pending payment requests
- **Premium User Management**: Track verified premium users
- **Push Notifications**: Send targeted or broadcast notifications via FCM
- **Payment Actions**: Verify, lock/unlock features, delete requests
- **Real-time Dashboard**: Live statistics and payment tracking
- **Offline Support**: Cached authentication for offline access

### Technology Stack
- **Language**: Java
- **Min SDK**: Android 24 (Android 7.0)
- **Target SDK**: 36
- **Key Libraries**: Firebase SDK, Google Auth OAuth2, OkHttp, Material Design

### Quick Start
```bash
cd "DP Admin"
# Add google-services.json to app/ directory
# Update service account credentials in AccessToken.java
# Update Firebase project ID in MainActivity.java
# Build and run
```

### Documentation
See [DP Admin/README.md](DP%20Admin/README.md) for complete setup, Firebase configuration, and security guidelines.

---

## ☁️ 3. Server Fun (Cloud Functions)

### Overview
Firebase Cloud Functions for secure AES key distribution using device-specific RSA key wrapping.

### Key Features
- **Secure Key Exchange**: RSA-OAEP key wrapping with device public keys
- **Authentication Required**: Firebase ID token verification
- **Private Bucket Access**: Fetches AES keys from secure storage
- **Audit Logging**: Tracks all key distribution requests
- **Environment Configuration**: Flexible bucket and path configuration

### Technology Stack
- **Runtime**: Node.js 20
- **Region**: us-central1
- **Authentication**: Firebase Admin SDK
- **Encryption**: Node.js crypto module (RSA-OAEP)

### Quick Deploy
```bash
cd "Server Fun"
deploy.cmd
```

Or manually:
```bash
firebase deploy --only functions:wrapAesKey --project dream-pedi
```

### Configuration
Edit `functions/.env.yaml`:
```yaml
TEXTBOOK_KEY_BUCKET: dream-pedi-secrets
TEXTBOOK_KEY_PATH: aes_key.bin
```

### Documentation
See [Server Fun/README.md](Server%20Fun/README.md) for deployment guide, testing, and troubleshooting.

---

## 🛠️ 4. Tools (Content Processing Utilities)

### Overview
Python scripts for converting, processing, and encrypting textbook content for the Dream Pediatrics platform.

### Available Tools

#### 📄 docx_to_markdown_json.py
Converts DOCX files to Markdown format with image extraction.

**Features:**
- Preserves formatting (bold, italic, tables, lists)
- Extracts inline images
- Handles nested bullet points
- Supports sub-bullets and indentation

**Usage:**
```bash
cd Tools
python docx_to_markdown_json.py
```

**Input:** `Document1.docx`  
**Output:** `output/output.md` + `output/images/`

---

#### 📝 md_to_json.py
Converts Markdown files to structured JSON format for the mobile app.

**Features:**
- Parses chapter structure from markdown
- Extracts chapter titles and content
- Identifies embedded images
- Creates hierarchical JSON structure
- Sequential topic ID generation (ch01_t01, ch01_t02, etc.)

**Usage:**
```bash
cd Tools
python md_to_json.py
```

**Input:** `output/output.md`  
**Output:** `output/output.json`

**JSON Structure:**
```json
{
  "chapters": [
    {
      "id": "ch01",
      "number": 1,
      "title": "Dream Pediatrics Textbook",
      "description": "Complete pediatrics textbook",
      "topics": [
        {
          "id": "ch01_t01",
          "number": 1,
          "title": "**CHAPTER 1**: **Title**",
          "content": "HTML content...",
          "images": ["images/image_1.jpg"]
        }
      ]
    }
  ]
}
```

---

#### 🔒 encrypt_dreampeditextbook_gcm.py
Encrypts JSON textbook content using AES-256-GCM encryption.

**Features:**
- AES-256-GCM encryption
- Random nonce generation (12 bytes)
- Generates encryption key (32 bytes)
- Creates metadata file
- Secure key storage instructions

**Usage:**
```bash
cd Tools
python encrypt_dreampeditextbook_gcm.py
```

**Input:** `output/output.json`  
**Output:**
- `output/encrypted/textbook.enc` - Encrypted content (nonce + ciphertext + tag)
- `output/encrypted/metadata.json` - Encryption metadata
- `output/encrypted/aes_key.bin` - AES encryption key (KEEP SECURE!)

**Security Notes:**
- ✅ Upload `textbook.enc` and `metadata.json` to public Firebase Storage
- ❌ **NEVER** upload `aes_key.bin` to public storage
- ✅ Store `aes_key.bin` in private bucket (`gs://dream-pedi-secrets/`)

---

### Tools Workflow

Complete content processing pipeline:

```bash
# Step 1: Convert DOCX to Markdown
cd Tools
python docx_to_markdown_json.py
# Output: output/output.md (7,240 lines) + images/ (58 images)

# Step 2: Convert Markdown to JSON
python md_to_json.py
# Output: output/output.json (313 KB, 37 chapters)

# Step 3: Encrypt JSON content
python encrypt_dreampeditextbook_gcm.py
# Output: 
#   - output/encrypted/textbook.enc (313,684 bytes)
#   - output/encrypted/metadata.json (132 bytes)
#   - output/encrypted/aes_key.bin (32 bytes) ⚠️ KEEP SECURE!

# Step 4: Upload to Firebase Storage
# Public bucket (app downloads)
gsutil cp output/encrypted/textbook.enc gs://dream-pedi/textbooks/
gsutil cp output/encrypted/metadata.json gs://dream-pedi/textbooks/

# Private bucket (Cloud Functions only)
gsutil cp output/encrypted/aes_key.bin gs://dream-pedi-secrets/

# Step 5: Deploy Cloud Function (if not already deployed)
cd "../Server Fun"
firebase deploy --only functions:wrapAesKey --project dream-pedi
```

### Performance Metrics

| Metric | Value | Notes |
|--------|-------|-------|
| **Chapters** | 37 | Complete textbook |
| **File Size** | 313 KB | Compressed JSON |
| **Encryption Overhead** | 28 bytes | Nonce + auth tag |
| **Download Time** | ~2 seconds | On 4G connection |
| **Decryption Time** | <1 second | On modern device |
| **Storage (App)** | ~500 KB | SQLite + images |
| **First Load** | ~5 seconds | Download + decrypt + parse |
| **Subsequent Loads** | Instant | From SQLite cache |

### Dependencies
```bash
pip install python-docx cryptography
```

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio** (Arctic Fox or later)
- **Java Development Kit (JDK) 11+**
- **Node.js 20+** (for Cloud Functions)
- **Firebase CLI** (`npm install -g firebase-tools`)
- **Python 3.8+** (for Tools)
- **Firebase Project** with Blaze plan (for Cloud Functions)
- **Google Cloud SDK** (optional, for advanced operations)

### Quick Start Guide

#### 1. Firebase Project Setup
```bash
# Login to Firebase
firebase login

# Initialize project
firebase init

# Select:
# - Functions (Cloud Functions)
# - Database (Realtime Database)
# - Storage (Cloud Storage)
# - Hosting (optional)
```

#### 2. Enable Firebase Services
- **Authentication**: Email/Password provider
- **Realtime Database**: Create database with security rules
- **Cloud Storage**: Create two buckets:
  - `dream-pedi` (public) - for encrypted content
  - `dream-pedi-secrets` (private) - for AES keys
- **Cloud Messaging**: Enabled by default
- **Upgrade to Blaze Plan**: Required for Cloud Functions

#### 3. Set Up Storage Buckets

**Public Bucket (Default Firebase Storage):**
```bash
# Already created with Firebase project
# Bucket name: dream-pedi or dream-pedi.appspot.com
# Access: Public read
# Purpose: Encrypted textbook files
```

**Private Bucket (For Secrets):**
```bash
# Create private bucket
gsutil mb -p dream-pedi -c STANDARD -l us-central1 gs://dream-pedi-secrets

# Enforce public access prevention
gsutil pap set enforced gs://dream-pedi-secrets

# Grant access to Cloud Functions service account
gsutil iam ch serviceAccount:dream-pedi@appspot.gserviceaccount.com:objectViewer gs://dream-pedi-secrets
```

**Detailed bucket setup:** See [Tools/BUCKET_SETUP_GUIDE.md](Tools/BUCKET_SETUP_GUIDE.md)

#### 4. Configure Projects

**DP App:**
1. Add `google-services.json` to `DP App/app/`
2. Update Cloud Function URL in `MainActivity.java` (line ~70):
   ```java
   private static final String CLOUD_FUNCTION_WRAP_URL = 
       "https://us-central1-dream-pedi.cloudfunctions.net/wrapAesKey";
   ```
3. Build and install

**DP Admin:**
1. Add `google-services.json` to `DP Admin/app/`
2. Update service account JSON in `AccessToken.java`
3. Update Firebase project ID in `MainActivity.java` (lines ~450, ~490):
   ```java
   .url("https://fcm.googleapis.com/v1/projects/YOUR_PROJECT_ID/messages:send")
   ```
4. Build and install

**Server Fun:**
1. Edit `functions/.env.yaml` with bucket configuration:
   ```yaml
   TEXTBOOK_KEY_BUCKET: dream-pedi-secrets
   TEXTBOOK_KEY_PATH: aes_key.bin
   ```
2. Deploy: `firebase deploy --only functions:wrapAesKey --project dream-pedi`
3. Note the function URL for DP App configuration

**Detailed Cloud Functions deployment:** See [Tools/CLOUD_FUNCTIONS_DEPLOYMENT_GUIDE.md](Tools/CLOUD_FUNCTIONS_DEPLOYMENT_GUIDE.md)

#### 5. Process Content
```bash
cd Tools

# Convert DOCX to Markdown
python docx_to_markdown_json.py

# Convert Markdown to JSON
python md_to_json.py

# Encrypt JSON content
python encrypt_dreampeditextbook_gcm.py
```

**Detailed content processing:** See [Tools/DEPLOYMENT_GUIDE.md](Tools/DEPLOYMENT_GUIDE.md)

#### 6. Upload Content
```bash
# Upload to public bucket (app downloads)
gsutil cp output/encrypted/textbook.enc gs://dream-pedi/textbooks/
gsutil cp output/encrypted/metadata.json gs://dream-pedi/textbooks/

# Upload to private bucket (Cloud Functions only)
gsutil cp output/encrypted/aes_key.bin gs://dream-pedi-secrets/

# Verify uploads
gsutil ls gs://dream-pedi/textbooks/
gsutil ls gs://dream-pedi-secrets/
```

#### 7. Set Firebase Security Rules

**Realtime Database Rules:**
```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid || auth.uid === 'ADMIN_UID'",
        ".write": "$uid === auth.uid || auth.uid === 'ADMIN_UID'"
      }
    },
    "payments": {
      "$uid": {
        ".read": "$uid === auth.uid || auth.uid === 'ADMIN_UID'",
        ".write": "$uid === auth.uid"
      }
    },
    "verified": {
      ".read": "auth.uid === 'ADMIN_UID'",
      ".write": "auth.uid === 'ADMIN_UID'"
    }
  }
}
```

**Storage Rules:**
```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /textbooks/{allPaths=**} {
      allow read: if request.auth != null;  // Authenticated users
      allow write: if false;  // No public write
    }
    match /{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

#### 8. Test the System

**Test DP App:**
1. Install app on device/emulator
2. Register new account
3. Verify email
4. Login
5. Submit test payment
6. Wait for admin verification

**Test DP Admin:**
1. Install admin app
2. Login with admin credentials
3. Verify test payment
4. Check user receives notification
5. Verify content unlocks in DP App

**Test Content Download:**
1. Open DP App after verification
2. Content should download automatically
3. Verify all 37 chapters appear
4. Test bookmarks, search, and history

---

## 🔐 Security Architecture

### Content Encryption Flow

```
1. Content Creation (Tools)
   ├─ DOCX → Markdown → JSON
   └─ Encrypt with AES-256-GCM
       ├─ Generate random AES key (32 bytes)
       ├─ Generate random nonce (12 bytes)
       └─ Output: nonce + ciphertext + auth_tag

2. Key Storage
   ├─ textbook.enc → Public bucket (gs://dream-pedi/)
   └─ aes_key.bin → Private bucket (gs://dream-pedi-secrets/)

3. App Key Exchange (Server Fun)
   ├─ Device generates RSA key pair (Android Keystore)
   ├─ Device sends public key to Cloud Function
   ├─ Cloud Function fetches AES key from private bucket
   ├─ Cloud Function wraps AES key with device public key
   └─ Returns wrapped key to device

4. Content Decryption (DP App)
   ├─ Device unwraps AES key using private key
   ├─ Downloads encrypted content from public bucket
   ├─ Decrypts content using AES-256-GCM
   └─ Populates local Room database
```

### Security Features
- ✅ **End-to-End Encryption**: Content encrypted at rest and in transit
- ✅ **Device-Specific Keys**: RSA key wrapping per device
- ✅ **Android Keystore**: Hardware-backed key storage
- ✅ **Authentication Required**: Firebase ID token verification
- ✅ **Private Key Storage**: AES keys in private bucket
- ✅ **No Key Caching**: Keys fetched on-demand
- ✅ **Audit Logging**: All key exchanges logged

---

## 📊 Firebase Database Structure

```json
{
  "users": {
    "{userId}": {
      "deviceId": "unique_device_id",
      "loggedIn": true,
      "featuresLocked": false,
      "fcm": "fcm_token",
      "serverTime": 1234567890
    }
  },
  "payments": {
    "{userId}": {
      "userName": "John Doe",
      "paymentMethod": "telebirr",
      "transactionId": "TXN123456",
      "fcm": "fcm_token",
      "amount": "500",
      "date": "2024-01-15",
      "status": "pending"
    }
  },
  "verified": {
    "{userId}": {
      "userName": "John Doe",
      "paymentMethod": "telebirr",
      "transactionId": "TXN123456",
      "serverTime": 1234567890
    }
  }
}
```

---

## 🔄 User Workflow

### Student/Professional Workflow (DP App)

1. **Registration**
   - Download and install DP App
   - Register with email/password
   - Verify email address
   - Login with credentials

2. **Payment**
   - App shows locked features
   - Click "Purchase" button
   - Select payment method (CBE/Telebirr/E-Birr)
   - Complete payment via bank/mobile money
   - Submit transaction ID in app
   - Wait for admin verification

3. **Content Access**
   - Admin verifies payment
   - App receives notification
   - Features unlock automatically
   - Content downloads and decrypts
   - Access all chapters and topics offline

4. **Usage**
   - Browse chapters and topics
   - Bookmark favorite topics
   - Search content
   - Track reading history
   - Resume from last position

### Admin Workflow (DP Admin)

1. **Login**
   - Open DP Admin app
   - Login with admin credentials
   - View dashboard

2. **Payment Verification**
   - Check "Home" tab for pending payments
   - Review payment details (method, transaction ID, amount)
   - Verify transaction with bank/payment provider
   - Click "Verify" to unlock user features
   - Click "Save as Premium" to move to verified list
   - User receives notification

3. **User Management**
   - View all premium users in "Premium" tab
   - Search by UID or transaction ID
   - Lock/unlock features as needed
   - Delete invalid payment requests

4. **Notifications**
   - Go to "Notifications" tab
   - Send broadcast to all users
   - Send targeted notification to specific user
   - Enter title and message
   - Click "Send"

---

## 📈 Monitoring and Maintenance

### Firebase Console Monitoring

**Authentication:**
- Monitor user sign-ups and logins
- Track email verification rates
- Review authentication errors

**Realtime Database:**
- Monitor read/write operations
- Track database size
- Review security rule violations

**Cloud Storage:**
- Monitor storage usage
- Track download bandwidth
- Review access logs

**Cloud Functions:**
- Monitor invocation count
- Track execution time and errors
- Review function logs: `firebase functions:log`

**Cloud Messaging:**
- Track notification delivery rates
- Monitor FCM token registrations
- Review notification errors

### Regular Maintenance Tasks

**Weekly:**
- Check Firebase Console for errors
- Review user feedback and issues
- Monitor app crashes (Firebase Crashlytics)
- Verify payment processing

**Monthly:**
- Update Android dependencies
- Review and optimize database queries
- Check for Android Studio updates
- Test on latest Android versions
- Backup Firebase data

**Quarterly:**
- Rotate encryption keys
- Update Firebase security rules
- Audit user accounts
- Review and update privacy policy
- Performance optimization

### Cost Analysis

#### Firebase Services Pricing (1,000 users/month)

| Service | Usage | Cost/Month | Notes |
|---------|-------|------------|-------|
| **Firebase Storage** | 313 KB × 1000 = 313 MB | $0.01 | First 5 GB free |
| **Cloud Functions** | 1,000 invocations | $0.00 | First 2M free |
| **Firebase Auth** | 1,000 users | $0.00 | Always free |
| **Realtime Database** | ~10 MB data | $0.00 | First 1 GB free |
| **Bandwidth** | 313 MB downloads | $0.01 | First 10 GB free |
| **Cloud Messaging** | 1,000 notifications | $0.00 | Always free |
| **Total** | | **~$0.02** | Essentially free |

#### At Scale (10,000 users/month)

| Service | Usage | Cost/Month | Notes |
|---------|-------|------------|-------|
| **Firebase Storage** | 3.13 GB | $0.08 | Still under 5 GB free tier |
| **Cloud Functions** | 10,000 invocations | $0.00 | Still under 2M free tier |
| **Bandwidth** | 3.13 GB | $0.00 | Still under 10 GB free tier |
| **Realtime Database** | ~100 MB | $0.00 | Still under 1 GB free tier |
| **Total** | | **~$0.08** | Minimal cost |

#### Performance Metrics

| Metric | Value | Impact |
|--------|-------|--------|
| **Chapters** | 37 | Complete textbook |
| **File Size** | 313 KB | Compressed JSON |
| **Encryption Overhead** | 28 bytes | Negligible |
| **Download Time (4G)** | ~2 seconds | Excellent |
| **Decryption Time** | <1 second | Excellent |
| **First Load Time** | ~5 seconds | Good |
| **Offline Access** | Instant | Excellent |
| **Storage per User** | ~500 KB | Minimal |

---

## 🐛 Troubleshooting

### Common Issues

#### DP App: Content Not Downloading
**Symptoms:** Loading dialog appears but content never loads

**Solutions:**
1. Check Cloud Function logs: `firebase functions:log`
2. Verify Storage rules allow authenticated reads
3. Check `featuresLocked = false` in database
4. Verify device internet connection
5. Check Logcat: `adb logcat | grep MainActivity`

#### DP Admin: Notifications Not Sending
**Symptoms:** "Failed to send notification" error

**Solutions:**
1. Verify service account JSON is correct in `AccessToken.java`
2. Check Firebase project ID in FCM URL
3. Ensure device has internet connection
4. Check Firebase Console → Cloud Messaging for errors
5. Verify OAuth2 token generation is successful

#### Server Fun: Key Wrapping Failed
**Symptoms:** "AES key not found on server" error

**Solutions:**
1. Check bucket name in `.env.yaml`
2. Verify `aes_key.bin` exists in private bucket
3. Check service account permissions
4. Review function logs: `firebase functions:log`

#### Tools: Encryption Failed
**Symptoms:** Python script errors during encryption

**Solutions:**
1. Verify `output.json` exists
2. Install dependencies: `pip install cryptography`
3. Check file permissions
4. Verify Python version (3.8+)

---

## 📚 Additional Documentation

### Project Documentation
- **[DP App/README.md](DP%20App/README.md)** - Complete mobile app build guide with Firebase setup
- **[DP Admin/README.md](DP%20Admin/README.md)** - Admin console setup and payment management
- **[Server Fun/README.md](Server%20Fun/README.md)** - Cloud Functions quick deployment guide

### Tools Documentation
- **[Tools/ARCHITECTURE.md](Tools/ARCHITECTURE.md)** - Detailed security architecture and data flow diagrams
- **[Tools/DEPLOYMENT_GUIDE.md](Tools/DEPLOYMENT_GUIDE.md)** - Step-by-step encrypted textbook deployment
- **[Tools/BUCKET_SETUP_GUIDE.md](Tools/BUCKET_SETUP_GUIDE.md)** - Firebase Storage bucket configuration (public & private)
- **[Tools/CLOUD_FUNCTIONS_DEPLOYMENT_GUIDE.md](Tools/CLOUD_FUNCTIONS_DEPLOYMENT_GUIDE.md)** - Complete Cloud Functions deployment with troubleshooting

### Root Documentation
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - System architecture overview (if exists)
- **[DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)** - General deployment guide (if exists)
- **[BUCKET_SETUP_GUIDE.md](BUCKET_SETUP_GUIDE.md)** - Bucket setup guide (if exists)
- **[CLOUD_FUNCTIONS_DEPLOYMENT_GUIDE.md](CLOUD_FUNCTIONS_DEPLOYMENT_GUIDE.md)** - Cloud Functions guide (if exists)

---

## 🔧 Development

### Project Structure
```
Dream Pediatrics/
├── DP App/                    # Mobile application
│   ├── app/
│   │   ├── src/main/java/com/dreampediatrics/app/
│   │   │   ├── activities/
│   │   │   ├── fragments/
│   │   │   ├── database/
│   │   │   └── utils/
│   │   └── build.gradle
│   └── README.md
│
├── DP Admin/                  # Admin console
│   ├── app/
│   │   ├── src/main/java/com/dream/pediadmin/
│   │   │   ├── LoginActivity.java
│   │   │   ├── MainActivity.java
│   │   │   ├── AccessToken.java
│   │   │   └── adapters/
│   │   └── build.gradle
│   └── README.md
│
├── Server Fun/                # Cloud Functions
│   ├── functions/
│   │   ├── index.js
│   │   ├── package.json
│   │   └── .env.yaml
│   ├── firebase.json
│   ├── deploy.cmd
│   └── README.md
│
├── Tools/                     # Content processing utilities
│   ├── docx_to_markdown_json.py
│   ├── md_to_json.py
│   ├── encrypt_dreampeditextbook_gcm.py
│   └── output/
│       ├── output.md
│       ├── output.json
│       ├── images/
│       └── encrypted/
│
├── converter/                 # Legacy converter (deprecated)
├── ARCHITECTURE.md
├── DEPLOYMENT_GUIDE.md
├── BUCKET_SETUP_GUIDE.md
└── README.md                  # This file
```

### Version Information

**DP App:**
- Version: 2.1.0
- Min SDK: 24 (Android 7.0)
- Target SDK: 34
- Package: `com.dreampediatrics.app`

**DP Admin:**
- Version: 1.0
- Min SDK: 24 (Android 7.0)
- Target SDK: 36
- Package: `com.dream.pediadmin`

**Server Fun:**
- Runtime: Node.js 20
- Region: us-central1
- Function: `wrapAesKey`

---

## 🤝 Contributing

This is a proprietary project for Dream Pediatrics. For internal development:

1. Create feature branch from `main`
2. Make changes and test thoroughly
3. Update relevant documentation
4. Submit pull request for review
5. Merge after approval

---

## 📄 License

Proprietary - Dream Pediatrics  
All rights reserved.

---

## 📞 Support

For technical support or questions:
- **Email**: support@dreampediatrics.com
- **Documentation**: See individual README files in each project folder
- **Firebase Console**: https://console.firebase.google.com/project/dream-pedi

---

## ✅ Quick Reference

### Essential Commands

**Firebase:**
```bash
firebase login
firebase deploy --only functions
firebase functions:log
gsutil ls gs://dream-pedi/
```

**Android:**
```bash
# Build APK
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk

# View logs
adb logcat | grep MainActivity
```

**Tools:**
```bash
cd Tools
python docx_to_markdown_json.py
python md_to_json.py
python encrypt_dreampeditextbook_gcm.py
```

### Important URLs

- **Firebase Console**: https://console.firebase.google.com/project/dream-pedi
- **Cloud Function**: https://us-central1-dream-pedi.cloudfunctions.net/wrapAesKey
- **Public Bucket**: gs://dream-pedi/
- **Private Bucket**: gs://dream-pedi-secrets/

---

**Last Updated**: May 2026  
**Maintained by**: Dream Pediatrics Development Team
