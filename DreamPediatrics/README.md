# Dream Pediatrics Mobile App

## Overview

**Dream Pediatrics** is a comprehensive Android mobile application designed for medical professionals and students to access pediatric medical textbook content. The app features secure content delivery, offline access, payment integration, bookmark management, search functionality, and reading history tracking.

## Key Features

### 1. **Secure Content Delivery**
- **AES-256-GCM Encryption**: Textbook content is encrypted and securely stored
- **RSA-OAEP Key Wrapping**: Device-specific key wrapping using Android Keystore
- **Cloud Function Integration**: Secure key exchange via Firebase Cloud Functions
- **Encrypted JSON Download**: Content downloaded and decrypted on-device

### 2. **Authentication & Authorization**
- Firebase Authentication (Email/Password)
- Email verification required
- Device ID verification to prevent account sharing
- Offline authentication support after first login
- Feature lock/unlock based on payment verification

### 3. **Payment Integration**
- In-app payment dialog with multiple payment methods:
  - **CBE Bank Transfer**
  - **Telebirr**
  - **E-Birr**
  - Manual payment submission
- Payment verification workflow
- FCM token submission for admin notifications
- Payment status tracking (Pending/Verified)

### 4. **Content Management**
- **Room Database**: Local SQLite database with encryption support (SQLCipher)
- **Chapter-based Organization**: Hierarchical content structure
- **Full-Text Search (FTS)**: Fast content search using Room FTS
- **Image Support**: Embedded images in topics
- **Offline Access**: All content available offline after download

### 5. **Reading Features**
- **Bookmarks**: Save and manage favorite topics
- **Reading History**: Track recently viewed topics with progress
- **Resume Reading**: Automatically resume from last opened topic
- **Progress Tracking**: Track reading progress per topic
- **PhotoView Integration**: Pinch-to-zoom for images

### 6. **User Interface**
- **Bottom Navigation**: Five main sections (Home, History, Bookmarks, Search, Settings)
- **Material Design**: Modern UI with Material Components
- **Dark Mode Support**: System-wide dark theme toggle
- **Custom Toolbar**: User initial display with profile access
- **Responsive Layouts**: Optimized for various screen sizes

### 7. **Push Notifications**
- Firebase Cloud Messaging (FCM) integration
- Topic subscription for broadcast notifications
- Admin-triggered notifications for payment verification
- Notification permission handling (Android 13+)

### 8. **Search Functionality**
- Full-text search across all topics
- Recent search history
- Search result highlighting
- Chapter and topic filtering

## Technical Architecture

### Technology Stack
- **Language**: Java
- **Minimum SDK**: Android 24 (Android 7.0)
- **Target SDK**: 34
- **Build System**: Gradle
- **Database**: Room (SQLite) with SQLCipher encryption

### Key Dependencies
```gradle
- AndroidX AppCompat 1.6.1
- Material Design Components 1.12.0
- Firebase Auth, Database, Storage, Messaging
- Room Database 2.6.1
- SQLCipher 4.5.3 (Database encryption)
- PhotoView 2.3.0 (Image zoom)
- FlexboxLayout 3.0.0
```

### Architecture Components

#### 1. **Database Layer (Room)**

**Entities**:
- `ChapterEntity`: Chapter metadata (id, number, title, description)
- `TopicEntity`: Topic content (id, chapterId, number, title, contentHtml, images)
- `BookmarkEntity`: User bookmarks (topicId, title, snippet, timestamp)
- `HistoryEntity`: Reading history (topicId, title, snippet, progress, timestamp)
- `TopicFts`: Full-text search virtual table

**DAO (Data Access Object)**:
- `AppDao`: Database operations interface
  - Chapter/Topic CRUD operations
  - Bookmark management
  - History tracking
  - Full-text search queries

**Database**:
- `AppDatabase`: Room database singleton
- Database name: `dreampedi_db`
- Version: 1
- Fallback to destructive migration

#### 2. **Content Security**

**Encryption Flow**:
1. Device generates RSA key pair in Android Keystore
2. Public key sent to Cloud Function
3. Cloud Function wraps AES key with device public key
4. Device unwraps AES key using private key
5. Encrypted JSON downloaded from Firebase Storage
6. Content decrypted using AES-256-GCM
7. Decrypted content populated into Room database

**Key Components**:
- `KEY_ALIAS`: "dp_device_key" (Android Keystore)
- `CLOUD_FUNCTION_WRAP_URL`: Firebase Cloud Function endpoint
- RSA-OAEP with SHA-1 (Android compatibility)
- AES-256-GCM for content encryption

#### 3. **Firebase Integration**

**Realtime Database Structure**:
```
firebase-realtime-database/
├── users/
│   └── {userId}/
│       ├── deviceId: String
│       ├── loggedIn: Boolean
│       ├── featuresLocked: Boolean
│       └── fcm: String
│
└── payments/
    └── {userId}/
        ├── paymentMethod: String
        ├── fullName: String
        ├── transactionId: String
        └── fcm: String
```

**Firebase Storage**:
- Encrypted textbook content (JSON)
- AES encryption key (binary)
- Bucket: `dreamprod` (configurable)

**Cloud Functions**:
- `wrapAesKey`: Wraps AES key with device public key
- Region: us-central1
- Authentication: Firebase ID token required

#### 4. **Activities**

**AuthActivity**:
- Login and registration forms
- Email verification flow
- Password reset functionality
- Device ID registration
- Offline login support

**MainActivity**:
- ViewPager with 5 fragments
- Bottom navigation
- Content download and decryption
- Feature lock/unlock management
- Device verification
- FCM token management

**ChapterActivity**:
- Display chapter topics
- Topic navigation
- Chapter-specific UI

**TopicActivity**:
- Display topic content (HTML)
- Image viewing with PhotoView
- Bookmark management
- Reading progress tracking
- Resume reading support

**FailedActivity**:
- Error display for device mismatch
- Account verification failures

**AboutActivity**:
- App information and credits

#### 5. **Fragments**

**HomeFragment**:
- Chapter list display
- Purchase button (if features locked)
- Content download trigger
- Feature unlock UI updates

**HistoryFragment**:
- Recent reading history
- Progress indicators
- Quick navigation to topics

**BookmarksFragment**:
- Saved bookmarks list
- Remove bookmark functionality
- Quick access to bookmarked topics

**SearchFragment**:
- Full-text search interface
- Recent searches
- Search results with highlighting

**SettingsFragment**:
- User profile management
- Dark mode toggle
- Notification preferences
- Logout functionality

**PaymentDialogFragment**:
- Payment method selection (CBE, Telebirr, E-Birr)
- Transaction ID input
- Payment submission to Firebase
- FCM token inclusion
- Payment status display

#### 6. **Utilities**

**TextbookRepository**:
- Content population from JSON
- Database insertion
- Background thread execution

**SearchUtil**:
- Full-text search implementation
- Result ranking and highlighting

**AnimationUtil**:
- UI animations and transitions

**UserSettings**:
- User preferences management
- Settings persistence

## Step-by-Step Build Instructions

### Phase 1: Environment Setup

#### Step 1.1: Install Required Software
1. **Download and Install Android Studio**
   - Visit: https://developer.android.com/studio
   - Download the latest stable version
   - Run installer with default settings
   - Wait for initial setup (downloads ~2GB of SDK components)
   - First launch will take 10-15 minutes

2. **Install Java Development Kit (JDK) 11**
   - Android Studio includes JDK
   - Verify installation:
     - Open Terminal in Android Studio
     - Run: `java -version`
     - Should show version 11 or higher

3. **Install Node.js (for Cloud Functions)**
   - Visit: https://nodejs.org/
   - Download LTS version (20.x recommended)
   - Install with default settings
   - Verify: Run `node --version` in terminal
   - Should show v20.x.x

4. **Install Firebase CLI**
   - Open terminal/command prompt
   - Run: `npm install -g firebase-tools`
   - Verify: Run `firebase --version`
   - Login: Run `firebase login`

#### Step 1.2: Set Up Firebase Project
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project"
3. Enter project name: "dream-pediatrics" (or your choice)
4. Enable Google Analytics (recommended)
5. Select Analytics account or create new
6. Click "Create project"
7. Wait 1-2 minutes for project creation

#### Step 1.3: Enable Firebase Services

**1. Enable Authentication:**
- Click "Authentication" in left sidebar
- Click "Get started"
- Go to "Sign-in method" tab
- Click "Email/Password"
- Toggle "Enable"
- Click "Save"

**2. Enable Realtime Database:**
- Click "Realtime Database" in sidebar
- Click "Create Database"
- Choose location: "United States (us-central1)" or nearest
- Start in "Test mode" (we'll secure it later)
- Click "Enable"

**3. Enable Cloud Storage:**
- Click "Storage" in sidebar
- Click "Get started"
- Start in "Test mode"
- Choose same location as database
- Click "Done"

**4. Enable Cloud Messaging:**
- Click "Cloud Messaging" in sidebar
- Note: FCM is enabled by default
- No additional setup needed

**5. Enable Cloud Functions:**
- Click "Functions" in sidebar
- Click "Get started"
- Upgrade to Blaze (pay-as-you-go) plan
  - Required for Cloud Functions
  - Free tier includes 2M invocations/month
  - Set billing alerts if concerned

### Phase 2: Project Configuration

#### Step 2.1: Open the Project
1. Launch Android Studio
2. Click "Open" (not "New Project")
3. Navigate to: `c:\Users\NaB\Videos\Dream Pediatrics\DreamPediatrics`
4. Click "OK"
5. Wait for Gradle sync (5-10 minutes first time)
6. If prompted to update Gradle, click "Update"

#### Step 2.2: Add Firebase to Android App
1. In Firebase Console, click the Android icon (</>) to add an app
2. Enter Android package name: `com.dreampediatrics.app`
3. Enter app nickname: "Dream Pediatrics"
4. Leave SHA-1 empty for now (optional for auth)
5. Click "Register app"
6. Download `google-services.json`
7. In Android Studio, switch to "Project" view (top-left dropdown)
8. Copy `google-services.json` to: `DreamPediatrics/app/` directory
9. Click "Next" through remaining Firebase setup steps
10. Click "Continue to console"

#### Step 2.3: Verify Gradle Configuration
1. Open `app/build.gradle`
2. Verify these plugins exist at top:
   ```gradle
   plugins {
       id 'com.android.application'
       alias(libs.plugins.google.gms.google.services)
   }
   ```
3. Verify Firebase dependencies:
   ```gradle
   implementation libs.firebase.storage
   implementation libs.firebase.auth
   implementation libs.firebase.database
   implementation libs.firebase.messaging
   ```
4. Click "Sync Now" if prompted
5. Wait for sync to complete

### Phase 3: Content Encryption Setup

#### Step 3.1: Generate AES Encryption Key
1. Open terminal/command prompt
2. Generate 256-bit AES key:
   ```bash
   # On Windows (PowerShell)
   $bytes = New-Object byte[] 32
   [Security.Cryptography.RNGCryptoServiceProvider]::Create().GetBytes($bytes)
   [System.IO.File]::WriteAllBytes("aes_key.bin", $bytes)
   
   # On Linux/Mac
   openssl rand -out aes_key.bin 32
   ```
3. This creates `aes_key.bin` file (32 bytes)
4. **Keep this file secure!** It encrypts all content

#### Step 3.2: Prepare Textbook Content
1. Create your textbook content in JSON format:
   ```json
   {
     "chapters": [
       {
         "id": "ch1",
         "number": 1,
         "title": "Introduction to Pediatrics",
         "description": "Basic concepts",
         "topics": [
           {
             "id": "ch1_t1",
             "number": 1,
             "title": "What is Pediatrics?",
             "content_html": "<h1>Pediatrics</h1><p>Content here...</p>",
             "images": ["image1.jpg", "image2.jpg"]
           }
         ]
       }
     ]
   }
   ```
2. Save as `textbook.json`

#### Step 3.3: Encrypt the Content
1. Create encryption script `encrypt_content.py`:
   ```python
   from cryptography.hazmat.primitives.ciphers.aead import AESGCM
   import os
   
   # Read AES key
   with open('aes_key.bin', 'rb') as f:
       key = f.read()
   
   # Read plaintext JSON
   with open('textbook.json', 'rb') as f:
       plaintext = f.read()
   
   # Generate random nonce (12 bytes for GCM)
   nonce = os.urandom(12)
   
   # Encrypt
   aesgcm = AESGCM(key)
   ciphertext = aesgcm.encrypt(nonce, plaintext, None)
   
   # Save: nonce (12 bytes) + ciphertext
   with open('textbook_encrypted.bin', 'wb') as f:
       f.write(nonce + ciphertext)
   
   print("Encryption complete!")
   print(f"Nonce: {nonce.hex()}")
   print(f"Encrypted size: {len(ciphertext)} bytes")
   ```

2. Install required library:
   ```bash
   pip install cryptography
   ```

3. Run encryption:
   ```bash
   python encrypt_content.py
   ```

4. You now have `textbook_encrypted.bin`

#### Step 3.4: Upload to Firebase Storage
1. Go to Firebase Console → Storage
2. Click "Upload file"
3. Upload `aes_key.bin` to root of bucket
4. Upload `textbook_encrypted.bin` to root of bucket
5. Note the bucket name (e.g., `dreampedi.appspot.com`)

### Phase 4: Cloud Functions Setup

#### Step 4.1: Initialize Firebase Functions
1. Open terminal in project root: `DreamPediatrics/`
2. Run: `firebase init functions`
3. Select your Firebase project
4. Choose language: **JavaScript**
5. Use ESLint? **No** (or Yes if you prefer)
6. Install dependencies? **Yes**
7. Wait for npm install to complete

#### Step 4.2: Configure Cloud Function
1. The `functions/` folder already exists with `index.js`
2. Open `functions/index.js` (already configured)
3. Verify the configuration:
   ```javascript
   const KEY_BUCKET = process.env.TEXTBOOK_KEY_BUCKET || 'dreamprod';
   const KEY_PATH   = process.env.TEXTBOOK_KEY_PATH   || 'aes_key.bin';
   ```
4. Update bucket name if different:
   - Replace `'dreamprod'` with your bucket name
   - Or set environment variable (recommended)

#### Step 4.3: Set Environment Variables (Optional)
1. Set custom bucket name:
   ```bash
   firebase functions:config:set textbook.key_bucket="your-bucket-name"
   firebase functions:config:set textbook.key_path="aes_key.bin"
   ```
2. View current config:
   ```bash
   firebase functions:config:get
   ```

#### Step 4.4: Deploy Cloud Function
1. Ensure you're in project root directory
2. Run deployment:
   ```bash
   firebase deploy --only functions
   ```
3. Wait 2-3 minutes for deployment
4. Note the function URL in output:
   ```
   Function URL (wrapAesKey): https://wrapaeskey-xxxxx-uc.a.run.app
   ```
5. Copy this URL - you'll need it next

#### Step 4.5: Update App with Function URL
1. Open `app/src/main/java/com/dreampediatrics/app/MainActivity.java`
2. Find line ~70:
   ```java
   private static final String CLOUD_FUNCTION_WRAP_URL = "https://wrapaeskey-4jzb4qgvzq-uc.a.run.app";
   ```
3. Replace with your function URL from Step 4.4
4. Save the file

### Phase 5: Firebase Database Setup

#### Step 5.1: Configure Database Structure
1. Go to Firebase Console → Realtime Database
2. Click "Data" tab
3. Click "+" next to database URL
4. Create root nodes:
   - Name: `users`, Value: `{}`
   - Name: `payments`, Value: `{}`

#### Step 5.2: Set Security Rules
1. Click "Rules" tab
2. Replace with:
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
       }
     }
   }
   ```
3. Replace `'ADMIN_UID'` with actual admin user ID (get from Authentication tab)
4. Click "Publish"

#### Step 5.3: Configure Storage Rules
1. Go to Firebase Console → Storage
2. Click "Rules" tab
3. Replace with:
   ```
   rules_version = '2';
   service firebase.storage {
     match /b/{bucket}/o {
       match /aes_key.bin {
         allow read: if request.auth != null;
       }
       match /textbook_encrypted.bin {
         allow read: if request.auth != null;
       }
       match /{allPaths=**} {
         allow read: if request.auth != null;
       }
     }
   }
   ```
4. Click "Publish"

### Phase 6: Build the App

#### Step 6.1: Sync and Build
1. In Android Studio, click "File" → "Sync Project with Gradle Files"
2. Wait for sync to complete
3. Go to "Build" → "Clean Project"
4. Wait for clean to finish
5. Go to "Build" → "Rebuild Project"
6. Wait for build (may take 5-10 minutes first time)
7. Check "Build" tab at bottom for any errors

#### Step 6.2: Resolve Common Build Issues

**Issue: SDK not found**
- Go to Tools → SDK Manager
- Install Android SDK 34
- Install Build Tools 34.0.0

**Issue: Gradle version mismatch**
- File → Project Structure → Project
- Update Gradle version to latest
- Click "Apply" → "OK"

**Issue: Dependency resolution failed**
- Check internet connection
- File → Invalidate Caches → Invalidate and Restart

#### Step 6.3: Build APK
1. Go to "Build" → "Build Bundle(s) / APK(s)" → "Build APK(s)"
2. Wait for build to complete
3. Click "locate" in notification
4. APK location: `app/build/outputs/apk/debug/app-debug.apk`

### Phase 7: Testing

#### Step 7.1: Set Up Test Device

**Option A: Android Emulator**
1. Click "Device Manager" icon (phone) in toolbar
2. Click "Create Device"
3. Select "Pixel 5" or similar
4. Click "Next"
5. Download system image: API 34 (Android 14)
6. Click "Next" → "Finish"
7. Click play button to start emulator
8. Wait for emulator to boot (2-3 minutes)

**Option B: Physical Device**
1. Enable Developer Options:
   - Settings → About Phone
   - Tap "Build Number" 7 times
2. Enable USB Debugging:
   - Settings → Developer Options
   - Toggle "USB Debugging" ON
3. Connect device via USB
4. Allow USB debugging on device
5. Device should appear in Android Studio

#### Step 7.2: Install and Launch
1. Select device from dropdown in toolbar
2. Click green "Run" button (or Shift+F10)
3. Wait for installation
4. App should launch automatically

#### Step 7.3: Test Registration Flow
1. App opens to AuthActivity (login screen)
2. Click "Sign Up" link
3. Enter test email: `test@example.com`
4. Enter name: "Test User"
5. Enter password: `testpass123`
6. Confirm password: `testpass123`
7. Click "Register"
8. Check email for verification link
9. Click verification link
10. Return to app and login

#### Step 7.4: Test Payment Flow
1. After login, you'll see MainActivity
2. Features should be locked (no content visible)
3. Click "Purchase" button
4. Payment dialog opens
5. Select payment method (e.g., Telebirr)
6. Click "I have Paid"
7. Enter transaction ID: "TEST123456"
8. Enter your name
9. Click "Submit"
10. Should see "Payment Pending" message

#### Step 7.5: Verify Payment (Admin Side)
1. Open Admin Console app (from DP Admin project)
2. Login with admin credentials
3. Go to "Home" tab
4. You should see the test payment
5. Click on payment
6. Click "Verify" button
7. Confirm verification
8. Click "Save as Premium"

#### Step 7.6: Test Content Download
1. Return to Dream Pediatrics app
2. Pull down to refresh or restart app
3. App should detect unlocked features
4. Content download should start automatically
5. Loading dialog shows "Installing textbook..."
6. Wait for download and decryption (30-60 seconds)
7. Content should appear in Home tab

#### Step 7.7: Test Core Features
1. **Browse Content**:
   - Tap a chapter
   - Tap a topic
   - Content should display

2. **Bookmark**:
   - Open a topic
   - Tap bookmark icon
   - Go to Bookmarks tab
   - Verify bookmark appears

3. **Search**:
   - Go to Search tab
   - Enter search term
   - Verify results appear

4. **History**:
   - Open several topics
   - Go to History tab
   - Verify reading history

5. **Settings**:
   - Go to Settings tab
   - Toggle dark mode
   - Verify theme changes

### Phase 8: Production Build

#### Step 8.1: Create Keystore
1. Go to "Build" → "Generate Signed Bundle / APK"
2. Select "APK"
3. Click "Next"
4. Click "Create new..."
5. Fill in details:
   - Key store path: `C:\keystore\dreampediatrics.jks`
   - Password: [Create strong password]
   - Alias: `dreampediatrics-key`
   - Password: [Same or different]
   - Validity: 25 years
   - First and Last Name: Your name
   - Organization: Dream Pediatrics
   - City, State, Country: Your location
6. Click "OK"
7. **CRITICAL**: Backup this keystore file securely!

#### Step 8.2: Configure Signing
1. Create `keystore.properties` in project root:
   ```properties
   storePassword=YOUR_STORE_PASSWORD
   keyPassword=YOUR_KEY_PASSWORD
   keyAlias=dreampediatrics-key
   storeFile=C:/keystore/dreampediatrics.jks
   ```
2. Add to `.gitignore`:
   ```
   keystore.properties
   *.jks
   ```

#### Step 8.3: Update build.gradle for Signing
1. Open `app/build.gradle`
2. Add before `android {` block:
   ```gradle
   def keystorePropertiesFile = rootProject.file("keystore.properties")
   def keystoreProperties = new Properties()
   if (keystorePropertiesFile.exists()) {
       keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
   }
   ```
3. Add inside `android {` block:
   ```gradle
   signingConfigs {
       release {
           keyAlias keystoreProperties['keyAlias']
           keyPassword keystoreProperties['keyPassword']
           storeFile file(keystoreProperties['storeFile'])
           storePassword keystoreProperties['storePassword']
       }
   }
   buildTypes {
       release {
           signingConfig signingConfigs.release
           minifyEnabled true
           proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
       }
   }
   ```

#### Step 8.4: Build Release APK
1. Go to "Build" → "Generate Signed Bundle / APK"
2. Select "APK"
3. Click "Next"
4. Select existing keystore
5. Enter passwords
6. Select "release" build variant
7. Click "Finish"
8. Wait for build (5-10 minutes with ProGuard)
9. Signed APK: `app/release/app-release.apk`

#### Step 8.5: Test Release Build
1. Uninstall debug version from device
2. Install release APK:
   ```bash
   adb install app/release/app-release.apk
   ```
3. Test all features thoroughly
4. Check for crashes or issues

### Phase 9: Deployment

#### Step 9.1: Prepare for Distribution

**Update Version**:
1. Open `app/build.gradle`
2. Update version:
   ```gradle
   versionCode 1  // Increment for each release
   versionName "2.1.0"  // Semantic versioning
   ```

**Create Release Notes**:
1. Document new features
2. List bug fixes
3. Note breaking changes

#### Step 9.2: Distribution Options

**Option A: Direct APK Distribution**
1. Upload APK to secure server
2. Share download link with authorized users
3. Users must enable "Install from Unknown Sources"

**Option B: Google Play Store**
1. Create Google Play Developer account ($25 one-time fee)
2. Create app listing
3. Upload signed APK or AAB
4. Fill in store listing details
5. Set pricing (free or paid)
6. Submit for review
7. Wait 1-3 days for approval

**Option C: Internal Testing**
1. Use Google Play Internal Testing
2. Add tester email addresses
3. Share testing link
4. Testers can install via Play Store

### Phase 10: Monitoring and Maintenance

#### Step 10.1: Set Up Firebase Analytics
1. Analytics is enabled by default
2. Go to Firebase Console → Analytics
3. View user engagement, retention, crashes

#### Step 10.2: Monitor Cloud Functions
1. Go to Firebase Console → Functions
2. View invocation count, errors, execution time
3. Set up alerts for errors

#### Step 10.3: Monitor Database Usage
1. Go to Firebase Console → Realtime Database
2. Click "Usage" tab
3. Monitor reads, writes, storage
4. Set up billing alerts

#### Step 10.4: Regular Maintenance Tasks

**Weekly**:
- Check Firebase Console for errors
- Review user feedback
- Monitor app crashes

**Monthly**:
- Update dependencies in `build.gradle`
- Review and update Firebase rules
- Check for Android Studio updates
- Test on latest Android version

**Quarterly**:
- Review and optimize database structure
- Update content encryption key (if needed)
- Audit user accounts
- Review and update privacy policy

### Troubleshooting Guide

#### Problem: Content Not Downloading
**Symptoms**: Loading dialog appears but content never loads

**Solutions**:
1. Check Cloud Function logs:
   ```bash
   firebase functions:log
   ```
2. Verify Storage rules allow authenticated reads
3. Check device internet connection
4. Verify `featuresLocked = false` in database
5. Check Logcat for errors:
   ```bash
   adb logcat | grep "MainActivity"
   ```

#### Problem: Decryption Fails
**Symptoms**: "Failed to decrypt content" error

**Solutions**:
1. Verify AES key in Storage matches encryption key
2. Check encrypted file format (nonce + ciphertext)
3. Verify RSA key wrapping is working
4. Check Cloud Function is returning wrapped key
5. Test key unwrapping in isolation

#### Problem: Device Verification Failed
**Symptoms**: Redirected to FailedActivity

**Solutions**:
1. Check device ID in Firebase Database
2. Verify user logged in from same device
3. Clear app data and re-register
4. Check `deviceId` field in `users/{uid}`

#### Problem: Payment Not Submitting
**Symptoms**: Error when clicking Submit in payment dialog

**Solutions**:
1. Check Firebase Database rules
2. Verify user is authenticated
3. Check internet connection
4. Verify FCM token is generated
5. Check Logcat for Firebase errors

#### Problem: Search Not Working
**Symptoms**: No results or app crashes on search

**Solutions**:
1. Verify content is downloaded
2. Check FTS table is populated:
   ```java
   List<TopicFts> fts = dao.searchTopics("test");
   ```
3. Rebuild FTS index if needed
4. Check Room database integrity

### Advanced Configuration

#### Enable Database Encryption
1. SQLCipher is already included
2. To enable, modify `AppDatabase.getInstance()`:
   ```java
   SQLiteDatabase.loadLibs(context);
   SupportFactory factory = new SupportFactory(SQLiteDatabase.getBytes("your-passphrase".toCharArray()));
   INSTANCE = Room.databaseBuilder(context, AppDatabase.class, "dreampedi_db")
       .openHelperFactory(factory)
       .build();
   ```

#### Configure Content Updates
1. Add version field to JSON
2. Check version on app start
3. Download new content if version changed
4. Merge or replace existing content

#### Add Crash Reporting
1. Add Firebase Crashlytics:
   ```gradle
   implementation 'com.google.firebase:firebase-crashlytics:18.6.0'
   ```
2. Initialize in Application class
3. View crashes in Firebase Console

### Performance Optimization

#### Reduce APK Size
1. Enable ProGuard (already configured)
2. Use APK Analyzer: Build → Analyze APK
3. Remove unused resources
4. Use WebP for images
5. Enable resource shrinking:
   ```gradle
   buildTypes {
       release {
           shrinkResources true
           minifyEnabled true
       }
   }
   ```

#### Optimize Database Queries
1. Use indexes on frequently queried fields
2. Limit query results
3. Use pagination for large lists
4. Cache frequently accessed data

#### Optimize Content Loading
1. Lazy load images
2. Use image compression
3. Implement progressive loading
4. Cache decoded content

### Security Best Practices

1. **Never hardcode secrets** in source code
2. **Use ProGuard** to obfuscate code
3. **Validate all user input** before database writes
4. **Use HTTPS** for all network requests
5. **Rotate encryption keys** periodically
6. **Implement certificate pinning** for API calls
7. **Use SafetyNet** to detect rooted devices
8. **Encrypt sensitive SharedPreferences**
9. **Implement rate limiting** on Cloud Functions
10. **Regular security audits** of Firebase rules

### Backup and Recovery

#### Backup Firebase Data
1. Go to Firebase Console → Database
2. Export data regularly
3. Store backups securely
4. Test restoration process

#### Backup Encryption Keys
1. Store `aes_key.bin` in multiple secure locations
2. Use hardware security module (HSM) for production
3. Document key rotation procedure
4. Maintain key version history

#### Disaster Recovery Plan
1. Document all Firebase configuration
2. Keep copy of all source code
3. Backup keystore file securely
4. Document deployment procedures
5. Test recovery process quarterly

---

## Congratulations!

You've successfully built and deployed the Dream Pediatrics mobile app. The app now features:
- ✅ Secure content delivery with encryption
- ✅ User authentication and authorization
- ✅ Payment integration
- ✅ Offline content access
- ✅ Full-text search
- ✅ Bookmark and history tracking
- ✅ Push notifications

### Next Steps
1. Gather user feedback
2. Plan feature enhancements
3. Monitor app performance
4. Regular maintenance and updates

### Firebase Realtime Database Rules

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid || auth.uid === 'ADMIN_UID'"
      }
    },
    "payments": {
      "$uid": {
        ".read": "$uid === auth.uid || auth.uid === 'ADMIN_UID'",
        ".write": "$uid === auth.uid"
      }
    }
  }
}
```

## User Workflow

### First-Time User
1. **Registration**: Create account with email/password
2. **Email Verification**: Verify email address
3. **Login**: Sign in with verified credentials
4. **Device Registration**: Device ID automatically registered
5. **Payment**: Submit payment via in-app dialog
6. **Verification Wait**: Admin verifies payment
7. **Content Download**: After verification, download and decrypt content
8. **Access Content**: Browse chapters and topics offline

### Returning User
1. **Login**: Automatic login if previously authenticated
2. **Device Check**: Verify device ID matches registered device
3. **Feature Check**: Check if features are unlocked
4. **Resume Reading**: Option to continue from last topic
5. **Browse Content**: Access all downloaded content offline

### Payment Verification Flow
1. User submits payment with transaction ID
2. Payment data saved to Firebase (`payments/{userId}`)
3. Admin receives notification (via FCM)
4. Admin verifies payment in Admin Console
5. Admin sets `featuresLocked = false` in user record
6. User receives notification of verification
7. App triggers content download and decryption
8. Content populated into local database
9. User gains full access to content

## Security Features

### 1. **Content Protection**
- AES-256-GCM encryption for textbook content
- Device-specific key wrapping (RSA-OAEP)
- Keys stored in Android Keystore (hardware-backed if available)
- No plaintext content in storage

### 2. **Account Security**
- Email verification required
- Device ID verification prevents account sharing
- Single device per account enforcement
- Secure password requirements (8+ characters)

### 3. **Database Encryption**
- SQLCipher integration for Room database
- Encrypted local storage
- Secure data at rest

### 4. **Network Security**
- HTTPS for all network requests
- Firebase Authentication tokens
- Cloud Function authentication via ID tokens

## Offline Capabilities

- **Full Offline Access**: All content available without internet after download
- **Offline Authentication**: Login works offline after first successful login
- **Local Database**: All content stored in encrypted Room database
- **Bookmark/History Sync**: Local storage with background sync capability
- **Feature Lock Check**: Cached feature unlock status

## Push Notifications

### Notification Types
1. **Payment Verification**: Sent when admin verifies payment
2. **Broadcast Announcements**: Sent to all users via "all" topic
3. **Targeted Messages**: Sent to specific users via FCM token

### Implementation
- FCM token generated on app start
- Token stored in SharedPreferences and Firebase
- Topic subscription: "all" for broadcasts
- Notification permission requested on Android 13+

## Error Handling

### Device Mismatch
- Detected when device ID doesn't match registered device
- User redirected to `FailedActivity`
- Database cleared for security
- User must login from registered device

### Payment Failures
- Error messages displayed in payment dialog
- Retry mechanism available
- Admin notification for failed submissions

### Content Download Failures
- Error dialogs with retry options
- Fallback to cached content if available
- Detailed error logging

## Performance Optimizations

1. **Background Threading**: Database operations on background executor
2. **Lazy Loading**: Content loaded on-demand
3. **Image Caching**: Efficient image loading and caching
4. **FTS Indexing**: Fast full-text search with Room FTS
5. **ViewPager Caching**: Fragment state preservation

## Testing

### Test Accounts
Create test accounts in Firebase Authentication for testing different scenarios:
- Verified user with unlocked features
- Verified user with locked features
- Unverified user
- User with pending payment

### Test Scenarios
1. Registration and email verification
2. Login (online and offline)
3. Payment submission
4. Content download and decryption
5. Bookmark and history management
6. Search functionality
7. Device verification
8. Feature lock/unlock

## Troubleshooting

### Content Not Downloading
- Check Firebase Storage rules
- Verify Cloud Function is deployed
- Check device internet connectivity
- Verify user authentication status

### Device Verification Failed
- User must login from the device used during registration
- Check device ID in Firebase Realtime Database
- Clear app data and re-register if needed

### Payment Not Submitting
- Check Firebase Realtime Database rules
- Verify user authentication
- Check internet connectivity
- Verify FCM token generation

### Search Not Working
- Ensure content is downloaded and populated
- Check FTS table is populated
- Verify database integrity

## Version Information

- **Version Code**: 1
- **Version Name**: 2.1.0
- **Package Name**: com.dreampediatrics.app
- **Compile SDK**: 34
- **Min SDK**: 24
- **Target SDK**: 34

## Cloud Functions

### wrapAesKey Function

**Purpose**: Securely wrap AES encryption key with device public key

**Endpoint**: `https://wrapaeskey-4jzb4qgvzq-uc.a.run.app`

**Method**: POST

**Authentication**: Firebase ID token (Bearer)

**Request Body**:
```json
{
  "device_public_key_b64": "base64_encoded_public_key"
}
```

**Response**:
```json
{
  "wrapped_key_b64": "base64_encoded_wrapped_key"
}
```

**Configuration**:
- Region: us-central1
- Node.js: 20
- Storage Bucket: `dreamprod` (configurable via env var)
- Key Path: `aes_key.bin` (configurable via env var)

## Future Enhancements

- [ ] Multi-device support with device management
- [ ] Content updates and versioning
- [ ] Annotation and note-taking features
- [ ] Offline payment verification
- [ ] Social features (sharing, discussions)
- [ ] Quiz and assessment modules
- [ ] Audio content support
- [ ] Tablet-optimized layouts

## License

Proprietary - Dream Pediatrics

## Support

For technical support or questions, contact the development team.

---

**Last Updated**: 2024
