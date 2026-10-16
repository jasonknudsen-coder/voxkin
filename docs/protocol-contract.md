# Protocol contract and trust boundaries

Implementation inspected against locally installed OpenClaw package version 2026.9.7 on 2026-10-12. Package version alone does not prove source/binary equivalence with a published tag. The installed `dist/version-CwNT1gaY.mjs` reports operator protocol **4**; the implementation fails closed on other versions. The initial feasibility brief predates this implementation inspection.

## Handshake

- Direct `wss://` URL only, normal OS certificate validation; no certificate exceptions, plaintext fallback, URL credentials, query tokens, or custom bridge.
- Compare socket's effective URL to the requested URL before processing every inbound frame, including the challenge. Do not forward credentials to a redirected challenge endpoint. Live redirect/TLS behavior is not yet qualified.
- Wait for `connect.challenge`; validate nonce and nonnegative integer server timestamp.
- Ed25519 identity (CryptoKit Curve25519.Signing); device ID is SHA-256 of raw public key; base64url public key/signature.
- v3 signature binds device ID, registered client ID `openclaw-ios`, mode `ui`, operator role, exact requested scope list, timestamp, token, nonce, platform `ios`, family `iphone`. Display name identifies this independent private prototype; no official-app branding claim.
- Request `operator.read` + `operator.write`. No administrator, pairing-mutation, or Talk secret scope. These are gateway-wide permissions, **not** agent isolation.
- Manual first pairing: enter gateway bootstrap/shared token (not a provider key), approve the pending device on the gateway separately, then reconnect. Built-in node setup-code/QR bootstrap is **not** implemented; do not paste those codes as tokens.
- Identity and issued device-token grant use endpoint-specific, nonsynchronizing, after-first-unlock-this-device-only Keychain entries (one unlock after reboot; existing entries migrate after successful access). Same token preserves its saved approved scopes; rotated token saves returned scopes. The pending bootstrap credential is saved endpoint-bound in Keychain for reconnect after pairing approval; an issued device grant replaces it. Unsent token-field contents are cleared when the endpoint changes.
- Forget removes local identity/grant, not server authorization. Revoke the old device on the gateway separately. No app-side administrative mutation.

## Conversation and speech

- `agents.list` supplies selectable IDs. Conversations use `agent:<id>:voxkin:<uuid>` keys; the app restores the selected conversation or explicitly starts a new UUID.
- `chat.send`: explicit `agentId`, session key, ordinary message, UUID idempotency key, `deliver:false`. No persona, system prompt, history import, provider override, or config rewrite.
- Only matching session+run final assistant text can trigger speech. Tool/thinking/delta messages are never spoken. Duplicate finals, stale epochs, and other sessions are ignored.
- `tts.speak({text})` uses **message TTS**, not Talk voice. A required user acknowledgment covers gateway provider/fallback policy before live sends. The reported provider is shown, but cannot prove that no other provider saw text. No strict-provider promise.
- Audio inline only: MP3/WAV/MP4/AAC supported MIME allowlist, base64 <=8 MB, decoded <=6 MB, actual decoding by AVAudioPlayer. Unsupported/malformed clips fail visibly; reply text remains. Text input and speech output <=4,000 characters; displayed reply <=32,000.
- Apple push-to-talk recognition **requires on-device recognition**, with 30-second capture cap. A second tap explicitly finishes and sends; failed/uncertain finalization leaves a draft for review and Send. No unsolicited send, cloud fallback, raw audio files, or transcript files.
- URLSession ephemeral; no app telemetry or vendor service. Tailscale, Apple OS services, and gateway/provider infrastructure are distinct boundaries, not claims about all device network traffic.

## Lifetimes

Single socket, <=8 in-flight requests, 15-second challenge deadline, 60-second RPC deadline, 120-second turn deadline. Foreground entry may connect once using saved credentials. No reconnect loop, history replay, automatic recording, turn replay, or automatic request retry. A timeout/disconnect can leave agent outcome unknown.

Cancellation before a queued wire submission suppresses the write; cancellation after the write may leave an uncertain remote outcome. Closing fails pending continuations and invalidates the socket generation. Reconnect is explicit or occurs once on a later foreground transition using saved credentials, with a new app session.

Local Stop cancels capture/playback and discards pending audio but preserves the run cancellation handle. A separate `chat.abort` targets that exact session/run. Delayed abort responses cannot clear a newer run. New turns/agent selection are blocked while the previous run remains outstanding. Terminal event, explicit cancel acknowledgment, or disconnect clears the handle. Cancellation cannot undo side effects.

Genuine audio interruption/route changes while recording, waiting, or speaking discard pending local audio. App's own category change and interruption-end notifications do not restart or stop audio. No automatic resume. Backgrounding disconnects and clears visible conversation; no lock-screen/background voice mode.

## Local source evidence

Read-only inspected installed docs `docs/gateway/protocol/{handshake,auth}.md`, and installed dist `device-auth-BT2rF0Jc.mjs`, `client-info-S_xwnr2o.mjs`, `sessions-BwaoTke0.mjs`, `tts-ybzxEGrp.mjs`, `version-CwNT1gaY.mjs`. Original Swift code implements the wire contract; no OpenClaw source library is vendored.

## Optional local voice extension (2026-10-13)

Both `voxkin.catalog` and `voxkin.speak` must be advertised in hello before
background discovery is attempted. Catalog v1 identifies the local provider and
available language/voice pairs. `voxkin.speak({text,language,voice})` uses
operator.write; catalog uses operator.read. Responses must match the selected
provider/language/voice. This route has no cloud fallback and does not replace
stock `tts.speak`. Missing saved local selections require explicit user action.

The app adds per-turn reply-length and, for a selected local voice, reply-language
preferences to ordinary message text (commands are passed unchanged). These are
not system-role instructions or hidden TTS routing markers. The agent/gateway
receives them even though the app displays the original user draft. Synthesis
routing uses explicit RPC fields. Recognizer locale candidates stay within the
selected language and require on-device support and availability. Preview sends
a fixed localized sample without invoking the agent; lifecycle fences discard
late audio after Stop/disconnect. See [contract and limits](local-voice-plugin.md).
