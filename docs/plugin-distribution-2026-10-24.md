# Plugin 0.4.2 installation and public-download candidate

Jason requested upgrading the shared gateway and making the plugin available to
other users. Prepared a source-only official-site download, not npm publication
or public visibility of the private app repository. Production activation/public
site deployment still require current authorization under workspace AGENTS.md.

## Exact candidate

- Package: `site/downloads/0.4.2/voxkin-openclaw-local-voice-0.4.2.tgz`.
- SHA-256: `93ef3b4426d32c20fe7ca64dddf41c4a081e8b40b50299cb284ef522f055a56d`.
- 23 source/documentation files. No credentials, state, node_modules, models,
  native binaries, Python wheels, app binaries or private app repository history.
- This supersedes the earlier **unpublished local**0.4.2 security tarball: runtime
  code unchanged, standalone installation/readme/notices added/updated, and peer
  host metadata includes the tested2026.10.6 as well as2026.9.7.
- Download page and links added to landing/support. Versioned file/checksum/guide
  routes under `/downloads/0.4.2/`. No public deployment performed yet.
- `private:true` intentionally remains: direct tarball install works; accidental
  npm registry publication remains prevented. No npm scope ownership assumed.

## Qualification

- Supported OpenClaw2026.10.6 archive install passed in an isolated state/config;
  explicit source confirmation and capability consent used after source review.
  `--no-enable` preserved isolation. Main gateway files/config untouched.
- Installed package actual English/Spanish synthesis passed with existing verified
  native runtime (24kHz WAV), through the new isolated worker environment.
- Separate loopback gateway loaded the installed archive and initialized restricted
  access; GET405 and unauthenticated POST401 verified. No model requests, main
  state, live enrollments or externally reachable test listener used.
- Updated opt-in host-load test fixture from old agents.list to agents.entries for
  current OpenClaw. Initial failure was fixture config schema, not plugin startup.
- Final archive matches all23 source files; installed executable/documentation files
  match archive (installer-owned package metadata may be normalized).
- Six local HTTP routes200 and exact source-byte parity; all site-local links
  resolve; archive checksum verified; private-pattern scan found no private host
  paths, account identities, private keys or known household agent names.
- Local browser navigation was blocked by browser policy. No alternate browser
  route was used; visual mobile/desktop rendering remains unverified. Existing
  stylesheet reused; no JavaScript or new third-party assets added.
- Independent read-only lane attempted twice, both failed before work with model
  ownership403. No independent-review claim for this packaging pass. Lead performed
  checks directly; earlier security implementation review remains separate.
- Linux/Windows worker-isolation changes not requalified here. Package guide states
  current native macOS evidence and experimental/historical other-platform scope.

## Shared-gateway preflight and rollback

Installed plugin remains0.4.1. Existing runtime,31-voice allowlist and restricted
agent settings must be preserved. Read-only installed/source runtime diff contains
expected Windows interpreter selection, UTF-8/isolated-mode and environment
hardening changes—not an unrelated local customization to discard.

Protected local evidence: `.tmp/plugin-distribution-2026-10-24/`. Backups include
installed directory, source-only rollback0.4.1 archive, config and SQLite-consistent
registry snapshot. These are **private operational backups**, never publish them.
Config/registry backups are recovery evidence; do not blindly restore the entire
shared database/config later and overwrite unrelated updates. Access/history state
is not migrated or replaced by this code update. Never remove a live writer lock.

After authorization, refresh relevant backups/config fingerprint immediately before
activation and verify exact candidate SHA. Install via supported host workflow:

```sh
openclaw plugins install /absolute/path/to/voxkin-openclaw-local-voice-0.4.2.tgz --no-enable --force --accept-capabilities
openclaw plugins reload voxkin-local --wait --json
```

These flags reflect explicit reviewed local-source replacement/capability consent;
they do not override a blocked install policy. Stop/report any actual policy block.
`--no-enable` does not disable an already enabled plugin. Do not treat installing
into its live directory as harmless staging. If cleanup cannot drain, preserve state
and use a separately coordinated controlled restart, not a process kill or lock delete.

Verify installed executable hashes/version and existing config preservation; prove
live gateway catalog, English/Spanish WAV and restricted route authorization. Check
normal gateway health and existing channel availability. Keep default TTS unchanged.
No new guest enrollment, invitation exposure or model turns are required. Roll back
through the supported installer with the captured prior code package if necessary;
keep current access/history data rather than restoring old user state.

## Public site deployment

After authorization, use `git archive` of the immutable candidate's `site/` only;
exclude `.tmp`, private backups and the rest of the repository. Stage a new directory
under `/var/www/voxkin/releases/<commit>/`, verify archive checksum and preserve the
current pointer. Atomically switch only Voxkin's current static-site pointer. The
existing Caddy root serves added static paths; no DNS, global edge changes, report
receiver restart, gateway replacement or Caddy config change is needed.

Verify public HTTPS download, SHA256SUMS, INSTALL.md, landing/support, unchanged
privacy/platform assets, headers and host/edge health. Download the public tarball
and byte-compare with the approved candidate before announcing availability. Restore
the prior static pointer on failure. No GitHub visibility change or npm publication.

App-store builds remain separate, uploaded but unsubmitted. Owner QR requires
updated apps; site/guide explicitly provide manual in-app setup for users still on
the prior app. Existing saved connections and guest invitations stay compatible.
