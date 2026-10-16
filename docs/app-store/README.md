# App Store Connect and TestFlight

## Current state — October 15, 2026

- Name: **Voxkin**
- Platform: iOS (iPhone), minimum iOS17; CarPlay voice category requires iOS26.4+
- Existing bundle ID retained: `com.iris.private.voice`
- Latest TestFlight: **0.4.1 (12)**; public draft **1.0**, no build attached
- Original suggested SKU: `voxkin-ios`
- Primary language: English (U.S.)
- Primary category: Utilities (separate from CarPlay entitlement category)

The bundle ID and initial App Store Connect app record have been created.
For a new deployment, create the app record once in the App Store Connect website;
Apple's public API supports reading/updating apps but not creating that initial
record. Subsequent uploads and beta metadata can use an App Store Connect API key.
Account-specific signing settings and Apple API keys are not committed. See
`qualification.md` for this candidate's distribution status.

## Local archive/export

```sh
VOXKIN_DEVELOPMENT_TEAM=YOUR_TEAM_ID VOXKIN_BUILD_NUMBER=13 VOXKIN_CARPLAY=1 \
  scripts/prepare-testflight.sh
```

This script archives and exports an App Store Connect IPA locally. It never
uploads, invites testers, submits for review, or changes a gateway. Artifacts
live in `.tmp/testflight/` by default and are ignored by Git. Increment build
numbers before subsequent uploads. The default build excludes the optional
CarPlay entitlement and background-audio mode.

The icon is opaque 1024px. The app includes a required-reason privacy manifest
for app-owned UserDefaults (`CA92.1`). `ITSAppUsesNonExemptEncryption=false`
reflects the app's use of OS HTTPS/WSS/Keychain/cryptographic APIs, not custom
non-exempt cryptography. Reassess export compliance if encryption changes.
The privacy manifest is not a completed App Store privacy-label questionnaire.

## Remaining Apple steps

Use the [October15 release audit](release-audit-2026-10-15.md) as the current
checklist. The app record, beta upload, storefront copy/screenshots, contact and
review environment already exist. Do not recreate them.

Finish independent phone acceptance, factual privacy/age questionnaires,
pricing/territory verification, and a matching release build. Revalidate isolated
review access immediately before submission; never give Apple an owner gateway
token or private conversations. Invitation expiry is not data deletion.

External TestFlight review and public App Store review are separate. Tester
invitations require authorized recipients; a TestFlight invitation does not replace
Voxkin gateway enrollment/network setup. Never grant unnecessary App Store Connect
roles to bypass beta review.

## Public store release is separate

Public release still needs approved privacy labels, accessible privacy/support
pages, screenshots, review access, age rating and release approval. Repository
publication and production gateway promotion are independent actions; no change
of GitHub visibility or production plugin configuration is implied by this work.

See [CarPlay](../carplay.md) for the separate capability approval and build mode.

Release-mode correction: uploaded build12 includes CarPlay, verified from its
archive. Preserve `VOXKIN_CARPLAY=1` for the 1.0 release; the script default remains
phone-only and must not be mistaken for the distributed configuration.
