# Android Pixel qualification — 2026-10-20

Status: native Android development preview installed for owner acceptance.
Not a Google Play release, full iOS parity, or universal Android support claim.

## Artifact and environment

- Version0.1.0 (1), package `studio.voxkin.android`, debug-signed.
- APK SHA256: `40e32ee2d7836f0c98dad353745e57111b0333dd2d1f6fc8057d788870865459`.
- Physical Pixel8a, Android17/API37. Minimum API31, target/compile API36.
- Existing OpenClaw2026.9.7 gateway and plugin; neither changed for Android.
- Both English (en-US) and Spanish (es-ES) reported installed by the on-device
  recognition support API after explicit Spanish-model download.

## Automated evidence

- **12/12 JVM tests:** endpoint/setup validation, Ed25519 signature, final-content
  filtering, speech chunk bounds, real TLS mock owner handshake and restricted
  request/redirect/polling contracts.
- **9/9 physical-device instrumentation tests:** encrypted credentials; guest/owner
  Forget isolation; restricted history max20; delete-then-send owned replacement;
  history lock under Stop; delayed synthesis cancellation; capture-context guards;
  background/foreground no automatic connection or recording; language support probe.
- Android lint: **0 errors,16 warnings** (dependency currency, style/manifest and
  launcher-icon shape). No clean-lint claim.
- Independent read-only review and follow-up confirmed fixes for the identified
  protocol, cancellation and context-switching bugs. See [review](review.md).

## Live physical-device evidence

- [x] APK install and launch; existing applications unaffected.
- [x] Tailscale route, TLS, owner challenge/signature, exact device approval and
  fresh connection; only operator.read/write requested (no admin scope).
- [x] Owner typed message → matching final response.
- [x] Owner history list/read/rename/delete on an isolated synthetic conversation.
- [x] English and Spanish gateway speech decoded/played to device speaker; Android media
  metrics recorded speech playback and audio-focus acquisition/release.
- [x] Restricted enrollment pending; Connect denied before owner approval.
- [x] Restricted approved connection, first Send without pressing New, final reply.
- [x] Restricted history list/read/rename/delete after correcting max20 page limit.
- [x] Next Send after deleting active restricted history creates owned replacement
  and receives final response.
- [x] Temporary guest conversations removed and test grant revoked; local guest
  access forgotten; saved owner identity/grant reconnects successfully afterward.
- [x] Microphone permission flow and recognizer Listening state reached. A silent
  acoustic attempt returned No speech recognized without sending a message.

## Owner acceptance still required

- [ ] Human English and Spanish dictation accuracy, stop/finalization, draft editing
  and explicit Send (installed model/support callback alone is not this proof).
- [ ] Perceived speaker volume/quality and Bluetooth/headset routing.
- [ ] Real incoming-call/audio-focus interruption and extended reconnect use.
- [ ] Accessibility, larger text and long conversation usability.

## Scope and privacy

Native Kotlin/Compose, existing direct gateway protocols, no vendor conversation
backend. Only the on-device Android recognizer is created; unavailable models
permit typing, never silent cloud transcription. Gateway speech remains opt-in.
Credentials are Keystore-encrypted and excluded from cloud backup/device transfer.
Setup links only stage configuration. No replay on reconnect/background return.

This preview has no camera QR scanner, owner guest-administration UI, hands-free
conversation, automatic owner-conversation naming, pagination or Android Auto.
Restricted history shows latest20 conversations; owner history latest50; message
view latest50. iOS CarPlay is unrelated to Android Auto eligibility.

Private raw device receipts remain ignored outside source. No credentials, host
addresses, device identifiers or private conversations are included in this report.
