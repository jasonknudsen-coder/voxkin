# Voxkin Local — private macOS prototype

Optional native OpenClaw plugin. No core fork, HTTP service, hidden language marker,
or change to stock `tts.speak`. Adds explicit RPCs on the existing authenticated
WebSocket. Experimental SDK: currently targets installed OpenClaw 2026.9.7 only.
No npm/ClawHub package has been published; no public release authorized.

Native Windows testers: use the [Windows setup guide](../docs/windows-setup.md).
The tested Windows phone flow currently needs a documented host workaround; it
is not stock-host release qualification.

## Setup on the gateway host

Review THIRD_PARTY_NOTICES.md. Full Xcode is only needed for iPhone development,
not this plugin. Supply Node supported by OpenClaw and Python 3.13 on the host.

```
python3.13 /absolute/path/to/plugin/setup.py --install
openclaw plugins install /absolute/path/to/plugin --no-enable
```

Setup downloads two pinned, SHA-256-verified artifacts from upstream and creates
an isolated venv. It is explicit and idempotent; no network downloads at gateway
startup. Dependencies are pinned versions, not yet wheel-hash locked. No sudo.
Default runtime is `~/.local/share/voxkin/kokoro`; `--runtime-dir` overrides it.
Set plugin config `runtimeDir` to that absolute path and `voices` to the desired
allowlist. With no `voices` setting, the catalog exposes all verified installed
voices supported by this plugin: currently 28 English (20 US, 8 UK) and 3 Spanish
(Dora, Alex, Santa). An explicit `voices` list restricts that inventory. Heart
(`af_heart`) and Dora (`ef_dora`) remain recommended starting choices; voice quality
beyond those two has not been audition-qualified. Normal OpenClaw plugin trust/enable/reload rules apply. For an unpublished local
checkout, the installer may require `--force` after source review and
`--accept-capabilities` after reviewing the requested permissions. These are
explicit operator decisions, not flags to hide in an unattended installer.
The plugin runs a local child process and registers two gateway RPCs.

After installation, configure `plugins.entries.voxkin-local.config` in your
OpenClaw configuration, for example:

```json
{"runtimeDir":"/absolute/path/to/kokoro","voices":["af_heart","ef_dora"]}
```

This example deliberately exposes only Heart and Dora. Omit `voices` to expose
all supported installed voices, or copy the desired IDs from setup output.

If `plugins.allow` exists, add `voxkin-local` without replacing other entries;
`--no-enable` intentionally preserves that list. Then enable `plugins.entries.voxkin-local.enabled` and activate using the host’s
normal plugin reload or gateway restart workflow. Coordinate that activation with
other users of the gateway. Installing with `--no-enable` alone does not activate it.
Reconnect the app, allow gateway speech, then choose Settings → Speech source →
Local voices → Language → Voice. Preview sends only a fixed sample, not a chat turn.
The device remembers the selection in its endpoint-bound, non-syncing Keychain.

`python3.13 setup.py` without --install performs verification and writes a local readiness receipt; it installs/downloads nothing. Test actual
speech from the app Preview action after the plugin is enabled and app reconnects.
Do not remove the existing default TTS provider: stock clients continue using it.

## Experimental Linux setup and container checks

The existing Debian/glibc Linux x86_64 Docker deployment passed real English and
Spanish voice probes with Node 24 and Python 3.13. The shared installer now exposes
an **experimental** Linux path; the revised installer passed fresh Debian Bookworm and Ubuntu24.04 x86_64
container qualification on October15 (see `../docs/linux-qualification.md`).
This covers installer/package/native speech, not new host-RPC or phone evidence. Alpine/musl, ARM Linux and Windows are not qualified.

```
python3.13 /absolute/path/to/plugin/setup.py --experimental-linux --install --smoke-test
```

Use the same service account, environment and `TMPDIR` as the gateway. Supply
Python 3.13 with venv support, CA certificates and libsndfile (Debian package
`libsndfile1`). No automatic sudo, package-manager changes or gateway activation.
The smoke test synthesizes fixed Heart English and Dora Spanish samples, checks
24 kHz WAV output and deletes them. It requires no model API key. A failed test
writes no new receipt; an existing receipt is not revoked.

Phonemizer loads a temporary native library: a `noexec` temporary directory can
pass imports but fail synthesis. On hardened containers keep general `/tmp`
noexec and explicitly provision a private, bounded executable tmpfs for voice
(e.g. 64 MiB, service-owned mode 0700, nosuid,nodev), setting gateway `TMPDIR`
there. The installer never weakens mount permissions automatically.

For container builds add `--container-timestamps` to normalize verified model
mtimes before receipt creation. This prevents Docker layer timestamp rounding
from invalidating readiness; strict runtime metadata checks remain unchanged.
Setup requires a writable runtime directory to issue its receipt. For a final
read-only container, run the existing `ops/review-gateway/voice-probe.mjs` under
the final service account and sandbox instead of rerunning setup. A build-time
pass alone does not qualify the runtime sandbox.

Package preparation (`npm pack`, including dry runs) normalizes only manifest-listed
public source files to 0644 so a nonroot service can read root-installed files.
Private state and credentials are not part of this package or this normalization.

## Upgrade from 0.1 to 0.2

Before activating the new plugin, refresh the existing runtime receipt using the
new package's setup script, preserving your actual configured runtime directory:

```
python3.13 /absolute/path/to/new/plugin/setup.py --runtime-dir /absolute/path/to/kokoro
```

Without `--install`, this verifies existing dependencies/models and records the
available voice inventory; no installation or download occurs. Old receipts lack
that inventory and the new plugin reports setup-required until refreshed.
Install the reviewed new package and activate it through the normal host workflow.
Existing explicit voice allowlists are preserved: refreshing the receipt alone
does not expand a Heart/Dora-only catalog. Update that allowlist deliberately or
omit `voices` to use the full verified inventory. Reconnect the app to refresh its
catalog; saved selections remain unchanged. Use an app build supporting en-GB
catalog entries before enabling UK voices.

## Spanish input setup on iPhone

Dora preview tests speech output, not microphone recognition. If Spanish on-device
dictation is unavailable, enable Dictation and add a Spanish keyboard in iPhone
Settings → General → Keyboard. Try dictating Spanish once in Notes with that
keyboard, then return to Voxkin. This sequence resolved the issue on our test
phone; it is not a guarantee of iOS model readiness on every device. No cloud
recognition fallback is enabled.

For the full connection, language selection, troubleshooting, and migration flow,
see the [setup guide](https://github.com/jasonknudsen-coder/voxkin/blob/main/docs/getting-started.md)
in the currently private source repository.

## Contract v1

- `voxkin.catalog({})`, operator.read: `{version:1,provider:"voxkin-local",
  local:true,ready:true,voices:[{id,name,language,locale}],status:"ready"}`.
  Missing setup yields ready:false/empty voices/status:setup-required, no paths.
- `voxkin.speak({text,language,voice})`, operator.write: inline WAV `audioBase64`,
  `mimeType`, `provider`, selected `language`/`voice`. Text1–4000 Unicode codepoints.
- English=en, Spanish=es. Voice IDs must be enabled and match the selected language.
- No shell interpolation, text on stdin only, no transcript logging, no cloud fallback.
- At most2 simultaneous syntheses (supports app one-clip prefetch),45s process bound,
  6,000,000-byte audio bound. Ephemeral private audio directory is removed on
  success/failure. Host-provided request abort signals and plugin service stop kill
  owned workers and await cleanup. A phone Stop immediately discards playback;
  it is not a protocol promise to cancel server synthesis. Without a host abort
  signal, work can continue until completion or the45s bound.
- Readiness uses the setup verification receipt and current model file metadata.
  Setup verifies model SHA-256 and imports; catalog does not rehash large models
  on each request or guarantee every voice can synthesize. Preview is the live test.
- Existing broad operator scopes remain; this plugin does not implement per-user
  agent authorization. Preview is synthesis, not a chat/agent invocation.

Client detects BOTH methods in hello, fetches catalog, and only enables options
actually advertised. It must validate v1 and provider before using this extension.
Selection is client-side per gateway. Another implementation can implement this
contract with its own public voice IDs; app UI should not embed Kokoro voice names.

## Testing

From the source checkout: `npm test` (Node test runner; test files are not in the installed package). Isolated plugin RPC/load and live local synthesis
proofs are separate from mock handler tests. A fresh venv/model install and both voices were tested on this Apple Silicon
Mac (pip could reuse its local wheel cache). That is not a clean-OS or cross-platform
qualification; Intel Mac/Windows support is not yet qualified; Linux evidence and limits are described above. See
[qualification](https://github.com/jasonknudsen-coder/voxkin/blob/main/docs/local-voice-plugin.md). Gateways without the plugin continue
using stock message TTS and its own configured provider/fallback policy.

## Optional restricted phone access

This package also contains an opt-in, same-port restricted channel. It is disabled
by default. See [restricted access](https://github.com/jasonknudsen-coder/voxkin/blob/main/docs/restricted-access.md) for private state
setup, safe agent requirements, owner approval, native-share invitations and current
text/voice-only limitations. Existing voice RPC methods remain unchanged.

## Phone setup QR from the shell (next Voxkin app build)

Run in this plugin directory on the gateway host. For source checkouts install
Node dependencies with `npm ci --ignore-scripts --legacy-peer-deps`. QR encoding
is local using pinned qrcode 1.5.4; no payload reaches a web QR service. Current
TestFlight build 3 handles restricted invitations, but owner setup QR scanning
requires app build 5 or later (build 4 had an owner-code camera filter bug).

### First owner phone

Use a phone-reachable WSS origin. Tailscale on both devices with a configured
**tailnet-only HTTPS/WSS endpoint** is a good private option. These commands do
not configure Tailscale, certificates, public exposure, or gateway policy.

```sh
node onboard.mjs owner --gateway wss://your-gateway.your-tailnet.ts.net --config
```

`--config` explicitly reads `OPENCLAW_CONFIG_PATH` or defaults to
`~/.openclaw/openclaw.json`, using `gateway.auth.token`. Literal tokens and file/env SecretRefs are supported;
SecretRefs are resolved through the installed OpenClaw SDK, preserving its provider
validation and file safety checks.
`--config /path/to/config.json` selects another strict-JSON config. JSON5, exec references and interpolation strings are not resolved; pipe the resolved token from
your existing secret manager to `node onboard.mjs owner --gateway wss://your-host
--token-stdin` instead (on one command line). Never put the token in arguments.

The QR contains the existing **owner/bootstrap token**, not a restricted,
single-use invitation. Display it privately; do not share screenshots or record
the terminal. No plaintext token/URI is printed, but the QR itself is sensitive.
Scan in Voxkin, review, then explicitly Connect. Standard gateway device pairing
and scope approval remain required; the QR does not grant `operator.admin` by
itself. Existing saved target credentials cannot be silently overwritten. Owner
QR generation changes no gateway configuration or permissions.

### First restricted user (no owner phone required)

Enable restricted access with a dedicated allowed agent first. The shell's
configured OpenClaw credentials need approved `operator.admin` access.

```sh
node onboard.mjs list
node onboard.mjs invite --gateway wss://your-gateway.your-tailnet.ts.net --agent guest-agent --label Guest
# After scanning and redeeming the invite:
node onboard.mjs list
node onboard.mjs approve --device DEVICE_ID_FROM_LIST
# Cancel an unused invitation:
node onboard.mjs cancel --invite INVITE_ID_FROM_LIST
```

Review the pending label/agent before approving. Invitations are single-use and
expire (normally ten minutes); creating one does not approve a phone. Calls use
the configured OpenClaw CLI gateway and auth, not the phone URL. It is normal for
the CLI to use loopback while the phone uses WSS/Tailscale; both must identify the
same gateway. Optional `--expect-url ws://127.0.0.1:18789` on restricted commands
verifies the CLI target without replacing authentication. Installation itself
creates no invitations, approvals, or deployment changes.

### Native Windows restricted access

Windows selects the public agent-ingress adapter; macOS/Linux keep channel
dispatch. Enable `plugins.entries.voxkin-local.hooks.allowConversationAccess=true`
for verified final-only output. OpenClaw itself must remain unpatched. See the
repository's `docs/windows-setup.md` for permissions, limitations and qualification.
