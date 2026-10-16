# Native Windows setup — experimental qualification path

This is **native Windows, not WSL or Docker**. The Windows-only plugin adapter
uses OpenClaw's public agent-ingress API; **do not patch OpenClaw**. macOS/Linux
keep the existing channel-dispatch path. The candidate has passed automated
English/Spanish model, audio, history and isolation checks on OpenClaw 2026.9.7.
Maintainer reported successful physical-phone testing of this adapter on October 15.
The retained test installation is now stopped with its task and SSH disabled; see
[resume instructions](windows-qualification.md#parked-for-future-testing--2026-10-15).

See [qualification evidence](windows-qualification.md) and the
[test harness](../ops/windows-qualification/README.md).
The normal [gateway setup guide](gateway-setup.md) still applies; this page
contains the Windows differences and the disposable-test procedure.

## 1. Prerequisites and account choice

Tested: Windows 11 Pro x64, Node24.20.0, Python3.13.15, OpenClaw2026.9.7.
Windows ARM is not qualified. Install Tailscale on the PC and iPhone when using
our private-network route. Python/Kokoro belongs on the PC, not the phone.

Use the **same ordinary Windows account** for voice installation and gateway
execution. Use elevation only for operations that require it, such as installing
OpenSSH Server. An elevated SSH session is not equivalent to a normal interactive
user session for file ownership and effective permissions.

Example prerequisites (version availability may change):

```powershell
winget install --id OpenJS.NodeJS.LTS --exact --source winget
winget install --id Python.Python.3.13 --exact --source winget --scope user
```

Open a new PowerShell afterward so PATH refreshes, then verify:

```powershell
node --version
npm.cmd --version
py -3.13 --version
npm.cmd install -g openclaw@2026.9.7
openclaw.cmd --version
openclaw.cmd gateway --help
```

Use `npm.cmd`/`openclaw.cmd` from PowerShell if execution policy blocks the `.ps1`
shims; do not globally weaken execution policy. Our npm version reported withheld
package installation scripts. A successful install/version command alone does
not prove native dependencies: inspect warnings and verify actual gateway/model
and plugin operations. Do not blindly enable every package script.

### Optional remote administration

SSH is **not required for Voxkin**. If using SSH for installation, check/install
OpenSSH Server in elevated PowerShell:

```powershell
Get-WindowsCapability -Online -Name OpenSSH.Server*
Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0
```

Then configure key-only access for the intended account, restrict the listener
and firewall to the intended tailnet/admin device, and verify the host key before
connecting. Administrator-group accounts can use
`C:\ProgramData\ssh\administrators_authorized_keys`; follow Windows OpenSSH ACL
requirements rather than assuming the user's ordinary `authorized_keys` is used.
Do not open router ports, enable password login, or turn a Public network profile
into Private just to make this test work. Keep SSH service startup intentional.

## 2. Create an isolated test environment

Do not overwrite an existing gateway configuration. For a disposable test, use
an explicit private root and set these variables in **both** the gateway launcher
and every administration shell:

```powershell
$testRoot = Join-Path $env:USERPROFILE 'voxkin-native-test'
$env:OPENCLAW_STATE_DIR = Join-Path $testRoot 'state'
$env:OPENCLAW_CONFIG_PATH = Join-Path $env:OPENCLAW_STATE_DIR 'openclaw.json'
```

Create private state, workspace and secrets directories before writing credentials.
Our NTFS guard accepts only the running user, SYSTEM and Administrators as owners
and allow-ACE principals, and rejects reparse points along the state path.
Broad Users/Everyone grants are rejected even if Unix-looking mode bits look safe.

For a **new test directory**, this is the ACL pattern used (inspect existing ACLs
before modifying any existing directory):

```powershell
New-Item -ItemType Directory -Force -Path $testRoot | Out-Null
$userSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
icacls.exe $testRoot /inheritance:r /grant:r "*${userSid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F'
if ($LASTEXITCODE -ne 0) { throw 'Private directory ACL setup failed' }
```

This removes inherited ACEs, not arbitrary pre-existing explicit grants. Inspect
`Get-Acl` and prove the guard accepts the resulting directory. Do not skip the
Windows ACL checks or substitute `chmod` for NTFS permissions.

Configure a dedicated non-main agent with a non-sensitive workspace, tools denied,
cron disabled and no family channels. Keep the gateway on loopback with token
authentication. Enable the plugin's restricted access only for that dedicated
agent. See [restricted access](restricted-access.md).

Keep model API credentials separate from the gateway token and phone invitation.
Our test loaded the model key from a protected file into the launcher process's
environment and referenced it with a provider SecretRef; the key was not embedded
in source, command arguments or QR codes. Set the same required environment for
CLI commands that resolve the configuration. Do not print the file or environment.
Test a direct agent reply before adding the phone path.

## 3. Install local voices as the gateway user

Obtain the current private plugin source; there is no public npm install name.
Review the [notices](../plugin/THIRD_PARTY_NOTICES.md), then from a **non-elevated**
PowerShell running as the gateway account:

```powershell
$pluginSource = 'C:\path\to\voxkin\plugin' # replace with your checkout
$voiceRoot = Join-Path $testRoot 'Voice Runtime'
py -3.13 "$pluginSource\setup.py" --experimental-windows --install --smoke-test --runtime-dir "$voiceRoot"
```

The Windows flag is explicit. Setup uses `.venv\Scripts\python.exe`, not the
Unix `.venv/bin/python`. The worker runs with UTF-8 enabled so Spanish `¿`, `ñ`
and accented characters survive Node-to-Python pipes. Paths containing spaces
passed. Non-ASCII directory names have not yet been qualified.

Setup verifies pinned model hashes, imports native dependencies, synthesizes real
Heart/Dora samples and writes `verified.json`. Keep the runtime and receipt
together. Do not hand-edit receipts or weaken timestamp/hash validation.

Install/enable the plugin through the [normal host workflow](gateway-setup.md#2-optional-install-local-voices-on-the-gateway-host), preserving existing allowlist entries and reviewing capability consent.
Set `plugins.entries.voxkin-local.config.runtimeDir` to the exact absolute
`$voiceRoot`, and optionally restrict `voices` to `af_heart` and `ef_dora`.

For a manually staged private test package, the plugin must be able to resolve
`openclaw/plugin-sdk/...` from the installed host. Our fixture used a junction to
the global package inside its `node_modules`; this is a test-harness detail, not a
second OpenClaw installation or an instruction to copy the global package.

### Voices work in SSH but catalog says setup-required

This happened in our test: elevated installation produced administrator-owned
voice files whose owner-based ACLs excluded the normal-user gateway. Inspect the
**voice runtime directory and descendants**, not just its parent:

```powershell
(Get-Acl $voiceRoot).Owner
(Get-Acl $voiceRoot).Access
(Get-Acl "$voiceRoot\.venv\Scripts\python.exe").Access
```

Repair only the intended runtime: assign its ownership to the actual gateway user
and grant that user explicit inherited access. We needed **both** ownership and
an explicit user grant; changing owner alone did not fix access. Do not grant
Everyone, run the gateway as administrator to hide the problem, or apply a
recursive ownership change to unrelated directories/junction targets.
Recheck the gateway catalog and actual speech under the running gateway identity.

## 4. Windows-only ingress and final-reply permission

The normal channel pipeline hits a Windows environment-Proxy `DataCloneError`
in OpenClaw 2026.9.7. Voxkin instead uses the public `agentCommandFromIngress`
API on Windows. No patched modules, private dist imports or owner-RPC proxy are
needed. Retain the dedicated non-sensitive agent and restricted device access.

Enable the host's explicit conversation-hook permission **for this plugin**:

```powershell
openclaw.cmd config set plugins.entries.voxkin-local.hooks.allowConversationAccess true
```

Run that command under the same isolated config/state environment as the gateway,
then restart that gateway. This permission permits the plugin to receive raw
conversation hooks. Its handler ignores all runs except an active exact
run/agent/session match; it captures terminal assistant text only and checks that
it corresponds exactly to the returned command reply. It does not store raw
reasoning or forward it to the phone. Missing permission, missing terminal
metadata, transformed/prefixed output or ambiguous replies fail closed.

The adapter supplies an empty tool allow-list, disables the message tool and
model overrides, and installs a matching-run tool-call blocker. Public ingress
sanitizes owner authority. Slash commands and client-supplied routes remain
unavailable. Verbose output is off. Providers/harnesses not producing the required
terminal hook shape must be qualified separately; do not weaken filtering to
make an unsupported backend pass.

If upgrading an earlier disposable test installation, stop its gateway and
restore the saved original host module (verify its hash), or reinstall the exact
stock package. The historical host patch is retained only as investigation
evidence. It is not a setup step.

## 5. Launch and prove the gateway

Set the isolated config/state variables and model credential environment in the
launcher. Run the gateway as the same normal user:

```powershell
openclaw.cmd gateway run --bind loopback --port 18789
```

Our remote test used a manual-start Scheduled Task with an interactive user token,
no reboot trigger and a four-hour wrapper execution limit. This is not permanent
service setup. A child started directly from SSH did not survive session exit.
**Stopping the Scheduled Task wrapper did not reliably stop the detached Node
listener**, so do not treat the time limit as guaranteed gateway expiry.

Check `openclaw.cmd gateway health`, then verify catalog, a direct model reply,
and a full restricted model-to-audio turn. A listening port alone is insufficient.
For programmatic onboarding, use `node onboard.mjs`; its Windows implementation
resolves the npm-installed JS CLI and launches Node directly, without a shell.
Do not use `execFile('openclaw')` expecting Windows to execute a `.cmd` shim.

## 6. Private phone reachability and approval

After confirming no existing Serve mapping will be replaced, our test used:

```powershell
tailscale.exe serve status
tailscale.exe serve --bg --https=8443 http://127.0.0.1:18789
```

Use your PC's actual tailnet DNS hostname in the QR, for example
`wss://YOUR-PC.YOUR-TAILNET.ts.net:8443`. This is **Serve, not Funnel**.
Both phone and PC need their tailnet connection and appropriate access rules.
Keep certificate validation on; do not substitute a bare `ws://` address.
Confirm an unauthenticated POST to `/voxkin/v1` is denied before sharing an invite.

Generate a restricted invitation privately with the onboarding helper (replace
the hostname and agent ID with your configured values):

```powershell
node "$pluginSource\onboard.mjs" invite --gateway 'wss://YOUR-PC.YOUR-TAILNET.ts.net:8443' --agent YOUR_TEST_AGENT --label 'My phone Windows test' --expect-url 'ws://127.0.0.1:18789'
```

Scan the privately displayed QR in Voxkin, then explicitly connect/join. Invitations are single-use and short-lived
(default ten minutes). The phone must be approved after redemption:

```powershell
openclaw.cmd gateway call voxkin.access.list --json
node "$pluginSource\onboard.mjs" approve --device MATCHING_DEVICE_ID
```

Verify the intended device before approval. This is not the owner-device
`openclaw devices approve` flow. Do not regenerate invitations for an already
redeemed, merely pending device. Keep existing gateway profiles saved when testing
another host; avoid overwriting a working connection unnecessarily.

Acceptance: English and Spanish replies with audible speech; create/reopen,
rename/delete and isolate history; background/reopen; reconnect after gateway
restart. Maintainer subsequently reported overall success on the patch-free adapter;
this is user-reported acceptance, not individual instrumentation of every action.
Automated unpatched results are recorded separately in the qualification report.

## 7. Restart and teardown nuances

- Inspect the test listener's PID and executable before stopping it. Never kill
  every Node process or stop another gateway on the PC.
- AccessStore's writer lock intentionally fails closed after an unclean stop.
  Only move aside a stale `writer.lock` after verifying its recorded PID has
  stopped and no writer remains; do not routinely delete locks to force startup.
- Stop the test's Serve mapping (`tailscale.exe serve --https=8443 off`) without
  resetting unrelated mappings. Remove only the temporary test task/profile.
- Remove junction entries without traversing targets before recursive cleanup:
  our fixture has a host-package junction and a deliberate security-test junction.
- Restore the backed-up host module if retaining OpenClaw; otherwise remove only
  the authorized disposable installation. Do not leave undocumented host patches.
- Remove the Windows model-key copy. If that key is shared with another active
  test host, do not revoke it until all users of it are retired.
- Retain sanitized qualification results, not credentials, invite links or QR images.
