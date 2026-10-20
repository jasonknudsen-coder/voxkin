# Android Auto feasibility — 2026-10-20

## Verdict

Research complete; no Android Auto implementation or device changes in this pass.
A messaging-shaped technical prototype is possible, but Voxkin eligibility is
unresolved. Do not claim a supported general-purpose AI assistant integration.

Google car quality requirement **MF-5** says messaging apps must implement a
**peer-to-peer messaging service**; MF-4 requires short-form messaging. The
supported category list has no general-purpose assistant category. Agent chat is
not demonstrably peer-to-peer under that policy. Media, navigation, calling or IoT
must not be used as labels for unrelated assistant features (PC-1).

## Available mechanisms

- Notification messaging: MessagingStyle with reply and mark-as-read actions,
  RemoteInput and a background service. Supported on all Play track types, subject
  to category eligibility. Only messages received during the active Android Auto
  session are available through this route. It is not a custom Talk screen.
- Templated messaging: CarAppService with MESSAGING category, Car API >=7,
  notification + template capability declarations, ConversationItem list and
  notification support. Current official docs allow anyone to publish to internal
  and closed testing; open testing/production remain disallowed. Older design docs
  saying beta-partners-only are not the current technical publishing guidance.
- Android Auto owns voice transcription and read-aloud in this messaging model.
  Do not promise Voxkin on-device-only dictation, gateway-selected voice, a custom
  wake word, steering-wheel takeover, or the iPhone record/review/send UI.

## Source audit and next implementation boundary

Android 0.2.16 has no car metadata, car service, notification integration, or car
library dependency. MainActivity.onStop calls VoiceModel.background, which stops
hands-free, disconnects transport and stops audio. A car session cannot just reuse
that foreground-only lifecycle unchanged.

Before coding a user-facing car feature:

1. Resolve category fit: prepare a concrete short-form agent messaging description
   for Google clarification; submitting a partner/support inquiry is a separate
   external action and has not been done.
2. If pursuing a private technical experiment, isolate it from the accepted phone
   build. First prove host notification + explicit reply with synthetic data in
   Desktop Head Unit (DHU). This proves mechanics, not policy acceptance.
3. Extract a service-owned, scoped conversation controller without weakening phone
   background cancellation. Bind every reply to endpoint/access mode/agent/session;
   retain explicit-send, dedupe and no-automatic-retry rules. Handle permission
   denial, revocation, missing credentials, process death and unknown send outcome.
4. Only after host mechanics work, assess templated recent-conversation browsing.
   Keep account, QR, pairing, admin, configuration and destructive actions on phone.
5. Qualify disconnect/reconnect, screen lock, navigation/phone-call audio focus,
   duplicate/stale replies, English/Spanish and real parked-car microphone/speaker
   routing. DHU supports driving restrictions and focus simulation, not acoustic proof.

Fallback: a deliberately designed phone car-audio mode over Bluetooth would be a
separate feature, not Android Auto. Current foreground-only app behavior does not
prove screen-off hands-free operation; background microphone/audio requirements
and user-visible stop controls would need dedicated implementation and testing.

Jason is testing the Pixel; do not interrupt it to run DHU, change Android Auto
settings, install builds, trigger audio, or restart ADB during that acceptance run.

## Sources checked

- https://developer.android.com/training/cars — categories and publishing tracks
- https://developer.android.com/docs/quality-guidelines/car-app-quality — PC-1, MF-4, MF-5
- https://developer.android.com/training/cars/messaging — notification integration
- https://developer.android.com/training/cars/communication/messaging — templates, API7, testing-only distribution
- https://developer.android.com/training/cars/testing/dhu — head-unit testing

Official pages fetched directly after web-fetch redirect failures. No runtime car
integration was exercised. Solo documentation/source investigation; no runnable
artifact or independent implementation lane warranted in this bounded research.
