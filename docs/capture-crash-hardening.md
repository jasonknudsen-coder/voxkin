# CarPlay microphone-start crash hardening — October 14, 2026

## Evidence and scope

Build 0.3.0 (2)'s TestFlight report terminates in an Objective-C exception at
AVAudioEngineGraph::InstallTapOnNode, through SpeechCapture.start and CarPlay
Start Conversation. The report does not contain the assertion reason. An invalid
or changed route/format and stale graph state are candidates, not proven causes.

This local candidate does not claim physical crash resolution. Build 3 remains
the shipped, locked-phone-tested build. No upload accompanies this change.

## Changes

- Each microphone attempt creates a fresh audio engine; failed graphs are discarded.
- Input availability and finite, positive, matching native input/output formats
  are checked. Tap installation uses nil format to preserve the negotiated route.
- A narrow Objective-C boundary contains native setup exceptions without unwinding
  through a Swift callback. iOS 27's NSError tap API is used when available;
  older systems retain the exception-contained legacy call.
- Cleanup removes the tap from the exact captured node, tolerates native cleanup
  exceptions, and is idempotent. No automatic microphone retry is introduced.
- ARC exception cleanup is enabled for native code so recovered exceptions release
  strong local references and request-capturing blocks.
- Failure returns a fixed retry message rather than native exception details.
- Permission awaits check cancellation and attempt identity. A stale attempt cannot
  start capture or deactivate a newer attempt after permission completion.
- On-device recognition, explicit microphone start, Stop and capture limits remain.

## Validation

- 81 signed simulator app tests passed: 73 model/lifecycle tests and eight native
  audio-boundary tests. Includes actual injected NSExceptions, invalid/NaN formats,
  engine-start errors, cleanup exceptions, fresh-engine recovery and permission races.
- Unsigned Release device build passed (compilation only, not installation).
- Logs: `.tmp/capture-hardening-tests-final.log`, `.tmp/capture-device-check.log`.

- All eight UI regressions passed, including conversation Start/Stop. Log:
  `.tmp/capture-ui-tests.log`.
- Independent review found one ARC exception-cleanup setting issue, corrected and
  re-reviewed with a weak-reference lifetime regression added. No remaining blockers.

## Physical qualification still needed

On the next installed candidate: parked test vehicle, repeated Start/Stop with phone
unlocked and locked; reconnect CarPlay; change audio route between attempts; verify
an unavailable microphone gives a recoverable error and never silently starts.
Retry explicitly after the route stabilizes. Confirm microphone capture, dictation,
and reply playback still work. Collect any new crash report before declaring the
original crash resolved. Simulator fixtures cannot reproduce the vehicle's graph.
