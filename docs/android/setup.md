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

Choose English or Spanish in Settings. On Talk, tap the microphone, wait for
“Listening · speak now”, speak, then tap the Stop square and review the transcript.
The primary control becomes a Send arrow only after transcription finishes.
Send is a separate explicit action; use the keyboard button to type instead. Missing
on-device speech support falls back to typing, not cloud transcription. If final
recognition fails or times out, the last useful recognized words remain in the
draft with an incomplete-transcription warning. Review them before sending;
Cancel still discards the current capture.

Speech source selects **Gateway default** (`tts.speak`) or **Local voices**
(`voxkin.speak`) when advertised by an owner gateway. Restricted access only permits
Local voices. Source choice is saved for this gateway/access mode; no alternate
source is silently substituted. “Local voices” means the gateway plugin's catalog,
not Android text-to-speech.

Android model checks/downloads are under **Troubleshooting → Show on-device
dictation details**. They concern speech input, not the spoken reply source.

Gateway speech is opt-in: reply text may be processed by the configured gateway
speech provider and fallback. Consent and voice selection are remembered for that
gateway after authentication. Preview voice uses the same consent and Stop audio
controls as replies. Brief / Balanced / Full applies only to new messages from
this phone; slash and bang commands are sent unchanged. Android does not replace the gateway's privacy
policy. Leaving the application stops capture/playback and disconnects. Returning
reconnects saved, previously approved access and restores the selected conversation.
No pending message, microphone capture, or speech is automatically replayed. Drafts
survive same-conversation backgrounding only in memory, not process termination.
Explicit Disconnect suppresses this reconnect for the current app process; a fresh
launch may reconnect saved access. Forget removes the saved reconnect permission.
If restoration fails, choose a conversation in History or explicitly start New;
sending remains blocked until a conversation is ready.

Replies can appear progressively under **Reply in progress** when the gateway
provides chat deltas. Only the completed reply is spoken. Speech uses a short
first clip and prepares one following clip during playback; Stop cancels both.
Restricted connections may remain final-only depending on their protocol.

History belongs to the gateway, not a vendor-hosted conversation service. Rows show
timestamps/previews when supplied, and mark the current conversation. Use Load more
conversations and Load older messages to browse additional pages (up to500 per
view). Refresh conversation returns to the latest messages without playing audio.
After deleting a conversation, refresh History before continuing through pages.
Delete
requires confirmation and affects the selected gateway conversation. Forgetting
local access is not server-side revocation; revoke a device at the gateway when
needed.

## Preview gaps

Camera QR scanning and Android Auto are not implemented in this first candidate.
Paste/deep-link setup is supported. The Android preview is not yet a claim of full
iOS feature parity (including owner invitation-management UI and hands-free
conversation). Physical microphone/language, routing, lifecycle and live gateway
checks must be recorded before calling it qualified.

See [iPhone presentation parity](iphone-parity.md) for the shared design and
explicit remaining feature differences.
