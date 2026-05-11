// small express wrapper that ensures the container listens on process.env.PORT
const express = require('express');
const app = express();

app.use(express.json({ limit: '10mb' }));

// import the raw handler exported by index.js
const mod = require('./index.js');
const rawWrap = mod.rawWrap;

if (typeof rawWrap !== 'function') {
  console.error('rawWrap handler not found - aborting');
  process.exit(1);
}

// mount at root; wrap to ensure async errors are caught
app.post('/', (req, res) => {
  try {
    return rawWrap(req, res);
  } catch (err) {
    console.error('Error in rawWrap wrapper:', err);
    res.status(500).json({ error: 'internal server error' });
  }
});

// healthcheck endpoint for quick checks
app.get('/_health', (req, res) => res.json({ ok: true }));

const port = process.env.PORT ? parseInt(process.env.PORT, 10) : 8080;
app.listen(port, () => {
  console.log(`Express server listening on port ${port}`);
});
