// RemotePair signaling server
// Pairs hosts and controllers by 9-digit ID, relays WebRTC SDP + ICE.
// Run: node server.js

const http = require('http');
const fs = require('fs');
const path = require('path');
const { WebSocketServer } = require('ws');

const WEB_DIR = path.join(__dirname, 'public');

const PORT = process.env.PORT || 8080;
const ID_EXPIRY_MS = 24 * 60 * 60 * 1000;        // 24h idle
const RATE_WINDOW_MS = 60 * 1000;                 // 1 min
const MAX_CONNECT_PER_WINDOW = 5;

// Map<id, { ws, role, lastSeen, peer? }>
const peers = new Map();
// Map<ip, { count, resetAt }>
const rateMap = new Map();

function makeId() {
  for (let i = 0; i < 10; i++) {
    const id = String(Math.floor(100_000_000 + Math.random() * 900_000_000));
    if (!peers.has(id)) return id;
  }
  throw new Error('id space exhausted');
}

function rateLimited(ip) {
  const now = Date.now();
  const entry = rateMap.get(ip);
  if (!entry || entry.resetAt < now) {
    rateMap.set(ip, { count: 1, resetAt: now + RATE_WINDOW_MS });
    return false;
  }
  entry.count += 1;
  return entry.count > MAX_CONNECT_PER_WINDOW;
}

function send(ws, obj) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(obj));
}

const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ ok: true, peers: peers.size }));
    return;
  }

  // Serve the web controller (single page) at / and /control.
  const urlPath = (req.url || '/').split('?')[0];
  if (urlPath === '/' || urlPath === '/control' || urlPath === '/index.html') {
    fs.readFile(path.join(WEB_DIR, 'index.html'), (err, buf) => {
      if (err) {
        res.writeHead(500, { 'Content-Type': 'text/plain' });
        res.end('web controller not found\n');
      } else {
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(buf);
      }
    });
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('not found\n');
});

const wss = new WebSocketServer({ server });

wss.on('connection', (ws, req) => {
  const ip = req.socket.remoteAddress || 'unknown';
  ws.id = null;
  ws.role = null;
  ws.peerId = null;

  ws.on('message', (raw) => {
    let msg;
    try { msg = JSON.parse(raw.toString()); } catch { return; }

    if (msg.type === 'register') {
      // Role: 'host' or 'controller'
      if (msg.role !== 'host' && msg.role !== 'controller') {
        send(ws, { type: 'error', error: 'bad_role' });
        return;
      }
      const id = makeId();
      ws.id = id;
      ws.role = msg.role;
      peers.set(id, { ws, role: msg.role, lastSeen: Date.now() });
      send(ws, { type: 'registered', id });
      console.log(`[+] ${msg.role} ${id} from ${ip}`);
      return;
    }

    if (msg.type === 'connect') {
      // Controller asks to pair with host id
      if (rateLimited(ip)) {
        send(ws, { type: 'error', error: 'rate_limited' });
        return;
      }
      const target = peers.get(msg.id);
      if (!target || target.role !== 'host') {
        send(ws, { type: 'error', error: 'host_not_found' });
        return;
      }
      ws.peerId = msg.id;
      target.ws.peerId = ws.id;
      send(ws, { type: 'connected', peerId: msg.id });
      send(target.ws, { type: 'controller_joined', peerId: ws.id });
      console.log(`[~] pair ${ws.id} <-> ${msg.id}`);
      return;
    }

    if (msg.type === 'signal') {
      // Relay WebRTC SDP or ICE to the paired peer
      if (!ws.peerId) return;
      const target = peers.get(ws.peerId);
      if (!target) return;
      send(target.ws, { type: 'signal', from: ws.id, payload: msg.payload });
      return;
    }

    if (msg.type === 'disconnect') {
      if (ws.peerId) {
        const target = peers.get(ws.peerId);
        if (target) send(target.ws, { type: 'peer_left' });
        ws.peerId = null;
      }
      return;
    }

    if (msg.type === 'ping') {
      send(ws, { type: 'pong' });
    }
  });

  ws.on('close', () => {
    if (ws.id) {
      peers.delete(ws.id);
      console.log(`[-] ${ws.role || '?'} ${ws.id}`);
    }
    if (ws.peerId) {
      const target = peers.get(ws.peerId);
      if (target) send(target.ws, { type: 'peer_left' });
    }
  });
});

// Expire idle registrations
setInterval(() => {
  const cutoff = Date.now() - ID_EXPIRY_MS;
  for (const [id, p] of peers) {
    if (p.lastSeen < cutoff && p.ws.readyState !== p.ws.OPEN) {
      peers.delete(id);
    }
  }
  // Prune rate map
  const now = Date.now();
  for (const [ip, e] of rateMap) if (e.resetAt < now) rateMap.delete(ip);
}, 60_000);

server.listen(PORT, () => {
  console.log(`RemotePair signaling listening on :${PORT}`);
  console.log(`health:  http://localhost:${PORT}/health`);
  console.log(`ws:      ws://localhost:${PORT}`);
});
