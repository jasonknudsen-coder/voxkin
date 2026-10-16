# Privacy and data flow

- iPhone microphone audio is used for Apple on-device recognition. Recognition
  must be available locally; Voxkin does not opt into cloud recognition fallback.
- The resulting text is sent to your selected gateway/agent. Its model and tools
  may use external services according to the gateway owner's configuration.
- Reply text is sent to the gateway's configured TTS route, or the optional local
  voice plugin. Gateway default can use remote providers/fallbacks: “direct” does
  not mean every downstream service is local.
- No app analytics, crash-reporting SDK, or vendor-operated Voxkin backend is
  included. No transcript or raw audio files are saved by default on the phone.
  Visible conversation is cleared on background; gateway history follows gateway
  policy and is not deleted by clearing the phone view.
- The app keeps the screen awake while foreground-active. Manual lock still works.
  An iOS inactive-state transition (such as switching apps or locking) ends
  hands-free mode; backgrounding disconnects. It does not record
  continuously in the background or automatically resume a conversation.
- Credentials use device-only Keychain storage. The Forget action is local, not
  gateway-side revocation. Selected preferences may persist separately.

These are application behaviors, not a promise about Apple's operating system,
your gateway's logs, model providers, or agents' downstream actions.

## Publisher-operated review/test exception

The normal app is bring-your-own-gateway; it includes no hosted AI service. A
separately supplied isolated publisher-operated review/test gateway is a distinct
data flow. It can retain prompts/replies, device/access state and technical logs,
and forward text to its model provider. Access expiry is not deletion. Actual
retention and publisher use of Apple diagnostics must be confirmed before final
App Privacy attestation. Do not automatically select Data Not Collected.
