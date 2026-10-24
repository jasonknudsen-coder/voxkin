# Security remediation — October 24, 2026

Candidate: plugin 0.4.2, iOS 1.0.1 (15, CarPlay), Android 0.2.20 (22).
Addresses the three findings in security-review-2026-10-24.md.

## Changes

- VX-SEC-001: authenticate bearer headers before body/capacity admission. Separate
  bounded enrollment pool from authenticated per-device budgets and concurrency;
  retain aggregate overload ceilings. Never trust forwarded IP identity.
- VX-SEC-002: reject slash commands before history/run admission. Limit retained
  records to 32 per device, with independent active-work (128) and aggregate
  memory (4096 records) overload ceilings. Keep unexpired duplicate results,
  including errors; capacity recovers after five-minute retention. No early
  eviction that silently permits duplicate execution. This bounded in-memory
  idempotency guarantee does not survive restart or conversation deletion.
- VX-SEC-003: generate non-URI owner QR text, accepted only via explicit in-app
  scan/paste. Both OS URL entrypoints accept guest invitations only. Preserve
  legacy owner URLs only for deliberate in-app imports. No auto-connect or
  bypass of guest approval. New owner codes require updated mobile clients;
  already saved connections and guest links remain compatible.
- Defense in depth: Python isolated mode and OS-environment allowlist exclude
  gateway/provider secrets and Python/dynamic-loader overrides. No shell added.
- Reconciled owner/restricted authority, actual storage, and security documentation.

## Verification

- Node plugin: 77 passed, two opt-in skipped; actual installed host SDK contract:
  one additional pass using the installed plugin SDK.
- Swift core: 46 passed. iOS simulator app boundary: 3 passed (non-URI owner
  staging; external owner rejection/guest staging; saved restricted credential).
- Android JVM: 74 passed. Android emulator onboarding: 6 passed.
- Python setup: 6 passed. Report receiver: 5 passed.
- Independent read-only review: 22 server/runtime tests passed plus synthetic
  slow-body isolation, minute rollover, 4096-record saturation, duplicate safety,
  output bound and TTL recovery. No blocking defects; zero requested corrections.
- Actual native macOS English/Spanish synthesis passed through the candidate
  isolated worker/environment, producing valid WAV outputs.
- Plugin tarball: all 22 packaged files byte-match current source.
- iOS archive/export succeeded; CarPlay metadata verified, codesign verified;
  exported IPA version/build/bundle identity verified.
- Android AAB passes bundletool validation and jarsigner verification. APK v2
  signature and 16KB ZIP alignment verified. Signed APK installed/launched on
  isolated emulator; explicit external owner intent refused with safe status.

Local evidence: `.tmp/security-fixes-2026-10-24/`, including artifact-sha256.json,
archive/upload logs, simulator xcresult, instrumentation log and QA probe.
Swift simulator build needed explicit arm64 and disabled explicit-module scanning
with this Xcode test setup; release archive passed its normal configuration.

## Release and remaining boundaries

Store uploads are authorized; production promotion is not implied. No installed
plugin, hosted gateway, store-review submission, or public rollout is changed by
this source commit. Existing Apple 1.0 and Android 0.2.19 are already distributed.
Track store upload receipts separately; do not claim the old binaries contain fixes.

Native Windows/Linux worker compatibility has not been rerun for the environment
change. Platform-specific transitive wheel hash locks/clean-install qualification,
external penetration testing, and physical-device competing-handler testing remain
follow-up work. New owner QR is not a URI, so it avoids scheme dispatch by design;
this is not a claim that old exported owner links can no longer be intercepted.
Maximum retained text is bounded but can be substantial; this is not an
Internet-scale or hostile multi-tenant service. Edge connection/body limits remain
necessary on public deployments.

Delegation checkpoint: one scoped onboarding implementation lane and one read-only
QA lane; lead owns integration/releases. Cockpit orchestration/lane registration
was attempted and rejected because Voxkin is not allowlisted. No allowlist change
or bypass was made. Independent work overlapped lead implementation/packaging;
exact overlap duration was not instrumented. Findings: 0, accepted corrections: 0.

## Upload receipts

Source commit: `bf5fd7cfde35a0f45b08d77f8faaecf4476f09c7` (pushed).

- Apple accepted IPA upload without errors; build15 processed VALID and is
  attached to version1.0.1. Version remains PREPARE_FOR_SUBMISSION.
  Jason subsequently requested auto-release; release type is AFTER_APPROVAL.
  Existing review instructions, storefront description and screenshot set inherited;
  security-update release notes added. No review submission yet.
- Google Play accepted bundle22 (0.2.20); saved production draft release2 with
  release notes. Preview has no blocking errors or device-support losses; only
  optional deobfuscation (R8 disabled) and native-symbol warnings. Prepared100%
  rollout to existing targeted countries. Public submission remains gated.
  Final Publishing overview: Managed publishing OFF, one change not yet submitted
  for review, start full rollout. Google automated quick checks are still running.
- Plugin0.4.2 tarball is prepared, not installed on any gateway.

Artifact SHA-256:

- IPA: `91f1cd73a0318e9f2dec2be448e66b0c655532e76e6dc0e77fff47573f9a1bd5`
- AAB: `4283f32efe84459a0f087b24605fca743c163580e58d059e7ac090c2aa2e78ad`
- APK: `33c7b216e9909b663e586f0e01158d24076b7b5d056aec60420b3e9a723aa95f`
- Plugin: `092b7440d4865c63edcd326029bb819f4e79e4d56826306f962ff7931f207975`

### Repeatable qualification/upload notes

Use Node24 explicitly (the default Node22 binary currently has a missing dynamic
library). Use full Xcode via DEVELOPER_DIR. Generate project after updating its
version source, then archive with the same CarPlay build configuration as released.
Run simulator tests with separate derivedData; this Xcode test setup needed
SWIFT_ENABLE_EXPLICIT_MODULES=NO ARCHS=arm64 ONLY_ACTIVE_ARCH=YES. Archive/export
used normal release settings. Android unit tests use the Android Studio JDK;
release signing uses existing protected upload key and Keychain lookup, never
creates/replaces keys as a recovery measure. Verify signed AAB/APK and source
package before upload, retain SHA receipts and exact source commit.

Apple upload uses existing App Store Connect API credential with altool, followed
by API processing-state readback. Relationship PATCH may return HTTP204/no JSON;
read back the relationship rather than repeating a mutation after empty-body JSON
parse failure. Stage version, inherited metadata and release notes, attach VALID
build, and explicitly verify releaseType before any review submission.

The available Android Publisher service identity lacks Voxkin permission (403).
Use the existing signed-in Play Console stores browser profile; do not change IAM
or conclude the human login is missing. Stage AAB and notes, save draft and inspect
validation separately from production submission. Browser CLI upload accepts a
copy under /tmp/openclaw/uploads. Snapshot after navigation; timed-out clicks may
already have navigated, so inspect before retrying. Do not infer release approval
from successful upload, saved draft, or an old production authorization.
