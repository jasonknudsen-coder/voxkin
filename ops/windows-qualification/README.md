Current Windows adapter uses public agent ingress with verified terminal output.
Use `docs/windows-setup.md`; the host patch below is historical evidence only.
The unpatched worker must match its original backup before qualification.

# Native Windows private qualification

These probes run inside the disposable `~/voxkin-native-test` directory on
Windows, alongside the extracted plugin at `package/`. They are not installers
or production deployment scripts. Never copy credentials into this directory in
source control. The remote test root must be NTFS-private to the current user,
SYSTEM and Administrators before creating state.

- `security-probe.mjs`: real NTFS ACL rejection, junction rejection and private
  access/history reopen. Creates only its dedicated `security-probe` subdirectory.
- `e2e.mjs`: uses host CLI authentication from the isolated config, creates two
  test devices, requires approval, proves model+speech/history and cross-device
  isolation, then revokes successful test devices. Failed runs may leave test
  invitations/devices; inspect/revoke them through the access API.

The temporary Scheduled Task has no startup trigger and a four-hour runtime
limit. Tailscale Serve 8443 is tailnet-only, not Funnel. Stop Serve and remove the
manual-start test task at teardown. Remove the test credential copy and revoke dedicated credentials when testing ends.

## Historical test-only OpenClaw host patch (do not apply for current adapter)

`openclaw-2026.9.7-worker-env.patch` snapshots an environment object into plain
string-keyed data before worker transfer, only on Windows. Stock host restricted
inbound dispatch failed `DataCloneError` at `input.request.env`, while direct
agent turns passed. The patch preserves request fields and permission checks;
it is a disposable-host compatibility experiment, not a shipped OpenClaw fix.
The remote original is retained as `worker-pool.original.mjs` in the test root.
Do not claim stock native Windows restricted access is qualified by patched
results. The current adapter was requalified after restoring the original host
module; the compatibility patch and diagnostic instrumentation are no longer active.

Teardown detail: `package/node_modules/openclaw` is a junction to the installed
host package, and `security-probe/junction` is a deliberate rejection fixture.
Remove those junction entries without traversing their targets before any
recursive test-directory cleanup. Verify the gateway listener PID is stopped;
Stopping the wrapper Scheduled Task alone was insufficient in this test.
