# Voxkin Local

Gateway plugin for the Voxkin iOS/Android voice client. Version **0.4.3** adds
guided setup, read-only diagnostics, and explicit repair with download progress,
on top of 0.4.2’s per-phone isolation and owner-onboarding security fixes.

**After installation, run `openclaw voxkin setup`.**
Use `openclaw voxkin doctor` to check readiness or `openclaw voxkin repair` to repair
voices with explicit download consent. See [INSTALL.md](INSTALL.md) for prerequisites
and installation options. Official downloads and checksums:
https://voxkin.builderofthings.studio/downloads/

- Optional offline English/Spanish speech:28 English and3 Spanish voices.
- Opt-in restricted-phone enrollment with explicit owner approval, agent-bound
  credentials, private conversation history and per-device limits.
- Existing authenticated OpenClaw gateway RPCs; stock `tts.speak` and shared speech
  defaults are unchanged. No publisher-operated conversation backend.
- Owner QR generated locally as non-URI text, scanned inside Voxkin. New owner QR
  requires iOS1.0.1/Android0.2.20; existing connections/guest invitations still work.

## Package and platform scope

This source-only download contains no models, native binaries, Python wheels,
node_modules, credentials, or private gateway state. Explicit setup obtains
upstream dependencies separately; read [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
Source is Apache-2.0; see [LICENSE](LICENSE). The npm manifest's `private:true`
prevents accidental registry publication; it does not prevent local archive install.
No npm/ClawHub listing is implied. The full app repository is not needed to install.

SDK integration was tested on OpenClaw2026.9.7 and2026.10.6. Current actual native
speech qualification is macOS Apple Silicon/Python3.13. Earlier Linux x86_64 and
Windows x64 results are not fresh qualification of0.4.2's worker-isolation change;
those setup paths remain experimental. See INSTALL.md for exact requirements.

## Security and authority

Owner mode uses broad gateway operator authority. Restricted mode is distinct;
use a non-main, non-sensitive agent. Tools/commands are disabled for restricted
turns, but the host, providers, hooks and agent workspace remain trusted.

Speech uses a configured Python executable with an argument array, not a shell;
text arrives as JSON on stdin. Isolated Python and a limited OS environment exclude
gateway/provider credentials and loader overrides. Runtime code/dependencies remain
trusted. Setup verifies model SHA-256; startup size/mtime checks indicate readiness,
not tamper-proof attestation. Python pins are not yet wheel-hash locked.

Per-device quotas and bounded enrollment protect phones from cheap shared-budget
starvation. Global resource bounds still apply. TLS, edge connection/slow-body
controls and appropriate gateway exposure remain operator responsibilities.

## RPC contract v1

- `voxkin.catalog({})`, `operator.read`: provider `voxkin-local`, readiness and
  enabled installed voice metadata (`id`, language, locale, name).
- `voxkin.speak({text,language,voice})`, `operator.write`: WAV audioBase64 and voice
  metadata.1–4000 Unicode characters, two simultaneous workers,45-second bound,
  maximum6MB output. Temporary audio is removed; no transcript/audio logging.
- Restricted ownership/storage details: [access/README.md](access/README.md) and
  [restricted/HISTORY.md](restricted/HISTORY.md).

Disconnect/revoke and history deletion have different effects; read the installed
history guide. No auth bypass, shell injection, or security certification is claimed.

Support and private security reports: jason@builderofthings.studio. Never include
owner tokens, invitation secrets, private conversations or credential-bearing logs.
