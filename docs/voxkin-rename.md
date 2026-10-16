# Voxkin identifiers and prototype reset

Voxkin is the product name. Tagline: **Talk to your agent.**

The 0.4.0 candidate uses Voxkin identifiers throughout its runtime contract:
- Plugin ID/provider `voxkin-local`; service `voxkin-local-workers`.
- RPC methods `voxkin.catalog` and `voxkin.speak`.
- Owner sessions `agent:<agent>:voxkin:<uuid>`; subscription `voxkin`.
- Restricted sessions are server-routed, device-and-conversation scoped.
- Keychain service `studio.builderofthings.voxkin.credentials`.
- Model/runtime defaults use Voxkin paths; installer receipts use the new identity.
- Canonical checkout: `projects/voxkin`. No legacy API aliases or history import.

Maintainer explicitly requested a clean prototype reset on October 15. This is a
breaking app/plugin change, not a transparent upgrade. Existing phone credentials
are not migrated: reconnect/re-pair, accept speech settings, and re-enroll restricted
phones. Old conversations are not included in the new History view. Existing model
files may be copied into the new runtime location during an approved deployment;
there is no need to download identical model assets again.

## One intentional exception

Apple’s fixed application bundle ID remains `com.iris.private.voice`. Maintainer chose
to retain it on October 15 because changing it would require a separate App Store
record/TestFlight app. Test bundle IDs and Keychain identity use Voxkin naming.

## Activation

Source changes do not update the installed phone or live gateway. Install the
0.4.0 plugin and app as a coordinated change. The previous live plugin must be
backed up and disabled before activating the replacement; never load both into
one access-state directory. Preserve runtime/access state backups for rollback.
Production activation requires a fresh exact authorization under workspace rules.
Public App Store release is separate. Historical qualification documents are not
proof that renamed artifacts were retested; use the current history qualification.
# Gateway default speech regression (October 15, 2026)

Physical build-10 testing exposed a missed host configuration reference:
`tts.providers.tts-local-cli.args[0]` still pointed to the removed
`projects/iris-voice/scripts/kokoro_tts.py`. Explicit local voices use
`voxkin.speak` and passed; Gateway default uses `tts.speak` and failed with
`UNAVAILABLE`. Pairing was not the cause.

Corrected the configured adapter path to `projects/voxkin/scripts/kokoro_tts.py`
and the Python command to the Voxkin runtime. Direct adapter synthesis passed
(24 kHz WAV), and live explicit Heart synthesis passed. Config validation and
save succeeded, but gateway channel reload was deferred behind active work;
live default-route verification remains pending activation.

Rename/release verification must exercise **both** `tts.speak` (Gateway default)
and `voxkin.speak` (explicit local voice). A successful plugin probe does not
verify the separately configured default speech adapter. A saved config is not
proof that a deferred reload has activated it.
