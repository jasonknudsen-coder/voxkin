# Android / iPhone presentation parity

The first Android preview proved direct gateway access and English/Spanish use,
but its stock controls were not a faithful presentation of the iPhone app.
The next preview uses `App/VoxkinApp.swift` and `App/VoxkinBrand.swift` as its
presentation reference, rather than introducing a second product design.

## Core phone flow

- Shared navy/surface/cyan/blue/purple/magenta palette and vector ribbon mark.
- Talk / History / Settings navigation, centered wordmark, connection indicator,
  introductory hero, and distinct user / agent message cards.
- Primary microphone control: start recording, stop to review, explicitly send.
  A ready indication is driven by the recognizer callback, not a pretend timer.
  Send is not offered while capture or final transcription is still running.
- Keyboard alternative, editable draft, clear/cancel, stop audio, and a separate
  cancel-request action. Stopping audio does not promise to cancel agent actions.
- Grouped gateway/voice/reply/privacy settings. Voice preview and Brief/Balanced/
  Full reply preferences match the iPhone options. Preferences affect this phone
  and its future turns, not other gateway users.
- Voice and speech-consent preferences are endpoint-bound. Saved consent restores
  only after authentication; unavailable voice IDs are not blindly sent.

## Deliberate or outstanding differences

This is **core phone presentation parity**, not a claim that every iOS feature
has shipped on Android. Android uses its own keyboard, permission dialogs,
accessibility services and on-device recognition implementation.

Still outside this preview: hands-free conversation, owner invitation/approval
management, camera QR scanner, automatic owner-conversation titles, history
pagination, response timing panel, conversational receipt feedback and Android
Auto. The iOS CarPlay integration does not automatically confer Android Auto
support. Background return still requires explicit reconnect on Android and never
replays speech or messages.

These differences should remain explicit in release notes. Do not render inert
controls or advertise these capabilities until implemented and qualified.

## Feature-by-feature audit (0.2.1, 2026-10-20)

This is a source audit plus bounded device tests, not a full parity declaration.

### Verified shared core

- Owner signed connection; restricted enrollment, approval requirement and agent scope.
- Staged setup links, encrypted device credentials, no credential forwarding redirects.
- Agent selection, English/Spanish on-device dictation, readiness, partial display,
  stop/review/explicit Send, typed alternative, clear/cancel and4,000-character limit.
- Per-turn reply-length preference; commands remain unwrapped.
- Explicit Gateway default / Local voices selection for supported owner connections;
  restricted connections remain local-only. No automatic source fallback.
- Voice preview/selection, endpoint consent, Stop audio separate from Cancel request.
- Latest user/assistant cards, history list/new/open/rename/delete, loaded transcript
  and Continue in Talk. Technical/tool messages are not displayed in the transcript.
- Matching final reply filtering and rejection of mismatched send acknowledgment IDs.
- No microphone, pending-message or audio replay on reconnect.

### Fixed during this audit

- Added the missing Speech source selector and explicit source-dependent routing.
- Moved Android-only model checks/downloads behind collapsed Troubleshooting.
- Made all loaded history messages readable instead of only the latest exchange.
- Reject an unexpected acknowledgment run ID without adopting it or retrying.
- Preserve a saved unavailable voice rather than silently substituting another voice.
- Strip reply-preference wrappers from derived history titles; hide truncated wrappers.

### Still different or missing (priority follow-up)

**Conversation continuity:** reconnect currently starts a fresh conversation instead
of restoring the last authorized endpoint/agent conversation. No automatic stable
conversation naming, timestamps/previews/selected-row marker, pagination or older
message loading. History capabilities are not yet independently gated per method.
Owner archive filtering should be explicitly aligned with iOS.

**Response experience:** final-only replies, no streaming/interim agent updates,
no receipt tone or spoken progress option, no response timing/provider panel.
Reply display is plain text, not formatted Markdown. Speech does not yet apply the
iOS Markdown cleanup or short-first-chunk/prefetch pipeline; Android currently
speaks bounded chunks for longer replies while iOS limits speech to4,000characters.

**Capture/lifecycle:** no hands-free conversation or haptic cues. Android may drop
partial hypotheses when final recognition fails, whereas iOS retains them for
review. Returning requires explicit reconnect instead of iOS foreground reconnect.
Android language is explicitly English/Spanish independent of speech source;
iOS gateway-default dictation follows device locale. Android allows text-only
Send without speech consent; iOS requires its speech-policy acceptance before Send.
These differences must not be hidden behind identical-looking controls.

**Setup/management:** no camera QR scanner or owner invite/approve/revoke UI.
Pairing-request guidance and persistent restricted-phone ID need iPhone alignment.
Forget removes credentials but currently retains address/voice preferences, unlike
iOS; do not describe it as erasing every setting. Android Auto remains separate.

**Platform-specific by design:** Keystore vs Keychain, Android permission/keyboard
UI, Android model downloads and audio focus, temporary private playback files
(cleaned after playback) versus iOS memory playback. These need equivalent privacy
and usability guarantees, not identical system implementation.
