# Native Android preview

This is a Pixel-first development candidate, not a Google Play release. See
[qualification](qualification.md) for evidence and remaining acceptance checks.
The repository remains private. No Android Auto support is claimed.

## Build locally

Install Android Studio, Android SDK platform 36 and a compatible JDK (17 or newer).
Set `ANDROID_HOME` to your SDK directory, or use an untracked
`android/local.properties` containing `sdk.dir=...`.

```sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug application ID is `studio.voxkin.android`. This does not replace the iOS
application or modify the gateway plugin. A debug APK is intended for local
qualification; retain its signing identity for upgrades, and never commit signing
keys. Minimum Android version is 12 (API 31); on-device recognizer availability
still depends on the device and installed language models.

## Connect

1. Ensure the phone can reach your gateway. For a private tailnet address, install
   Tailscale, join the same authorized tailnet and turn its VPN connection on.
2. Open Voxkin Settings. Paste an owner setup link or a restricted invitation
   supplied by the gateway owner, then stage it. Staging does not send credentials.
3. Verify the displayed gateway. For owner access, tap Connect and approve the exact
   native device request in the gateway. For guest access, tap Enroll, have the
   owner approve the displayed phone ID, then tap Connect.
4. Never share setup links in public issues, screenshots, logs or source files.
   A restricted guest grant is separate from an operator device grant.

The client requires TLS and rejects redirect-based credential forwarding. Do not
bypass certificate validation to connect a local test server.

## Talk and history

Choose English or Spanish. Tap Record, wait for the Listening indicator, speak,
then stop and review the transcript. Send is a separate explicit action. Missing
on-device speech support falls back to typing, not cloud transcription.

Gateway speech is opt-in: reply text may be processed by the configured gateway
speech provider and fallback. Android does not replace the gateway's privacy
policy. Leaving the application stops capture/playback and disconnects; reconnect
explicitly when returning. No pending message is automatically replayed.

History belongs to the gateway, not a vendor-hosted conversation service. Delete
requires confirmation and affects the selected gateway conversation. Forgetting
local access is not server-side revocation; revoke a device at the gateway when
needed.

## Preview gaps

Camera QR scanning and Android Auto are not implemented in this first candidate.
Paste/deep-link setup is supported. The Android preview is not yet a claim of full
iOS feature parity (including owner invitation-management UI and hands-free
conversation). Physical microphone/language, routing, lifecycle and live gateway
checks must be recorded before calling it qualified.
