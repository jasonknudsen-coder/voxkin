# Release readiness — 2026-10-15

## Current release checkpoint — October 15

Latest beta is **0.4.1 (12)**, VALID and internally IN_BETA_TESTING. The public
1.0 draft is PREPARE_FOR_SUBMISSION with manual release and no attached build.
Free pricing/all 175 territories saved and verified. Guest acceptance,
privacy/age answers, matching
release build and final review-access checks remain. See the
[read-only Apple audit](app-store/release-audit-2026-10-15.md) for exact evidence.

## Historical commentary-contract fix — October 14, 2026

**0.3.0 (9) is available in internal TestFlight**, verified VALID and
IN_BETA_TESTING with existing internal-group membership. Supersedes build 8.
Fixes real commentary being ignored: supports explicit segment markers and nested
message run IDs while rejecting conflicting/terminal/reasoning/tool content.
Synthetic live gateway event capture reproduced the defect; 92 app/native and
40 core tests pass, with independent re-review complete. Signed archive/export
checks passed. No gateway/plugin changes. Physical audio confirmation remains.
Evidence: `.tmp/commentary-release-9/delivery-checkpoint.json`.
Earlier build sections below are historical evidence.


## Historical conversational feedback build — October 14, 2026

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


**Private source preparation only. No public repository, package, App Store or
production activation is authorized by this checklist.**

## Current acceptance

- User reports ordinary phone reliability checks complete, including Cancel while
  pending and longer-request progress. English/Spanish voices and hands-free
  alternating conversation have been tried successfully on the phone.
- Maintainer reports AirPods working on October 14. Do not extrapolate to every Bluetooth or incoming-call route.
- Latest speech formatting changes: 24 core + 51 model tests passed; conversation
  UI had 6 passing tests in a separate run. Screen-awake change compiled/signed.
- App branding, icon, speech-only Markdown cleanup and foreground screen-awake
  behavior installed. Manual lock still works; background voice remains out of scope.

## Source readiness work

- Apache-2.0 root/plugin license and matching package metadata; package stays private.
- Setup, contribution, security and privacy guides; current/historical evidence separated.
- All 16 pinned Python dependencies inventoried; binary/model redistribution
  obligations are explicitly not declared complete. See runtime-licenses.md.
- Publication privacy checks must run against the exact candidate snapshot; older
  development-history audits do not certify later source changes.
- Historical simulator screenshot is synthetic and privacy-clean but depicts the
  old Voxkin prototype. Keep it as historical evidence, not current marketing.
- This publication snapshot uses project-level attribution; private development
  history is intentionally not included.

## Before a public source release

- Owner approves visibility/publication separately and chooses whether to retain
  original author metadata. Repeat audit on the final release commit/artifact.
- Perform fresh-checkout setup on a clean supported environment. Existing-host
  venv setup was tested, but that is not clean-OS or cross-platform qualification.
- Preserve upstream notices and resolve applicable distribution obligations for
  any artifacts actually included; do not bundle runtime/models incidentally.
- Name/mark availability checks were not trademark clearance. No public store
  availability, supported-platform matrix, or production support SLA is implied.

## Latency decision

Existing measurements show reply generation dominates the delay for longer answers;
no convincing gain from reducing reasoning was found. Keep settled-final-only speech,
nonblocking subscriptions, bounded chunking and one-clip prefetch. Do not enable
speculative speech that could say text the agent later revises.

Potential next measured experiment: persistent local synthesis worker to avoid
repeated model/import startup. It needs explicit worker lifecycle/cancellation tests
and comparative measurements; not a safe branding-only deployment. No latency
improvement is claimed from this readiness pass; see reply-latency.md.

## Plugin branding promotion

Installed runtime/worker/setup/voice inventory match source; remaining changes are
Voxkin display/package naming and docs/license packaging. Preserve RPC/provider IDs,
voice allowlist, runtime location, stock speech defaults and model files. Stage a
package from an immutable reviewed commit; do not activate without production
approval. Use supported installer/reload, check catalog and preview, and retain the
old package/config for rollback. Reload previously timed out; no restart-free promise.

## Post–App Review roadmap — decision October 14, 2026

Maintainer deferred cross-platform installer qualification until after App Review.
These are follow-up work, not additional App Review blockers.

- [x] **Linux installer/runtime:** clean Debian Bookworm and Ubuntu24.04 x86_64
  container qualification passed October15, including packaged nonroot loading,
  unchanged receipt after restart, and hardened offline English/Spanish synthesis.
  See [evidence and scope](linux-qualification.md); no new host-RPC or phone claim.
- [x] **Native Windows:** patch-free plugin path and phone English/Spanish/history
  qualified October15; final owner acceptance reported. Installation retained,
  gateway startup disabled and SSH stopped/disabled for future testing.
  See [Windows qualification](windows-qualification.md).
- [x] Setup documentation and public supported-platform matrix updated/deployed
  October15. Ubuntu full gateway/phone evidence is distinct from Debian component
  qualification. See [Linux qualification](linux-qualification.md).

## Android roadmap — updated October 20, 2026

Native Pixel-first implementation is now in local qualification. See the
[Android setup guide](android/setup.md) and [qualification evidence](android/qualification.md).
This is not a Google Play release or a universal Android support claim.

- [ ] Native Android phone client, preferably Kotlin + Jetpack Compose, reusing
  the gateway/plugin protocols rather than adding a vendor conversation backend.
- [ ] First MVP: connect/pair → speak or type → hear reply → resume history;
  retain gateway voice selection and restricted-access boundaries.
- [ ] Pixel-first on-device transcription qualification for English and Spanish.
  Check `SpeechRecognizer.isOnDeviceRecognitionAvailable` and use
  `createOnDeviceSpeechRecognizer` (Android12+) where supported. Verify actual
  device/language models and downloads; Pixel Recorder/Gboard capabilities are not
  proof of third-party API availability. Offer typing when unavailable, with no
  silent cloud-recognition fallback.
- [ ] Android Keystore-backed credential protection, microphone permissions,
  audio focus/interruptions, Bluetooth routing and background lifecycle tests on
  physical devices. Do not infer coverage for all Android phones from one Pixel.
- [ ] Investigate Android Auto separately: verify current Google app-category
  eligibility and review/design requirements for a conversational voice client
  before promising support. Android phones do not run Apple CarPlay, and the
  existing CarPlay implementation cannot simply be reused. Android Auto is not
  a dependency for the first Android phone MVP.

## Parked directions

Other backends (including Hermes), full-duplex, and language learning are
not implemented or committed release promises.

## Restricted access candidate (0.3)

Opt-in implementation is documented in [restricted access](restricted-access.md).
Local automated evidence is not physical-phone qualification. Before promoting or
releasing, verify owner admin scope upgrade, shared/QR invite enrollment, matching
pending-device approval, Spanish speech, two-phone isolation and revocation. Tools
are disabled in the first restricted channel; never advertise this as unrestricted
agent functionality or hostile multi-tenant isolation.

CarPlay now has an opt-in implementation; see [approval and qualification gates](carplay.md).
TestFlight preparation is tracked in [App Store setup](app-store/README.md).

## Post-build5 pairing guidance candidate

Maintainer physically verified owner QR scanning and admin connection after host approval.
Local next-build candidate adds a prominent approval panel, validated request ID
command, explicit Retry, and pending-state retention across background/foreground.
40 core +84 app/native tests passed; targeted final lifecycle and UI panel tests
passed after reviewer-identified background retention correction. Not uploaded.
Setup docs now spell out devices list/approve and scope verification.
