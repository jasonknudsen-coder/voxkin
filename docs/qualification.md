> Current status: see [release readiness](release-readiness.md). Entries below are
> chronological evidence, not a single current checklist; later entries supersede earlier pending items.

# Prototype qualification — 2026-10-12

Latest setup instructions: [Getting started](getting-started.md). English/Spanish
plugin activation and physical Spanish success on 2026-10-13 are recorded in
[local voice qualification](local-voice-plugin.md). Earlier dated entries below
are historical snapshots, not the current language-support status.

## Verdict
**Runnable private prototype; mock/simulator-qualified only.** Not a live gateway, microphone, configured-voice, or physical-device acceptance claim. CarPlay remains parked. Initial prototype qualification used no production gateway changes, live credentials, provider requests, family data, account/signing changes, or physical installation. See the subsequent authorized installation checkpoint below.

## Observed evidence

- Xcode **27.0**, build **27A266a**, per-command `DEVELOPER_DIR`; global developer selection unchanged.
- SwiftUI iPhone app **simulator build succeeded**.
- Unsigned **iphoneos SDK build succeeded** (`build-device/Build/Products/Debug-iphoneos/Voxkin.app`). This is compile evidence, not a signed/installable release or device execution.
- **17 Swift package tests passed**, zero failures. Includes six initial scripted-wire tests plus cancellation-before-submission/pending-cancellation tests, signed challenge/token/scope payload verification, endpoint rejection, cross-session/stale-epoch fences, assistant-only final extraction, clip limits, ten synthetic turns, and mock abort suppression.
- **4 iOS app-model regression tests passed**, zero failures: category changes versus headset removal, waiting-state interruption suppressing late TTS, Stop before queued Send, and delayed old-abort response not clearing a newer run.
- **2 XCUITest UI tests passed**, zero failures on dedicated **Voxkin Private QA / iPhone 17 Pro / iOS 27.0 arm64** simulator. Live form does not auto-connect; synthetic Connect → typed message → Send → synthetic reply → Stop → Disconnect works and clears reply.
- **23 automated tests total passed.** The ten turns are within one core mock test, not ten live voice conversations.
- App launch screenshot visually inspected: [simulator start](evidence/simulator-start.png). Native form, mock default, state, Connect, and privacy copy render without clipping on this simulator. No wider accessibility/device-layout claim.
- Final Xcode result bundle (local, ignored): `build/RegressionResults2.xcresult`. Core log: `/tmp/voxkin-tests.log`. iOS regression log: `/tmp/voxkin-regression2.log`. Unsigned device compile log: `/tmp/voxkin-device-build.log`.

## Failed attempts and remaining warnings

- First generic test build attempted x86_64 app linkage against an arm64 package object. Corrected with explicit arm64 simulator destination / `ONLY_ACTIVE_ARCH=YES`; native app generator also sets active architecture.
- First live-form UI check did not locate the secure field after a row-wide toggle tap. Stable field identifier and switch-control hit point corrected the automation; final test passes. No live connection was attempted.
- Test-only transport factory initially needed an explicit `@MainActor` closure annotation; corrected before successful final tests.
- Simulator emits an AVAudioSession warning about synchronous activation/deactivation on the main thread. No test hang observed, but UI latency under real device audio-route changes remains a known qualification/performance item. No audio-latency claim is made.
- Xcode reports missing `simctl` in its automatic post-test diagnostic collector because global Xcode selection remains Command Line Tools. Explicit per-command simctl succeeds; final test suite still reports `TEST SUCCEEDED`. AppIntents extraction warning is expected: no AppIntents integration.

## Delegation checkpoint / independent review

One bounded **read-only reviewer**; lead retained all write/integration ownership. Review overlapped initial core tests, transport test expansion, and simulator work. Six concrete defects/recommendations accepted and fixed:

1. Own audio category change could interrupt capture/playback.
2. Interruption while waiting could permit late autoplay.
3. Cancelled queued model tasks could still submit chat/TTS.
4. Local Stop erased the handle needed for separate agent Cancel.
5. Delayed old cancellation response could clear a new run.
6. Transport caller cancellation did not propagate to queued writes.

Reviewer rechecked the final cancellation fixes and found no further issue in those bounded sections. Review is not comprehensive certification. Exclusive file ownership held; reviewer made no edits/commits/external changes. Project is not Quest Console allowlisted; no fabricated mission/lane/completion or orchestration pilot records were created. This local review note is not counted as a completed Console pilot.

## Required next gate: isolated live + physical device

- Human-authorized signing/install and isolated gateway pairing using synthetic content and no real agent integrations.
- Actual challenge/auth/granted-scope behavior; pending pairing, issued/reused token, revocation, denied permissions, endpoint changes, Keychain lock/forget, TLS/redirect/invalid certificate behavior.
- Live `agents.list`, selected app session routing, `chat.send` event correlation, `chat.abort`, configured message TTS and accepted/disabled provider fallback. No secret-bearing Talk config read.
- Physical microphone + on-device language support; denied/revoked permissions; partial recognition and capture cap; unsupported language must not cloud-fallback.
- Actual audible voice correctness, codec failures, provider reporting, genuine phone-call/navigation interruption and headset/Bluetooth route changes.
- Ten **live synthetic** turns with end-of-speech-to-first-audio median/tail measured; mock delays are not relevant evidence.
- Lock/background/foreground, cellular handoff, tailnet loss/reconnect, stop during reply/TTS, cancellation outcome uncertainty. Background voice is deliberately not implemented.
- Network destination capture to verify no unintended app endpoints. OS/Tailscale/provider traffic must be distinguished.
- Larger text/VoiceOver and additional phone sizes.

No GO beyond the bounded prototype until the applicable acceptance items have actual evidence.

## Authorized physical installation checkpoint — 2026-10-12

Following Maintainer’s go-ahead in the project channel:
- Built unchanged app source at `726b6f2` with the existing Apple development team, automatic development provisioning, and an explicit iPhone destination. Signing settings were supplied on the command line, not committed. `BUILD SUCCEEDED`; local log `/tmp/voxkin-signed-build.log`.
- `codesign --verify --deep --strict` passed for the signed device app.
- `devicectl device install app` confirmed installation of `com.iris.private.voice` on the paired physical iPhone Air.
- Remote launch was rejected by iOS because the phone was locked (`FBSOpenApplicationErrorDomain`, code 7). This is **installation evidence only**, not successful device execution or audio qualification.
- Next: unlock the iPhone and open Voxkin; keep synthetic demo enabled, Connect, and try a harmless typed turn and on-device dictation. The synthetic response plays a test tone, not Iris’s gateway voice.
- Isolated trusted-WSS gateway setup/pairing and live voice qualification remain pending. No live gateway credentials were entered, no provider requests were made, and no production gateway settings changed.
- Solo checkpoint: signing → install → locked-device launch is a serial operational sequence on unchanged reviewed source; no additional implementation or reviewer lane was needed.


## Physical first connection fix — 2026-10-12

- Maintainer confirmed the physical phone displays the synthetic reply and plays the tone. Real speech remains unverified.
- First live phone attempt reached the gateway, but the client closed before sending connect. A no-credential URLSession probe reproduced the cause: a requested `wss://host` appears as `https://host/` in `currentRequest.url`; strict URL equality falsely identified a redirect.
- Endpoint comparison now normalizes only secure WebSocket/HTTPS representation, default TLS port, host case, and empty root path. Host, non-default port, path, query, user-info and fragment changes remain rejected. A URLSession delegate declines HTTP redirects.
- All **19 core tests pass**, including normalized handshake and destination-change/no-credential regressions. Independent read-only QA found no actionable defect; the new redirect delegate itself is not covered by a real redirect-response test.
- Live corrected production transport with a deliberately invalid, non-secret probe token reached `AUTH_TOKEN_MISMATCH` rather than failing before handshake. This proves challenge receipt and connect submission only, not real credential acceptance or device pairing.
- Signed device build and strict codesign verification passed. Corrected app installed on the paired physical iPhone Air. Local build evidence: `/tmp/voxkin-handshake-build.log`.
- Next: reopen app, enter live endpoint and token, retry, correlate the exact pending device request before approval. No real token was included in probe code/output; no gateway configuration changes or voice-provider calls were made. Live session and physical voice qualification are still pending.


## Secure saved connection and speech follow-up — 2026-10-12

- Maintainer confirmed live pairing, Main selection, and a text response on his phone. Speech failed server-side: local `say` could not produce the configured WAV format.
- Corrected only local TTS format arguments to explicit WAVE/LEI16 at 22050 Hz; provider and Samantha voice unchanged. After the deferred runtime reload, a live synthetic `tts.speak` returned valid audio/wav (106340 bytes, 2.32 seconds). Physical speech playback remains unconfirmed.
- Connection attempts now save the validated address and pending bootstrap credential in non-synchronizing, WhenUnlockedThisDeviceOnly Keychain entries. The issued grant replaces pending bootstrap; saved credentials never populate the text field. Relaunch restores the address/live form without connecting. Endpoint changes clear unsent input; Forget removes that endpoint’s identity/grant/bootstrap and matching remembered address.
- Reviewer caught stale-grant precedence over replacement bootstrap after failed attempts; fixed and covered by a real Keychain regression. Re-review found no further blocking issues.
- **19 core tests + 7 signed simulator app/Keychain tests + 2 UI tests pass.** Initial unsigned simulator tests could not access Keychain; signed rerun passed. Evidence: `/tmp/voxkin-persistence-core.log`, `/tmp/voxkin-persistence-signed-tests.log`, and `build/Logs/Test/Test-Voxkin-2026.10.12_13-42-24--0600.xcresult`.
- Signed device build and strict signature check passed; updated app installed on paired physical iPhone Air. Build log: `/tmp/voxkin-persistence-device.log`.
- Next phone check: enter the same address once, leave token blank if marked saved, Connect; close/reopen and verify saved address and manual reconnect, then send a short phrase and confirm audible speech. No physical persistence/playback claim until Maintainer confirms.


## Remember speech acceptance — 2026-10-12

- Maintainer confirmed audible live phone speech and saved reconnection, and requested remembering the acceptance toggle.
- Acceptance now persists in device-only Keychain per exact gateway URL; disconnect/relaunch retain it, another gateway does not inherit it, switching it off removes it, and Forget removes it alongside credentials. No automatic connect or send added.
- Regression coverage exercises persisted acceptance, relaunch restoration, endpoint isolation, revocation, and Forget cleanup. Signed simulator app-test and device-build evidence: `/tmp/voxkin-consent-final-tests.log`, `/tmp/voxkin-consent-device.log`.


## Physical acceptance and latency/voice investigation — 2026-10-12

- Maintainer confirmed on-device dictation plus audible reply, immediate local Stop during the longer forest paragraph, and lock/background stopping audio with silent/disconnected return and no replay. Bluetooth remains untested. The earlier count was NOT a valid Stop pass; only the subsequent long reply establishes it.
- Counting investigation: sanitized session history shows one through nine delivered through interim message calls and only Ten as final output. This final-only client therefore displayed/spoke Ten. Gateway milestone for that turn shows 65.5 seconds before assistant output; another turn showed 23.2 seconds. Observed TTS requests took 551–697 ms. Agent/turn delivery, not those TTS durations, accounts for the main wait; no latency fix is claimed.
- Jessica migration is blocked: ElevenLabs plugin is disabled, and a synthetic direct synthesis probe rejects the existing environment credential as `api_key_id_used_as_api_key`. Talk references the same rejected environment credential. No credential value was displayed, no provider/plugin changes made, and working Samantha remains active. A valid ElevenLabs API key is needed before migration; provider voice/model fields also need current `voiceId`/`modelId` names when enabled.

## Heart selection — 2026-10-12
- Maintainer selected Kokoro Heart after four local auditions. Durable isolated runtime and newly written stdin-based CLI adapter prepared; short/long and empty/oversize checks passed. Shared local TTS configuration updated with compare-and-set; initial live probe still returned old Samantha 22050 Hz output because channel reload was deferred. Do not claim live Heart until 24000 Hz output verified. See local-heart.md.


## Talk/Settings redesign and foreground connection — 2026-10-12

- Maintainer confirmed Heart playback on the physical phone after runtime activation. Heart remains unchanged by this UI update.
- Replaced the connection-heavy main form with Talk and Settings tabs, a large tap-to-record/tap-to-finish-and-send button, optional keyboard composer, scrolling replies, pinned Stop and separate Cancel request. Removed the user-facing synthetic demo; only a simulator-only test fixture remains.
- Saved credentials connect on launch/foreground once; no automatic microphone, send, replay, or background reconnect loop. Background still stops and clears local conversation.
- Added bounded progress/delta/interim text with strict session/run correlation and message-ID deduplication. Only final replies generate speech. Mock protocol tests pass; live interim source/client run-ID compatibility remains a physical verification item, not a claimed live fix.
- Independent read-only review identified final-word truncation, hidden naturally completed drafts, and stale text on agent switch. All three were fixed and re-reviewed. Recognition now waits up to two seconds for final transcription, otherwise exposes the draft for review. Physical final-word capture still needs user verification.
- **31 tests pass: 19 core, 10 app/Keychain, 2 UI.** Signed simulator suite: `Test-Voxkin-2026.10.12_17-12-10--0600.xcresult`; logs `/tmp/voxkin-redesign-tests2.log` and `/tmp/voxkin-redesign-core.log`. Simulator and signed device builds succeed; strict codesign verification passes. Simulator Talk screen visually inspected; Settings and keyboard/send/Stop interactions exercised by UI tests.
- Updated signed app installed successfully on the paired iPhone Air, preserving local Keychain credentials. Next physical check: foreground reconnect, tap-talk final words, pinned Stop, and no replay after returning. Bluetooth remains untested.

## Responsiveness and polish — 2026-10-13
- Implemented bounded final-reply speech chunks with one prefetch, monotonic in-memory timing breakdowns, subscription reuse, listening/send/Stop haptics, actionable recovery messages and orbit/waveform app icon. No gateway permissions/settings or new service.
- Local synthetic Heart baseline: 37 chars 1.420–1.619s; 137 chars 2.050s; 1161 chars 9.894s. These do not establish end-to-end phone latency. See responsiveness.md for implementation/tradeoffs.
- Read-only reviewer found trailing whitespace-only synthesis and unresolved playback decode errors; fixed and re-reviewed. No additional blockers reported.
- **36 tests pass: 20 core, 14 app, 2 UI.** Coverage includes exact chunk text/bounds, trailing-whitespace synthesis, sequential audio completion, pending-prefetch Stop, background/foreground no replay, timing resets and interim correlation. Evidence: `/tmp/iris-polish-core.log`, `/tmp/iris-polish-final-tests.log`, `Test-Voxkin-2026.10.13_11-19-05--0600.xcresult`.
- Simulator and signed iPhone builds succeed; strict codesign verification passes. Icon visually inspected as opaque 1024px PNG. Device build log `/tmp/iris-polish-device.log`.
- Physical final-word capture, chunk naturalness/latency, foreground reconnect and live interim display remain user checks; automated tests are not physical proof. Bluetooth/AirPods/phone-call testing explicitly excluded by Maintainer for this iteration.
- Signed update installed successfully on the paired iPhone Air, preserving credentials.

## Natural dictation completion / draft send — 2026-10-13
- Maintainer reported no response and status “Review your draft before sending.” That is the natural-recognition completion path retaining an unsent draft, not confirmed gateway submission.
- Found the large control reverted to microphone after natural completion; tapping it began new dictation instead of sending retained words. Changed idle nonempty-draft control to Send draft, up-arrow and explicit caption; tapping submits once through the existing guarded send path.
- Focused UI regression proves an existing draft is submitted with the large button, displayed as sent text, receives a fixture reply and returns to microphone. Passed `Test-Voxkin-2026.10.13_11-53-55--0600.xcresult`; log `/tmp/iris-draft-send-test.log`. No gateway, speech permissions or provider changes.

## Stock-gateway reply latency — 2026-10-13
- Maintainer chose no forks, retaining final-only speech. Four isolated150-word reply probes show low reasoning14.980/15.005s versus medium14.651/15.725s server-side: no convincing win. Current model/reasoning/fast mode, shared config and permissions left unchanged.
- Moved optional interim subscription off Send's critical path; prepare on connect/agent switch, reuse pending work, retry failed subscription non-blockingly on later sends. Reviewer confirmed broad-read pre-materialization subscription support and cancellation/connection fencing; minor missing-retry concern addressed with generation-fenced task cleanup.
- Final focused suite15 app tests passes, including blocked optional subscription not preventing chat.send. Signed device build and strict codesign pass. Evidence `/tmp/iris-send-overhead-final-tests.log`, `/tmp/iris-send-overhead-final-device.log`. This is an overhead fix, not a measured end-to-end reduction of the17.4s story run. See reply-latency.md.

## Repeated draft status investigation — 2026-10-13
- Maintainer again reported “Review your draft before sending” after tapping Send on14fc159. Source review does not establish why this physical tap failed. The generic status can indicate natural completion, an empty final transcription replacing partial text, or a guarded send not proceeding; do not assert missing intent from status alone.
- Added injectable speech capture and3 deterministic lifecycle regressions: explicit finish waits for last words and submits once, natural completion retains draft until explicit send, and Stop rejects late completion. All18 app tests pass (`/tmp/iris-dictation-handoff-tests.log`). Tests do not reproduce the reported physical issue.
- Requested a screenshot showing draft, status and primary control. No new phone install or claimed functional fix while awaiting that evidence.

### 2026-10-13 — disappearing dictation draft
Maintainer supplied a screenshot showing Connected, an empty composer, idle microphone,
and “Review your draft before sending.” Injected recognition tests reproduced a
matching failure: whitespace/empty final callbacks erased a nonempty partial and
prevented the explicit send. VoiceModel now ignores blank recognition hypotheses
within the current capture epoch, retaining nonempty revisions. New recordings
still clear prior drafts; errors/timeouts remain review-only. Entirely empty
captures now show retry guidance. Three new cases failed before the patch and
all 21 app tests passed afterward (`/tmp/iris-empty-final-after.log`). Signed device
build succeeded. Independent read-only review found no new cancellation or
cross-turn risk. Physical cause and final-word accuracy still require retesting;
a retained partial is not a guarantee that recognition captured the final words.

### 2026-10-13 — concise replies preference
Added Settings → Replies → Concise replies, default on, persisted as a nonsecret
local UserDefaults Boolean. Each ordinary chat.send message includes a turn-only
reply-style annotation asking for 2–3 short natural sentences, with explicit
exceptions for requested detail and essential information. Off explicitly requests
normal length to counter earlier guidance in the voice session history. Original
user text remains in the app's sent-text display. Commands beginning with / or !
pass through verbatim (review caught the initial command-prefix regression).
Preference is captured synchronously at Send; changing it cannot modify an
already queued turn. No model, reasoning, gateway config, or source-fork changes.
Response length remains model guidance, not truncation or a guaranteed speedup.
Tests cover persistence, both states, queued-turn capture, retained original
text, and command pass-through. Live model compliance/latency and phone toggle
interaction remain user qualification items.

### 2026-10-13 — concise/formatting/timing polish
Concise guidance now targets about 30–50 words in 1–3 sentences, retaining detail
and essential-information exceptions. Inline Markdown rendering fixes literal
bold/emphasis markers and preserves paragraph whitespace; not a full block
Markdown renderer. First speech preparation is now split into gateway audio
request (including transfer and brief pre-request scheduling/chunk work) and
local decoding/playback setup. Both are in-memory durations with stale-response
epoch checks. No speech engine or gateway settings changed.
25 app tests passed, including inline emphasis/paragraphs and timing split/reset;
signed device build succeeded. Read-only review found no blocker. Phone rendering
and timing breakdown await real-device feedback.

A fresh local synthetic 108-character sample (no audio file written) measured
imports0.171s, model load0.431s, synthesis1.443s, total2.044s for6.84s audio.
The adapter starts a fresh engine per CLI request. This demonstrates startup
cost but does not explain the screenshot's5.9s combined preparation or isolate
network/queue time. No persistent service added, no speedup claimed.

### 2026-10-13 — three reply-length choices
Replaced the concise Boolean UI with Brief / Balanced / Full segmented picker.
Brief uses the unchanged current 30–50-word guidance, Balanced restores the exact
original 2–3-sentence guidance (observed around 100 words, not a hard guarantee),
and Full requests normal response length. Detail exceptions remain. Preference
is captured per Send and saved as a nonsecret local string. Legacy true/missing
maps to Brief and false to Full; a valid new selection takes precedence.
All 26 app tests pass, including migration, all-mode persistence and transport
payloads. No gateway or speech-engine changes. Phone selector interaction and
relative answer lengths still require user confirmation.

### 2026-10-13 — everyday reliability pass
See reliability-pass.md. Two reviewer-found defects reproduced before fixes:
empty final left Waiting until timeout; socket close during agents.list could
resurrect Connected. Fixes passed reviewer recheck. Added stale prior dictation
callback coverage. Final selected suite:20 core +27 app +3 UI =50 passed;
route/headset/interruption cases excluded per requested scope. Logs:
`/tmp/iris-reliability-core.log`, `/tmp/iris-reliability-after.log`.
Signed device build succeeded. Physical repeated-dictation/reconnect/Stop/Cancel
checks remain pending; no live destructive actions tested.

Spanish requested today: stock tts.speak schema accepts text only; current
Heart adapter forces en-us. Installed Kokoro voices include Spanish, but need
an explicit routing contract or a different playback path. User asked to choose
phone-local Spanish voice versus custom gateway-adapter enhancement; no language
or shared voice changes made while awaiting that choice.

### 2026-10-13 — physical checks and clear draft
Maintainer reported: three separate dictated words each produced a response; sentence
ending pineapple was repeated with its final word; background/reopen cleared
visible conversation without replay, and a fresh hello request succeeded; Stop
before speech stayed silent. Cancel-while-pending and long-request progress
physical checks remain pending. No Bluetooth/AirPods/phone-call tests requested.

Added accessible 44-point Clear draft × in the composer, available during
recording/finalization as well as typed drafts. It stops capture, clears words,
resets finalization and invalidates the epoch; late callbacks cannot restore or
send discarded text. Active agent runs cannot be affected by this action.
Reviewer found no blocker; noted duplicate recording transcript UI as minor
existing/new-composer crowding, not a functional issue.
Clear-draft verification:28 selected app +4 simulator UI tests passed, including
clearing during pending finalization, rejecting late callbacks, and typed clear
restoring the mic without a send. `/tmp/iris-clear-tests.log`. Signed device
build passed; interaction on the physical phone remains to be confirmed.

### 2026-10-13 — familiar clear-field styling
Following Maintainer's request to mimic familiar app conventions, clear is a small
secondary-gray circled x inside the draft field with a44-point hit target. It
only appears for nonempty draft text. Send is a separate44-point control outside
the field with standard enabled/disabled tinting. Recording/clear behavior is
unchanged. This supersedes the briefly considered same-color/same-size treatment;
that intermediate treatment was never installed. Targeted simulator UI test
checks clear absent when empty, present for text, absent after clear, no reply
sent and Send disabled after clear.

## Voxkin branding rename — 2026-10-13
- App display name/module/project/scheme and plugin display branding renamed to Voxkin. Existing bundle ID, Keychain service, RPC/provider/session identities and runtime paths retained; see voxkin-rename.md.
- Private GitHub repository renamed to `jasonknudsen-coder/voxkin`; local working directory remains `projects/voxkin`.
- Passed: 20 Swift core tests, 39 app tests, 5 UI tests, 8 plugin tests (72 total). Simulator app Info.plist reports display name Voxkin and unchanged `com.iris.private.voice`.
- Evidence: `build/Logs/Test/Test-Voxkin-2026.10.13_17-30-17--0600.xcresult`; local logs `.tmp/voxkin-signed-sim-test.log`, `.tmp/voxkin-swift-test.log`, `.tmp/voxkin-plugin-test.log` (ignored build artifacts).
- Initial unsigned simulator run failed Keychain tests and one sent-text UI assertion. Full ad-hoc-signed rerun passed with no product-code workaround; first UI failure cause not established.
- Independent read-only review caught two broad-replacement defects: legacy model directory changes and rewritten historical evidence names. Both restored; no remaining actionable review defects. Review overlapped test execution; no timing metric collected. Pilot CLI rejected project as not allowlisted, so no cockpit record was created.
- Phone installation and live gateway plugin activation are NOT established by this rename. Historical qualification above is unchanged.

## Voxkin visual identity — 2026-10-13
- Applied approved ribbon V, two voice bars per side, cyan/blue left and purple/magenta right. Added SVG color/monochrome/icon masters and reproducible opaque 1024px icon generator under brand/.
- Native Talk/Settings now use navy backgrounds, branded header/empty state, gradient action control and purple reply accents. Audio/connection/credential logic unchanged.
- Read-only review identified aspect-ratio distortion and duplicate toolbar styling; fixed both. Standard Talk and voice-settings screenshots inspected; maximum accessibility text exposed oversized pinned controls, fixed by capping only transport controls at XXXL and clipping scroll content against an opaque control surface. Conversation text still scales and scrolls.
- Final regression: 39 app + 5 UI tests passed, result `build/Logs/Test/Test-Voxkin-2026.10.13_17-46-32--0600.xcresult`. Final simulator and signed iphoneos builds passed. No additional core/plugin changes in this visual task.
- Signed build installed on paired iPhone Air. Maintainer noticed icon appeared high on Home Screen; moved square icon mark down 20/1000 units, regenerated 60/120px previews and rebuilt for reinstall. Horizontal geometry unchanged. No behavior retest needed for pixel-only centering adjustment; final physical centering feedback remains with Maintainer.
- Two bounded helpers: exclusive asset implementation and read-only UI review, overlapping lead UI implementation/tests. Two reviewer recommendations accepted. Project remains outside orchestration CLI allowlist; no cockpit/pilot record claimed.
- Gateway plugin unchanged; no public release.

### 2026-10-13 — latest native app and physical acceptance

- Voxkin branding/icon and in-place rename installed; compatibility IDs retained.
- Hands-free alternating conversation installed (2ee2eb9); user confirmed working
  on the phone. No full-duplex or background listening claim.
- Speech-only Markdown rendering (ea28f63) removes emphasis markers without
  changing displayed replies; 24 core + 51 app tests passed. Prior conversation UI
  run passed 6 UI tests; these counts are separate runs, not a new combined suite.
- Foreground idle-timer override installed (93c59e8); signed build/codesign passed.
- At 18:59 MDT the user confirmed the remaining non-AirPods phone reliability
  checklist complete, including pending Cancel and longer-request progress.
  This is user-reported acceptance, not an instrumented timing study. AirPods
  remain pending; no independent Bluetooth route/phone-call interruption claim.
