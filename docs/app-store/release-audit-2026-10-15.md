# App Store release audit — October 15, 2026

Initial read-only Apple API audit, followed by explicitly authorized free/all-
territory draft settings. No submission, release, upload or questionnaire attestation. Private response: `.tmp/app-store-audit-private.json`.

## Verified in Apple

- iOS listing **1.0**, **PREPARE_FOR_SUBMISSION**, **MANUAL** release.
- Latest uploaded build **0.4.1 (12)**: VALID, IN_BETA_TESTING internally,
  READY_FOR_BETA_SUBMISSION externally, APP_STORE_ELIGIBLE, not expired.
  The external status is not external-review approval.
- **No build attached** to the 1.0 listing. Prepare/select a matching marketing
  version before submission; the existing beta remains the latest beta.
- Two screenshots (`01-talk.png`, `02-conversation.png`) are COMPLETE.
- Review contact fields and review notes are present. Demo username/password is
  not required by the saved draft; the separate invitation-based review flow must
  remain functional. Private access details are not reproduced here.
- Age-rating questionnaire answers are **null**. Overrides remain **NONE**.
- Export-compliance flag on build 12: usesNonExemptEncryption=false.

## Remaining before submission

- [ ] Guest's independent phone acceptance and any resulting fixes.
- [ ] Final matching 1.0 release archive/upload, processing, build attachment and
  physical smoke test. Recheck screenshots/copy against that candidate.
- [ ] App Privacy: hosted scope resolved by Maintainer: strictly App Store review
  testing, never a customer service; customers bring their own gateway. Resolve
  transcript/access/log retention and downstream-provider retention. Maintainer approved
  both Apple crash-report and App Analytics usage. Complete truthful labels. The worksheet is not an attestation; live label
  completion was not verified through this audit's API access.
- [x] Owner completed age questionnaire; API readback confirmed all answers saved.
  Apple reports FOUR_PLUS (4+), with age overrides NONE. This is the owner's
  completed assessment, not an independently established AI-content guarantee.
  Prior 13+ suggestion was not applied. Receipt: `.tmp/age-owner-completed.json`.
- [x] Pricing and territories: Maintainer authorized free/all territories. Saved and
  read back October15: USA base price 0, 174 automatic prices all 0; all 175
  territory records available=true; availableInNewTerritories=true. The draft
  remains PREPARE_FOR_SUBMISSION, not published. Regional requirements may still
  constrain actual distribution after review. Receipts:
  `.tmp/free-territories-readback.json`.
- [ ] Review access: recheck reachability, enrollment, remaining request budget and
  invitation lifetime immediately before submission. Saved expiry is
  **2026-10-28T17:42:32Z**; documented trial budget is 100 requests, not a verified
  current balance. Extend/reissue if needed. Access expiry is not data deletion.
- [ ] Final submission review and explicit release authorization. Manual release
  means approval alone should not automatically publish the app.

## Already done / not additional release blockers

- Store copy, category, support/privacy URLs and screenshot upload are prepared.
- Public website/platform matrix deployed; macOS, Ubuntu and native Windows
  qualification evidence is documented. Windows is parked with startup/SSH disabled.
- CarPlay is included in current build12; preserve its explicit build flag for1.0.
  Earlier parked wording was stale. Public repository/package publication
  and bundled-model redistribution are separate decisions from App Store submission.

See [privacy worksheet](privacy-and-age-review.md), [review setup](review-access.md),
[storefront history](storefront-status.md), and [platform evidence](../../site/platforms/index.html).

## Approved release upload completed

CarPlay-enabled1.0(13) uploaded successfully, processed VALID and attached to the
1.0 draft. Readback confirms PREPARE_FOR_SUBMISSION/manual release. No review
submission, public release or new tester invitation. This supersedes the initial
no-attached-build snapshot above. Candidate physical smoke/Guest acceptance and
final privacy/review-access checks remain distinct from upload completion.
