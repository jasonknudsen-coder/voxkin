# CarPlay voice conversations

## Current release configuration — October 15, 2026

Build **0.4.1 (12)** contains the CarPlay scene and background audio, verified
from its signed archive. Preserve this mode in release1.0(13). Older “parked”
wording in channel metadata and release notes is stale, not release authority.

## Historical internal TestFlight availability — October 14, 2026

Voxkin **0.3.0 (7)** is available to the existing internal tester group. Apple
processing is VALID; READY_FOR_BETA_TESTING advanced to IN_BETA_TESTING after
group assignment, and membership was verified. Build 7 supersedes build 6.
Includes separate admin-permission and management-API status, pairing approval/retry,
tap-to-talk acknowledgment, optional truthful agent progress speech, and a 60-second
idle window separate from the 30-second spoken capture limit. No gateway/plugin
production deployment occurred. Build 6 had 88 passing tests; build 7's small
Settings change passed simulator build, archive, signature, provisioning and CarPlay
verification. Maintainer reports AirPods working; this is not universal Bluetooth qualification.


## Supported Apple category

Apple explicitly supports **voice-based conversational apps** on CarPlay from
**iOS26.4**. Voxkin uses that category, not a workaround audio/navigation category.
The required entitlement is `com.apple.developer.carplay-voice-based-conversation`.

Official references checked 2026-10-13:

- https://developer.apple.com/carplay/
- https://developer.apple.com/download/files/CarPlay-Developer-Guide.pdf
- https://developer.apple.com/contact/carplay/
- https://developer.apple.com/design/human-interface-guidelines/carplay

The guide requires primary voice interaction, a voice-control template during
voice services, no response text/imagery, and an audio session only while voice
features are active. It allows up to two action buttons in a voice state and up
to five voice states. Apple approval and an entitlement-bearing provisioning
profile are needed; compile success is not CarPlay runtime qualification.

## Implementation

`App/CarPlayCoordinator.swift` shares the phone's existing authorized VoiceModel.
No second gateway credential, operator escalation, or agent routing path is added.
A `CPTemplateApplicationScene` delegate presents `CPVoiceControlTemplate` with
neutral setup/ready/listening/working/speaking states. No transcripts, reply text,
tool output or reasoning is rendered in the vehicle. Setup, enrollment and access
management remain on the phone while parked.

Start is explicit; attaching CarPlay or reactivating its scene does not open the
microphone. **Stop / discard** ends voice and discards only unsent dictation from
that CarPlay-owned capture. It does not cancel agent work; **Cancel request**
separately requests that cancellation. Scene resignation/background/disconnection
stops voice. System suspension preserves unsent drafts; if one remains, review or
clear it on the phone when parked before starting again. Existing phone-authored
drafts are never silently discarded.

While CarPlay owns an active conversation, phone backgrounding does not tear it
down. Without an active CarPlay surface, the original phone-only lock/background
behavior still applies. A connected idle CarPlay session may retain its gateway
socket but must not retain an active audio session. Phone and car share one run.

Credentials use `AfterFirstUnlockThisDeviceOnly` in the non-syncing Keychain.
After each reboot, iOS requires one phone unlock before credentials are available;
subsequent phone locking does not prevent saved-gateway connection. Existing
unlock-only entries migrate in place after a successful read (or save), without
changing their data. Open the updated app and connect once while unlocked to
migrate the old endpoint, credentials and device identity before locked use.
No credentials are copied to preferences, files, iCloud or a different gateway.

CarPlay scene activation reconnects the saved gateway without starting audio.
The car shows Connecting or a Disconnected/Retry control for saved connections,
not a blanket instruction to unlock. Initial setup and unavailable credentials
still require the phone while parked. Cold launch and locked reconnect remain
physical-device qualification items; simulator Keychain tests cannot prove them.

## Explicit build flag; enabled in current TestFlight

`App/Info.plist` is the ordinary phone build. `App/Info-CarPlay.plist` adds the
CarPlay scene and background audio; `App/CarPlay.entitlements` requests the
voice-conversation capability. After Apple approves it and provisioning matches:

```sh
VOXKIN_DEVELOPMENT_TEAM=YOUR_TEAM_ID VOXKIN_BUILD_NUMBER=2 VOXKIN_CARPLAY=1 \
  scripts/prepare-testflight.sh
```

Do not add this entitlement to a distributed build before approval, or claim that
manually inserting it grants access. Simulator/runtime validation also requires
Apple's supported capability/provisioning setup. In Xcode27, Apple documents
Device Hub → attached iPhone → Device → CarPlay Simulator; a car/head-unit test
remains essential for audio routing and interruptions.

## Draft entitlement application

> Voxkin is a native iPhone voice client for a user's self-hosted OpenClaw
> assistant. The CarPlay experience is voice-first: the driver explicitly starts
> a conversation, asks a spoken question and hears an audio response. We use
> CPVoiceControlTemplate for active voice services with neutral listening,
> working and speaking states. We do not display response text or imagery.
> Start, Stop and Cancel controls are provided; microphone/audio use stops when
> the voice interaction is ended or the CarPlay scene becomes inactive.
> Account enrollment, gateway configuration and administration occur on the
> iPhone while parked. The app does not control the vehicle or provide navigation.
> We request the voice-based conversational entitlement for iOS26.4 and later.

The account owner submitted the voice-based conversational app request on
October 14, 2026 and reported Apple's confirmation: “We’ll review your request
and contact you soon with a status update.” Maintainer subsequently confirmed on
October 14 that Apple had approved it earlier that day. Approval is owner-reported;
entitlement-bearing provisioning and physical runtime qualification remain to be
verified. The text above
is the prepared draft, not a verified copy of the submitted form contents.
Working external beta-review access must be prepared separately.

## Qualification still required

Entitlement-backed template rendering; screen visibility and privacy; physical
microphone/speaker routing; phone locking and cold-start behavior; interruptions,
CarPlay disconnect/reconnect; Stop versus Cancel; no unintended audio in idle;
restricted-user Spanish and concurrent-user behavior. No claim of hands-free
reliability or safety follows from compilation or mocked-model tests alone.

## October 14 capability-enabled build checkpoint

Owner confirmed approval; automatic signing attempted for build 2. Apple's current
Voxkin provisioning profile does not include the requested CarPlay capability,
and Xcode refused the device archive. The App Store Connect capability listing
returned only In-App Purchase. This is evidence of missing app/profile assignment,
not proof the owner's approval was denied. Developer portal verification is blocked
on account sign-in. No CarPlay IPA was exported, uploaded or installed.

An unsigned ARM64 CarPlay simulator build passed, including packaged scene,
background-audio and privacy-manifest assertions. This is not signed-device or
head-unit qualification. Once portal access is available: check the approved
capability on the exact Voxkin App ID, refresh provisioning, rerun archive/export,
then verify both archive and exported app signatures and embedded profiles
(entitlement true, matching app/team, unexpired; distribution get-task-allow false).
The current archive verifier checks metadata only, so signature/profile checks
must be performed separately before handoff.

### Parked physical test card

- iPhone iOS 26.4+, approved capability-enabled build. Connect/enroll unlocked;
  grant microphone and speech permissions on the phone first.
- Attach: neutral CarPlay template, no transcript, no automatic microphone/audio.
- Explicit Start: car microphone captures and car speakers play the response.
- Stop discards only CarPlay-owned unsent speech; Cancel requests run cancellation.
- Test phone lock during an established car-owned conversation; locked cold
  reconnect should work after first unlock and migration; verify on the device.
- Test calls/navigation interruptions, leaving the scene, disconnect/reconnect:
  audio stops as appropriate, no automatic recording or replay, idle stays silent.
- Repeat a Spanish exchange; record actual vehicle/audio-route results.

Regression results: signed ARM64 simulator run passed all 54 model tests (including
CarPlay lifecycle coverage) and the one repeated UI test. The earlier unsigned run
had Keychain-access failures and one UI failure; six other UI tests passed there.
The signed rerun resolved the observed failures; no runtime code changes were made.
Logs: `.tmp/carplay-qa-2-signed.log`, `.tmp/carplay-enabled-2.log`, and
`.tmp/carplay-release-2.log` (local ignored artifacts).

Read-only reviewer identified missing signature/profile checks in the existing
metadata-only verifier; accepted as an explicit pre-handoff check above. No new
runtime defect established. Review overlapped build/testing, duration unmeasured.
Pilot tooling rejects this project as not allowlisted; no policy change made.

## October 14 provisioning resolved — build 2 ready

After the owner signed in, the approved **CarPlay Voice Based Conversation**
capability was available but unchecked on Voxkin's App ID. Enabled and saved it;
Apple's capability API then returned `CARPLAY_VOICE_BASED_CONVERSATION`.
Xcode refreshed provisioning successfully. The earlier blocker is resolved.

CarPlay-enabled **0.3.0 (2)** archive and App Store Connect IPA export passed.
Both archive and exported app passed strict codesign verification, matching
app/team/profile checks, profile expiry, CarPlay entitlement=true in signature
and profile, and packaged scene/background-audio/privacy-manifest checks.
Exported distribution app/profile have get-task-allow=false.

IPA SHA-256: `f44ec857c4e7530e01158c9d408ec570f1d6ca5b32f489a47fe72a67ddad60d8`.
Local artifact: `.tmp/carplay-release-2/export/Voxkin.ipa`.
Build log: `.tmp/carplay-release-2-approved.log`.
No upload, review submission or phone install performed. Physical CarPlay
qualification remains pending; simulator regression results above remain unchanged.

### Internal TestFlight delivery — October 14

Maintainer authorized proceeding with TestFlight delivery. Build 2 upload succeeded
with no errors (delivery `183bb919-eaf6-43f2-8e4f-8c0e34dae05f`). Existing Apple API
credentials were used without copying them into source or logs. Apple processing
and existing internal-group attachment are being checked; this upload is not
external beta review or public App Store submission.

Apple processing completed **VALID**, with internal state
**READY_FOR_BETA_TESTING**. Build 2 was attached to **Voxkin Internal** and the
group/build relationship read back successfully. CarPlay parked-test notes were
saved to the existing en-US build localization. Existing testers can update via
TestFlight. No new testers, external beta-review submission or public release.
Physical installation and head-unit testing remain user-side, unverified.

## Locked-phone follow-up — October 14 (local candidate)

Maintainer reports build 2 mostly works in the test vehicle. Photos show Ready to talk and
Working states; those do not prove locked startup or full audio reliability.
He also reports one crash submitted through TestFlight; cause is not established
and this patch must not be presented as its fix.

Candidate changes: in-place device-only Keychain accessibility migration, eager
shared model creation for CarPlay-first launches, saved-gateway reconnect on car
activation, credential restoration retry after unlock, and distinct connection
states with manual retry. No automatic microphone, turn send or replay added.

Physical checks for the next build: upgrade and connect once unlocked, then
lock/reopen CarPlay; disconnect/reconnect while locked; cold-launch from CarPlay
after first unlock; reboot-before-first-unlock gives phone guidance and recovers
after unlock; check Stop/Cancel, interruptions and idle silence. Do not treat the
reported crash as resolved without its diagnostic evidence and a targeted fix.

Delegation checkpoint: one independent read-only reviewer; implementation and
simulator testing remain with the lead. Orchestration pilot CLI refused start
because voxkin is not allowlisted; no registry or policy bypass attempted.

Review outcome: no blocking findings. One recovery gap was found and fixed:
unlocking refreshes credential/consent state and retries connection for an active
car scene; inactive scenes stay disconnected. Re-review accepted the fix. One
review recommendation accepted; review overlapped testing, duration unmeasured.
Signed simulator regression: 58 model/Keychain tests passed, including migration
by read/save and activation/unlock recovery without recording or sending. Log:
`.tmp/carplay-lock-fix-tests-final.log`. Physical locked behavior remains unproven.
No upload, installation or crash-resolution claim.

Targeted phone UI regressions also passed: fresh launch/no automatic recording,
and explicit conversation start/Stop (2 tests). Log: `.tmp/carplay-lock-fix-ui.log`.

## Build 3 locked-phone delivery — October 14

Maintainer authorized a new internal TestFlight build for locked-phone retesting.
Serial release checkpoint: reviewed source already passed 58 model/Keychain and
2 UI tests; this step only archives, verifies and delivers, with no new runtime
implementation. Existing unrelated installer/review-gateway changes are preserved.

CarPlay-enabled 0.3.0(3) archive/export passed signature, matching profile, expiry,
CarPlay entitlement, distribution get-task-allow=false, and scene/audio/privacy
metadata checks. The source-file hash manifest was unchanged during archiving.
IPA SHA256: `0c94a6fe6fe84ff8cea8b75060d3cf5a60b17f716215b4eed043e2e77e15627f`.
Local artifacts: `.tmp/carplay-release-3/`; upload/processing pending.

This build does not fix the separately retrieved build2 SIGABRT at
SpeechCapture.swift:62 (audio input tap installation during CarPlay Start).
Locked-phone physical behavior is still pending real-device retest.

Build3 upload succeeded; Apple processing is VALID with internal state
READY_FOR_BETA_TESTING. Attached to existing Voxkin Internal group and verified
by readback; en-US test notes saved, including migration and unfixed-crash caveat.
No new tester invitations, external beta review or public App Store release.

Follow-ups from Maintainer: evaluate a larger, easier-to-target microphone control
within Apple's permitted voice-template UI; no custom-center-button promise yet.
He reports ChatGPT voice responses felt notably faster. Measure end-of-speech,
on-device transcription, model response and first-audio latency before attributing
the gap to local synthesis hardware or recommending a Mac Studio. Guest plans
a separate account-activation test; invite/approval/first conversation remain
physical second-user qualification, not completed by this delivery.

### Build 3 physical feedback

Maintainer reports “Carplay is officially solid while phone is locked now” after
test vehicle retesting October 14. Record locked-phone operation as user-verified on
that setup, not a full reboot/cold-start/vehicle matrix or crash-resolution claim.
The previously captured audio input-tap crash remains separate and unresolved.

## Local CarPlay UI polish — October 14

Authorized by Maintainer after reviewing Apple template constraints. Status art is
now a 128pt canvas with up to112pt visible SF Symbol, template-rendered for system
contrast (Apple maximum150pt). No custom central hit target, transcript, background
requirement, or animation added. Ready has only Start; listening/speaking/preparing
have Stop when applicable; pending work exposes Cancel, retaining it after local
Stop. Setup/inactive/connecting phases do not show irrelevant disabled controls.
Offline exposes Reconnect. Apple still owns button sizing and placement.

Independent reviewer found the documented maximum of five template states;
previous code already exceeded it. Candidate now uses exactly five states:
attention, ready, listening, thinking, speaking. Multiple internal phases share
neutral Working/Not ready wording while keeping distinct control policies.
Attention copy was shortened to Not ready to talk so it does not imply a locked
phone must be opened when Reconnect is available. Existing explicit-start and
suspend/disconnect behavior remains unchanged.

70 signed simulator app/model tests passed, including action policy, the
five-state mapping, image size and factory button checks. Artwork contact sheet
was exported and visually inspected; that is not head-unit layout qualification.
Log: `.tmp/carplay-ui-polish-tests-final.log`; image: `.tmp/carplay-ui-artwork/`.
Final copy-only attention-title adjustment followed the test run.
Review/re-review complete: one blocking API finding fixed and one wording
recommendation accepted. One read-only reviewer overlapped testing (duration
unmeasured); project remains unavailable to pilot CLI because not allowlisted.

Not uploaded. Build3 remains Maintainer's locked-phone-tested version. Real vehicle
button refresh, tap targets, contrast and accessibility need next-build QA.
Audio input-tap crash remains unresolved and separate.

## Local capture-crash mitigation candidate

Fresh per-attempt engines, native-format taps, Objective-C exception containment,
ARC exception cleanup and permission cancellation guards are implemented.
81 signed simulator app/native tests, eight UI tests and unsigned Release device
build passed. Independent review/re-review complete. See
[capture-crash-hardening.md](capture-crash-hardening.md) for evidence and limits.
Not uploaded; real-car retest remains necessary to qualify the original crash.

## Combined build 4 internal delivery — October 14

Authorized internal TestFlight upload completed. 0.3.0(4) is VALID and
READY_FOR_BETA_TESTING; membership in existing Voxkin Internal group verified.
Build notes updated. Archive/export signatures, profile match/expiry, CarPlay
entitlement and metadata passed. Includes responsiveness, UI, capture hardening
and owner QR import/permission guidance. No gateway/plugin changes, new testers,
external review or public release. Physical regression checks remain required.
Artifacts: `.tmp/carplay-release-4/`; delivery80741ca3-6bca-4626-a5b2-718b4f539cd7.

## Build 5 owner QR scanner correction

Build4 camera delegate still filtered only restricted invitations, silently
ignoring valid owner setup codes. Delegate now uses a shared validated acceptance
policy for both types; no auto-import/enrollment/connection.39core +83app tests
passed, independent review complete. Archive/export signatures/CarPlay passed;
build0.3.0(5) VALID/internal READY_FOR_BETA_TESTING and existing group membership
verified. Notes updated. Delivery22622dd0-746b-4295-a7f7-b41682128fbc.
Physical camera retest remains needed. No PNG helper added: actual failure was
the camera filter, not established terminal rendering trouble.
