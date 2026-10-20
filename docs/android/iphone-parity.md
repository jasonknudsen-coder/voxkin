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
