# Google Play preparation — 2026-10-20

## Status

**0.2.19 (21) production release submitted: In review. Not yet publicly available.**
Managed publishing is off; full rollout to178countries/regions is queued after approval.
See [public release qualification](public-release-2026-10-20.md) for current declarations, artifact and evidence.

0.2.18 (20) remains available to the owner-only internal track.
Play app `studio.voxkin.android`, app ID `4972664749241251932`, was created as a
free app, matching the approved iPhone pricing. Signed AAB uploaded and Google
processed it successfully. Internal release1 saved/published; exact track
readback says **Available to internal testers**. Selected existing First Tester
list after verifying it contains only Jason; no other people/groups selected.
Internal opt-in: https://play.google.com/apps/internaltest/4701176384112235431
Google temporarily labels the app `studio.voxkin.android (unreviewed)`.

Reporting endpoint and Android privacy deployed/verified from4234e9e. No actual
customer reports were read; synthetic report triage/deletion/expiry tested.
Release warnings: absent optional deobfuscation file (no R8 enabled) and native
symbol archive for bundled dependency. No blocking release error remained.

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
- `data-safety-draft.md`: historical worksheet; current declarations are recorded in the public release qualification. `review-access.md`: qualified persistent isolated Play access runbook; no credentials in source.

Capture with the isolated test runner and `StoreScreenshotsTest` on an emulator.
Actual phone data, saved access and private messages were not used. Images were
visually inspected after capture. Screenshot files are not post-edited.

## Build/signing

Current candidate:0.2.19 (21), application ID `studio.voxkin.android`, minSdk31,
target/compileSdk36. Release upload key is new and protected outside the repository;
password is in macOS Keychain. Its upload certificate is registered with Google Play.
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

## Operational follow-up

- Monitor Google automated checks and public review. Submission is not approval.
- Maintain reviewer capacity/budget and manual publisher report triage; AI does not automatically investigate submitted reports.
- Physical Pixel debug-to-Play signing migration is separate; do not uninstall without a concrete re-enrollment plan.

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
No Pixel install or real report was submitted. Service/site were subsequently deployed and Play submission is recorded above.

Deployment receipt: `.tmp/reporting-deployment/live-receipt.json`. Runtime storage uses private OS permissions; no independently verified provider disk-encryption claim. Provider backup concern was resolved by owner confirmation, not a provider-console inspection. Cloudflare edge peers share conservative rate-limit buckets; client headers are not trusted. Publisher report triage remains a manual operational responsibility.

No Pixel uninstall/install performed. Existing debug signing differs from Play signing: back up connection setup and explicitly plan re-enrollment before replacing the sideloaded app.
