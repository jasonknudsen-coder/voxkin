# Android Pixel qualification — 2026-10-20

Status: native Android development preview installed for owner acceptance.
Not a Google Play release, full iOS parity, or universal Android support claim.

## Artifact and environment

- Version0.2.3 (5), package `studio.voxkin.android`, debug-signed.
- APK SHA256: `248b528344742b09d21afbe0d9f32a0d9a442a3be9b4b6fc2a2e35c30a8d493a`.
- Physical Pixel8a, Android17/API37. Minimum API31, target/compile API36.
- Existing OpenClaw2026.9.7 gateway and plugin; neither changed for Android.
- Both English (en-US) and Spanish (es-ES) reported installed by the on-device
  recognition support API after explicit Spanish-model download.

## Automated evidence

- **16/16 JVM tests:** endpoint/setup validation, Ed25519 signature, final-content
  filtering, speech chunk bounds, real TLS mock owner handshake and restricted
  request/redirect/polling contracts, reply-language/length preferences and unwrapped commands.
- **28/28 physical-device instrumentation tests:** encrypted credentials; guest/owner
  Forget isolation; restricted history max20; delete-then-send owned replacement;
  history lock under Stop; delayed synthesis cancellation; capture-context guards;
  fresh/staged setup no automatic connection; foreground restoration without replay or recording; language support probe;
  clear-capture cancellation; preview consent; recording hides Send; explicit reviewed-draft
  send state and keyboard editing; over-limit draft preservation.
- Android lint: **0 errors,19 warnings** (dependency currency, style/manifest and
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

## Owner acceptance

On 2026-10-20 the owner confirmed the physical Pixel flow worked in English and
Spanish. The requested follow-up is iPhone-style presentation and interaction
parity; that acceptance does not qualify every audio route or accessibility case.

## Remaining acceptance

- [x] Owner-reported English/Spanish end-to-end use.
- [ ] Retest recording/review/send after the presentation update.
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

## Presentation follow-up

The core phone screens now use the iPhone brand palette, vector mark, message cards,
floating navigation and recording/review controls. Physical Pixel screenshots of
recording, review and keyboard-open layouts were compared with the iPhone build14 reference. A fresh owner
connection succeeded with the existing saved grant after the update.

The UI tests initially failed before executing because the transitive Espresso3.5
input injector was incompatible with Android17. Pinning Espresso3.7 resolved it;
the final full14-test device run passed. These are not hidden application failures.
See [parity scope](iphone-parity.md) for features still outside this preview.

## Speech-source and parity audit follow-up

- Explicit owner Gateway default / Local voices routing tested with fake transport;
  restricted devices cannot select Gateway default.
- Unexpected send acknowledgment run ID is rejected without replay/adoption.
- Loaded earlier history messages and hidden technical roles tested in Compose.
- Dictation diagnostics remain collapsed until deliberately expanded.
- Physical Pixel owner Gateway default preview completed playback with Reply ready.
- Source audit records remaining iPhone differences in iphone-parity.md; this is
  not full-feature parity.

## Recovery and speech follow-up (0.2.2)

- JVM speech fixtures cover nested/reference links, formatting, English/Spanish
  punctuation, ranges, arithmetic, Unicode, and paragraph boundaries.
- Physical instrumentation verifies failed-final recovery into an existing draft,
  final-over-partial preference, stale callbacks after Cancel, and the real
  five-second settle timeout with a simultaneously cancelled capture.
- All37 automated tests pass; Android lint remains0errors/19warnings.
- Independent source review found no blocking defects. Test runner removed after
  qualification; application data/pairing retained during the APK upgrade.
- Saved owner pairing reconnected successfully after the upgrade; returned to Talk.
- Recovery fault paths are synthetic on-device tests, not a real microphone-error
  acceptance test. Human audio quality/route acceptance remains outstanding.

## Conversation continuity follow-up (0.2.3)

- 16 JVM +28 physical Pixel instrumentation tests pass (44 total); lint0errors,
  19warnings. The UI test runner explicitly disables automatic connection so tests
  never reconnect the phone owner's real gateway or overwrite live history.
- New regressions cover owner/restricted/endpoint/agent isolation, successful
  history-only restoration, denied/malformed history, explicit New recovery,
  restricted restoration without extra create, reconnect eligibility, staged setup,
  explicit Disconnect, draft-scope clearing, and background during a pending turn
  with a late final event. Restored replies do not synthesize audio.
- Independent reviewer identified draft/context crossover; fixed with bound draft
  context and verified by follow-up review. One finding, one accepted fix.
- Restricted reconnect is covered with synthetic on-device transports; not a new
  live restricted enrollment/approval acceptance claim.

- Live owner Pixel check: selected an existing conversation from History, verified
  its visible conversation text was unchanged after Home/foreground and after a
  force-stop/cold launch; both displayed the restored-conversation status with no
  manual Connect. No new chat message was sent for this check.
- Explicit Disconnect followed by Home/foreground remained disconnected. The final
  installed app retains the existing saved pairing. Raw UI receipts remain local
  and are not included in source or shared screenshots.
