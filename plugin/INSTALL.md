# Install Voxkin Local 0.4.3

This is the gateway-side plugin, not the iOS/Android app. It provides optional
local speech and separately enrolled, restricted phones. It does not create a
hosted assistant, open a firewall, change your default speech provider, or install
model-provider credentials. Keep your existing OpenClaw configuration and data.

## Requirements and tested scope

- An existing OpenClaw gateway and its supported Node runtime. The source SDK
  contract passes on OpenClaw 2026.10.6; earlier integration used 2026.9.7.
- Python 3.13, with venv support, for local speech. Xcode is not required.
- This release's actual speech smoke tests cover macOS Apple Silicon. Earlier
  0.4.1-era tests covered Ubuntu/Debian x86_64 and Windows11 x64, but the new
  worker environment isolation has not yet been requalified on those platforms.
  Linux/Windows setup is opt-in experimental; other architectures are unqualified.
- Network access for explicit dependency/model setup; offline synthesis afterward.
  Two model files and Python dependencies are downloaded, not bundled here.
- A trusted HTTPS/WSS gateway reachable from the phone. VPN/tailnet configuration
  is separate. Never expose an unprotected operator interface to make setup easy.

## Guided setup (recommended on OpenClaw 2026.10.6)

After installing the plugin through OpenClaw, run on the gateway computer:

```sh
openclaw voxkin setup
```

The wizard finds its installed files, checks Python 3.13, asks before downloading
speech dependencies/model/voices, shows progress, verifies checksums, tests English
and Spanish, fills only missing speech settings, and attempts plugin reload. It
preserves existing runtime paths, voice allowlists, guest access and default TTS.
If activation fails, it reports that separately: downloaded voices do not mean a
ready gateway. Run from your own terminal, not an agent turn holding the plugin.

If installed disabled and the host does not expose the `voxkin` command yet,
explicitly enable it with `openclaw plugins enable voxkin-local`, then rerun setup.
The standalone `node /installed/plugin/path/setup.mjs setup --enable` is also
available; it asks before enabling unless `--enable` is provided.

```sh
openclaw voxkin doctor                 # read-only checks
openclaw voxkin repair                 # asks before downloads/repair
openclaw voxkin setup --download       # explicit download consent
openclaw voxkin setup --pair --gateway wss://your-gateway.example
```

Pairing is optional, only prints in an interactive terminal after setup succeeds,
and asks before displaying the reusable owner secret. Keep the terminal private.
No credentials are sent to a QR service. No automatic firewall or TLS changes.

Repair reinstalls Python dependencies and replaces only missing/corrupt model files;
valid model downloads are reused. A runtime lock prevents overlapping setup/repair.
If an interrupted operation leaves `.setup.lock`, first confirm all setup and child
processes have stopped; inspect the lock locally before removing it. Never delete
it merely to force a second installer through. Doctor reports the lock without
changing it.

Python 3.13 must already be installed; the wizard explains missing prerequisites,
not silently installing system software. Linux/Windows still require their
experimental flags below. For other supported host versions, use the manual
steps in this guide. Catalog publication/install availability is tracked in the
repository; do not assume a release under review can be installed yet.

## 1. Download, verify, and review

Download the versioned `.tgz` and `SHA256SUMS` from
https://voxkin.builderofthings.studio/downloads/. Do not pipe downloads into a shell.
On macOS, in the directory containing those two files:

```sh
shasum -a 256 -c SHA256SUMS
```

On Linux use `sha256sum -c SHA256SUMS`. On Windows PowerShell use
`Get-FileHash .\voxkin-openclaw-local-voice-0.4.3.tgz -Algorithm SHA256` and compare
with the checksum file. A checksum detects corruption; it is not an independent
publisher signature. Use the official HTTPS download origin.

Inspect the archive and extract to a new directory for source review:

```sh
tar -tzf voxkin-openclaw-local-voice-0.4.3.tgz
mkdir voxkin-review-0.4.3
tar -xzf voxkin-openclaw-local-voice-0.4.3.tgz -C voxkin-review-0.4.3
```

Read `package/README.md`, `package/THIRD_PARTY_NOTICES.md` and the source.
It launches a local Python worker, registers gateway RPCs and optionally a
restricted HTTP route. Download includes no personal credentials, models, native
binaries or node_modules. Dependencies are installed by the host tooling.

## 2. Back up and install through OpenClaw

Run as the gateway service user, not root. For an upgrade, first save a protected
backup of the existing plugin directory, OpenClaw config/registry and Voxkin access
state according to your gateway's backup procedure. Do not put backups in the web
root. Preserve the existing runtime directory, agent allowlist and access state.
Never run two gateways against the same Voxkin state directory.

For a first installation:

```sh
openclaw plugins install ./voxkin-openclaw-local-voice-0.4.3.tgz --no-enable
```

Local archives are outside registry trust metadata. If OpenClaw requires source
confirmation, review the artifact and rerun with `--force`. For an existing
installation, `--force` also authorizes replacing that plugin. If it requests
capability consent, inspect the declared capabilities/source and explicitly add
`--accept-capabilities` only after accepting them. Do not bypass a blocked install
policy or use deprecated unsafe-install flags.

**`--no-enable` preserves existing enablement.** It does not disable an already
active plugin and is not a guarantee that changing its files cannot affect a live
gateway. Coordinate an upgrade's install/reload with other users. Never manually
copy files into a live plugin directory as a substitute for the supported installer.

## 3. Prepare local speech explicitly

Find the installed root with `openclaw plugins inspect voxkin-local --json`.
The usual default is `~/.openclaw/extensions/voxkin-local`, but custom profiles can
place it elsewhere. Substitute that exact path for `/path/to/voxkin-local` below.

macOS:

```sh
python3.13 /path/to/voxkin-local/setup.py --install --smoke-test
```

Experimental Linux x86_64 (requires CA certificates, Python3.13 venv and libsndfile):

```sh
python3.13 /path/to/voxkin-local/setup.py --experimental-linux --install --smoke-test
```

Experimental Windows x64, using a real native Python3.13 installation:

```powershell
py -3.13 C:\path\to\voxkin-local\setup.py --experimental-windows --install --smoke-test
```

Default runtime is `~/.local/share/voxkin/kokoro`; pass `--runtime-dir` to use your
existing absolute runtime path. Setup verifies model SHA-256 and synthesizes fixed
English/Spanish samples. Without `--install`, it verifies the existing runtime and
refreshes its readiness receipt; it downloads or installs nothing. Avoid rerunning
setup during active synthesis. Runtime pins are not yet wheel-hash locked.

Hardened Linux: native speech may need an executable service-owned temporary
directory for library loading. Provide a bounded dedicated TMPDIR, not a broadly
writable executable directory; do not weaken general system mount protections.

## 4. Configure, enable, and verify

Merge—not replace—this plugin entry into your OpenClaw configuration:

```json
{
  "plugins": {
    "entries": {
      "voxkin-local": {
        "enabled": true,
        "config": {
          "runtimeDir": "/absolute/path/to/kokoro",
          "voices": ["af_heart", "ef_dora"]
        }
      }
    }
  }
}
```

Keep all other plugin entries. If `plugins.allow` exists, add `voxkin-local` without
removing others. Heart and Dora are English/Spanish starting choices. Omit `voices`
to expose all31 supported installed voices; preserve an existing explicit allowlist
unless intentionally changing it. Validate configuration using the host's normal
workflow, then use `openclaw plugins reload voxkin-local --wait` for an installed,
enabled plugin. A controlled gateway restart is a fallback, not an automatic step
in this guide. Do not delete a live access-store lock to force a reload.

Verify `openclaw plugins inspect voxkin-local --json`, gateway health, and the
catalog/speech functions. Reconnect the phone, choose Local voices, then Preview
English and Spanish. Stock/default TTS remains unchanged.

## 5. Connect the owner phone

Owner mode has gateway operator authority; choosing an agent is not an access
boundary. Use only a trusted gateway. You can enter the gateway address and token
manually inside the app. For a local terminal QR, using the installed plugin:

```sh
node /path/to/voxkin-local/onboard.mjs owner --gateway wss://your-gateway.example --config
```

The helper reads the normal strict-JSON OpenClaw config or an explicitly supplied
config path. Supported secret references resolve locally; no QR web service is
used. For other auth/config forms, use `--token-stdin` with your protected secret
source; never put the token in command arguments or shell history.

**New owner QR codes require iOS1.0.1 / Android0.2.20 or later.** If that update is
not yet available, use manual in-app setup or an existing saved connection.
Scan inside Voxkin Settings, never the system camera. Owner codes contain a
reusable secret, not a guest invitation. Connect explicitly and approve normal
OpenClaw device pairing as needed. Keep the QR private; avoid terminal recordings.

## 6. Optional restricted guest phones

Leave `access` disabled unless needed. Create a separate non-main agent with a
non-sensitive workspace; tools and commands are disabled on the restricted path,
but trusted workspace/bootstrap content, providers and hooks still matter.
Merge this into the plugin's existing `config`, preserving speech settings:

```json
{
  "access": {
    "enabled": true,
    "stateDir": "/absolute/private/voxkin-access",
    "allowedAgents": ["guest-helper"]
  }
}
```

Use a local service-owned0700 state directory; credential files use0600. The host
creates it when absent. Do not share it across replicas. Configure HTTPS forwarding
for the restricted `/voxkin/v1` endpoint deliberately; the plugin does not manage
DNS/TLS/firewalls. Do not expose the owner's entire RPC interface as a shortcut.
Windows restricted dispatch also requires the supported agent-ingress integration
and `plugins.entries.voxkin-local.hooks.allowConversationAccess=true`; do not
enable that plugin-specific conversation-hook permission without reviewing its impact. Windows is not requalified by this release.

After supported activation, on the gateway host:

```sh
node /path/to/voxkin-local/onboard.mjs invite --gateway wss://your-gateway.example --agent guest-helper --label 'Guest phone'
node /path/to/voxkin-local/onboard.mjs list
node /path/to/voxkin-local/onboard.mjs approve --device DEVICE_ID_FROM_LIST
```

Deliver the invitation privately. The guest scans/opens it and explicitly enrolls;
verify the intended pending device before approval. Invitations are short-lived
and single-use. Never share the owner token. Revoke access through the owner's
normal gateway controls when no longer needed. See `access/README.md` and
`restricted/HISTORY.md` for storage, ownership and deletion boundaries.

## Upgrade and rollback

0.4.2 fixes authentication-budget starvation, cross-phone retained-run starvation
and externally dispatched owner-token links. Existing guest credentials and saved
connections remain valid; no forced re-enrollment or state migration is required.
It also isolates the Python worker environment. New owner QR needs the app update;
guest invitations are unchanged. Limits are basic hardening, not hostile
multi-tenant or Internet-scale denial-of-service protection.

If activation fails, stop at the error and preserve logs without credentials.
Restore the backed-up plugin through supported installation/activation, preserving
current access/history data; do not roll back user state blindly. Keep the previous
package/receipt until real phone and gateway checks pass. Stock speech is an
available fallback. Support: jason@builderofthings.studio.
