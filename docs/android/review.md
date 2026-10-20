# Android candidate review — 2026-10-20

Scope: native phone client; existing iOS and gateway source untouched.
Initial APK compiled and installed on physical Pixel 8a/Android17. Launch rendered
successfully. This is not proof of connection, microphone or speech playback.

## Findings requiring retest

Parent integration review:
- In-flight synthesis must be cancelled/invalidated by Stop, new recording and
  background transitions, not merely stop an existing player.
- History and agent changes must not race capture, pending history loads or sends.
- Owner links must require WSS and staged secrets must remain bound to their origin.
- Enrollment and reconnect must use consistent credential keys.
- Backups/device-transfer exclusions must cover secrets and software identity.

Independent read-only review:
- Restricted first Send needs a server-created owned conversation.
- Final spoken content must explicitly be assistant-role, not the broader history parser.
- Spanish selection must select a compatible voice; the catalog language is material.
- Speech synthesis must respect the plugin's per-request length bound.
- Recognizer initialization failures must clean up and permit typed fallback.
- Playback needs Android audio-focus loss handling.
- Forget should remove local identity as well as access credentials.

All initial findings above were addressed and independently re-reviewed with no
remaining concrete blockers in that review scope. Twelve JVM tests and seven
physical-device instrumented tests passed, including delayed synthesis cancellation,
history locking, capture-context guards and replacement restricted conversation
creation. This is a bounded review, not a security certification.

Live integration additionally found the restricted history-list limit must be at
most20, not50. The limit/UI were corrected and the live guest history flow passed.
Guest Forget now preserves the owner identity/grant at the same origin; both the
mode-isolation regression and live return-to-owner reconnect passed. Final device
suite:9/9. Final JVM suite:12/12.
