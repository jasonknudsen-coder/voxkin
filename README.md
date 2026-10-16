# Voxkin
Talk to your agent.

Native iPhone voice-client prototype. Currently connects directly to OpenClaw; other backends are a future direction, not implemented support.

Start with the [setup and troubleshooting guide](docs/getting-started.md), including Spanish dictation preparation.

## Project guides
- [Current status and remaining work](docs/release-readiness.md)
- [Standalone maintenance and releases](docs/maintaining.md)
- [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [Privacy](docs/privacy.md)

## What exists
SwiftUI app with Talk, History and Settings tabs, a large tap-to-talk control, signed direct-gateway pairing, endpoint-bound Keychain credentials, explicit agent selection and dedicated app sessions, on-device-only dictation, gateway speech playback, pinned local Stop and separate agent Cancel controls. Opening or returning to the app reconnects with saved credentials; it never automatically records, sends, or replays a turn. No user-facing synthetic demo remains.

**Mock/simulator evidence is not live gateway or physical-device qualification.** See [qualification](docs/qualification.md), [protocol contract](docs/protocol-contract.md), and [initial feasibility](docs/feasibility.md).

## Boundaries
- Direct authenticated WSS gateway connection; Tailscale runs separately. No unauthenticated bridge, developer backend, analytics, crash SDK, or OTA code delivery.
- No provider keys, persona injection, gateway configuration writes, or imported family conversations.
- Explicit gateway message-TTS provider/fallback acceptance before live turns. No strict-provider or Talk-voice equivalence claim.
- No transcript/raw-audio files on the phone by default. Gateway history follows gateway settings.
- Apple recognition must work on-device or visibly fail; typed messages remain available.
- Phone-only backgrounding disconnects and clears conversation. CarPlay is an opt-in, entitlement-gated implementation (see below), not enabled in the ordinary beta. No public release or App Store submission has occurred.

## Build and test
Requires full Xcode with an iOS SDK. Do not change global Xcode selection. No third-party packages or project-generator installation required.

```sh
python3 scripts/generate-project.py
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer swift test
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
  -project Voxkin.xcodeproj -scheme Voxkin -sdk iphonesimulator \
  -configuration Debug -derivedDataPath build CODE_SIGNING_ALLOWED=NO build
```

For UI + app lifecycle regression tests, choose an available simulator ID:

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
  -project Voxkin.xcodeproj -scheme Voxkin \
  -destination 'platform=iOS Simulator,id=YOUR_SIMULATOR_ID,arch=arm64' \
  -derivedDataPath build ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- test
```

Open `Voxkin.xcodeproj` and run the app. Configure a gateway in **Settings**, then use **Talk**: tap the microphone, speak, and tap again to finish transcription and send. If recognition cannot finalize cleanly, review the draft and use Send. Hold the mic to try experimental hands-free alternating turns: pause to send, hear the reply, then listen again. End/Stop exits; see the [conversation guide](docs/using-voxkin.md#hands-free-conversation-experimental) for limits. The keyboard button enables typing; Stop remains pinned below the conversation. Dictation needs a supported real audio/recognition environment. Simulator UI tests inject a synthetic transport through a simulator-only launch hook; it is not a phone feature.

Physical installation requires selecting your own signing team in Xcode (not committed), and explicit device testing authorization. There is no provisioning profile, private key, team ID, or real endpoint/token in this repository.

## Connection setup
Use an isolated gateway with synthetic-only agent tools/data; use a trusted WSS endpoint reachable over your tailnet. In Settings, enter endpoint and gateway pairing token, and approve the new device separately. Connect saves the address and endpoint-bound token in device-only, non-synchronizing Keychain storage. Reconnect after approval without re-entering the token; the issued device credential replaces the pending bootstrap token. Later launches and foreground returns automatically connect using the saved credential. Explicit Disconnect stays disconnected until a new foreground transition or manual Connect; failed connections are not retried in a loop. Leave the token field blank to reuse the saved credential; an explicitly entered replacement takes priority until successful issuance. Changing addresses clears unsent token input. Forget removes the selected endpoint’s identity, tokens, and remembered address locally. This implementation accepts manual gateway tokens, **not** node QR/setup codes. Provider API keys must never be entered.

Read the gateway's speech-provider/fallback policy before accepting it in the app. Acceptance is remembered per exact gateway URL in this device’s Keychain, can be switched off, and is cleared by Forget local pairing. Revisit acceptance if the gateway’s provider policy changes. Selection of an agent is routing, not per-agent authorization. Stop affects local audio; Cancel requests remote cancellation and cannot undo actions. Forget removes local credentials only; revoke the device on the gateway separately.

## Responsiveness
Long final replies play in bounded chunks with one clip prepared ahead; interim/draft text is never spoken. Settings shows in-memory timing breakdowns. See [measurements and physical checks](docs/responsiveness.md).

## Optional local English/Spanish voices
The [native local-voice plugin](plugin/README.md) adds a per-phone language/voice
picker and preview over the existing gateway connection. It requires host-side
Python/Kokoro setup, but no OpenClaw fork or additional listening server. It does
not replace the gateway’s default TTS route. Gateways without it keep their current
behavior; a previously selected missing local voice requires an explicit choice,
not silent fallback. Setup, qualification limits, and activation notes are in
[local voice qualification](docs/local-voice-plugin.md).

## Visual identity
The app uses the approved Voxkin ribbon-V and two sound bars per side. Vector masters, palette, and icon generation instructions are in [brand/README.md](brand/README.md).

## Naming and compatibility
See [identifier and upgrade notes](docs/voxkin-rename.md) for current identifiers and the breaking prototype reset.

## Distribution
Private repository and personal testing only. Source is licensed under [Apache-2.0](LICENSE); no public release authorized. Third-party runtime components retain their own licenses (see [notices](plugin/THIRD_PARTY_NOTICES.md)). See the [release checklist](docs/release-readiness.md).

## Restricted shared-gateway access (experimental)

The same plugin now includes opt-in per-phone enrollment, owner approval, agent-bound
access and revocation. The app includes QR/native-share invitations and a separate
restricted transport. Read the [setup and security boundary](docs/restricted-access.md)
before enabling it. Restricted agents currently have tools disabled; owner mode
remains broad operator access. No live deployment is implied by a source update.

## Distribution and CarPlay

See [TestFlight preparation](docs/app-store/README.md) and the
[CarPlay implementation/approval guide](docs/carplay.md). The ordinary beta build
does not request the gated CarPlay entitlement; Apple approval and physical
qualification are separate release gates.
