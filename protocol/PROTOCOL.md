# RemotePair Protocol

## Signaling (WebSocket, JSON)

Client → server:
- `{type:"register", role:"host"|"controller"}` → `{type:"registered", id:"<9 digits>"}`
- `{type:"connect", id:"<host id>"}` (controller only) → `{type:"connected", peerId}` to controller; `{type:"controller_joined", peerId}` to host
- `{type:"signal", payload:<SDP or ICE>}` relayed to paired peer as `{type:"signal", from, payload}`
- `{type:"disconnect"}`

Server → client:
- `{type:"peer_left"}` when the other side disconnects
- `{type:"error", error:"host_not_found"|"rate_limited"|"bad_role"}`

## WebRTC

Controller is the offerer. It creates two DataChannels before `createOffer`:
- `input` — unordered-ish (ordered=true, maxRetransmits=0) for low-latency input events
- `files` — reliable, ordered

Host publishes one video track (host's screen).

Signal payload shapes:
```json
{"type":"offer","sdp":"..."}
{"type":"answer","sdp":"..."}
{"candidate":"...","sdpMid":"0","sdpMLineIndex":0}
```

## Input DataChannel (JSON lines)

Controller → host:
- `{t:"tap", x:0.5, y:0.3}`  (normalized 0..1)
- `{t:"swipe", x1,y1,x2,y2, ms:200}`
- `{t:"key", code:67}`  (Android KeyEvent)
- `{t:"text", s:"hello"}`
- `{t:"back"}` / `{t:"home"}` / `{t:"recents"}`

## Files DataChannel

Framing: each message is a JSON header line followed by 0+ binary chunks on the same channel. Chunk size 64KB; up to 8 in flight.

- `{op:"list", id:1, path:"/storage/emulated/0"}` → `{op:"list_res", id:1, path, entries:[{name,isDir,size,mtime}]}`
- `{op:"get", id:2, path, offset, length}` → `{op:"get_res", id:2, size}` + binary chunks `{op:"chunk", id:2, seq, done}`
- `{op:"put", id:3, path, size}` → controller streams `{op:"chunk", id:3, seq, done}` + binary
- `{op:"delete", id:4, path}` → `{op:"ok", id:4}` or `{op:"err", id:4, msg}`
- `{op:"mkdir", id:5, path}` → `{op:"ok"/"err"}`
