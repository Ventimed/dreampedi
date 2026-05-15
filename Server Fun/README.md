# Dream Pediatrics Cloud Functions

Firebase Cloud Functions for secure AES key distribution.

## 🚀 Quick Deploy

### Option 1: Using the Deploy Script (Easiest)

```cmd
deploy.cmd
```

This automatically deploys with your configuration.

### Option 2: Manual Deployment

```bash
cd "c:\Users\NaB\Videos\Dream Pediatrics\Server Fun"

# Deploy with environment variables
firebase deploy --only functions:wrapAesKey --project dream-pedi ^
  --set-env-vars TEXTBOOK_KEY_BUCKET=dream-pedi-secrets,TEXTBOOK_KEY_PATH=aes_key.bin
```

### Option 3: Using .env.yaml (Recommended for Production)

1. Edit `functions/.env.yaml`:
   ```yaml
   TEXTBOOK_KEY_BUCKET: dream-pedi-secrets
   TEXTBOOK_KEY_PATH: aes_key.bin
   ```

2. Deploy:
   ```bash
   firebase deploy --only functions:wrapAesKey --project dream-pedi
   ```

---

## ⚙️ Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `TEXTBOOK_KEY_BUCKET` | Private bucket name | `dream-pedi-secrets` |
| `TEXTBOOK_KEY_PATH` | Path to AES key file | `aes_key.bin` |

### Configuration Files

- **`functions/.env`** - Local development (not committed to git)
- **`functions/.env.yaml`** - Production deployment (committed to git)
- **`functions/.env.example`** - Template for new developers

---

## 📁 Project Structure

```
Server Fun/
├── functions/
│   ├── index.js              # Main Cloud Function code
│   ├── package.json          # Dependencies
│   ├── .env                  # Local config (gitignored)
│   ├── .env.yaml             # Production config
│   └── .env.example          # Config template
├── firebase.json             # Firebase configuration
├── .firebaserc               # Firebase project settings
├── deploy.cmd                # Quick deployment script
└── README.md                 # This file
```

---

## 🔧 Development

### Install Dependencies

```bash
cd functions
npm install
```

### Test Locally

```bash
# Start Firebase emulators
firebase emulators:start --only functions

# Function available at:
# http://localhost:5001/dream-pedi/us-central1/wrapAesKey
```

### View Logs

```bash
# Real-time logs
firebase functions:log --project dream-pedi --follow

# Recent logs
firebase functions:log --project dream-pedi
```

---

## 🔐 Security

### What This Function Does

1. **Authenticates** user via Firebase ID token
2. **Receives** device's RSA public key
3. **Fetches** AES key from private bucket (`dream-pedi-secrets`)
4. **Wraps** AES key with device's public key (RSA-OAEP)
5. **Returns** wrapped key to app

### Security Features

- ✅ Authentication required (Firebase ID token)
- ✅ Device-specific key wrapping
- ✅ Private bucket access only
- ✅ No key caching
- ✅ Audit logging enabled

---

## 🧪 Testing

### Test with curl

```bash
# Get ID token from your app, then:
curl -X POST https://us-central1-dream-pedi.cloudfunctions.net/wrapAesKey ^
  -H "Authorization: Bearer YOUR_ID_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"device_public_key_b64\":\"YOUR_PUBLIC_KEY\"}"
```

**Expected response:**
```json
{
  "wrapped_key_b64": "BASE64_ENCODED_WRAPPED_KEY..."
}
```

---

## 🔄 Migration from Deprecated functions.config()

**Old approach (deprecated):**
```bash
firebase functions:config:set textbook.key_bucket="dream-pedi-secrets"
```

**New approach (current):**
```bash
# Option 1: Edit .env.yaml
echo TEXTBOOK_KEY_BUCKET: dream-pedi-secrets > functions\.env.yaml

# Option 2: Deploy with --set-env-vars
firebase deploy --only functions:wrapAesKey ^
  --set-env-vars TEXTBOOK_KEY_BUCKET=dream-pedi-secrets
```

**Why the change?**
- Firebase deprecated `functions.config()` and Runtime Config API
- New `params` package is more flexible and modern
- Environment variables are easier to manage
- Better support for local development

---

## 📊 Monitoring

### Firebase Console

- **Functions Dashboard**: https://console.firebase.google.com/project/dream-pedi/functions
- **Logs**: https://console.firebase.google.com/project/dream-pedi/functions/logs

### Key Metrics

- Invocations per day
- Execution time (should be < 2 seconds)
- Error rate (should be < 1%)
- Memory usage (should be < 256 MB)

---

## 🐛 Troubleshooting

### "AES key not found on server"

**Cause:** Wrong bucket name or key doesn't exist

**Solution:**
```bash
# Check bucket
gsutil ls gs://dream-pedi-secrets/aes_key.bin

# Update .env.yaml with correct bucket name
```

### "Permission denied accessing bucket"

**Cause:** Service account lacks access

**Solution:**
```bash
gsutil iam ch serviceAccount:dream-pedi@appspot.gserviceaccount.com:objectViewer gs://dream-pedi-secrets
```

### "Token verification failed"

**Cause:** Invalid or expired ID token

**Solution:** Refresh the token in your app (tokens expire after 1 hour)

---

## 📚 Documentation

- **[CLOUD_FUNCTIONS_DEPLOYMENT_GUIDE.md](../converter/CLOUD_FUNCTIONS_DEPLOYMENT_GUIDE.md)** - Complete deployment guide
- **[CUSTOMIZE_BUCKET.md](CUSTOMIZE_BUCKET.md)** - How to customize bucket configuration
- **[BUCKET_SETUP_GUIDE.md](../converter/BUCKET_SETUP_GUIDE.md)** - How to create storage buckets

---

## 🎯 Quick Commands

```bash
# Deploy
firebase deploy --only functions:wrapAesKey --project dream-pedi

# View logs
firebase functions:log --project dream-pedi

# Test locally
firebase emulators:start --only functions

# Check environment variables
cat functions\.env.yaml

# List deployed functions
firebase functions:list --project dream-pedi
```

---

**Function URL:** `https://us-central1-dream-pedi.cloudfunctions.net/wrapAesKey`  
**Project:** `dream-pedi`  
**Bucket:** `dream-pedi-secrets`  
**Runtime:** Node.js 20  
**Region:** us-central1
