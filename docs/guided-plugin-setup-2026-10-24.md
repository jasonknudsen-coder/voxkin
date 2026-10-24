# Guided plugin installation and catalog distribution

Scope: Jason requested guided setup, discoverable catalog installation, and progress/repair (items 1–3). No mobile changes or platform qualification expansion.

## Design

- A terminal helper runs beside the installed package, so users do not supply its internal Python script path.
- Downloads remain explicit. Setup checks prerequisites, obtains verified voice assets, preserves configuration and tests speech before claiming readiness.
- Doctor must be read-only; repair is deliberate and repeatable. Gateway activation and local runtime readiness are distinct states.
- Owner QR remains local/private and opt-in; no token in command arguments or public reports. Guest access, default TTS, existing voices and existing runtime directory remain untouched unless specifically changed.

## Catalog evidence

- Installed host docs identify ClawHub as the community-plugin discovery surface. `openclaw plugins install clawhub:<published-name>` is the supported install form; a bare package name resolves npm instead.
- ClawHub CLI 0.23.3 supports `package publish <folder> --dry-run --json` and `--wait` for review completion.
- Initial dry-run rejected the old manifest because `openclaw.build.openclawVersion` was missing. New metadata must state the tested host, not broaden untested compatibility.
- Publisher authentication completed through a private device-flow message; account verified. The `voxkin` publisher namespace was created under that account (not a trusted/official publisher endorsement).
- Jason subsequently authorized public visibility for the existing repository after a privacy scan. Independent bounded scan covered all33 commits/489 reachable blobs,5 screenshot images and metadata, embedded23-file archive; remote had no issues/PRs/workflow runs/releases/artifacts. No publication blocker found. Known token-like values were synthetic fixtures; operational narratives contain no private family data/endpoints/credentials. This was pattern/manual review, not a guarantee of no encoded secret. Existing repository visibility changed and verified PUBLIC.
- Catalog uploads are not immediately installable: review/verification must finish. Do not announce an install command as working until public discovery and installation are verified.

## Delegation checkpoint

Bounded implementation lane owns wizard/setup and related tests/manifest. Lead owns distribution/docs/integration. Independent review will inspect the runnable result. Voxkin is not allowlisted in the workspace mission cockpit, so no cockpit/production approval configuration is changed to manufacture a pilot entry.

## Review and tests

- Lead review added host CLI registration and explicit enable consent for first installs.
- Independent reviewer reproduced a timeout leaving a grandchild alive. Fixed with bounded process-tree cleanup before reporting failure; real TERM-resistant writer regression added. Windows termination path is implemented but not natively requalified.
- Root identity now validates before enable mutation. Modifying Python setup uses an exclusive owned runtime lock; doctor writes no lock. Deliberate repair forces Python dependency reinstall while reusing verified models.
- Existing absent-leaf compare-and-set config behavior preserves user settings and rejects concurrent edits.

## Existing operational follow-up

Prior 0.4.2 website package is public. Shared gateway files are installed but the reload handoff was denied `operator.admin`; previous generation remains active until an authorized admin reload. This installer project must not bypass that permission or report it fixed without proof.

## End-to-end qualification

- Separate loopback gateway with temporary HOME/config/state and synthetic auth. Reused verified model assets read-only, wrote only its own readiness receipt. No real agent turns, guest enrollments, production reload, or default speech changes.
- `openclaw voxkin setup` completed local English/Spanish tests, supported plugin reload and ready catalog. `doctor` passed. Existing voice configuration preserved exactly.
- Actual gateway `voxkin.speak`: English81,036 bytes and Spanish69,996 bytes, both valid24kHz WAV.
- Initial host CLI integration exposed staged-module root differences. Fixed by passing host-provided plugin root to the helper and retaining canonical equality against the inspected installation; standalone path validation remains.
- SIGINT/SIGTERM now forward to the owned process tree; interrupt cannot fall through to a download. Regression tests cover signal-resistant descendants and listener cleanup.
- One full-suite run during concurrent native synthesis hit an existing worker startup deadline (`worker started`,1second). Rerun after native probe is recorded separately; no claim that this was an implementation regression or fixed runtime issue.

- Final Node suite:93 passed,2 opt-in skipped,0 failures; Python setup suite9 passed. Independent final wizard review:16 passed and no remaining blocker found.
- Package0.4.3 source parity:24 files, SHA256 `9eef7f25968987847a7bb185c314177804f6636e21c6dcb5d287f221c5513867`.
