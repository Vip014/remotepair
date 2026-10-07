# RemotePair Signaling Server

Tiny WebSocket server that pairs hosts and controllers by 9-digit ID and relays
WebRTC SDP + ICE between them. No video or files pass through it — only
signaling.

## Run

```bash
npm install
npm start
```

Default port: `8080`. Override with `PORT=3000 npm start`.

## Run on Android (Termux)

1. Install Termux from F-Droid
2. `pkg install nodejs`
3. Copy this folder onto the phone (via GitHub, email, or `termux-setup-storage`)
4. `cd signaling-server && npm install && npm start`
5. Your phone's IP is in `ifconfig` — use `ws://<that-ip>:8080` from the Controller APK on the same Wi-Fi

## Run on a VPS

Any $5/month VPS works. Open port 8080, run `npm start` under `pm2` or
`systemd`. The Controller APK talks to `wss://your-domain:8080` if you put it
behind TLS (nginx + Let's Encrypt).

## Test with mock host

Terminal 1:
```bash
npm start
```

Terminal 2:
```bash
node mock-host.js
```

The mock prints a 9-digit host ID. Type that into the Controller APK and the
"connect" step will succeed (WebRTC itself won't complete against the mock,
but you can verify signaling, UI, and the connection flow).

## Protocol

See `../protocol/PROTOCOL.md`.
