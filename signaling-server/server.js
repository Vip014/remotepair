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

// Map<id, { ws, role, lastSeen, peer?, lat?, lon? }>
const peers = new Map();
// Map<ip, { count, resetAt }>
const rateMap = new Map();

// Admin connection log (most recent last, capped).
const LOG_MAX = 1000;
const connLog = [];
function logEvent(e) {
  connLog.push({ ...e, at: Date.now() });
  if (connLog.length > LOG_MAX) connLog.splice(0, connLog.length - LOG_MAX);
}

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

  // ---- Admin (IP + location log), gated by ADMIN_PASSWORD ----
  if (urlPath === '/admin' || urlPath === '/admin/data') {
    const ADMIN = process.env.ADMIN_PASSWORD || '';
    const key = new URL(req.url, 'http://x').searchParams.get('key') || '';
    if (ADMIN === '' || key !== ADMIN) {
      res.writeHead(401, { 'Content-Type': 'text/plain' });
      res.end(ADMIN === '' ? 'ADMIN_PASSWORD not set on server\n' : 'unauthorized\n');
      return;
    }
    if (urlPath === '/admin/data') {
      const live = [];
      for (const [id, p] of peers) {
        live.push({ id, role: p.role, lat: p.lat, lon: p.lon, hasPassword: !!p.password });
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ live, log: connLog.slice(-300).reverse() }));
      return;
    }
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(adminPage(key));
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('not found\n');
});

function adminPage(key) {
  const k = JSON.stringify(key);
  return `<!doctype html><html><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>RemotePair Admin</title>
<style>
  body{margin:0;background:#0b0e14;color:#e6e8ec;font-family:system-ui,sans-serif;padding:16px}
  h1{font-size:18px}h2{font-size:14px;color:#9aa1ad;margin:18px 0 8px}
  table{width:100%;border-collapse:collapse;font-size:13px}
  th,td{text-align:left;padding:8px 10px;border-bottom:1px solid #2a3140}
  th{color:#9aa1ad;font-weight:600}
  a{color:#3b82f6}
  .pill{display:inline-block;padding:2px 8px;border-radius:999px;background:#1c212d;font-size:12px}
</style></head><body>
<h1>RemotePair — Admin</h1>
<div id="meta" class="pill">loading…</div>
<h2>Live devices</h2>
<table id="live"><thead><tr><th>ID</th><th>Role</th><th>Password</th><th>Location</th></tr></thead><tbody></tbody></table>
<h2>Recent connections</h2>
<table id="log"><thead><tr><th>Time</th><th>Event</th><th>ID</th><th>Role</th><th>IP</th><th>Location</th></tr></thead><tbody></tbody></table>
<script>
const KEY=${k};
function maps(lat,lon){return (lat!=null&&lon!=null)?'<a href="https://maps.google.com/?q='+lat+','+lon+'" target="_blank">'+lat.toFixed(5)+', '+lon.toFixed(5)+'</a>':'—';}
async function load(){
  try{
    const r=await fetch('/admin/data?key='+encodeURIComponent(KEY));
    if(!r.ok){document.getElementById('meta').textContent='unauthorized';return;}
    const d=await r.json();
    document.getElementById('meta').textContent=d.live.length+' live · '+d.log.length+' logged';
    document.querySelector('#live tbody').innerHTML=d.live.map(p=>
      '<tr><td>'+p.id+'</td><td>'+p.role+'</td><td>'+(p.hasPassword?'yes':'no')+'</td><td>'+maps(p.lat,p.lon)+'</td></tr>').join('')||'<tr><td colspan=4>none</td></tr>';
    document.querySelector('#log tbody').innerHTML=d.log.map(e=>
      '<tr><td>'+new Date(e.at).toLocaleString()+'</td><td>'+e.type+'</td><td>'+(e.id||'')+'</td><td>'+(e.role||'')+'</td><td>'+(e.ip||'')+'</td><td>'+maps(e.lat,e.lon)+'</td></tr>').join('');
  }catch(e){document.getElementById('meta').textContent='error';}
}
load();setInterval(load,5000);
</script></body></html>`;
}

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
      // Let a reconnecting client keep its previous id if it's a valid,
      // free 9-digit id (or held only by a dead socket we can reclaim).
      let id;
      const wanted = msg.id;
      if (typeof wanted === 'string' && /^\d{9}$/.test(wanted)) {
        const existing = peers.get(wanted);
        if (!existing) {
          id = wanted;
        } else if (existing.ws.readyState !== existing.ws.OPEN) {
          peers.delete(wanted);
          id = wanted;
        } else {
          id = makeId();
        }
      } else {
        id = makeId();
      }
      ws.id = id;
      ws.role = msg.role;
      // Optional host password: controllers must supply it (or the admin
      // password) to connect. Empty means the host is open.
      const password = typeof msg.password === 'string' ? msg.password : '';
      peers.set(id, { ws, role: msg.role, lastSeen: Date.now(), password });
      send(ws, { type: 'registered', id });
      logEvent({ type: 'register', id, role: msg.role, ip });
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
      // Password gate: if the host set a password, require it — or the global
      // admin password (set via ADMIN_PASSWORD env var on the server).
      const ADMIN = process.env.ADMIN_PASSWORD || '';
      const needed = target.password || '';
      if (needed) {
        const given = typeof msg.password === 'string' ? msg.password : '';
        const ok = given === needed || (ADMIN !== '' && given === ADMIN);
        if (!ok) {
          send(ws, { type: 'error', error: 'auth_failed' });
          return;
        }
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

    if (msg.type === 'loc') {
      // Host reports its location; store on the peer and log it.
      const lat = Number(msg.lat), lon = Number(msg.lon);
      if (ws.id && isFinite(lat) && isFinite(lon)) {
        const self = peers.get(ws.id);
        if (self) { self.lat = lat; self.lon = lon; }
        logEvent({ type: 'loc', id: ws.id, role: ws.role, ip, lat, lon });
      }
      return;
    }

    if (msg.type === 'ping') {
      if (ws.id) {
        const self = peers.get(ws.id);
        if (self) self.lastSeen = Date.now();
      }
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
