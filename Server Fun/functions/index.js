// functions/index.js
'use strict';

const functions = require('firebase-functions');
const { defineString } = require('firebase-functions/params');
const admin = require('firebase-admin');
const crypto = require('crypto');

admin.initializeApp();

console.log('wrapAesKey module loaded (index.js)');

process.on('unhandledRejection', (reason, p) => {
  console.error('Unhandled Rejection at:', p, 'reason:', reason);
});

// Modern approach: Use Firebase params (replaces deprecated functions.config())
const KEY_BUCKET = defineString('TEXTBOOK_KEY_BUCKET', {
  description: 'Private bucket name where aes_key.bin is stored',
  default: 'dream-pedi-secrets'
});

const KEY_PATH = defineString('TEXTBOOK_KEY_PATH', {
  description: 'Path to the AES key file within the bucket',
  default: 'aes_key.bin'
});

console.log('wrapAesKey configured with params');

async function verifyIdTokenFromRequest(req) {
  const authHeader = req.get('Authorization') || req.get('authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    throw new Error('Missing Authorization bearer token');
  }
  const idToken = authHeader.split(' ')[1];
  const decoded = await admin.auth().verifyIdToken(idToken);
  return decoded;
}

async function handleWrapAesKey(req, res) {
  try {
    const keyBucket = KEY_BUCKET.value();
    const keyPath = KEY_PATH.value();
    
    console.log('Received request for wrapAesKey, method:', req.method);
    console.log('Using bucket:', keyBucket, 'path:', keyPath);

    if (req.method !== 'POST') {
      res.status(405).send('POST only');
      return;
    }

    await verifyIdTokenFromRequest(req);

    const body = req.body || {};
    const pubB64 = body.device_public_key_b64;
    if (!pubB64) {
      return res.status(400).json({ error: 'missing device_public_key_b64' });
    }

    const bucket = admin.storage().bucket(keyBucket);
    const file = bucket.file(keyPath);
    const [exists] = await file.exists();
    if (!exists) {
      console.error('AES key not found at', keyBucket, keyPath);
      return res.status(500).json({ error: 'AES key not found on server' });
    }
    const [keyBuf] = await file.download();

    const der = Buffer.from(pubB64, 'base64');
    const b64 = der.toString('base64');
    const lines = b64.match(/.{1,64}/g) || [];
    const pem = '-----BEGIN PUBLIC KEY-----\n' + lines.join('\n') + '\n-----END PUBLIC KEY-----\n';

    // Use SHA-1 for maximum Android compatibility
    const wrapped = crypto.publicEncrypt({
      key: pem, 
      padding: crypto.constants.RSA_PKCS1_OAEP_PADDING, 
      oaepHash: 'sha1'
    }, keyBuf);

    const wrappedB64 = wrapped.toString('base64');
    console.log('Successfully wrapped key with SHA-1 OAEP; returning result');
    return res.json({ wrapped_key_b64: wrappedB64 });
  } catch (err) {
    console.error('Error processing request:', err);
    const msg = (err && err.message) ? err.message : 'internal error';
    if (msg.toLowerCase().includes('token')) {
      return res.status(401).json({ error: msg });
    }
    return res.status(500).json({ error: msg });
  }
}

try {
  if (typeof functions.region === 'function') {
    exports.wrapAesKey = functions.region('us-central1').https.onRequest(handleWrapAesKey);
    console.log("Exported wrapAesKey with functions.region('us-central1')");
  } else {
    exports.wrapAesKey = functions.https.onRequest(handleWrapAesKey);
    console.log('Exported wrapAesKey with functions.https.onRequest (no region())');
  }
} catch (err) {
  console.warn('functions.region() not available or threw; exporting using functions.https.onRequest', err);
  exports.wrapAesKey = functions.https.onRequest(handleWrapAesKey);
}

module.exports.rawWrap = handleWrapAesKey;