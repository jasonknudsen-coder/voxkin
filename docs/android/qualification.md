# Android Pixel qualification — 2026-10-20

Status: native Android development preview installed for owner acceptance.
Not a Google Play release, full iOS parity, or universal Android support claim.

## Artifact and environment

- Version0.2.12 (14), package `studio.voxkin.android`, debug-signed.
- APK SHA256: `3818ece5be292b3d1d286842d3ae898aaa5468e66d36069a9182faa44c1cd6f1`.
- Physical Pixel8a, Android17/API37. Minimum API31, target/compile API36.
- Existing OpenClaw2026.9.7 gateway and plugin; neither changed for Android.
- Both English (en-US) and Spanish (es-ES) reported installed by the on-device
  recognition support API after explicit Spanish-model download.

## Automated evidence

- **50/50 JVM tests:** endpoint/setup validation, Ed25519 signature, final-content
  filtering, speech chunk bounds, real TLS mock owner handshake and restricted
  request/redirect/polling contracts, reply-language/length preferences and unwrapped commands.
- **75/75 physical-device instrumentation tests:** encrypted credentials; guest/owner
  Forget isolation; restricted history max20; delete-then-send owned replacement;
  history lock under Stop; delayed synthesis cancellation; capture-context guards;
  fresh/staged setup no automatic connection; foreground restoration without replay or recording; language support probe;
  clear-capture cancellation; preview consent; recording hides Send; explicit reviewed-draft
  send state and keyboard editing; over-limit draft preservation.
- Android lint: **0 errors,20 warnings,2 hints** (dependency currency, style/manifest and
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

This preview includes camera QR setup, owner guest-administration UI, hands-free
conversation and automatic owner-conversation naming; their real-use acceptance
limitations are recorded below. Android Auto remains outside scope.
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


## QR setup follow-up (0.2.7)

- Full suite27 JVM +53 physical Pixel tests passed (80 total), lint0errors/20warnings.
  QR tests use the actual bundled decoder on generated owner and restricted QR
  images; malformed codes, web links and insecure endpoints are rejected. Device
  tests cover review-versus-stage, explicit use, cancel/invalid/discard field
  preservation, background clearing, replacing review with a deep link, restored
  suppression of an existing approved connection, internal-only scanner contract,
  QR-only formats and disabled barcode-image saving.
- Synthetic review-dialog screenshot inspected: gateway/access explanation and
  distinct Discard/Use actions are legible; no credential is displayed. Use stages
  fields without connecting/enrolling. Raw synthetic test material stays local.
- Live Pixel: saved owner connection restored, explicitly disconnected for scan QA.
  Denied camera permission returned through the camera-denied dialog to setup.
  Retried with one-time permission: SetupScannerActivity resumed and camera-service
  connection was observed; Back returned to setup with Connect (not Disconnect).
  No invitation was redeemed and no real gateway message sent. Camera permission
  returned to ungranted after QA; test package removed.
- Independent read-only review found process recreation could reconnect saved
  access after a scan result. Added separate credential-free setupFlowActive
  restoration before foreground, plus regression test; follow-up found no blockers.
  Also accepted restored-Settings explanation polish. One defect/two accepted
  recommendations; lead owns all implementation/device work. Cockpit not allowlisted.
- Bundled QR component attribution and Apache-2.0 license under assets/licenses.
  Final rebuild added only these license assets after the full test suite; final
  debug APK installed; saved owner connection/conversation restored on Talk without
  replaying speech or sending. No iOS/gateway or production/store change.
- Physical optical QR decoding of a displayed/printed invitation remains owner
  acceptance. Generated-image decode and camera-opening tests do not claim that.


## Owner phone-management follow-up (0.2.8)

- 30 JVM +63 physical Pixel tests passed (93 total), lint0errors/20warnings/1hint.
  Optional admin handshake is exercised against a real TLS mock WebSocket: default
  asks only read/write; explicit opt-in adds admin; an approved read/write fallback
  still connects without enabling management. Dedicated-agent/label and invitation
  URL policy checks are covered with synthetic material.
- Model tests verify all permission gates, read-only refresh, confirmation before
  each mutation, no duplicate confirmation replay, exact target IDs, dismissal,
  failed-outcome refresh requirement, background/disconnect clearing, stale list
  rejection, share-secret expiry and endpoint-scoped enrolled-phone-ID Forget.
- Pixel UI tests inspect a synthetic named phone approval dialog and cancel it,
  render a synthetic invitation QR, verify FLAG_SECURE while shown, and remove
  that protection after Hide. Approval screenshot contains synthetic data only.
- Independent read-only access reviewer found no blockers. Prior reviewer restart
  failed service authorization, so one fresh reviewer was used; no false pass from
  the failed lane. Parent reviewed the final authenticated-hello phone-ID recovery.
- All management tests use synthetic transport data. No real invitation created,
  approval granted, device revoked or invite cancelled; no admin permission enabled
  on the live gateway. Real owner-admin acquisition and enrolled guest lifecycle
  still need acceptance. Existing read/write owner connection remains the handoff.
- Initial lint flagged casting LocalContext to Activity; fixed using LocalActivity,
  reran build/lint. Final APK includes the authenticated-hello ID recovery and the
  complete device suite was repeated after that change. Test package removed; saved
  owner conversation restored on Talk without Send/audio. No iOS/gateway/store change.


## Automatic history metadata follow-up (0.2.9)

- 39 JVM +65 Pixel tests passed (104 total), lint0errors/20warnings/1hint.
- New deterministic tests cover iPhone-style greeting/topic choice, wrapper cleaning,
  safe Unicode limits, manual/stable exclusions, wrong-key rejection, missing/failed
  preview and patch capabilities, exact expected session identity, two-user threshold,
  context cancellation, reasoning exclusion, bounded truncated-wrapper fallback,
  and the50-row/two-preview/four-fallback request budget.
- On-device model tests exercise list→preview→expected-session patch using synthetic
  data, verify resulting title/preview, and verify missing capability makes only the
  original list request with a nonblank fallback title. Existing UI/history/voice/
  access tests rerun unchanged; no live history list/patch performed for this batch.
- Independent source reviewer identified title fallback/Unicode and wrapper-counting
  issues; addressed and regression-tested by the lead. No code-writing helper or
  live gateway mutation. Cockpit remains not allowlisted.
- Real gateway title persistence remains acceptance work; installed handoff restores
  the existing conversation without sending or replaying audio. iOS/gateway unchanged.

## Response diagnostics follow-up (0.2.10)

Last response timing now reports first visible text, complete reply, gateway audio
preparation/transfer, local playback setup and Send-to-answer-audio. Monotonic
durations remain in memory and clear at disconnect/new Send. Provider attribution
changes only when that answer clip starts playback, not when a future chunk is
prefetched. Previews cannot fill or overwrite answer measurements.

42 JVM and67 Pixel instrumentation tests passed (109 total). New coverage verifies
first-only measurements, reset/out-of-order guards, wrong-run/private/empty event
exclusion, disconnect clearing and actual silent preview playback without changing
an earlier answer's timings/provider. Two old reflection-based tests initially
failed because the speech method gained an argument; updated call sites and the
complete67-test rerun passed. Independent read-only review found the preview and
empty-update cases; both fixed with regression coverage. No live chat/admin/history
mutation was used for this batch. Dictation-tail/receipt timings are not implemented.

## Granular history capability follow-up (0.2.11)

Authenticated owner methods separately gate list/read/rename/delete; delete needs
both archive-patch and delete support. Local owner New remains available. Restricted
hello history/historyActions flags gate model controls and transport requests before
HTTP. No read support starts a fresh usable owner conversation without overwriting
the saved restore pointer; restricted sending stays disabled with an update explanation.
Advertised read failures retain fail-closed restoration. Preview enrichment cannot
fall back to an unadvertised read. Mutation targets require scoped keys and valid
expected IDs. Pending dialogs are keyed to connection epoch and agent context.

46 JVM +72 Pixel tests passed (118 total), lint0errors20warnings2hints. Coverage
includes capability truth table, zero-HTTP restricted rejection, unsupported owner
restore, restricted no-send, invalid/stale targets, no-read preview fallback, disabled
UI and same-context reconnect dismissal. One old deletion fixture used a non-UUID
expected session ID; corrected to a valid synthetic UUID and full suite reran green.
Independent read-only review found no blocker; recommended epoch-keyed dialogs,
implemented and UI-tested. No live history/admin changes were made.

## Persisted commentary/subscription follow-up (0.2.12)

50 JVM +75 Pixel tests passed (125 total); lint:0errors20warnings2hints.
Strict persisted-commentary parser, bounded dedupe/accumulator, transient combined
display and optional nonblocking owner subscriptions are implemented. Device tests
exercise Send during a hanging subscription, A→B→A replacement, disconnect and
final/cancel late events. Static independent review identified the stale successful
subscription edge; fixed and regression-tested, with no remaining review blocker.
No live chat sends, history mutations or audio were used for qualification.
Real commentary wire delivery and audio acceptance are not claimed by these fixtures.
Receipt/spoken feedback and remaining presentation parity continue next.
