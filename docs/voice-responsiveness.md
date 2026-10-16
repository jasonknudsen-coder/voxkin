# Voice responsiveness candidate (October 14)

## Current commentary-contract fix — October 14, 2026

**0.3.0 (9) is available in internal TestFlight**, verified VALID and
IN_BETA_TESTING with existing internal-group membership. Supersedes build 8.
Fixes real commentary being ignored: supports explicit segment markers and nested
message run IDs while rejecting conflicting/terminal/reasoning/tool content.
Synthetic live gateway event capture reproduced the defect; 92 app/native and
40 core tests pass, with independent re-review complete. Signed archive/export
checks passed. No gateway/plugin changes. Physical audio confirmation remains.
Evidence: `.tmp/commentary-release-9/delivery-checkpoint.json`.
Earlier build sections below are historical evidence.


## Current conversational feedback build — October 14, 2026

**0.3.0 (8) is available in internal TestFlight**, verified VALID and
IN_BETA_TESTING with existing internal-group membership. Supersedes build 7.
Replaces canned spoken acknowledgment with a local receipt cue; owner-mode real
agent updates have an 800ms grace window to skip quick-answer preambles. Restricted
connections remain cue + final only. 89 app/native tests and one Settings persistence
UI test passed; independent review found no blockers. Archive/export signatures,
provisioning and metadata verified. No gateway/plugin changes. Physical cue volume,
AirPods routing and contextual opening timing remain to test.
Evidence: `.tmp/conversational-release-8/delivery-checkpoint.json`.
The build-7 and earlier sections below are historical evidence.


## Current internal TestFlight availability — October 14, 2026

Voxkin **0.3.0 (7)** is available to the existing internal tester group. Apple
processing is VALID; READY_FOR_BETA_TESTING advanced to IN_BETA_TESTING after
group assignment, and membership was verified. Build 7 supersedes build 6.
Includes separate admin-permission and management-API status, pairing approval/retry,
tap-to-talk acknowledgment, optional truthful agent progress speech, and a 60-second
idle window separate from the 30-second spoken capture limit. No gateway/plugin
production deployment occurred. Build 6 had 88 passing tests; build 7's small
Settings change passed simulator build, archive, signature, provisioning and CarPlay
verification. Maintainer reports AirPods working; this is not universal Bluetooth qualification.


Baseline responsiveness shipped in build 4 and is included in current build 7.
The follow-up tap-to-talk/contextual-update and idle-window changes below are now
available in build 7. Capture crash hardening is documented separately.

## Perceived responsiveness

Conversation mode optionally prepares one generic acknowledgment (English “Got
it.” or Spanish “Recibido.”) using the selected, consented gateway speech route.
The clip stays in memory for that conversation, never on disk. Preparation runs
while listening and does not delay microphone startup. No new provider fallback
is introduced. Selected local voices retain exact voice/language verification.

Playback happens only when `chat.send` acknowledges the matching run, while that
run is still waiting. If the cache is not ready, skip acknowledgment rather than
wait. The phrase confirms receipt, not a tool action, successful outcome, or
completion. It never guesses that weather/search work has started.

Acknowledgment playback has separate ownership: no transition into reply-speaking,
no deadline cancellation, no microphone rearm, no answer timing mutation. A final
reply stops it immediately. Stop, Cancel, disconnect, voice change and consent
withdrawal clear it. Late synthesis or acceptance callbacks cannot resurrect it.
Preparation cancellation does not guarantee a remote provider stops work already
accepted; do not claim server-side priority. Optional synthesis/playback failure
must not fail the actual turn. A Settings switch disables the feature.

## Actual latency opportunities

- Completed replies above 180 characters use a smaller opening speech chunk
  (first sentence after at least 40 characters, or bounded whitespace/token split)
  instead of synthesizing ordinary replies up to 500 characters all at once.
  Later chunks retain bounded one-ahead prefetch. All text is preserved; no partial
  model deltas or intermediate reasoning are spoken. More requests may add gaps
  or overhead: physical timing determines whether this is a net improvement.
- Identical repeated dictation updates no longer restart the conversation pause
  timer. New words still restart it; the two-second pause itself is unchanged to
  avoid clipping slower speakers.

## Measurement

In-memory Settings timings distinguish last recognized-word update to Send,
finishing dictation, Send-to-acknowledgment, first text update, complete answer,
gateway speech request including transfer, playback setup, and Send-to-answer
audio. Last-word-update is not a measured acoustic/VAD endpoint. No transcript,
audio recording, user identifier or telemetry is collected for timing.

Compare the same short and medium question over several trials on phone/CarPlay,
in English and Spanish, with acknowledgment on/off. Record warm versus cold
synthesis and network conditions. Do not describe character-count reduction as a
measured latency speedup, or attribute model/network delay to local speech hardware.

## Local verification

- 36 VoiceCore tests passed (including ordinary-reply first-chunk coverage).
- 67 signed-simulator app/model tests passed: accepted-only acknowledgment,
  final-before-acceptance, late warmup after Stop, optional failure, active Stop,
  consent withdrawal, selected Spanish local voice, disabled setting, separate
  timing, duplicate recognition, plus existing lifecycle/security coverage.
- Independent read-only review found no blocking issue. Three recommendations
  accepted: cancel pending warmup on final, remove duplicate audio deactivation,
  and add explicit Spanish/selected-voice coverage. Re-review passed.
- Review overlapped tests (duration unmeasured); one bounded helper, lead-only
  implementation. Pilot CLI is unavailable for this non-allowlisted project,
  as established in the earlier CarPlay pass; no registry bypass.

Logs: `.tmp/voice-responsive-core.log`, `.tmp/voice-responsive-model.log`.
No live latency benchmark or physical audio acceptance yet; no speedup percentage
claimed. No gateway deployment, new TestFlight upload, or hardware purchase.

Targeted UI checks passed: conversation Start/Stop, plus acknowledgment setting
toggle and persistence after relaunch. Initial whole-row toggle tap did not
change state; retest targeted the actual switch and awaited its value, then
verified persistence. No app change was needed for that retest. Evidence:
`.tmp/voice-responsive-ui.log` and `.tmp/voice-responsive-ui-retest.log`.
Total distinct covered tests: 36 core +67 model +2 UI, all passing on final runs.

## Follow-up polish: tap-to-talk and real progress updates

- Acknowledgment warms during either kind of recording. Accepted spoken single-turn
  requests now qualify, alongside conversation mode; purely typed/edited turns do
  not acquire a receipt cue just because a cached clip exists.
- At most one short (180-character maximum) explicit **user-visible agent update**
  may be spoken after matching request acceptance. It must come from the existing
  subscribed `session.message` path, matching active session/run and carrying the
  assistant-role, non-final source-reply marker. Message IDs are deduplicated.
- No partial model deltas, reasoning, tool arguments/results, or guessed weather
  intent are spoken. Long/code/URL updates remain visual-only. Markdown is prepared
  for speech. The selected voice and consent apply; audio must be at most12 seconds.
- A per-turn hint asks for one brief truthful progress update only when the spoken
  owner connection supports message subscriptions. Actual delivery is optional,
  not guaranteed. Restricted backend currently emits final replies only, so keeps
  the generic receipt cue and receives no progress hint.
- Progress waits for the receipt cue and never changes answer timings, rearms the
  microphone or extends the response deadline. Final/Stop/voice change/consent
  withdrawal/disconnect cancel pending playback, including late synthesis results.
  Remote synthesis already underway may still finish; no server priority claim.

## Listening window follow-up

Idle listening now pauses after60 seconds without recognized words, with a clear
no-speech message. The30-second spoken-turn limit begins with the first nonempty
recognized transcript, not when the microphone opens. Repeated transcripts do not
extend it. Finish retains its separate2-second finalization deadline; late words
after microphone closure cannot replace it. No automatic send or indefinite
microphone restart occurs on a limit. Both changes require the next app build.

The explicit idle message is shown on the phone; CarPlay returns to Ready to talk.
iOS speech recognition can end independently before the app deadline.

Validation:88 signed simulator app/native tests passed, including tap-versus-typed
acknowledgment, scoped/deduplicated progress, late progress after Stop/final, and
idle/speech policy. Existing acceptance/consent/lifecycle regressions pass.
Read-only review completed; on-device timing/audio and idle behavior remain to test.

## Conversational feedback candidate — October 14, after build 7

Replaces the fixed “Got it” / “Recibido” speech with a quiet 120ms local receipt
sound. The sound requires no speech-provider request and is played only after
matching request acceptance. Existing opt-out is preserved under the renamed
**Conversational feedback** setting; no gateway configuration changes.

Owner spoken turns ask the agent to answer quick questions directly, ask needed
clarification as its answer, and optionally send one brief contextual source-reply
opening only for longer work. A progress event gets an 800ms grace window before
synthesis; final/Stop/consent withdrawal cancels pending work. Existing exact
session/run, explicit non-final source-reply, length, deduplication and voice checks
remain. Partial model tokens, reasoning and tool payloads are never spoken.

Restricted connections still deliver only final replies: they get the local cue,
not contextual openings. Agent updates are optional and depend on real delivery;
this is not token-streaming TTS or a guaranteed conversational preamble. Guidance
asks the agent not to repeat an opening in its final; the client does not strip
final text, which could lose information if interim audio was interrupted.

One bounded helper owned only model-test updates, then reviewed implementation
read-only. Review found no blocking defects. Console pilot start rejected this
project as not allowlisted; no policy bypass or pilot completion claimed.
Physical cue volume, AirPods routing, and natural opening/final timing need testing
in the next build. Build 8 upload succeeded; availability is recorded separately after Apple processing.

Candidate verification: 89 signed-simulator app/native tests passed, including
local-cue/no-provider checks, fast-final suppression, scoped update deduplication,
and late synthesis cancellation. The initial command named the wrong test target;
the corrected `VoxkinTests` run passed. Build 8 archive/export signature, matching
provisioning, expiry, capability and metadata checks passed. No new gateway/plugin
change is included. Physical listening remains unverified.

Settings UI toggle/relaunch persistence check passed with the new label. Evidence:
`.tmp/conversational-feedback-tests.log`, `.tmp/conversational-feedback-ui.log`,
`.tmp/conversational-release-8-verification.log`. 89 app/native + 1 UI tests passed.

Build 8 upload succeeded (delivery `13e4f766-3860-44b2-9839-291a76c61c1e`).
Apple processing/group availability is not implied by upload success.

## Build 8 field defect and commentary-contract correction

Maintainer reported hearing only the receipt beep during real questions. Sanitized
phone-session history showed a genuine commentary update, but the app recognized
only `openclawDeliveryMirror.kind=message-tool-source-reply` with `final=false`.
A fresh synthetic live WebSocket probe confirmed two mismatches:

- Ordinary visible commentary arrives as `openclawStreamFallback.source=segment`
  with a nonempty `itemId`, not necessarily a delivery-mirror marker.
- Its event may omit top-level `runId`; `message.__openclaw.runId` carries the
  matching run. Session-level `lastRunId` and `activeRunIds` are not correlation.

The correction accepts explicitly classified text-only commentary, preserves
legacy nonfinal delivery mirrors, and accepts nested run IDs only when they do not
conflict with an outer run ID. It deduplicates by segment itemId. Final/analysis
phases, terminal metadata, errors, nontext/mixed tool or reasoning blocks, and
mixed signature-classified/unclassified blocks are rejected. Existing final/Stop
cancellation, selected-voice checks and short-update limits remain.

Evidence: synthetic `.tmp/commentary-wire-fixture.json`, 92 app/native tests in
`.tmp/commentary-contract-tests-final.log`, 40 core tests in
`.tmp/commentary-contract-core-final.log`. Independent investigator/test author
owned only AppTests; lead integrated core/model. Two review findings accepted
(error metadata, mixed phase blocks); re-review found no blockers. Review and
implementation overlapped, timing unmeasured. Pilot CLI rejected non-allowlisted
project; no cockpit or policy bypass. No live gateway/plugin modification.

Lesson: mocked acceptance/race tests did not establish the live commentary
contract. Future event integrations need a synthetic live event capture and a
regression fixture before calling the path supported. On-device audible timing
still requires physical confirmation; a contextual opening is not guaranteed for
every question.
