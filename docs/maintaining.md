# Maintaining the standalone repository

This tree contains the app, core library, plugin, setup instructions, tests and
static website. Building or developing it does not require the private development
repository. Historical notes may mention old commit IDs or ignored `.tmp/` receipts;
those are provenance only, are not included here, and must never be required inputs
to a build or claimed as fresh verification. Run the checked-in tests for your build.

## Development

See [Contributing](../CONTRIBUTING.md) for Xcode and plugin dependency installation.
See [getting started](getting-started.md), [Windows setup](windows-setup.md), and
[CarPlay](carplay.md) for supported runtime configuration and qualification limits.
The plugin remains a private npm package until a separate package release is made;
source installation does not depend on publishing it to npm.

## iOS release preparation

1. Set the intended marketing version/build in `scripts/generate-project.py` and
   regenerate the project. Do not use an already-uploaded build number.
2. Run core and app tests, then physical-device checks for changed behavior.
3. With your own Apple account/team and approved CarPlay capability, run:

   ```sh
   VOXKIN_DEVELOPMENT_TEAM=YOUR_TEAM_ID VOXKIN_BUILD_NUMBER=NEXT_BUILD \
     VOXKIN_CARPLAY=1 scripts/prepare-testflight.sh
   ```

   The default script mode is phone-only; explicitly preserve CarPlay for the
   current release configuration. `NEXT_BUILD` must be replaced with an integer.
4. Verify exported version/build, signature, distribution profile and entitlement.
   Keep signing keys and provisioning artifacts outside source control.
5. Upload through Xcode Organizer/Transporter with your own credentials, wait for
   Apple processing, and attach the matching build to the App Store version.
6. Recheck privacy, age rating, reviewer access and release mode before submission.
   Build/upload, review submission and public release are separate operations.

A separately signed fork should use its own bundle identifier; existing keychain
identity is not migrated by changing the bundle ID. Do not copy another publisher's
review credentials, signing identity or live service configuration.

## Website and review infrastructure

`site/` is deployable static content. `ops/site/` describes deployment conventions;
use your own host, DNS and TLS configuration. `ops/review-gateway/` provides isolated
review-service source/templates, not a hosted service or credentials. Replace example
hostnames and provision dedicated credentials privately before deploying.

## Canonical-repository cutover

Before adopting this as the canonical remote, choose the final GitHub URL and update
repository links, checkout/automation paths and issue destinations. Make future
changes here rather than maintaining parallel development trees. Keep the original
private history only as an optional archive. Do not copy its `.git`, ignored files,
private deployment receipts or credentials into this repository. Publication,
remote renaming and deletion/archiving of the original are separate owner decisions.
