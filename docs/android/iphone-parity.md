# Android / iPhone presentation parity

The first Android preview proved direct gateway access and English/Spanish use,
but its stock controls were not a faithful presentation of the iPhone app.
The next preview uses `App/VoxkinApp.swift` and `App/VoxkinBrand.swift` as its
presentation reference, rather than introducing a second product design.

## Core phone flow

- Shared navy/surface/cyan/blue/purple/magenta palette and vector ribbon mark.
- Talk / History / Settings navigation, centered wordmark, connection indicator,
  introductory hero, and distinct user / agent message cards.
- Primary microphone control: start recording, stop to review, explicitly send.
  A ready indication is driven by the recognizer callback, not a pretend timer.
  Send is not offered while capture or final transcription is still running.
- Keyboard alternative, editable draft, clear/cancel, stop audio, and a separate
  cancel-request action. Stopping audio does not promise to cancel agent actions.
- Grouped gateway/voice/reply/privacy settings. Voice preview and Brief/Balanced/
  Full reply preferences match the iPhone options. Preferences affect this phone
  and its future turns, not other gateway users.
- Voice and speech-consent preferences are endpoint-bound. Saved consent restores
  only after authentication; unavailable voice IDs are not blindly sent.

## Deliberate or outstanding differences

This is **core phone presentation parity**, not a claim that every iOS feature
has shipped on Android. Android uses its own keyboard, permission dialogs,
accessibility services and on-device recognition implementation.

Still outside this preview: conversational receipt feedback and Android
Auto. The iOS CarPlay integration does not automatically confer Android Auto
support. Returning to the foreground reconnects previously approved saved access and
restores the scoped conversation without replaying speech or messages.

These differences should remain explicit in release notes. Do not render inert
controls or advertise these capabilities until implemented and qualified.

## Feature-by-feature audit (0.2.1, 2026-10-20)

This is a source audit plus bounded device tests, not a full parity declaration.

### Verified shared core

- Owner signed connection; restricted enrollment, approval requirement and agent scope.
- Staged setup links, encrypted device credentials, no credential forwarding redirects.
- Agent selection, English/Spanish on-device dictation, readiness, partial display,
  stop/review/explicit Send, typed alternative, clear/cancel and4,000-character limit.
- Per-turn reply-length preference; commands remain unwrapped.
- Explicit Gateway default / Local voices selection for supported owner connections;
  restricted connections remain local-only. No automatic source fallback.
- Voice preview/selection, endpoint consent, Stop audio separate from Cancel request.
- Latest user/assistant cards, history list/new/open/rename/delete, loaded transcript
  and Continue in Talk. Technical/tool messages are not displayed in the transcript.
- Matching final reply filtering and rejection of mismatched send acknowledgment IDs.
- No microphone, pending-message or audio replay on reconnect.

### Fixed during this audit

- Added the missing Speech source selector and explicit source-dependent routing.
- Moved Android-only model checks/downloads behind collapsed Troubleshooting.
- Made all loaded history messages readable instead of only the latest exchange.
- Reject an unexpected acknowledgment run ID without adopting it or retrying.
- Preserve a saved unavailable voice rather than silently substituting another voice.
- Strip reply-preference wrappers from derived history titles; hide truncated wrappers.

### Still different or missing (priority follow-up)

**Conversation continuity:** reconnect now restores the last authorized conversation
per endpoint/access mode/agent. Gateway-provided timestamps/previews, current-row
marker, conversation pagination and older-message loading are implemented.
Automatic stable owner titles and missing-preview enrichment are implemented (0.2.9). History capabilities are independently gated per method (0.2.11).
Owner archive filtering now explicitly excludes archived sessions, as on iOS.

**Response experience:** chat delta/status streaming is implemented; persisted
`session.message` commentary/interim updates and optional owner subscriptions are implemented (0.2.12). Optional spoken-turn receipt tone and one real progress update are implemented (0.2.13). Response timing/provider diagnostics are implemented (0.2.10), excluding dictation-tail and receipt-cue timings.
Reply display now supports iPhone-style inline Markdown (0.2.14), preserving whitespace; Talk user/progress cards remain plain and History formats both roles. Speech now applies inline Markdown and punctuation cleanup; the
short-first-chunk and one-next-prefetch pipeline is now implemented. Android currently
speaks bounded chunks for longer replies while iOS limits speech to4,000characters.

**Capture/lifecycle:** explicit hands-free opt-in is implemented (0.2.6); haptic cues remain missing. Android now retains the last nonempty
partial hypothesis for review when final recognition fails or times out; Cancel
still discards it. Foreground reconnect and saved conversation restoration are now implemented.
Android language is explicitly English/Spanish independent of speech source;
iOS gateway-default dictation follows device locale. Android allows text-only
Send without speech consent; iOS requires its speech-policy acceptance before Send.
These differences must not be hidden behind identical-looking controls.

**Setup/management:** camera QR setup is implemented (0.2.7); owner invite/approve/revoke/cancel UI and persistent restricted-phone ID are implemented (0.2.8).
Pairing-request detail UI still needs iPhone alignment.
Forget removes credentials but currently retains address/voice preferences, unlike
iOS; do not describe it as erasing every setting. Android Auto remains separate.

**Platform-specific by design:** Keystore vs Keychain, Android permission/keyboard
UI, Android model downloads and audio focus, temporary private playback files
(cleaned after playback) versus iOS memory playback. These need equivalent privacy
and usability guarantees, not identical system implementation.

## Recovery and speech follow-up (0.2.2, 2026-10-20)

- Empty partial callbacks preserve the last useful hypothesis. Error, empty final,
  and settle timeout recover it into the editable draft with an incomplete-words
  warning. Final text wins when available; neither path sends automatically.
- Cancel, context change and stale callbacks cannot restore discarded capture.
- Speech-only CommonMark parsing removes inline emphasis/code delimiters and link
  destinations while preserving their visible words. iPhone punctuation/range
  fixtures pass in English and Spanish. Original reply/history text is unchanged.
- Owner history explicitly excludes archived sessions.
- Independent read-only review: no blocking findings. Accepted recommendation:
  exercise the real settle timeout plus cancellation before timeout on-device.
- Delegation checkpoint: one read-only reviewer; lead retained all file edits and
  device ownership. Cockpit orchestration/lane registration was unavailable because
  Voxkin is not allowlisted; no allowlist or control-plane policy was changed.

These close specific audit gaps, not conversation restoration, streaming,
hands-free, QR, administration, or full feature parity. Synthetic fault-path tests
are not a claim of real microphone-failure or accessibility acceptance.

## Conversation continuity follow-up (0.2.3, 2026-10-20)

- Persist the selected conversation and agent in encrypted storage, separated by
  normalized endpoint and owner/restricted access. Restricted restoration reads
  the existing server-owned conversation instead of creating a new one each time.
- Read history only after authenticated connection. Failed, malformed or denied
  restoration blocks Send and points to History/New conversation. No silent fallback
  from a failed saved history read to another conversation.
- Foreground/cold launch reconnect requires a prior successful connection marker,
  saved credentials, and no staged credential. Fresh setup and enrollment remain
  explicit. One attempt per foreground entry; no background reconnect loop.
- Explicit Disconnect suppresses foreground reconnect for the current app process.
  A fresh process can reconnect saved approved access. Forget removes the reconnect
  marker and all per-agent continuity for the selected endpoint/access mode.
- Backgrounding discards active capture/audio/request tracking. Returning can read
  a completed response from history but never resends the turn, restarts capture,
  adopts its old run, or plays restored speech.
- Unsent drafts survive same-conversation backgrounding in memory only; they are
  cleared on explicit disconnect or context change. They are not persisted to disk.
- Independent read-only review found draft/context crossover; scope binding fixed
  it and the follow-up review found no remaining blockers. One reviewer, all edits
  and device operations owned by the lead. Cockpit remains unavailable because the
  project is not allowlisted; no control-plane configuration was changed.

Remaining work includes persisted commentary/progress feedback, richer
history/pagination, hands-free, QR setup, and owner phone administration.

## Streaming and speech pipeline (0.2.4, 2026-10-20)

- Matching active-run/session chat deltas appear under Reply in progress. Snapshot,
  append and replace forms are supported; text is bounded to16,000UTF-16 units.
  Final replaces this transient view and is added to history once. Error, cancel,
  timeout and disconnect clear it; stale/cross-context events are ignored.
- Assistant text snapshots reject tool/thinking content and explicit non-visible
  channel/phase values. Raw deltaText is treated as the gateway's display-text
  contract, with the same context and channel/phase checks. No partial speech.
- Gateway status phases become waiting/thinking/getting-ready labels. Restricted
  transports remain final-only where their polling protocol does not provide deltas.
- Speech starts with a sentence-sized clip (~180 code points, hard280), then larger
  clips (~600, hard700). UTF-16 surrogate pairs are preserved. No measured latency
  improvement is claimed; actual provider latency/network/audio routing still vary.
- At most one next clip is synthesized while the current clip plays. Stop,
  background, context change and failure cancel outstanding prefetch; clips remain
  ordered. Existing provider/voice/language/MIME/size checks apply to each clip.
- Parent source review and automated/device checks completed. Independent reviewer
  could not run (tool authorization failure, including one retry); this batch has
  no independent-review pass. Cockpit remains unavailable/not allowlisted.
- This is not persisted commentary streaming, spoken progress feedback, Markdown
  display rendering, or full iPhone parity. Android's longer-reply speech policy
  still differs from the iPhone4,000-character cap.

## History details and pagination (0.2.5, 2026-10-20)

- Conversation rows display gateway-provided previews and locally formatted updated
  timestamps when present. Current conversation is marked explicitly. Restricted
  customTitle is preferred; previews hide incomplete reply-preference wrappers.
- Load more conversations uses owner offsets or restricted opaque before cursors.
  Refresh starts at the newest page. Duplicate conversation IDs are not appended.
- Load older messages prepends chronological pages while retaining the current
  latest reply and any unsent draft. Stable nonempty IDs deduplicate; equal text
  with distinct IDs is not collapsed. Current messages win over overlapping pages.
- Refresh conversation returns to the latest page without playing speech or
  discarding a same-context draft. Sending invalidates older-message paging until
  refreshed, because owner offsets can shift when a new turn is added.
- Views are bounded to500 conversations/500 messages. At the message cap, the most
  recent messages are retained. Limits are visible in the UI; this is not unlimited
  transcript browsing or a claim of large-text accessibility qualification.
- Deleting resets list pagination and asks for Refresh History, avoiding invalid
  restricted cursors and shifted owner offsets. A repeated nonadvancing cursor
  stops further page requests. Cancellation/context guards apply before commits.
- Independent source review found the deletion-cursor issue; fixed and re-reviewed
  with no remaining blockers. One read-only reviewer; lead owns edits and device QA.
  Cockpit remains unavailable because this project is not allowlisted.

Remaining: automatic stable owner naming and preview generation, granular history
capabilities, persisted commentary/progress feedback, hands-free, QR, owner access
management and the other explicit platform/experience differences above.


## Hands-free conversation (0.2.6, 2026-10-20)

- Explicit per-session opt-in explains automatic Send on pauses and microphone
  restart after speech. Requires ready authenticated conversation, empty draft,
  speech consent and available voice/source. No persistent automatic opt-in.
- A two-second unchanged nonempty hypothesis requests finalization; Android may
  deliver its own final earlier. Only a successful nonempty final can auto-send.
  Errors, empty final and settle timeout recover partial words for manual review
  and end hands-free. Over-limit drafts remain unsent. Normal capture never auto-sends.
- Successful speech schedules listening after350ms and rechecks readiness.
  Tap the mic during speech to interrupt and listen. Stop, leaving Talk, background,
  context changes, consent loss and failures end the loop. Pending timers and late
  recognizer/permission callbacks cannot restart the ended session.
- Stop preserves partial words for review and does not abort an already-sent turn;
  Cancel request remains separate. Foreground reconnect never resumes hands-free.
- Delegation checkpoint: independent read-only reviewer was requested but could not
  run due to service authorization failure. Parent review and tests only; no
  independent-review pass for this batch. Cockpit not allowlisted, unchanged.
- Actual acoustic EN/ES conversation loops, Bluetooth/headset routing, interruption
  timing and accessibility still require owner acceptance. Not full iPhone parity.


## QR setup (0.2.7, 2026-10-20)

- Settings offers a local QR scanner while disconnected, plus existing paste/deep-link
  setup. Camera hardware is optional and camera permission is requested only for scanning.
  QR-only decoder, no beep/image saving, non-exported scanner with secure window.
- Valid owner/restricted QR values open gateway/access review; credential stays hidden.
  Use scanned setup only stages fields. Connect and Enroll are separate explicit actions.
  Cancel, invalid scan and Discard preserve prior fields; arbitrary URLs are not opened.
- Setup suppresses automatic saved-gateway reconnect. A credential-free saved-state
  flag restores that suppression across Activity/process recreation; decoded review
  credentials are not saved by app code. Background clears the review. Restored setup
  returns to Settings with an explicit paused explanation. New deep links invalidate
  a pending scan result.
- One independent read-only reviewer found suppression was initially lost after
  scan-result process recreation. Fixed, regression-tested and re-reviewed with no
  remaining blockers; also accepted restored-Settings explanation polish. Lead owns
  all edits/build/device work. Cockpit remains unavailable/not allowlisted.
- Uses [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded)
  4.3.0 and its bundled ZXing core decoder; no hosted scanning service. The upstream
  [integration guide](https://github.com/journeyapps/zxing-android-embedded#usage-with-scancontract)
  documents the explicit ScanContract and disabled image-saving options.
- Real optical scanning of a displayed/printed invitation still needs owner acceptance;
  generated-image decoder tests and a camera permission/cancel check are distinct evidence.
  No real enrollment, device approval or gateway configuration change is part of this batch.


## Owner phone access (0.2.8, 2026-10-20)

- Owner-only explicit admin request, off on fresh app process. Signed handshake
  scopes add operator.admin only on opt-in; missing optional admin still permits
  an approved read/write connection. Controls require opt-in, granted admin scope,
  and all five advertised access methods. Restricted phones cannot use the controls.
- Refresh lists gateway-controlled allowed agents, phone labels/status/ID suffixes
  and outstanding invitations. Create/approve/revoke/cancel each has a target-naming
  confirmation; consuming it once prevents duplicate taps from replaying mutations.
- Connection generation, list revision, expiry/status checks and operation locking
  reject stale confirmation/response state. Unknown outcomes require a fresh list
  before another mutation; never automatically retry a security-affecting request.
- Invitation QR/private sharesheet uses only the validated returned secret and
  original gateway. No automatic send/clipboard. Secret stays in memory and clears
  on expiry/background/disconnect; QR window is protected against screen capture.
- Restricted phone ID persists encrypted by endpoint after enrollment, or from a
  valid authenticated hello ID for existing phones. Last eight characters match
  owner display; Forget removes only the selected endpoint's local ID.
- Read-only review: first prior lane could not restart due service authorization;
  one fresh reviewer completed successfully with no blockers. Parent owns all edits
  and device operations. No live invitations/approvals/revocations were performed.
  Cockpit remains not allowlisted; no control-plane changes.
- Real admin grant acquisition and owner/guest access lifecycle acceptance remain
  separate from synthetic protocol/model/UI coverage. Full iPhone parity unfinished.


## Automatic history metadata (0.2.9, 2026-10-20)

- Local title rules match the iPhone reference: clean exact reply-preference wrappers,
  collapse whitespace, consider the first three user messages, skip greetings/tests
  when a real topic exists, bound generated titles to80 Unicode code points.
- Manual label/customTitle and existing nonempty autoLabel are never fetched for
  renaming or overwritten. Only owned listed session keys enter preview requests.
  Latest visible text can fill a missing preview; tool/reasoning content is excluded.
- Android's50-row owner pages use at most two preview calls (32-key schema limit),
  eight items/800characters each. At most four32KB/eight-message fallback history
  reads handle truncated preference wrappers. Unavailable/failed enrichment retains
  the original safe list metadata; incomplete wrappers never become titles.
- Persist autoLabel only after two cleaned nonblank visible user messages, a
  nontrivial topic, advertised sessions.patch, and a valid expectedSessionId. Every
  request/result checks cancellation and current connection/agent; never auto-retry
  a failed patch. A request already sent can still finish server-side after cancel.
- Read-only review identified empty-title/Unicode clipping and incomplete-wrapper
  counting edges; fixed with regression tests. Parent verified final changes.
  No live history renaming was performed during QA; transport fixtures are synthetic.
- Restricted server-generated history metadata remains unchanged. Granular gating
  for other history operations, commentary/progress/timing, Markdown display and
  haptics remain parity work; full iPhone parity is not claimed.

## Response timing/provider follow-up (0.2.10)

In-memory monotonic first-update/final/audio timings and actual-playing provider
attribution now appear in Settings. Empty/private/wrong-run events do not count;
preview speech and later chunks do not overwrite first-answer measurements.
42 JVM +67 Pixel tests passed. Read-only review found two edge cases, both fixed.
This closes answer diagnostics, not receipt feedback, persisted commentary,
Markdown rendering, haptics or the remaining granular capability gates.

## History capability follow-up (0.2.11)

Owner list/read/rename/delete and restricted history/action support are now gated
from authenticated capabilities. Unsupported controls are disabled, with clear
explanations; owner Talk remains usable without history reading. Mutations reject
stale cross-agent/malformed targets, and reconnect dismisses pending dialogs.
46 JVM +72 Pixel tests passed. Remaining response work: persisted commentary,
receipt/spoken progress, Markdown rendering and haptics; pairing detail UI also remains.

## Persisted progress follow-up (0.2.12)

Explicit public commentary is shown only during the matching active run/session,
separate from transcript history and final speech. Strict phase/signature/mirror/
segment validation rejects private, terminal, error, malformed and mixed content.
Message/segment dedupe and 100-ID, 4k-notice, 12k-notices, 16k-combined bounds apply.
Final, error, cancellation and disconnect clear transient notices.

Optional owner subscriptions run independently from Send and busy state, with
connection/transport/session/generation guards. Switching A→B→A invalidates the
old successful subscription before replacing it. Unsupported/restricted gateways
retain delta/final behavior. Failures retry only on later context changes or Send.

Synthetic parser and Pixel lifecycle tests cover these contracts; real gateway
commentary and acoustic acceptance remain separate. Receipt/spoken progress,
Markdown display, haptics and pairing detail UI remain unfinished.

## Conversational feedback follow-up (0.2.13)

An enabled-by-default local preference adds a subtle 120ms accepted-request tone
and at most one short spoken persisted-commentary update. Both require speech
consent and an exact finalized, unedited dictated draft. Typed prefixes, recovered
partials, edits (including edit→restore), and missing/mismatched acceptance IDs
stay silent. Restricted turns get receipt/final only. Commands remain unwrapped.

Feedback has independent job/player/file/focus ownership and never fills answer
speech timings/provider or resumes the microphone. Final replies synchronously
preempt feedback. Stop, background/disconnect, consent withdrawal, option-off and
focus loss revoke the turn; focus loss also ends hands-free. Voice/language/source
are captured at Send. One eligible notice waits for acceptance and receipt, then
800ms before synthesis, with a12-second playback cap. Optional failures are silent.

Pure state/tone tests and synthetic Pixel lifecycle tests cover ordering, captured
settings and cancellation. Actual Pixel local-tone playback and receipt→final
focus handoff also passed. This is not all-route/acoustic stress qualification;
real long-running commentary and owner acceptance remain separate.

## Inline Markdown follow-up (0.2.14)

Talk final assistant replies and both roles in History now use selectable inline
formatting: emphasis/strong, code, single/double-tilde strikethrough and explicit
HTTP(S) links. Talk user/progress text remains plain. Stored text is never rewritten.
Image alt text is flattened without descendant annotations or image fetching;
HTML remains literal. Unsafe links remain readable, without actions.

The isolated CommonMark0.30.1 inline adapter preserves tabs/spaces, blank lines,
trailing newlines, literal reference definitions and cross-blank-line emphasis.
It normalizes CRLF to LF. Triple-backtick sequences follow inline-code semantics,
not fenced-block layout, matching Swift Foundation probes. Source/output offsets
are UTF-16, including emoji. This deliberately uses a pinned internal parser API;
keep the Swift-derived whitespace/annotation fixtures on dependency upgrades.

Read-only review caught nested image-alt links/styles; fixed with regression
coverage. This closes inline display, not full Markdown block/table rendering.
Pairing approval details, haptics and real human acceptance remain separate work.
