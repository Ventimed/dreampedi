# Dream Pediatrics Admin Console

## Overview

The **Dream Pediatrics Admin Console** is an Android application designed for administrators to manage premium user subscriptions and payment verifications for the Dream Pediatrics mobile app. It provides a comprehensive dashboard for handling payment requests, verifying transactions, managing premium users, and sending push notifications.

## Features

### 1. **Authentication**
- Firebase Authentication with email/password
- Persistent login with offline support
- First-time login requires internet connection
- Subsequent logins work offline using cached credentials

### 2. **Payment Management**
- **Pending Payments Dashboard**: View all pending payment verification requests
- **Payment Details Modal**: Detailed view of each payment request including:
  - User ID (UID)
  - Transaction ID
  - Payment amount
  - Payment method (with visual icons for CBE, Telebirr, E-Birr)
  - FCM token (for push notifications)
  - Copy FCM token to clipboard functionality

### 3. **Payment Actions**
- **Verify Payment**: Unlocks features for the user by setting `featuresLocked = false`
- **Save as Premium**: Moves verified payment from pending to verified list with server timestamp
- **Lock Features**: Re-locks user features by setting `featuresLocked = true`
- **Delete Payment Request**: Removes payment request from pending list

### 4. **Premium Users Management**
- View all verified premium users
- Search functionality to filter by UID or transaction ID
- Display payment method and transaction details
- Copy transaction ID to clipboard

### 5. **Push Notifications**
- **Send to All Users**: Broadcast notifications to all app users via FCM topic subscription
- **Send to Specific User**: Target individual users using their FCM token
- Custom notification title and body
- Uses Firebase Cloud Messaging HTTP v1 API with OAuth2 authentication

### 6. **Statistics Dashboard**
- Real-time count of pending payment requests
- Total count of verified premium users

## Technical Architecture

### Technology Stack
- **Language**: Java
- **Minimum SDK**: Android 24 (Android 7.0)
- **Target SDK**: Android 36
- **Build System**: Gradle with Android Gradle Plugin

### Key Dependencies
```gradle
- Material Design Components 1.13.0
- AndroidX AppCompat 1.7.1
- Firebase Realtime Database 22.0.1
- Firebase Authentication 24.0.1
- Google Auth Library OAuth2 1.19.0
- OkHttp 4.11.0
- RecyclerView, CardView
```

### Firebase Structure

#### Database Schema
```
firebase-realtime-database/
├── payments/                    # Pending payment requests
│   └── {userId}/
│       ├── userName: String
│       ├── paymentMethod: String
│       ├── transactionId: String
│       ├── fcm: String
│       ├── amount: String
│       ├── date: String
│       └── status: String
│
├── verified/                    # Verified premium users
│   └── {userId}/
│       ├── userName: String
│       ├── paymentMethod: String
│       ├── transactionId: String
│       └── serverTime: Timestamp
│
└── users/                       # User account data
    └── {userId}/
        ├── featuresLocked: Boolean
        ├── deviceId: String
        ├── loggedIn: Boolean
        └── serverTime: Timestamp
```

### Key Components

#### 1. **LoginActivity**
- Handles admin authentication
- Stores login state in SharedPreferences
- Supports offline access after first successful login
- Network connectivity check for first-time login

#### 2. **MainActivity**
- Three-tab interface: Home, Premium, Notifications
- Real-time Firebase listeners for pending and verified payments
- Payment dialog with multiple action buttons
- FCM notification sender with OAuth2 authentication

#### 3. **PendingAdapter**
- RecyclerView adapter for pending payment requests
- Displays user info, payment method, transaction ID, and status
- Click handler to open payment details dialog

#### 4. **VerifiedAdapter**
- RecyclerView adapter for verified premium users
- Search/filter functionality
- Copy transaction ID feature

#### 5. **AccessToken**
- OAuth2 token generator for FCM HTTP v1 API
- Uses service account credentials
- Generates short-lived access tokens for authenticated API calls

#### 6. **UserModel**
- Data model for user payment information
- Fields: uid, fullName, paymentMethod, transactionId, fcm, amount, date, status

### Payment Method Icons
The app displays visual icons for different payment methods:
- **CBE Bank**: `ic_cbe.png`
- **Telebirr**: `ic_tele.png`
- **E-Birr**: `ic_ebirr.png`
- **Default/Other**: `ic_bank_placeholder.xml`

Payment method detection uses case-insensitive substring matching:
- "e-birr" or "e birr" → E-Birr icon
- "telebirr" → Telebirr icon
- "cbe" or "bank" → CBE icon

## Step-by-Step Build Instructions

### Phase 1: Environment Setup

#### Step 1.1: Install Required Software
1. Download and install **Android Studio** (Arctic Fox or later)
   - Visit: https://developer.android.com/studio
   - Install with default settings
   - Wait for initial setup and SDK downloads to complete

2. Install **Java Development Kit (JDK) 11**
   - Android Studio usually includes this
   - Verify: Open Terminal and run `java -version`
   - Should show version 11 or higher

3. Install **Git** (if not already installed)
   - Windows: Download from https://git-scm.com/
   - Verify: Run `git --version` in terminal

#### Step 1.2: Set Up Firebase Project
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project" or select existing project
3. Enter project name (e.g., "dream-pediatrics")
4. Enable Google Analytics (optional)
5. Click "Create project" and wait for completion

#### Step 1.3: Enable Firebase Services
1. **Enable Authentication**:
   - In Firebase Console, go to "Authentication"
   - Click "Get started"
   - Go to "Sign-in method" tab
   - Enable "Email/Password" provider
   - Click "Save"

2. **Enable Realtime Database**:
   - Go to "Realtime Database" in sidebar
   - Click "Create Database"
   - Choose location (e.g., us-central1)
   - Start in "Test mode" (we'll add rules later)
   - Click "Enable"

3. **Enable Cloud Messaging**:
   - Go to "Cloud Messaging" in sidebar
   - Note down your Server Key (for legacy API)
   - We'll use HTTP v1 API with service account instead

### Phase 2: Project Configuration

#### Step 2.1: Clone/Open the Project
1. Open Android Studio
2. Click "Open" (not "New Project")
3. Navigate to: `c:\Users\NaB\Videos\Dream Pediatrics\DP Admin`
4. Click "OK"
5. Wait for Gradle sync to complete (may take 5-10 minutes first time)

#### Step 2.2: Add Firebase to Android App
1. In Firebase Console, click the Android icon to add an app
2. Enter package name: `com.dream.pediadmin`
3. Enter app nickname: "DP Admin Console"
4. Click "Register app"
5. Download `google-services.json` file
6. Place the file in: `DP Admin/app/` directory
7. Click "Next" through the remaining steps
8. Return to Android Studio

#### Step 2.3: Verify Gradle Configuration
1. Open `app/build.gradle`
2. Verify these lines exist:
   ```gradle
   plugins {
       alias(libs.plugins.google.gms.google.services)
   }
   ```
3. Check dependencies section includes:
   ```gradle
   implementation 'com.google.firebase:firebase-database:22.0.1'
   implementation 'com.google.firebase:firebase-auth:24.0.1'
   ```
4. Click "Sync Now" if prompted

### Phase 3: Firebase Service Account Setup

#### Step 3.1: Generate Service Account Key
1. In Firebase Console, click the gear icon → "Project settings"
2. Go to "Service accounts" tab
3. Click "Generate new private key"
4. Click "Generate key" in the confirmation dialog
5. A JSON file will download (e.g., `dreampedi-firebase-adminsdk-xxxxx.json`)
6. **Keep this file secure!** Never commit to Git

#### Step 3.2: Update AccessToken.java
1. Open the downloaded JSON file in a text editor
2. Copy the entire JSON content
3. In Android Studio, open: `app/src/main/java/com/dream/pediadmin/AccessToken.java`
4. Find the `jsonString` variable (around line 17)
5. Replace the existing JSON with your copied JSON
6. Make sure to escape quotes properly (use `\"` for quotes inside the string)
7. Save the file

Example format:
```java
String jsonString = "{\n" +
    "  \"type\": \"service_account\",\n" +
    "  \"project_id\": \"your-project-id\",\n" +
    "  \"private_key_id\": \"your-key-id\",\n" +
    // ... rest of your JSON
    "}\n";
```

#### Step 3.3: Update FCM Project ID
1. Open: `app/src/main/java/com/dream/pediadmin/MainActivity.java`
2. Press `Ctrl+F` and search for: `projects/dreampedi/messages:send`
3. You'll find it in two places (around lines 450 and 490)
4. Replace `dreampedi` with your actual Firebase project ID
5. Your project ID is shown in Firebase Console → Project Settings

Before:
```java
.url("https://fcm.googleapis.com/v1/projects/dreampedi/messages:send")
```

After:
```java
.url("https://fcm.googleapis.com/v1/projects/YOUR_PROJECT_ID/messages:send")
```

### Phase 4: Firebase Database Setup

#### Step 4.1: Set Up Database Structure
1. Go to Firebase Console → Realtime Database
2. Click on the "Data" tab
3. Click the "+" icon next to your database URL
4. Create these three root nodes (leave them empty for now):
   - `payments`
   - `verified`
   - `users`

#### Step 4.2: Configure Security Rules
1. Go to "Rules" tab in Realtime Database
2. Replace the default rules with:
```json
{
  "rules": {
    "payments": {
      ".read": "auth != null",
      ".write": "auth != null"
    },
    "verified": {
      ".read": "auth != null",
      ".write": "auth != null"
    },
    "users": {
      ".read": "auth != null",
      "$uid": {
        ".write": "auth != null"
      }
    }
  }
}
```
3. Click "Publish"

### Phase 5: Create Admin Account

#### Step 5.1: Add Admin User in Firebase
1. Go to Firebase Console → Authentication
2. Click "Users" tab
3. Click "Add user"
4. Enter admin email (e.g., `admin@dreampediatrics.com`)
5. Enter a strong password (min 8 characters)
6. Click "Add user"
7. **Important**: Note down these credentials securely

#### Step 5.2: Verify Email (Optional but Recommended)
Since this is an admin account, you can manually verify it:
1. In the Users list, find your admin user
2. Click the three dots menu
3. Select "Edit user"
4. Check "Email verified"
5. Click "Save"

### Phase 6: Build and Test

#### Step 6.1: Build the APK
1. In Android Studio, go to "Build" menu
2. Select "Build Bundle(s) / APK(s)" → "Build APK(s)"
3. Wait for build to complete (check bottom status bar)
4. Click "locate" in the notification to find the APK
5. APK location: `app/build/outputs/apk/debug/app-debug.apk`

#### Step 6.2: Install on Device/Emulator

**Option A: Using Android Emulator**
1. In Android Studio, click "Device Manager" (phone icon in toolbar)
2. Click "Create Device"
3. Select a phone (e.g., Pixel 5)
4. Select system image (API 24 or higher)
5. Click "Finish"
6. Click the play button to start emulator
7. Once emulator is running, click "Run" (green play button) in Android Studio

**Option B: Using Physical Device**
1. Enable Developer Options on your Android device:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
2. Enable USB Debugging:
   - Go to Settings → Developer Options
   - Enable "USB Debugging"
3. Connect device via USB
4. Allow USB debugging when prompted on device
5. In Android Studio, select your device from dropdown
6. Click "Run" (green play button)

#### Step 6.3: First Login Test
1. Launch the app on device/emulator
2. You should see the login screen
3. Enter the admin credentials you created in Step 5.1
4. Click "Login"
5. If successful, you'll see the main dashboard with three tabs

### Phase 7: Testing Payment Workflow

#### Step 7.1: Create Test Payment Data
1. Go to Firebase Console → Realtime Database
2. Click on `payments` node
3. Click the "+" icon
4. Add a test payment entry:
   - Name: `testUserId123`
   - Click "+" to add children:
     - `userName`: "Test User"
     - `paymentMethod`: "telebirr"
     - `transactionId`: "TXN123456"
     - `fcm`: "test_fcm_token"
     - `amount`: "500"
     - `status`: "pending"
5. Click "Add"

#### Step 7.2: Verify Payment in App
1. In the app, go to "Home" tab
2. You should see the test payment in the pending list
3. Click on the payment item
4. Payment details dialog should open
5. Test each button:
   - **Copy FCM**: Should copy token to clipboard
   - **Verify**: Should show confirmation dialog
   - **Save as Premium**: Should move to verified list
   - **Lock**: Should update user status
   - **Delete**: Should remove from pending

#### Step 7.3: Test Notifications
1. Go to "Notifications" tab
2. Click "Send to All"
3. Enter title: "Test Notification"
4. Enter body: "This is a test message"
5. Click "Send to All"
6. Check for success toast message

### Phase 8: Production Preparation

#### Step 8.1: Security Hardening
1. **Remove Service Account from Code**:
   - Move service account JSON to a secure backend
   - Use environment variables or secure storage
   - Never commit `AccessToken.java` with real credentials

2. **Update Firebase Rules**:
   - Add admin UID check to rules
   - Restrict write access to admin only

3. **Enable ProGuard** (for release builds):
   - Open `app/build.gradle`
   - In `release` build type, set:
   ```gradle
   minifyEnabled true
   proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
   ```

#### Step 8.2: Generate Signed APK
1. Go to "Build" → "Generate Signed Bundle / APK"
2. Select "APK" and click "Next"
3. Click "Create new..." to create a keystore
4. Fill in keystore details:
   - Key store path: Choose location
   - Password: Create strong password
   - Alias: `dp-admin-key`
   - Validity: 25 years
   - Certificate info: Fill in your details
5. Click "OK"
6. Select "release" build variant
7. Click "Finish"
8. **Important**: Backup your keystore file securely!

#### Step 8.3: Version Management
1. Open `app/build.gradle`
2. Update version for each release:
```gradle
versionCode 2  // Increment by 1 for each release
versionName "1.1.0"  // Follow semantic versioning
```

### Phase 9: Deployment

#### Step 9.1: Internal Testing
1. Install signed APK on test devices
2. Test all features thoroughly
3. Check for crashes or errors
4. Verify Firebase connectivity

#### Step 9.2: Distribution
1. **Option A**: Direct APK distribution
   - Share signed APK via secure channel
   - Only to authorized admins

2. **Option B**: Google Play Internal Testing
   - Create Google Play Console account
   - Upload signed APK
   - Add internal testers
   - Distribute via Play Store

### Troubleshooting Common Issues

#### Issue 1: Gradle Sync Failed
**Solution**:
- Check internet connection
- Go to File → Invalidate Caches → Invalidate and Restart
- Update Gradle: File → Project Structure → Project → Gradle Version

#### Issue 2: google-services.json Not Found
**Solution**:
- Verify file is in `app/` directory (not `app/src/`)
- File name must be exactly `google-services.json`
- Re-sync Gradle after adding file

#### Issue 3: FCM Notifications Not Sending
**Solution**:
- Verify service account JSON is correct
- Check project ID in FCM URL matches Firebase project
- Ensure device has internet connection
- Check Firebase Console → Cloud Messaging for errors

#### Issue 4: Login Fails
**Solution**:
- Verify Firebase Authentication is enabled
- Check email/password in Firebase Console → Authentication → Users
- Ensure device has internet connection for first login
- Check Firebase Console for authentication errors

#### Issue 5: Payment Data Not Showing
**Solution**:
- Verify Firebase Realtime Database rules allow read access
- Check database structure matches expected format
- Ensure app has internet connection
- Check Logcat for Firebase errors

### Next Steps

1. **Monitor Usage**:
   - Check Firebase Console → Analytics
   - Monitor Realtime Database usage
   - Track authentication events

2. **Regular Maintenance**:
   - Update dependencies monthly
   - Rotate service account keys quarterly
   - Review and update Firebase rules
   - Backup database regularly

3. **Feature Enhancements**:
   - Add payment analytics
   - Implement admin roles
   - Add bulk operations
   - Create admin activity logs

### Firebase Rules (Recommended)

```json
{
  "rules": {
    "payments": {
      ".read": "auth != null",
      ".write": "auth != null"
    },
    "verified": {
      ".read": "auth != null",
      ".write": "auth != null"
    },
    "users": {
      ".read": "auth != null",
      "$uid": {
        ".write": "auth != null"
      }
    }
  }
}
```

## User Interface

### Home Tab
- Statistics cards showing pending and total premium users
- List of pending payment verification requests
- Each item shows: username, UID, payment method, transaction ID, status

### Premium Tab
- Search bar for filtering users
- List of all verified premium users
- Copy transaction ID functionality

### Notifications Tab
- Toggle between "Send to All" and "Send to Specific User"
- Input fields for:
  - Notification title
  - Notification body
  - FCM token (for specific user)
- Send button triggers notification delivery

### Payment Details Dialog
- User information display
- Payment method icon
- Transaction details
- Action buttons:
  - **Verify**: Unlocks app features + sends notification
  - **Save as Premium**: Moves to verified list
  - **Lock**: Re-locks user features
  - **Delete**: Removes payment request

## Security Considerations

⚠️ **Important Security Notes**:

1. **Service Account Credentials**: The `AccessToken.java` file contains sensitive Firebase service account credentials. In production:
   - Move credentials to a secure backend service
   - Use environment variables or secure key storage
   - Never commit credentials to version control

2. **Admin Authentication**: Ensure only authorized administrators have login credentials

3. **Firebase Rules**: Implement proper security rules to restrict database access

4. **API Keys**: Rotate service account keys periodically

## Notification Permission

The app requests `POST_NOTIFICATIONS` permission on Android 13+ (API 33+) to ensure notifications can be sent successfully.

## Troubleshooting

### FCM Notifications Not Sending
- Verify service account credentials are correct
- Check Firebase project ID in the FCM URL
- Ensure FCM is enabled in Firebase Console
- Verify OAuth2 token generation is successful

### Payment Verification Not Working
- Check Firebase Realtime Database rules
- Verify user UID exists in database
- Ensure internet connectivity

### Login Issues
- First-time login requires internet connection
- Check Firebase Authentication is enabled
- Verify email/password credentials

## Version Information

- **Version Code**: 1
- **Version Name**: 1.0
- **Package Name**: com.dream.pediadmin
- **Compile SDK**: 36
- **Min SDK**: 24
- **Target SDK**: 36

## License

Proprietary - Dream Pediatrics

## Support

For technical support or questions, contact the development team.

---

**Last Updated**: 2024
