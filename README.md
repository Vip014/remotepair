# RemotePair

A remote-access app similar to AnyDesk. Two Android apps:

- **Controller APK** — view host's screen, send input, browse host's files. **This repo contains the Controller only (MVP).**
- **Host APK** — to be built in phase 2. Captures its own screen via `MediaProjection`, injects input via an Accessibility service, serves files over the files DataChannel.

Plus a tiny **Node.js signaling server** that pairs them by 9-digit ID.

```
remotepair/
├── signaling-server/      Node.js WebSocket signaling + mock host
├── controller-app/        Android (Kotlin + Compose) — this is the APK
├── protocol/              PROTOCOL.md — signaling + DataChannel formats
└── .github/workflows/     Auto-build Controller APK on every push
```

## How to get the APK on your phone (no laptop needed)

1. Create a GitHub account (if you don't have one) and install the **GitHub** mobile app.
2. Create a new **public** repo named `remotepair` (public = free Actions minutes).
3. Upload this folder's contents to the repo. On phone, easiest route:
   - Install **Termux** (from F-Droid) → `pkg install git` → `git clone <your repo>` → copy these files in → `git add . && git commit -m "init" && git push`.
   - Or: use the GitHub mobile app's "Add file" to upload file by file (slow but works).
4. Open the repo on github.com in your phone browser → **Actions** tab → wait ~5 minutes.
5. Open the finished green run → scroll to **Artifacts** → download `RemotePair-Controller-debug` → inside the zip is `app-debug.apk`.
6. Open the APK on your phone to install (Android will ask to allow "install from unknown sources" for your browser or file manager — grant it).

## How to run the signaling server

Easiest on phone: **Termux**.
```bash
pkg install nodejs
cd signaling-server
npm install
npm start
```
Note your phone's Wi-Fi IP (`ifconfig | grep inet` or Settings → About phone). In the Controller APK, open Settings and set:
```
ws://<that-ip>:8080
```

For production, run the same thing on a VPS behind nginx + Let's Encrypt and use `wss://`.

## Current scope (MVP)

- ✅ Signaling server pairs host and controller by 9-digit ID
- ✅ Mock host (`mock-host.js`) so you can test the Controller end-to-end before the Host APK exists
- ✅ Controller APK: Home / Connected / Live Control / File Browser / Settings screens
- ✅ WebSocket signaling client
- ✅ WebRTC PeerConnection skeleton (offer + ICE)
- 🚧 File DataChannel wire-up (stub list shown; wiring pending real Host APK)
- 🚧 Input DataChannel wire-up (toggle shown; events pending real Host APK)
- ⏭ Host APK — phase 2

## Testing the Controller against the mock host

Terminal 1:
```bash
cd signaling-server && npm start
```
Terminal 2:
```bash
cd signaling-server && node mock-host.js
# prints a 9-digit host ID
```
In the Controller APK:
1. Open Settings → set signaling URL to `ws://<server-ip>:8080`
2. On Home → enter the mock's 9-digit ID + any passphrase → Connect
3. You should land on the "Connected" screen with a green "Paired" pill.

See `protocol/PROTOCOL.md` for message formats.
