# Optional local voice plugin — 2026-10-13

## Delivered contract

Native OpenClaw plugin `voxkin-local`, private version0.2.0. Tested host2026.9.7,
macOS Apple Silicon, Python3.13. Registers `voxkin.catalog` (operator.read) and
`voxkin.speak` (operator.write). No core patch or separate listener. It does
**not** register/replace stock `tts.speak` and does not change shared voice defaults.

The catalog supports 28 English and 3 Spanish voices. Omitting the allowlist
exposes verified installed supported voices; an explicit allowlist restricts them.
All 31 generated audio in the catalog expansion check; that is not a subjective
quality certification of every voice. Heart and Dora remain familiar starting choices.
The optional client contract is provider-neutral; catalog names populate the UI.

App changes:
- Nonblocking, connection-fenced capability/catalog discovery; both methods required.
- Local/Gateway-default source, English/Spanish language and advertised voice pickers.
- Endpoint-bound device-only Keychain selection, removed by Forget.
- Explicit selected locale for on-device dictation. Unavailable recognition means
  type instead, never cloud dictation. Spanish physical success is recorded below.
- Per-turn reply-language preference and fixed language/voice synthesis parameters.
  Language is a preference, not a translator or hard model guarantee; explicit user
  requests can override reply language. Voice remains selected until changed.
- Preview invokes synthesis only, preserves draft/conversation and response timing,
  obeys Stop/background/disconnect. Choices frozen during recording/agent run/playback.
- No automatic local selection, no silent rerouting if saved voice/catalog disappears.
- Gateway speech acceptance still required. No transcript/audio persistence in app.

## Initial 0.1 evidence (historical)

- 6 Node plugin tests pass: validation, scopes, missing runtime, concurrency, worker
 abort/retirement and public error behavior. Process-abort test verifies PID gone.
- 32 app lifecycle tests plus1 targeted nonblocking/stale-catalog test pass.
- 5 UI tests pass, including discovered Spanish Dora → English Heart selection;
 Settings screenshot inspected. Existing4 UI regression checks still pass.
- 20 core Swift tests pass. Signed Debug iPhone build succeeds and was installed on the paired phone.
- Direct runtime English/Spanish output24kHz, plus WebSocket proof through isolated
 stock gateway: catalog advertised; read/write generated both voices; read-only
 could list catalog and was denied synthesis.
- Native plugin installer copied/linked package into separate temporary OpenClaw
 profile after explicit local-source and capability review. Packaged plugin loaded
 with fresh runtime and produced both voices through the same RPC connection.
- Setup created a fresh venv and downloaded both SHA-pinned models; both languages
 synthesized successfully. Pip reused some cache on this existing development Mac.
- Source review found and resolved path-leaking errors, mismatched audio limit,
 worker retirement, readiness evidence, host compatibility metadata, blocking
 discovery, receive timing attribution, and stale preview timer.

## Remaining qualification / boundaries

The development tests did not change the shared gateway. Isolated profiles use
loopback only and synthetic phrases, no agent runs or personal message content.
The subsequent authorized activation is recorded below.
No public npm/ClawHub/App Store publication. Package remains private, with Apache-2.0 source licensing. Third-party binary/model
redistribution review remains separate; see the notices and release checklist.

Clean OS, Intel Mac, Linux and Windows remain unqualified. Spanish dictation and
Dora preview have now been confirmed on one physical iPhone, not a device matrix. Runtime wheel versions are pinned but not hash-locked.
The read/write scopes are gateway-wide, not per-user/agent isolation. No permission
redesign, CarPlay, or Bluetooth/AirPods/phone-call testing was included.

## Activation and rollback

Follow [plugin setup](../plugin/README.md). For this existing development host, the
verified existing Kokoro runtime can be reused; there is no need to change the
legacy Heart stdin adapter or stock speech-provider configuration. Before activation,
review the immutable commit, install the plugin disabled, set runtimeDir and the
Heart/Dora allowlist, then enable/reload using the gateway’s supported workflow.

Rollback: choose Gateway default on this phone, disable/reload the optional plugin.
The existing provider remains available. If the plugin disappears while a saved
local choice remains, the app asks for an explicit source choice before sending.

## Authorized activation — 2026-10-13

Maintainer supplied the required deployment confirmation for code commit
`2fc89b0b0b8205345c3153218d33350c87a4f173`. Installed plugin files were hash-matched
to that source; existing verified Kokoro runtime reused, Heart/Dora allowlist enabled.
Initial installation refresh was superseded by a runtime config change; no replacement
was applied in that attempt. After configuring and allowlisting the plugin, the
supported enable lifecycle completed after retained work drained.

Live shared-gateway hello advertised both RPCs. Read/write client catalog returned
Heart and Dora ready; English and Spanish synthesis returned WAV audio at24kHz.
Read-only client could list the catalog but synthesis was denied. Gateway listener
PID was identical before/after: no full restart. The legacy tts-local-cli provider
remains enabled; no stock TTS configuration was replaced. Physical phone Spanish
recognition/preview was awaiting verification at activation time; see the later result below.

## Physical Spanish feedback and dictation follow-up

Maintainer confirmed Dora preview plays Spanish on the iPhone. Spanish recording instead
reported unavailable on-device recognition; this does not indicate a synthesis failure.
App now considers supported regional locales within the selected language, preferring
the phone’s languages before the voice locale, and chooses only a recognizer reporting
on-device support and current availability. It never switches Spanish to English or
permits cloud recognition. No usable recognizer produces language-specific keyboard/
dictation setup guidance. 36 app tests pass including locale ordering, alternate-region
availability, no-cross-language candidates and deduplication. No model download is forced by the app.

## Physical Spanish success — 2026-10-13, 14:43 MDT

Maintainer reported adding a Spanish keyboard alone still left the error. He then
successfully dictated Spanish in Notes, returned to Voxkin, and confirmed
Spanish input and a correct Spanish response. Dora preview had already been
confirmed separately. This qualifies the tested phone workflow; it does not prove
which iOS asset initialization/download event caused the availability change.
The [user setup guide](using-voxkin.md#prepare-spanish-dictation-on-the-iphone)
records this as a troubleshooting sequence, not a universal prerequisite or fix.

## Catalog expansion 0.2 — activated

The setup-verified model inventory now drives discovery, intersected with bundled
supported metadata and the operator's explicit allowlist (if present). The current
model contains 31 supported voices: 20 US English, 8 UK English, and 3 Spanish.
The app accepts en-GB metadata and preserves saved selections.

Verification: 8 Node tests pass; 37 app tests pass (the two previously excluded
audio-route/interruption cases remain excluded). All 31 voices generated 24 kHz
WAV through the runtime in the isolated fresh-runtime directory. This is synthesis
verification, not a listening-quality review or cross-platform qualification.
Independent read-only review found no code blocker; its receipt migration and
allowlist documentation recommendations were incorporated.

Maintainer authorized production activation of `6b5f73f` with the configured confirmation
phrase. The runtime receipt was refreshed without installation/download, the
allowlist expanded, and installed plugin files verified byte-for-byte against
reviewed source. Plugin-only reload failed with a retained-work cleanup timeout;
a standard gateway restart completed activation. No OpenClaw package update occurred.

Post-restart live verification on 2026-10-13:
- Gateway healthy; both RPCs advertised; catalog ready with all 31 voices.
- Heart (US), Emma (UK), and Alex (Spanish) returned valid 24 kHz WAV audio.
- Read-only scope can discover voices but cannot synthesize.
- Legacy tts-local-cli remains enabled; shared default voice was not replaced.

Reconnect the installed app to refresh Settings → Speech source → Local voices.
Saved voice selections are preserved. Expanded voice selection/playback on the
physical phone still needs user confirmation; live RPC synthesis is not a listening
audit. See the plugin README upgrade section for repeatable migration steps.
