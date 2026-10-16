# Storefront preparation — October 15, 2026

## Current checkpoint

See [October15 release audit](release-audit-2026-10-15.md): latest beta is
**0.4.1 (12)**, no build attached to public 1.0; review contact/notes exist.
Age questions remain blank and retention facts unresolved. Subsequently authorized
free/all-territory settings saved/read back: 175 territories at zero price, new
territories enabled. No submission or release.
The platform-matrix site subsequently deployed from `b5a0c56`; the policy-only
deployment below is historical.

## Earlier storefront preparation — historical record

- Existing iOS **1.0** version remains **PREPARE_FOR_SUBMISSION**.
- English name/subtitle, 2,492-character description, keywords, promotional copy,
  Utilities primary category and copyright saved. Copy: `storefront.json`.
- Real marketing/support/privacy URLs saved; public privacy and support pages
  returned HTTPS 200. Privacy URL was previously blank in the App Info localization.
- Release mode changed from AFTER_APPROVAL to **MANUAL**; no submission or release.
- Two current, fictional-content 1320×2868 screenshots uploaded, both COMPLETE.
- Existing TestFlight 0.3.0 build 9 unchanged. No build attached to the 1.0 listing;
  matching release build/version selection belongs in the final release pass.
- Pricing, territory availability, review contact/credentials and submission were
  not changed. No new tester invitations.

## Privacy worksheet and published website

- `privacy-and-age-review.md`: evidence-backed questionnaire worksheet and exact
  unresolved operator facts. No App Privacy/age-rating attestation was made.
- `site/privacy/index.html`: review/test-gateway exception and support-feedback
  disclosure. `site/support/index.html`: general release support wording.
- Website updates approved and deployed October 15 from immutable snapshot
  `b002bc2a4acd181789262e171e857fe9e33ecb49`. All four public routes returned
  HTTPS 200 with exact source hashes. Prior release retained for rollback.
  Receipt: `.tmp/site-policy-live-receipt.json`. No app submission or gateway change.

## Decisions before closing this item

1. Resolved: Maintainer declined the adult-only override; retain NONE and use truthful
   questionnaire answers for Apple’s calculated rating.
2. Actual publisher-operated review/test retention and provider retention; whether
   it will remain restricted testing or become a public service; actual use of
   Apple analytics/diagnostics. Resolve these before privacy attestation.
3. Finish truthful content-frequency answers; 18+ override is not a substitute.
4. Policy clarification is live and verified. Complete the App Privacy form
   after resolving the actual data-flow facts above.

## Verification

- Metadata field limits passed and API values read back after saving.
- UI capture test passed; selected screenshot pixels reviewed for clipping,
  credentials/private content and unsupported claims; Apple processing COMPLETE.
- Independent read-only privacy/age guidance reviewer used current Apple guidance.
- Console pilot start refused the non-allowlisted project; no policy bypass or
  fabricated pilot record. One helper, read-only; lead owns all source edits.
- `git diff --check` passed. Simulator-only screenshot fixtures do not require or
  constitute a new TestFlight release. Existing unrelated dirty work preserved.

Private operational receipts are in `.tmp/storefront-before.json`,
`.tmp/storefront-details-before.json`, `.tmp/storefront-saved.json`, and
`.tmp/storefront-screenshot-receipts.json`; no credentials in these documents.

## Privacy-first positioning revision — October 15

At Maintainer’s request, description and promotional text now lead with direct gateway
access, no Voxkin-hosted AI backend or added AI/speech provider, existing gateway
configuration, and optional separately configured Tailscale private access. Copy
keeps downstream-provider processing/retention explicit and makes no blanket
no-third-parties or all-local claim. Both fields saved to the unpublished 1.0 draft
and API readback verified. No submission, website change or permission-dialog change.
Receipt: `.tmp/storefront-privacy-copy-saved.json`.
