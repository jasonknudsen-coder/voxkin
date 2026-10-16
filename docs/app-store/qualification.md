# Current release candidate — October 15, 2026

Local **1.0 (13)** CarPlay-enabled archive/export passed. Exported IPA version,
distribution profile, signature and CarPlay entitlement verified; full packaged
Info.plist matches build12 except marketing version/build number. No application
logic changes. Explicitly approved by Maintainer to include CarPlay. Independent read-only
review confirmed baseline match. Receipt:
`.tmp/app-store-release-13-carplay/verification.json`.

Uploaded with approval and Apple processing VALID; attached to public1.0 draft.
State remains PREPARE_FOR_SUBMISSION, release MANUAL. No review submission or
public release. Receipt: `.tmp/app-store-release-13-carplay/apple-receipt.json`.
Earlier phone-only13 artifact is superseded and must
not be uploaded. The earlier parked notes were stale; current build12 already
includes CarPlay. Maintainer subsequently completed the age questionnaire: API readback reports4+,
no override. Privacy completion is not independently verified via this API.

## Historical qualification record (superseded configuration)

# Distribution / CarPlay checkpoint — 2026-10-13

- Explicit Voxkin bundle ID registered successfully with Apple; existing identifier
  retained, no existing app record modified.
- Release **0.3.0 (1)** archive and App Store Connect IPA export passed using the
  existing developer account. Distribution signature verification passed; IPA is
  not debuggable and does not contain the gated CarPlay entitlement.
- Final IPA SHA-256:
  `18a0b102861b35097a75261e9f9410ff2741380f40a43e5050cb832057f1501c`.
- Ordinary IPA includes privacy manifest and no background audio or CarPlay scene.
- Separate CarPlay simulator variant compiles. Packaged metadata contains the
  CarPlay scene delegate and background audio. An initial metadata check caught
  Xcode replacing the custom scene manifest; the opt-in build now explicitly
  disables automatic scene-manifest generation and archive verification checks
  the final packaged values.
- **54 iOS app tests passed**, including three new attach/suspend/disconnect tests
  proving no automatic capture/replay and preservation of phone-authored drafts.
- Independent CarPlay SDK/official-guide review and lifecycle review completed;
  fixes include start-task ownership/generation fencing and owned-draft Stop behavior.
- Icon is opaque 1024px. Required-reason UserDefaults manifest is included. Store
  privacy labels are still a separate, unsubmitted questionnaire.

## Not completed / no success claim

The owner created the initial App Store Connect record on 2026-10-13. API
readback confirmed the matching bundle identifier. Existing account API credentials
then uploaded the signed 0.3.0 (1) IPA successfully with no upload errors. English
beta description and build testing notes are saved. Apple processing returned
`VALID`; internal state is `READY_FOR_BETA_TESTING` and external state is
`READY_FOR_BETA_SUBMISSION`. This is not external beta-review approval.

No tester invitation, beta review, CarPlay entitlement application/approval, or
actual CarPlay UI/vehicle validation. No production gateway change or
installed-phone replacement was performed.

Physical testing needs the capability/profile for CarPlay and the staged restricted
plugin for multi-user enrollment. Public store review also needs accessible privacy
and support URLs plus a safe functional review gateway, not private owner credentials.
