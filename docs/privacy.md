# Privacy and data flow

- Microphone audio is used for platform on-device recognition: Apple on iPhone,
  Android on supported devices with the selected language model installed. Recognition
  must be available locally; Voxkin does not opt into cloud recognition fallback.
- The resulting text is sent to your selected gateway/agent. Its model and tools
  may use external services according to the gateway owner's configuration.
- Reply text is sent to the gateway's configured TTS route, or the optional local
  voice plugin. Gateway default can use remote providers/fallbacks: “direct” does
  not mean every downstream service is local.
- No app analytics or crash-reporting SDK is included. Normal conversations go
  directly to your gateway; optional Android response reports go to the publisher as described below. No transcript or raw microphone audio files are saved by default on the phone.
  Android temporarily caches generated reply audio for playback and removes it after
  normal playback/Stop. Interrupted cleanup can leave files in its private cache
  until OS cache cleanup or app-data removal.
  Visible conversation is cleared on background; gateway history follows gateway
  policy and is not deleted by clearing the phone view.
- The app keeps the screen awake while foreground-active. Manual lock still works.
  An iOS inactive-state transition (such as switching apps or locking) ends
  hands-free mode; backgrounding disconnects. It does not record
  continuously in the background or automatically resume a conversation.
- iPhone credentials use device-only Keychain storage. Android credentials use
  AES-GCM encrypted preferences with Android Keystore keys; app backup is disabled. The Forget action is local, not
  gateway-side revocation. Selected preferences may persist separately.

These are application behaviors, not a promise about Apple’s or Google’s operating systems,
your gateway's logs, model providers, or agents' downstream actions.

## Publisher-operated review/test exception

The normal app is bring-your-own-gateway; it includes no hosted AI service. A
separately supplied isolated publisher-operated review/test gateway is a distinct
data flow. It can retain prompts/replies, device/access state and technical logs,
and forward text to its model provider. Access expiry is not deletion. Actual
retention and publisher use of Apple diagnostics must be confirmed before final
App Privacy attestation. Do not automatically select Data Not Collected.

## Optional Android response reports (release candidate; not yet deployed)

Only explicit **Submit report** sends the previewed response text, selected reason,
optional note and random report reference to Builder of Things Studio. You may edit
or entirely remove response text first. No other conversation messages, microphone
audio, gateway address or credentials are automatically attached. The receiving
host necessarily handles your network address; temporary rate-limit counters are
kept in memory, not attached to report records. Reports are used to investigate
unsafe/offensive AI output and retained for up to 30 days. They are not used for
advertising. No automatic background reporting or retry occurs. A failed receipt
can be retried explicitly with the same reference to avoid duplicate storage.
Contact support with the reference to request earlier deletion. Hosting must be
configured without request-body logs, report access logs or report database backups.
These source changes must ship with the verified report service; they do not claim
that the endpoint or updated public privacy page is live today.
