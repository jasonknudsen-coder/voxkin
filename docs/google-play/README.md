# Google Play preparation — 2026-10-20

## Status

Reporting service/privacy deployed and verified; **app not uploaded, not submitted, not publicly released**.
Play Console organization access verified. `studio.voxkin.android` reports available.
The create-app form was inspected and name/package entered, but **Create app was not
clicked** and required policy/export declarations were not attested. No persistent
Play app record, signing enrollment, tester list, track or release was created.

Google requires in-app offensive AI-output reporting. The Android reporting endpoint is deployed and qualified;
see [reporting proposal](reporting-proposal.md). Do not certify policy compliance
until this is resolved. Internal testing is not a blanket policy exemption.

## Prepared assets

- `screenshots/01-talk.png`, `02-conversation.png`: real Android Compose UI captured
  at1080×1920 on API35 emulator, density320. Same fictional Personal agent and
  unwind-after-a-busy-day exchange as the iPhone listing. Offline instrumentation
  fixture only; not live model evidence. Test APK only; no fixture in release.
- `store-icon.png`:512×512 opaque RGB export from shared source geometry.
- `feature-graphic.png`:1024×500 brand art; editable SVG beside it. No device frame,
  screenshot compositing, ratings, awards, price or Android Auto claims.
- `listing-en-US.json`: Play-length-checked Android copy. Requires own compatible
  gateway; English/Spanish input depends on installed local recognition models.
- `data-safety-draft.md`, `review-access.md`: worksheets, not submitted attestations
  or functioning reviewer credentials.

Capture with the isolated test runner and `StoreScreenshotsTest` on an emulator.
Actual phone data, saved access and private messages were not used. Images were
visually inspected after capture. Screenshot files are not post-edited.

## Build/signing

Current candidate:0.2.18 (20), application ID `studio.voxkin.android`, minSdk31,
target/compileSdk36. Release upload key is new and protected outside the repository;
password is in macOS Keychain. It is **not yet registered with Google Play**.
Do not confuse the upload certificate with Google's eventual app-signing certificate.
No signing secrets, keystore or password files belong in source control.

Set these via protected local secret retrieval, without printing values:

- `VOXKIN_UPLOAD_STORE_FILE`
- `VOXKIN_UPLOAD_STORE_PASSWORD`
- `VOXKIN_UPLOAD_KEY_PASSWORD`
- optional `VOXKIN_UPLOAD_KEY_ALIAS` (defaults to `voxkin-upload`)

Then run `./gradlew :app:bundleRelease :app:assembleRelease` from `android/`.
Missing signing configuration fails release preparation; debug remains independent.
Final upload artifact: `android/app/build/outputs/bundle/release/app-release.aab`.
Local protected delivery copies/receipts are under ignored `.tmp/google-play/`.
The release APK is for smoke qualification, not a substitute for the Play AAB.

The existing Pixel debug install uses a different signing identity. A future Play
install cannot be treated as an in-place update over that debug package. Arrange
an explicit migration/re-enrollment plan before uninstalling anything on the phone.
No phone uninstall was performed in this preparation.

## Checks

See `verification.json` for immutable artifact digests and exact observed results.
Signed AAB validates with bundletool1.18.3 and jarsigner; release APK verifies with
apksigner and16KB ZIP alignment. Bundle config declares PAGE_ALIGNMENT_16K.
Release manifest has no debuggable=true, disallows cleartext, and disables backup.
Lint:0errors,22warnings,2hints. No clean-lint or Play-review acceptance claim.

## Remaining before submission

1. Reporting is live: HTTPS new/duplicate receipt, invalid-schema rejection, synthetic triage/deletion and aged-row purge passed October20. Owner confirmed no Voxkin backup set; live application backup scope excludes reporting storage.
2. Android-aware privacy changes are live and byte-matched to approved source.
3. Confirm publisher review-environment retention, access expiry and model-provider
   processing; create dedicated Play review access and verify Android enrollment,
   conversation, speech and reconnect. Do not repurpose Apple review credentials.
4. Finish Data Safety, target audience/content rating, app access and other Console
   declarations truthfully. Do not default to “Data not collected.”
5. Create Play app, enroll app signing, upload the exact approved AAB to internal
   testing, configure explicit testers, and verify track/readback. Internal tester
   invitation delivery and public production release are separate actions.

## Review/delegation

One independent read-only readiness/reviewer lane; lead owns all edits, artifacts,
Console and emulator operations. Cockpit orchestration start was attempted from its
correct project directory and rejected because Voxkin is not allowlisted. No
allowlist or production-approval configuration was changed to bypass it.

## Reporting qualification

0.2.18: 73 JVM tests, 3 report UI tests (including Activity recreation),
1 refreshed screenshot fixture and 5 receiver tests passed. Independent review
verified fixes for emoji truncation and rotation state. Reporting text is memory-only;
process termination loses references, so cross-process deduplication is not claimed.
No Pixel install or real report was submitted. Service/site and Play remain unchanged.

Deployment receipt: `.tmp/reporting-deployment/live-receipt.json`. Runtime storage uses private OS permissions; no independently verified provider disk-encryption claim. Provider backup concern was resolved by owner confirmation, not a provider-console inspection. Cloudflare edge peers share conservative rate-limit buckets; client headers are not trusted. Staffed report-review cadence still needs owner assignment before broader rollout.
