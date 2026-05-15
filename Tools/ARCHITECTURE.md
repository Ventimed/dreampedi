# Dream Pediatrics - Textbook Security Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                    DREAM PEDIATRICS TEXTBOOK SYSTEM                  │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────┐
│  DOCX Source    │  Document1.docx (Medical Textbook)
│  (Converter)    │  ↓
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
│                                                                       │
│  AES-256-GCM Encryption                                              │
│  ├─ Algorithm: AES-GCM                                               │
│  ├─ Key Size: 256 bits (32 bytes)                                   │
│  ├─ Nonce: 12 bytes (random)                                        │
│  └─ Output: nonce || ciphertext || auth_tag                         │
│                                                                       │
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
│                                                                       │
│  PUBLIC BUCKET                    PRIVATE BUCKET                     │
│  gs://dreampedi.appspot.com       gs://dreamprod                     │
│  ├─ textbooks/                    ├─ aes_key.bin                    │
│  │  ├─ textbook.enc               │  (Cloud Functions only)          │
│  │  └─ metadata.json              └─ backups/                        │
│  └─ images/                          └─ YYYYMMDD/                    │
│     └─ image_*.jpg/png                                               │
│                                                                       │
│  Access: Public Read              Access: Private                    │
│  URL: https://firebasestorage...  Service Account Only               │
│                                                                       │
└───────────────────────┬───────────────────────────────────────────┬─┘
                        │                                           │
                        ↓                                           ↓
┌─────────────────────────────────────────────────────────────────────┐
│                      CLOUD FUNCTION (Node.js)                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
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
│                                                                       │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
                                ↓
┌─────────────────────────────────────────────────────────────────────┐
│                      ANDROID APP (DP App)                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
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
│  │  ├─ chapterId (e.g., "ch01_t01")                                │
│  │  ├─ number (1-37)                                                │
│  │  ├─ title                                                         │
│  │  └─ description                                                   │
│  └─ Shared Preferences (RSA keys, metadata)                         │
│                                                                       │
│  Security:                                                           │
│  ✓ RSA private key stored in Android Keystore                       │
│  ✓ AES key never persisted (memory only)                            │
│  ✓ Decrypted content in SQLite (device-encrypted)                   │
│  ✓ Offline access after first download                              │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

## Data Flow Diagram

```
┌──────────┐
│   User   │
└────┬─────┘
     │ 1. Login
     ↓
┌─────────────────┐
│  Firebase Auth  │
└────┬────────────┘
     │ 2. ID Token
     ↓
┌─────────────────────────────────────────────────────────────┐
│                        Android App                           │
├─────────────────────────────────────────────────────────────┤
│  3. Generate RSA Key Pair                                    │
│     ├─ Public Key (send to server)                          │
│     └─ Private Key (store in Keystore)                      │
└────┬────────────────────────────────────────────────────┬───┘
     │                                                     │
     │ 4. Download textbook.enc                           │ 5. Request wrapped key
     ↓                                                     ↓
┌──────────────────┐                            ┌──────────────────┐
│ Firebase Storage │                            │  Cloud Function  │
│  (Public)        │                            │   wrapAesKey     │
└────┬─────────────┘                            └────┬─────────────┘
     │                                                │
     │ textbook.enc                                   │ 6. Fetch aes_key.bin
     │ (313 KB)                                       ↓
     │                                           ┌──────────────────┐
     │                                           │ Firebase Storage │
     │                                           │   (Private)      │
     │                                           └────┬─────────────┘
     │                                                │
     │                                                │ aes_key.bin
     │                                                ↓
     │                                           ┌──────────────────┐
     │                                           │  RSA Wrapping    │
     │                                           │  (OAEP SHA-1)    │
     │                                           └────┬─────────────┘
     │                                                │
     │                                                │ wrapped_key_b64
     ↓                                                ↓
┌─────────────────────────────────────────────────────────────┐
│                        Android App                           │
├─────────────────────────────────────────────────────────────┤
│  7. Unwrap AES key (RSA Private Key)                        │
│  8. Decrypt textbook.enc (AES-GCM)                          │
│  9. Parse JSON (37 chapters)                                │
│  10. Store in SQLite                                         │
│  11. Display in UI                                           │
└─────────────────────────────────────────────────────────────┘
```

## Security Layers

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

## File Locations

```
Dream Pediatrics/
│
├── converter/                          # Conversion & Encryption Tools
│   ├── Document1.docx                  # Source textbook
│   ├── docx_to_markdown_json.py        # DOCX → Markdown
│   ├── md_to_json.py                   # Markdown → JSON
│   ├── encrypt_dreampeditextbook_gcm.py # JSON → Encrypted
│   ├── output/
│   │   ├── output.md                   # Markdown (7,240 lines)
│   │   ├── output.json                 # JSON (313 KB, 37 chapters)
│   │   ├── images/                     # Extracted images (58 files)
│   │   └── encrypted/
│   │       ├── textbook.enc            # ✅ Upload to Firebase Storage (public)
│   │       ├── metadata.json           # ✅ Upload to Firebase Storage (public)
│   │       └── aes_key.bin             # ⚠️ Upload to Private Bucket ONLY
│   └── DEPLOYMENT_GUIDE.md             # This guide
│
├── Server Fun/                         # Cloud Functions
│   └── functions/
│       ├── index.js                    # wrapAesKey function
│       └── package.json                # Dependencies
│
├── DP App/                             # Android User App
│   └── app/src/main/java/com/dreampediatrics/app/
│       ├── ChapterEntity.java          # SQLite model
│       ├── ChapterItem.java            # UI model
│       └── ChapterActivity.java        # Chapter viewer
│
└── DP Admin/                           # Admin App
    └── app/src/main/java/com/dream/pediadmin/
        └── MainActivity.java           # Payment verification
```

## Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Source** | DOCX | Medical textbook content |
| **Conversion** | Python + python-docx | Document processing |
| **Encryption** | AES-256-GCM | Content protection |
| **Key Wrapping** | RSA-2048-OAEP | Secure key distribution |
| **Storage** | Firebase Storage | File hosting |
| **Backend** | Cloud Functions (Node.js) | Key wrapping service |
| **Authentication** | Firebase Auth | User verification |
| **Mobile** | Android (Java) | User interface |
| **Database** | SQLite (Room) | Local storage |
| **Keystore** | Android Keystore | Secure key storage |

## Performance Metrics

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

## Cost Analysis

| Service | Usage | Cost/Month | Notes |
|---------|-------|------------|-------|
| **Firebase Storage** | 313 KB × 1000 users | $0.03 | First 5 GB free |
| **Cloud Functions** | 1000 invocations | $0.00 | First 2M free |
| **Firebase Auth** | 1000 users | $0.00 | Always free |
| **Bandwidth** | 313 MB | $0.01 | First 10 GB free |
| **Total** | | **~$0.04** | Essentially free |

## Monitoring & Logging

```
Firebase Console
├── Storage
│   ├── Usage metrics
│   ├── Download counts
│   └── Bandwidth usage
├── Functions
│   ├── Invocation count
│   ├── Execution time
│   ├── Error rate
│   └── Logs
└── Authentication
    ├── Active users
    ├── Sign-in methods
    └── User activity

Cloud Logging
├── Function logs
├── Storage access logs
└── Error tracking
```

## Disaster Recovery

| Scenario | Recovery Plan | RTO | RPO |
|----------|---------------|-----|-----|
| **Storage failure** | Restore from backup | 1 hour | 24 hours |
| **Function failure** | Redeploy from git | 15 min | 0 |
| **Key compromise** | Rotate key + re-encrypt | 4 hours | 0 |
| **Data corruption** | Restore from source | 2 hours | 0 |

---

**Architecture Version:** 1.0
**Last Updated:** 2026-05-14
**Status:** Production Ready
