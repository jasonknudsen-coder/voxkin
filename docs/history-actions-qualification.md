# History naming and actions qualification — October 15, 2026

Local candidate: Voxkin 0.4.1 (11), plugin 0.4.1. Not uploaded or deployed.
No credential reset or namespace change in this candidate.

## Implemented

Readable automatic excerpt names, stable owner autoLabel after a real exchange,
manual gateway-backed names, local date/time, trailing swipe Rename/Delete,
explicit Cancel/Delete alert and full-swipe disabled. Owner deletion uses
expectedSessionId with archive-then-delete; failed second step attempts restoration,
unknown result is described honestly. Restricted mutations enforce device+agent
ownership, live authorization, per-conversation operation exclusion and settled
runs. Deleting selected history resets its send context. Pagination refreshes.
Gateway transcript archives/backups may remain; no secure-erasure promise.

## Evidence

- 45 Swift core tests: `.tmp/naming-core.log`.
- 104 app/native tests: `.tmp/naming-app-final.log`.
- 65 plugin tests, zero skipped, including installed SDK and isolated host load:
  `.tmp/naming-plugin-final.log`.
- 2 UI tests: `.tmp/naming-ui-confirmation.log`; opening/continue with no autoplay,
  swipe rename, cancel delete and confirmed delete. Actual screenshot inspected
  under `.tmp/naming-ui-verified-attachments/`; all sample data fictional.
- Signed archive/export and metadata check: `.tmp/naming-release-verified.log`;
  strict codesign validation passed. Final export `.tmp/naming-release-11-verified/`.
- Packed plugin verified against all 20 source files; immutable IPA/plugin hashes
  in `.tmp/naming-candidate-receipt.json`.
- `git diff --check` passed; unrelated pre-existing work preserved.

## Review and test recovery

Two bounded helpers: gateway API/read-only review and exclusive restricted-history
implementation. Independent review found pagination loss, unstable title override,
regex inconsistency, and unknown delete-outcome wording; all four recommendations
accepted. Overlap time not measured. Console pilot start refused non-allowlisted
project `voxkin`; no bypass or unsupported finish record. No active helper work.

UI testing found confirmationDialog omitted Cancel in its popover presentation;
replaced with an explicit two-button alert, retest passed. Initial UI filter selected
zero tests; corrected to target/class/test and required executed-test counts.
Two xcodebuild invocations used the workspace rather than project cwd and failed
before building; reran from project root. Shell simctl initially used the CLT
selection; all simulator work used explicit Xcode DEVELOPER_DIR.

## Remaining rollout / physical checks

Fresh production authorization for this new candidate is required before plugin
activation/TestFlight upload. Public store release remains out of scope. Physical
phone swipe/rename/delete and two-phone isolation await that rollout. Default
stock speech path correction from the earlier rename remains pending live proof
after deferred gateway reload; do not claim explicit Heart validates default TTS.
