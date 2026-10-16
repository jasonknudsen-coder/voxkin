# History and identifier reset qualification — October 15, 2026

Released to internal TestFlight: iOS0.4.0(10); local gateway plugin0.4.0 activated
on October 15 after explicit production authorization. No public App Store submission. Apple app bundle ID retained
at Maintainer’s explicit choice; all other runtime product identifiers use Voxkin.
Saved credentials are intentionally not migrated; re-pair/re-enroll is required.
Old prototype history is not imported. Retired local checkout alias was moved into
ignored candidate scratch storage; canonical checkout and QA simulator use Voxkin.

## Evidence

- 42 Swift core tests passed (`.tmp/history-core.log`).
- 97 signed simulator app/native tests passed (`.tmp/history-app-final.log`).
- Focused History UI open/read/continue test passed (`.tmp/history-ui.log`).
  Actual screenshot reviewed: History list and selected messages readable, no
  overlap/clipping; fixture data only. `.tmp/history-ui-attachments/`.
- 58 plugin tests passed, including installed SDK dispatch and isolated gateway
  plugin loading, zero skips (`.tmp/history-plugin-host.log`).
- 5 Python installer tests passed (`.tmp/history-installer.log`).
- Signed CarPlay-enabled iOS archive/export succeeded, packaged metadata and
  strict codesign verification passed. `.tmp/history-release-10-final/`.
- Packed plugin allowlist compared byte-for-byte with source. Artifact SHA256
  receipt: `.tmp/history-candidate-receipt.json`.
- `git diff --check` passed. Previous unrelated work preserved.

## Review and failures resolved

Independent implementation lane owned restricted plugin files; independent
read-only QA lane reviewed the integration. Six correctness findings fixed:
late-selection save, failed-read send, restricted-device preference scope,
reply-wrapper title pollution, abort/final persistence race, and late connect
continuation. Added deterministic regressions. Shared archive capacity mitigated
with per-device quota; no silent eviction. Restricted list intentionally remains
newest-created rather than activity-sorted. Overlap duration not measured.
Console orchestration start refused the non-allowlisted project; no bypass.

First Swift run hit old-path module cache collision; fresh scratch build passed.
First simulator invocation selected mismatched architectures; explicit arm64 fixed it.
New readiness gate initially blocked old no-history mock connections; restored
sessions now require supported history, and full regression passes. UI fixture
initially used a different agent than its list entry; correct scoped fixture passes.
One SDK check used package root instead of dist/plugin-sdk; correct path passes.

## Activation plan and remaining physical qualification

Fresh production authorization required for coordinated plugin replacement and
TestFlight rollout. Before promotion, back up current plugin/config/runtime/access
state; prepare Voxkin runtime paths using existing model assets. Disable prior
plugin ID before enabling voxkin-local against approved state, verify full voice
catalog and synthesis plus history endpoints, and preserve rollback. Upload the
verified build10 only; public App Store submission remains separate.

Phone checks: reconnect/re-pair, start chat, close/reopen and resume, open another
conversation, page older history, switch agents, and confirm no automatic audio.
Enroll Guest anew and verify device-scoped history/revocation. Gateway-backed
history remains online-only; deletion/retention UI is not part of this candidate.

## Approved rollout result

Gateway restart completed. Initial probes during restart returned HTTP503; recovered
health, catalog (31 voices), Heart synthesis (24kHz WAV), and bounded owner history
list/read pass. Restricted service ready with only the configured allowed agent;
unauthenticated history returns401. No enrolled restricted devices yet, so live
authenticated restricted-history/physical phone checks remain pending.
Apple build10 is VALID, IN_BETA_TESTING, and verified in the existing internal group.
Beta notes explain QR setup/reset. Receipt: `.tmp/history-promotion/live-receipt.json`.
Old disabled plugin/runtime backup retained for rollback, not compatibility.
