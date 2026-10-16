# Early speech contract check — 2026-10-13

Maintainer requested speaking completed streamed sentences before the full reply, conditional on those sentences not being revised. No gateway modification or separate service is in scope.

## Observed phone baseline
Maintainer's Settings screenshot: dictation finalization0.2s, first text3.4s, final reply17.4s, speech preparation2.2s, first audio19.6s. First content update is not an immutable/speakable sentence guarantee.

## Installed gateway evidence
- `docs/gateway/protocol/rpc-bootstrap-and-events.md:126–139`: snapshots authoritative; non-prefix replacements use `replace=true`, including empty replacements clearing earlier text.
- Same file148–157: raw `agent` text also has authoritative snapshots/replacements; switching projections does not make content append-only.
- `dist/server-chat-state-CoRVfzIV.mjs:505–518`: `takeBufferDelta` emits full replacements when new text is not an extension of sent text.
- `dist/server-chat-BqGrZRC9.mjs:598–623`: exposes those replacements in normal chat delta events.
- `dist/server-chat-BqGrZRC9.mjs:714+`: terminal projection remains the settled reply boundary used by the app.

A read-only synthetic probe imported the installed `createChatRunState`, streamed “The meeting is on Tuesday.” then replaced the same assistant item with “The meeting is on Wednesday.” The actual projection returned `replace:true` and the second sentence. Assertion passed. No agent/model call, personal text, credentials or configuration changes were involved.

Punctuation and an unchanged snapshot over a short timer are not commit signals. Playback cannot retract already spoken content. The app should not silently label speculative pre-final speech as guaranteed stable. Existing final-reply chunking remains enabled.

## Independent review
Read-only protocol reviewer confirmed `ChatDeltaEventSchema` (`sessions-BwaoTke0.mjs:2399–2410`) has no committed-prefix or segment-completion field. `session.message` publishes stored/projected transcript messages without a sentence-commitment guarantee. Channel block streaming is a separate delivery contract, not a native operator-chat event guarantee. No compatible early immutable sentence path was found in the inspected installed version.

## Outcome
No early playback enabled and no new device build needed. Options for a subsequent decision: explicitly experimental speculative playback with acknowledged revision risk; an upstream committed-speech-segment contract; or keep final-only playback and reduce agent reply latency. A speculative audio cache validated against final text could save some post-final preparation, but cannot turn the3.4s first update into guaranteed audible output.
