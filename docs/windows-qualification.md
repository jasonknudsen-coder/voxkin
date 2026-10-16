# Native Windows qualification — patch-free plugin adapter


## Current candidate (supersedes the historical patched baseline below)

The plugin now selects `restricted/windows-dispatch.js` only on native Windows.
It calls public `agentCommandFromIngress`, caps tools, and verifies returned
text against an exact-run/agent/session terminal `llm_output` message before
publishing. Requires the host's explicit conversation-hook permission. Missing
or ambiguous final evidence fails closed; reasoning and commentary are not
selected. No global event is used as a phone broadcast.

- Local Node suite: 72 passed, 2 skipped (74 total).
- Package dry-run confirms the adapter is included.
- Stock worker restored and hash-checked against the original before tests.
- Hardened adapter EN/ES model, WAV (69,292 / 70,604 bytes), history CRUD,
  pending-device denial, cross-device isolation and revocation: passed.
- Active revocation, cross-device send/poll denial, overlapping-turn rejection,
  and multi-turn random-code recall: passed.
- Restart random-code recall and immediate cancellation/no-final on the hardened
  adapter: passed.
- Live slash-command rejection and a prompted filesystem-tool write probe passed:
  the requested test file was not created. Tool allow-list forwarding and scoped
  blocker are additionally covered by source tracing/unit tests; a refusal probe
  alone is not proof of every tool backend.
- Existing macOS/Linux channel dispatch is retained; unit coverage exercises it.
  No live family/Ubuntu gateway changes were made.
- Maintainer reported successful physical-phone testing of the patch-free adapter
  at 16:31 MDT. The test installation is now parked: files retained, task and SSH
  disabled, gateway stopped and Serve mapping removed. Resume instructions below.

Scope: tested native Windows 11 AMD64/OpenClaw 2026.9.7 and the configured review
model. This is not a claim of universal provider/harness/Windows ARM support.
Independent reviewer was unavailable (403); no independent review is claimed.

2026-10-15: Windows 11 Pro AMD64, native SSH/PowerShell, no WSL.

## Historical baseline results (before plugin-only adapter)

- Node 24.20.0, Python 3.13.15, OpenClaw 2026.9.7 CLI and gateway health.
- Experimental Windows installer with pinned native Python wheels and verified
  model hashes. A runtime directory containing spaces is supported.
- Real Heart English and Dora accented Spanish setup synthesis; Node consumes
  the Python receipt and returns valid WAV payloads.
- Native NTFS private access/history restart; Everyone read grants and junction
  paths rejected. POSIX permission checks remain intact.
- Native JS CLI entry used for invitation/approval, avoiding `.cmd` shell launch.
- Direct gateway model turn returns requested text using an isolated test credential.
- Tailnet HTTPS rejects unauthenticated restricted requests with401.
- With the test-only host patch: two approved devices, pending-device denial,
  cross-device history isolation, real EN/ES model replies and WAV synthesis,
  history rename/delete and test-device revocation. First full run: English
  69,292 bytes, Spanish70,604 bytes.
- Local plugin suite before final cleanup:63 passed,2 skipped.

## Historical channel-path blocker and scope

Unmodified OpenClaw2026.9.7 restricted inbound dispatch failed with
`WorkerTaskError: DataCloneError` at `input.request.env` during worker postMessage.
Direct agent turns worked. Plugin config/context snapshots did not fix it and
were removed. A reversible Windows-only host patch copies request environment
properties into a plain object before worker transfer. See
`ops/windows-qualification/openclaw-2026.9.7-worker-env.patch`.

**Patched baseline success alone did not qualify stock Windows.** The later
plugin-only adapter bypasses this channel-path bug; see current results below. Physical
iPhone round trip, cancellation/deadline, network-off synthesis and non-ASCII
runtime directory coverage remain separately unproven.

## Operational findings

Run voice setup as the actual non-elevated gateway user. Elevated SSH created
voice files whose owner-based ACL granted only administrator access to the
normal-user Scheduled Task. Assign the runtime to the non-elevated gateway user
with an explicit user-scoped full-control inheritance grant; no broad-user
access was added.

The manual-start Scheduled Task has no reboot trigger. Its wrapper has a
four-hour execution limit, but stopping the task did not stop the detached Node
listener in testing: verify the exact listener process before teardown. Gateway
restarts preserved state and moved stale writer locks only after confirming the
recorded writer PID was stopped. Tailscale Serve8443 is tailnet-only, not Funnel.

Model credentials stay in protected remote test storage, never in source.
At teardown, remove test credentials from the machine and revoke dedicated
test credentials once no longer needed.
No family gateway changes, Ubuntu changes, public deployment or new iOS build.

Final restart proof passed with the original plugin dispatch logic restored and
only the documented host environment-snapshot patch retained: EN69,292-byte
and ES70,604-byte WAV responses, model turns, history actions and cross-device
isolation all passed again. Temporary diagnostic logging was removed. Final
local suite63 passed/2 skipped. No independent implementation review is claimed:
the retained reviewer follow-up failed with a403 runtime error.

## Physical iPhone acceptance

At15:43 on2026-10-15 Maintainer reported English and Spanish both working and history
working on the Windows test connection. This establishes user-reported physical
phone acceptance of those flows on the patched host. Background/reopen,
cancellation and offline checks are not inferred from this report.

## Plugin-only workaround investigation

The supported lower-level
`api.runtime.channel.reply.dispatchReplyWithBufferedBlockDispatcher` was tested
on the native Windows gateway after restoring the original OpenClaw worker module
and verifying its hash against the saved original. Restricted dispatch still
failed (the E2E turn reached `error`, not `final`; recent host logs contained
`DataCloneError`). The alternate dispatch implementation was discarded and the
previous working plugin plus documented host patch restored.

Source tracing identifies `cloneEnvWithPlatformSemantics` creating a Proxy on
Windows for case-insensitive environment lookup. Session reader code passes that
environment to the worker boundary; a Proxy cannot be structured-cloned.
Snapshotting plugin configuration/context cannot repair an object subsequently
created inside OpenClaw. The tested lower-level SDK route does not avoid it.

At this investigation stage no safe plugin-only solution had been demonstrated.
Do not substitute owner access, disable isolation, or conceal a host monkeypatch
inside the plugin. At that stage an upstream fix was the proposed path; the
later public agent-ingress adapter superseded that recommendation.

After rollback of the experiment, the documented patched baseline passed the
complete native E2E probe again: English model/audio (69,292 bytes), Spanish
model/audio (70,604 bytes), history CRUD, device isolation and revocation.

## Later finding: public agent-ingress alternative

Further investigation found a different public SDK path:
`agentCommandFromIngress` from `openclaw/plugin-sdk/agent-runtime`. On the
hash-verified unpatched Windows worker module it passed EN/ES model/audio,
history CRUD, pending denial, cross-device isolation, revocation, two-turn
random-code recall, recall after restart, and immediate-abort/no-final probing.
This supersedes the earlier conclusion that no plugin-only candidate had been
demonstrated. It is a feasibility proof, **not a qualified release adapter**:
JSON projection loses reasoning/commentary flags and final-only handling,
effective tool policy, harness coverage and lifecycle regressions need hardening.
See [the retained spike](../ops/windows-qualification/ingress-spike/README.md).

## Physical-phone acceptance of the patch-free adapter

2026-10-15 16:31 MDT: after being asked to check English, Spanish, reopening
history and reconnecting on the existing Windows profile, Maintainer reported
“Everything seems to work!” Record this as overall user-reported acceptance of
the patch-free adapter, not separate instrumented proof of each action or of
untested providers/harnesses. No teardown was requested.

## Parked for future testing — 2026-10-15

At Maintainer's request, retained the installation, models, configuration and test
state on disk. Disabled the manual-start `Voxkin-Native-Windows-Test` scheduled
task, stopped its exact gateway listener and matching test Python workers,
removed the Tailscale Serve8443 mapping, and archived the stale writer lock only
after confirming its writer had stopped. Verified no gateway listener and no
Serve configuration. Set Windows `sshd` startup to Disabled and stopped it;
service query confirmed Stopped/Disabled. SSH keys and OpenSSH remain installed.

To resume locally in elevated PowerShell, enable SSH only when needed:

```powershell
Set-Service sshd -StartupType Manual
Start-Service sshd
Enable-ScheduledTask -TaskName 'Voxkin-Native-Windows-Test'
Start-ScheduledTask -TaskName 'Voxkin-Native-Windows-Test'
& 'C:\Program Files\Tailscale\tailscale.exe' serve --bg --https=8443 http://127.0.0.1:18789
```

Check gateway readiness before phone testing. The task still has no startup
trigger. Re-disable SSH and the task when finished. Do not regenerate config or
credentials just to restart the retained installation.
