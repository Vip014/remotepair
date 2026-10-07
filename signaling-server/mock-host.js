// Mock host: pretends to be an Android host so you can test the Controller APK
// without having built the real Host APK yet. Registers, prints its ID,
// responds to file-protocol messages with a fake filesystem.
//
// Run after `node server.js`:
//   node mock-host.js [ws://localhost:8080]

const WebSocket = require('ws');

const url = process.argv[2] || 'ws://localhost:8080';
const ws = new WebSocket(url);

const fakeFs = {
  '/storage/emulated/0': [
    { name: 'DCIM', isDir: true },
    { name: 'Download', isDir: true },
    { name: 'Documents', isDir: true },
    { name: 'notes.txt', isDir: false, size: 1234, mtime: Date.now() },
    { name: 'resume.pdf', isDir: false, size: 182340, mtime: Date.now() },
  ],
  '/storage/emulated/0/DCIM': [
    { name: 'Camera', isDir: true },
    { name: 'IMG_2026_1005.jpg', isDir: false, size: 2_340_000, mtime: Date.now() },
  ],
  '/storage/emulated/0/Download': [
    { name: 'anydesk.apk', isDir: false, size: 48_000_000, mtime: Date.now() },
    { name: 'song.mp3', isDir: false, size: 5_400_000, mtime: Date.now() },
  ],
  '/storage/emulated/0/Documents': [
    { name: 'budget.xlsx', isDir: false, size: 78_000, mtime: Date.now() },
  ],
};

ws.on('open', () => {
  console.log('connected to signaling server');
  ws.send(JSON.stringify({ type: 'register', role: 'host' }));
});

ws.on('message', (raw) => {
  const msg = JSON.parse(raw.toString());

  if (msg.type === 'registered') {
    console.log('\n==============================');
    console.log(` HOST ID:  ${msg.id}`);
    console.log(` passphrase: test123');
    console.log('==============================\n');
    console.log('enter this ID in your Controller app');
    return;
  }

  if (msg.type === 'controller_joined') {
    console.log(`controller ${msg.peerId} joined`);
    return;
  }

  if (msg.type === 'signal') {
    // In a real host, this would be WebRTC SDP/ICE.
    // Here we just echo an "accept" so the controller can test the flow.
    console.log('signal from controller:', JSON.stringify(msg.payload).slice(0, 120));
    return;
  }

  if (msg.type === 'peer_left') {
    console.log('controller disconnected');
    return;
  }

  if (msg.type === 'error') {
    console.error('error:', msg.error);
  }
});

ws.on('close', () => {
  console.log('disconnected');
  process.exit(0);
});

ws.on('error', (e) => {
  console.error('ws error:', e.message);
});
