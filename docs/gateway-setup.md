# Gateway setup reference

Host-administrator instructions. For phone setup, start with [Getting started](getting-started.md).

## What goes where

- **iPhone:** Voxkin app, Tailscale when needed to reach your gateway, and Apple
  on-device dictation. No Kokoro/Python installation on the phone.
- **Gateway host:** your existing OpenClaw instance and configured agent. For our
  local English/Spanish voices, install the optional Voxkin Local plugin plus
  its Python/Kokoro runtime. The normal plugin needs no OpenClaw fork or additional
  listening server. Native Windows uses a plugin-owned adapter and explicit
  conversation-hook permission, not a host patch.
- **Other speech providers:** keep Gateway default in the app and configure speech
  through OpenClaw. Our local voice selector requires our plugin; its absence does
  not configure another provider for you.

Qualified local stack: OpenClaw 2026.9.7, macOS Apple Silicon, Python 3.13.
For the experimental Linux x86_64 installer, container receipt handling and live
voice checks, see [plugin setup](../plugin/README.md#experimental-linux-setup-and-container-checks).
For native Windows, see the [experimental Windows setup guide](windows-setup.md):
the patch-free adapter passed automated checks and user-reported phone acceptance
on Windows 11 AMD64/OpenClaw 2026.9.7. Other Windows architectures, harnesses and
host versions require separate qualification. The app still requires the
stock gateway methods, including `tts.speak`, even when using the optional plugin.

The local development checkout is `~/.openclaw/workspace/projects/voxkin`.
Use the canonical `projects/voxkin` checkout for local tooling.

## Private access with Tailscale

**Tailscale is a secure option for reaching your private OpenClaw gateway** without
opening a public router port. Its encrypted tailnet connects authorized devices;
install/sign in on the gateway host and iPhone, and permit the phone to reach the
host using your tailnet access rules. For another person's phone, use an appropriate
invitation/device-sharing policy rather than sharing your Tailscale password.

Use a trusted `wss://` endpoint reachable through the tailnet (for example, an
HTTPS/WSS endpoint provided by Tailscale Serve with a valid certificate). Tailscale
Funnel/public exposure is not required. Do not disable certificate validation or
put a bare `ws://` private IP into the QR as a shortcut. Voxkin does not install,
connect or configure Tailscale automatically; ensure it is connected on the phone,
including when away from home.

Tailscale controls network reachability, **not Voxkin permissions**. Gateway
authentication, device pairing and restricted-user approval still apply. Test
reachability before generating an invitation so its short lifetime is not spent
troubleshooting the network.

## Check pending requests from the shell

Run these commands in Terminal on the **gateway host**, not on the iPhone.
Owner pairing and restricted Voxkin invitations use separate request lists.
An owner setup QR still uses owner pairing; scanning a QR alone does not determine
which list to check.

**Owner phone — list pending requests and paired devices:**

```sh
openclaw devices list
```

**Restricted phone — list Voxkin access records, including pending enrollments:**

```sh
openclaw gateway call voxkin.access.list --json
```

The second command requires the Voxkin plugin with restricted access enabled and
an administrator-authenticated CLI. For a restricted phone, compare its device ID
with the recipient, then approve it in the owner app under **Settings → Manage
access**. `openclaw devices approve` applies to owner-device pairing, not restricted
Voxkin enrollments.

## Approve a newly paired owner phone

After **Connect**, first-time pairing (and later permission upgrades) can require
approval even with management permission off. On the gateway host run:

```sh
openclaw devices list
```

Copy the matching pending request ID, then replace `REQUEST_ID` below:

```sh
openclaw devices approve REQUEST_ID
```

Replace `REQUEST_ID` with the pending request belonging to your phone. Verify the
device and requested scopes first; do not blindly approve the newest request.
A request made with management enabled can retain `operator.admin` even if you
later turn that toggle off. Approval grants the scopes shown in the pending list.
Then tap **Connect** again (or **Retry connection** in the improved approval panel).
No reinstall, new QR, or gateway restart is needed. Never share the gateway token
as troubleshooting output. Current internal TestFlight builds include the approval/retry panel.

## 1. Prepare gateway access

1. Confirm your OpenClaw agent works before adding the phone.
2. Provide a trusted `wss://` gateway endpoint reachable from the iPhone. When using
   Tailscale, connect it separately on both devices and verify network access.
   Voxkin does not open router ports or configure Tailscale for you.
3. Have a gateway bootstrap/shared token available and access to approve the new
   device if pairing is requested. Do not use a model-provider API key or an
   OpenClaw node QR/setup code in this app's token field.
4. The app requests operator read/write scopes. Agent selection is routing, not a
   security boundary: owner mode does not restrict a person to only their agent. For opt-in restricted
   phones, follow [restricted access](restricted-access.md) instead; do not share
   the owner token.

## 2. Optional: install local voices on the gateway host

Use the [plugin installation instructions](../plugin/README.md). In summary:

1. Review the source and [third-party notices](../plugin/THIRD_PARTY_NOTICES.md).
2. Obtain this repository's current private source checkout. A future published
   package will replace that distribution step; there is no public install name yet.
3. Run `python3.13 /absolute/path/to/plugin/setup.py --install` on the gateway host.
   This creates an isolated runtime and downloads checksum-verified models.
   An existing runtime can be verified with `--runtime-dir /absolute/path/to/runtime`
   without `--install`; that verifies hashes/imports and writes a readiness receipt.
4. Install the plugin with `openclaw plugins install /absolute/path/to/plugin --no-enable`.
   Review any local-source/capability consent prompts; do not skip them blindly.
5. Set `plugins.entries.voxkin-local.config` to the verified absolute runtime
   directory and enabled voice IDs. Recommended starting choices: English Heart
   (`af_heart`) and Spanish Dora (`ef_dora`). Version 0.2 supports 28 English
   (US/UK) and 3 Spanish voices; omitting the `voices` allowlist exposes all
   verified installed supported voices. An explicit list restricts the choices. The adapter ships inside the plugin; users do not
   maintain a separate Python adapter script.
6. If the gateway has a `plugins.allow` list, **add** `voxkin-local` while
   preserving every existing entry. `--no-enable` does not add it; enable will
   otherwise report that the allowlist blocks it.
7. Enable with `openclaw plugins enable voxkin-local` after the host owner's
   normal deployment approval. Capability consent may be requested. For an already
   enabled plugin needing refresh, use `openclaw plugins reload voxkin-local`.
   The supported hot lifecycle worked without a full gateway restart in our test;
   it may wait for retained work. Follow reported failures rather than force-killing
   runs or automatically restarting a shared gateway.
8. Keep the existing default speech provider during migration. The plugin adds its
   own methods; it does not replace stock `tts.speak` or other users' voices.

If installation is saved but runtime publication fails, inspect the error and
retry the supported plugin reload after resolving it. Reinstalling or restarting
is not automatically necessary. Never treat a saved config alone as live proof.
