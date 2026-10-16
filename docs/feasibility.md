# Direct-gateway voice client feasibility
Date: 2026-10-12
Verdict: PARTIAL — GO for a bounded prototype, not a production-readiness claim.
Read-only evaluation. No app, gateway, pairing, signing or account changes. No live speech requests or provider charges.
Evidence: Apple CarPlay Developer Guide dated 2026-06-08, Apple entitlement documentation, OpenClaw v2026.9.7 TTS source, upstream commit 3ba9c7d5ec00309389e08b7f8f58f1c7dfe071e0 and current Talk docs. Source excerpts saved alongside this report. Installed CLI reports 2026.9.7; live runtime capability/permission parity NOT established.

## Gate 1 — CarPlay: documented category fit, approval pending
Apple supports voice-based conversational apps from iOS 26.4 using com.apple.developer.carplay-voice-based-conversation.
Guide pp.7,12–14,26 and audio-handling section:
- Voice is primary modality on launch.
- Respond to questions/requests and perform actions.
- Do not show response text or imagery for queries in driving UI.
- Hold audio sessions only while voice features are active.
- Show voice-control template during active voice service; microphone use in this category must accompany that template.
- Voice template supports up to two action buttons from 26.4.
- Apple reviews entitlement request and requires CarPlay Entitlement Addendum and appropriate provisioning. Guide says Xcode/Simulator require supporting provisioning too.
Consequence: no avatar or chat transcript on car display. Status, pause/end controls; full transcript only on phone. Simulator UI alone is not proof of eligibility/distribution.
Not checked: this developer team's existing entitlements, actual approval, physical car compatibility.

## Gate 2 — gateway voice: inline output established in source
v2026.9.7 src/gateway/server-methods/tts.ts includes tts.speak(text). It invokes configured gateway synthesis and returns audioBase64, provider, outputFormat, mimeType, fileExtension.
Current official iOS ChatMessageSpeechClient calls it. TalkGatewaySpeechClient uses talk.speak and decodes inline audio too.
This avoids tts.convert's local-path problem entirely; do not build or install an audio file bridge.
Talk configuration and ordinary message-TTS configuration are distinct. Select an explicit supported mode, display gateway-reported choices, do not write config or collect provider keys.
Caveat: configured synthesis can include gateway fallback chains. Current tts.speak path does not demonstrate a per-request strict-provider switch. We cannot promise no provider fallback merely by checking the returned provider AFTER synthesis. Before real personal speech, establish/accept the gateway's fallback policy or use a server-enforced strict route. Do not silently modify it.
Base64 clip delivery is buffered, not proof of realtime streaming/low latency. Bound text/clip sizes and queue length; account for encoding overhead. Unsupported codecs should fail visibly.
Readiness unknowns: actual device-role/scopes, read-only nonsecret settings availability, binary method exposure, revoked-pairing behavior.

## Gate 3 — input and transport: partial
Current Talk docs expose transcription-only gateway-relay sessions:
talk.session.create({mode:"transcription",transport:"gateway-relay",brain:"none"}), appendAudio and close.
This is a documented path, not proof all installed/stable providers support it or that it inherits arbitrary existing voice-note STT settings.
Native Apple recognition may contact Apple. On-device-only mode must require local recognition and fail visibly when unsupported, not fallback.
Some iOS realtime modes connect client directly to providers through WebRTC. These violate a strict phone-only-to-gateway design. Require supported gateway-relay mode; do not silently switch configured transports or providers.
Gateway-relay realtime may use a different model/voice/agent-consult path than normal chat. For first proof prefer normal agent chat + gateway TTS; keep agent/session choice explicit.

## Proposed first prototype
Native SwiftUI iPhone interface, Apple AVFoundation/Speech/Network/CryptoKit/Security as appropriate; Tailscale separate, no bundled VPN SDK.
No vendor backend, analytics, crash-reporting SDK, OTA code, custom host bridge, persona injection or provider-key entry.
Explicit pairing/device token lifecycle, Keychain, permission-scoped selected agent/session. Do not assume operator.read/write is single-agent isolation.
First loop: explicit push-to-talk, supported transcription, ordinary agent chat, inline gateway TTS, play/stop.
Use synthetic content on isolated test gateway. No family files or actual integrations.
CarPlay front end shares the state machine; dedicated voice-control template, not SwiftUI chat on the car screen.
States: disconnected / ready / recording / transcribing / waiting / speaking / interrupted / failed.
On disconnect stop capture, show state, do not automatically resend an uncertain action. Distinguish stopping playback from cancelling agent work; cancellation cannot undo side effects.
Do not persist transcript or raw audio locally by default. Gateway retains its normal history; app privacy copy must say so.
App's own network traffic can be gateway-only; Tailscale, OS services and gateway providers remain explicit infrastructure dependencies.

## Device acceptance evidence required before GO beyond prototype
- Pair/revoke and denied-scope tests, with no administrator-config access needed.
- Verify only intended destinations; no provider key returned to phone.
- Configured voice plus fallback-disabled/accepted-policy cases.
- At least ten synthetic turns, recording end-of-speech to first audio; report median/tail, no invented performance claims.
- Stop/interruption discards stale audio without cancelling unrelated sessions.
- Lock screen, Bluetooth/car microphone routes, phone-call/navigation interruption, cellular handoff and Tailscale reconnect.
- Parked physical CarPlay check after entitlement/provisioning; no driving test as initial validation.
- No source/binary equivalence claim without release-tagged build evidence.

## Licensing
Our original code: Apache-2.0 is viable. OpenClaw root source is MIT; any reused code needs its existing notices retained and file/dependency-specific licenses checked. Own branding, no implication of official endorsement. Prefer protocol-sized integration over importing a large SDK if dependency minimization is a core promise.

## Recommendation
Proceed to a narrowly scoped iPhone vertical slice, with CarPlay entitlement request prepared in parallel for human submission/approval. This evaluation does not submit it or authorize publishing.
Do not promise universal gateway voice compatibility or native CarPlay launch until corresponding live tests pass.

Sources:
https://developer.apple.com/carplay/
https://developer.apple.com/download/files/CarPlay-Developer-Guide.pdf
https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.carplay-voice-based-conversation
https://github.com/openclaw/openclaw/blob/v2026.9.7/src/gateway/server-methods/tts.ts
https://github.com/openclaw/openclaw/blob/3ba9c7d5ec00309389e08b7f8f58f1c7dfe071e0/apps/ios/Sources/Voice/TalkGatewaySpeechClient.swift
https://docs.openclaw.ai/nodes/talk
https://docs.openclaw.ai/nodes/talk/session-ownership
