# Phone recording review flow

TestFlight candidate: 1.0 (14), October 17, 2026. After explicit owner authorization, uploaded successfully and verified VALID / IN_BETA_TESTING with the internal test group. Public App Store release unchanged.

## Behavior

Ordinary phone recording now follows record → stop/review → send. Microphone opening has a separate readiness state; the pulsing waveform denotes ready/listening, not measured sound amplitude. Recognized words update the transcript. Stop ends audio input and permits final recognition results before review. Clear/cancel discards the capture; no message is sent by Stop. The send arrow appears after recording finishes. Existing experimental hands-free and CarPlay conversation paths retain pause-to-send behavior.

## Evidence

- Swift core: 45 tests passed.
- App tests: 107 passed (99 model tests and 8 native audio input tests), including late final words, explicit review/send, discard with late callbacks, permission cancellation, and existing CarPlay controls.
- Initial UI suite: 12/14 passed. Two unrelated existing assertions failed: outdated permission explanatory text and an offscreen Preview voice button.
- Updated those assertions and reran both, plus the new phone recording test and hands-free start/stop: 4/4 passed on final source.
- Independent read-only review: no blocking lifecycle defects; preserved the send icon for hands-free mode after review noted that its action still sends.
- Signed Release archive and export succeeded; archive verifier confirmed CarPlay packaging.

## Remaining physical acceptance

Use the new candidate on a real phone: microphone permission/opening, English and Spanish final words, stop/edit/send, cancel/re-record, VoiceOver, and interruption/backgrounding. Confirm the listening indicator is obvious in actual use. Simulator capture is synthetic and does not establish real microphone or vehicle performance. The separate reported CarPlay loudness issue is not addressed here.

## App Store submission — October 20, 2026

Version 1.0 / build 14 submitted under owner authorization. Independent Apple readback confirms WAITING_FOR_REVIEW with exactly one matching app-version item and AFTER_APPROVAL release behavior. Content Rights is set to no third-party content based on the current client functionality. Isolated review service restored and public HTTPS enrollment, model reply, and English/Spanish speech reverified before submission. Not yet approved or publicly released.
