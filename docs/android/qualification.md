# Android Pixel qualification — 2026-10-20

Status: native Android development preview installed for owner acceptance.
Not a Google Play release, full iOS parity, or universal Android support claim.

## Artifact and environment

- Version0.2.6 (8), package `studio.voxkin.android`, debug-signed.
- APK SHA256: `47c439339c6a8000dcf9e0ae35d32f49019e7ba2a25cebe68c448b61b5728a2a`.
- Physical Pixel8a, Android17/API37. Minimum API31, target/compile API36.
- Existing OpenClaw2026.9.7 gateway and plugin; neither changed for Android.
- Both English (en-US) and Spanish (es-ES) reported installed by the on-device
  recognition support API after explicit Spanish-model download.

## Automated evidence

- **25/25 JVM tests:** endpoint/setup validation, Ed25519 signature, final-content
  filtering, speech chunk bounds, real TLS mock owner handshake and restricted
  request/redirect/polling contracts, reply-language/length preferences and unwrapped commands.
- **47/47 distinct physical-device instrumentation tests:** encrypted credentials; guest/owner
  Forget isolation; restricted history max20; delete-then-send owned replacement;
  history lock under Stop; delayed synthesis cancellation; capture-context guards;
  fresh/staged setup no automatic connection; foreground restoration without replay or recording; language support probe;
  clear-capture cancellation; preview consent; recording hides Send; explicit reviewed-draft
  send state and keyboard editing; over-limit draft preservation.
- Android lint: **0 errors,19 warnings** (dependency currency, style/manifest and
  launcher-icon shape). No clean-lint claim.
- Independent read-only review and follow-up confirmed fixes for the identified
  protocol, cancellation and context-switching bugs in earlier batches. The0.2.6
  reviewer could not run; no independent-review pass for hands-free. See [review](review.md).

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
conversation, automatic owner-conversation naming or Android Auto.
Restricted conversation pages contain up to20, owner pages50 and message pages50;
explicit pagination can load up to500 conversations/messages per view. iOS CarPlay is unrelated to Android Auto eligibility.

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

## Streaming and short-first speech follow-up (0.2.4)

- 20 JVM +31 physical Pixel tests pass (51 total), lint0errors/19warnings.
- Pipeline tests verify short-first chunk bounds/Unicode preservation, ordered
  playback, at most one prefetched clip, cancellation of playing/prefetched work,
  and failure without skipping to later clips. They use synthetic playback, not
  a stopwatch measurement of live gateway synthesis or subjective audio quality.
- On-device event tests cover append/replace/snapshot deltas,16K display bounds,
  gateway status labels, final replacement/deduplication, stale/cross-context
  events, explicit reasoning/tool filtering, and cancel clearing transient text.
- Synthetic Pixel UI screenshot inspected: user card, labelled Reply in progress,
  assistant text, status and Cancel request remain visible; old intro hero is
  hidden during an active run. No private conversation appears in this screenshot.
- Final APK installed; saved owner connection and conversation restored on launch.
  Test package removed. No new live chat request was submitted for this batch;
  streaming verification used synthetic gateway events on the physical phone.
- Parent review completed. Independent reviewer failed to start due tool
  authorization errors (one retry); no independent pass is claimed for this batch.

## History metadata and pagination follow-up (0.2.5)

- 20 JVM +38 physical Pixel tests pass (58 total); lint0errors/19warnings.
- New tests cover restricted cursors, owner raw-row offsets, metadata, deduplication,
  chronological prepend, preservation of current replies/drafts, refresh from the
  latest page, nonadvancing cursors, background cancellation, and pagination reset
  after deletion in both access modes.
- Synthetic Pixel screenshot inspected: current marker, title, preview, updated
  time and Load more/Refresh/Load older controls are legible. Test package removed.
- Independent source review found deletion invalidates pagination; fixed and
  re-reviewed with no remaining blockers. One finding/accepted recommendation.
- Full suite passed before a final count-label wording cleanup; final APK assembled
  and installed afterward. No new live conversations were created or deleted.
- Full multi-page protocol qualification uses synthetic transports on-device,
  not newly populated real owner/guest histories. Accessibility/large-text and
  very large transcript performance remain human acceptance work.
- Live owner saved pairing/conversation restored; History loaded successfully with
  updated timestamps visible. Returned to Talk for handoff. Raw device data stays local.


## Hands-free follow-up (0.2.6)

- 25 JVM +47 distinct physical Pixel tests passed (72 total). Initial full device
  suite:43/44 passed; only failure was the screenshot helper selecting two roots
  when an AlertDialog was open. Corrected the helper, added two safety cases, and
  reran HandsFreeTest + PresentationTest:15/15 passed. Final additional real audio
  handoff test:1/1 passed. Production APK unchanged throughout these test-only fixes.
- Scheduler tests cover explicit readiness, debounce, finish cancellation, stop/
  restart invalidation and readiness recheck before resume. Model tests cover
  successful-final-only automatic Send, late callback rejection, error recovery,
  oversized drafts, background/stale permission, navigation, consent loss, request
  failure and Stop versus explicit request cancellation. Synthetic gateways only.
- Physical MediaPlayer played a short silent WAV, completed/released playback,
  reopened the actual on-device recognizer, then explicit Stop ended the mode.
  No real gateway request was sent. This is pipeline/lifecycle evidence, not
  acoustic English/Spanish conversation, voice quality or Bluetooth qualification.
- Opt-in dialog screenshot inspected: automatic sending/listening and stopping
  behavior are explicit and legible. Normal recording/review/Send UI tests pass.
- Lint remains0errors/19warnings. Parent source review completed; independent review
  failed to start because of service authorization. No independent pass claimed.
- Test package removed; debug APK remains installed for owner acceptance. Saved
  owner connection/conversation restored without speech or Send; hands-free entry
  is enabled on Talk and the mode remains off until explicit opt-in.
