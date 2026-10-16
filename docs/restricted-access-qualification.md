# Restricted access qualification — 2026-10-13

Status: **local candidate, not deployed or physically qualified**.

## Passing automated evidence

- Node plugin tests: **39 passed**, zero skipped with both opt-in host probes enabled.
  Includes storage, HTTP boundary, admin registration, ownership/revocation races,
  real installed SDK reply pipeline and real isolated gateway startup.
- Swift package: **35 passed**, including invitation validation and seven mocked
  HTTP transport tests. No external network or production credentials used.
- iOS app tests: **51 passed** after lifecycle fixes.
- iOS UI tests: **6 existing passed**, plus **1 new enrollment test passed**.
  New test verifies explicit enrollment action, empty invitation disabled, and no
  unauthorized admin controls. Its captured enrollment screen was visually reviewed.
- Simulator app build passed; plugin dry-run package has **16 source/doc files**,
  no model weights, runtime environment, credentials or access state.

Commands are in [restricted access](restricted-access.md). Xcode commands used
`DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`, arm64 simulator target.
An initial build with command-line-tools selection failed; selecting Xcode fixed
that. Initial mixed-architecture test linking failed; explicit arm64 test target
passed. These are not device-qualification results.

## Review outcomes

Independent review led to fixes for synchronous retained dispatch admission,
unsettled cancellation gating, changed-payload idempotency, bounded shutdown,
startup health reporting, enrollment cancellation generations, admin response
connection fencing, pending phone identity persistence and bounded HTTP reads.
Tests separately verify two users cannot poll or cancel one another's runs.

The first restricted channel explicitly disables tools and slash commands. Agent
bootstrap, hooks and model infrastructure remain trusted; private workspace setup
is a precondition. No claim of adversarial multi-tenant isolation is made.

## Remaining release gates

- Real owner device scope upgrade/pairing and admin management.
- QR camera scan and native share-sheet/messenger deep-link on physical phones.
- Two-phone approval, Spanish audio, cancellation, revocation and simultaneous use.
- Deliberate gateway restart/recovery with enrolled physical clients.
- Clean-environment installation and separately authorized live promotion/public release.

Neither the production gateway nor the owner's installed phone was changed by
this implementation. The isolated host tests replace model response resolution
or make no model calls; they do not prove an end-to-end model conversation.
