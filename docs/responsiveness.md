# Responsiveness and polish — 2026-10-13

## Scope
Maintainer chose latency + polish, followed by final-word/reconnect/interim checks. Permissions, conversation continuity, Bluetooth, AirPods and phone-call testing are excluded from this iteration. No gateway settings or additional services were introduced.

## Measured local Heart baseline
Harmless synthetic text through the existing CLI, fresh process for each call (individual runs, not percentiles):
- 37 characters: 1.619s and 1.420s; 2.541s audio.
- 137 characters: 2.050s; 8.544s audio.
- 1,161 characters: 9.894s; 70.610s audio.
- Separate phase probe: imports 0.092s, model setup 0.351s, first short inference 0.868s, same-engine subsequent inference 0.376s.

These are local synthesis timings, NOT measured phone round trips. Agent generation/network latency remains separate. Gateway `tts.speak` returns a complete base64 audio clip, not a stream; changing the CLI to produce chunks would not change that contract.

## Implementation
- Final replies over 500 characters are split into bounded chunks, preferring punctuation. Short first chunk targets 120 characters with whitespace/hard bounds; later chunks target 320. Every source character remains in the visible reply. Whitespace-only speech chunks are ignored.
- Synthesize the first chunk, play it, and prefetch at most one next clip. No concurrent synthesis fanout. No speech from provisional deltas or interim notices. The existing 4,000-character speech limit remains.
- Stop/background invalidate the turn, stop playback, cancel prefetch, and discard late clips. Decode failures release the playback continuation. Cancellation cannot undo a TTS request already running at the gateway.
- This reduces text that must be synthesized before first audio. Expected first-audio benefit is supported by the baseline comparison, but actual phone improvement and transitions/prosody must be checked. Extra subprocess starts can increase total CPU/time; this is not true streaming generation.
- In-memory Settings timings: transcription finalization, first content update, complete reply, speech preparation and send-to-first-audio. Uses monotonic clock; no transcript, identifiers, raw audio, file log or telemetry. Clears on disconnect/new turn.
- Successful interim subscription is reused within the same app session instead of awaited before every turn.
- Light/soft haptics for listening readiness, send and explicit Stop; actionable pairing/token/network error wording; code-native orbit/waveform icon matching Talk colors.

## Physical acceptance still needed
1. Speak a phrase with a distinctive last word, tap Send, and confirm that word survives. If finalization times out, verify the draft remains for review.
2. Ask for a roughly 150-word reply: compare Settings timing values, listen for natural chunk transitions, and test Stop mid-reply.
3. Background during pending/prepared audio, then reopen: reconnect without replay or automatic recording.
4. Verify live interim updates on a run that actually emits them; strict run/session filtering remains. Synthetic coverage is not live event proof.

No Bluetooth/AirPods/phone-call test request is part of this checklist.
