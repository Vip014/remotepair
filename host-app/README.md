# Host APK — phase 2 (not yet built)

The device-being-controlled app. Responsibilities:

1. Register with signaling server as `role: "host"`.
2. Capture screen via `MediaProjection` + `MediaCodec` H.264 encoder, publish as a WebRTC video track.
3. Receive input events on DataChannel `input` and dispatch them via an `AccessibilityService` (`dispatchGesture`, `performGlobalAction`).
4. Serve files on DataChannel `files` using the protocol in `../protocol/PROTOCOL.md`.
5. Show the user:
   - 9-digit Host ID + session passphrase on the main screen
   - A persistent "Session active" notification while a controller is paired

Design decisions pending:
- Whether to require passphrase match before accepting the WebRTC offer (recommended yes).
- Whether file browser mode runs without MediaProjection grant (recommended yes — storage permission only).
- Which OEM input plugins to support later (Samsung, Xiaomi) for lower-latency injection.
