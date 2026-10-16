# Everyday reliability pass — 2026-10-13

Scope: dictation finalization, repeated captures, foreground/reconnect without
replay, Stop/Cancel during waiting or audio preparation, correlated progress.
Bluetooth/AirPods/phone-call paths explicitly excluded at Maintainer's request.

## Review findings and fixes
- Missing/blank final assistant output cleared the active run but left the turn
  waiting until the 120-second deadline. Empty finals now settle ready without
  TTS, with a visible no-text-completion status.
- Unexpected socket close while agents.list completed did not change the
  connection generation; a stale completion could resurrect Connected on a dead
  socket. Unexpected close now runs full disconnect cleanup and invalidation.
- Both regressions failed before fixes. A repeated-capture test also verifies
  saved old text/end callbacks cannot alter or submit a newer recording.

## Evidence
- Initial core suite:20 passed, `/tmp/iris-reliability-core.log`.
- Initial iOS run:24 app +3 UI passed, `/tmp/iris-reliability-all.log`.
- Two additional pre-fix cases failed, `/tmp/iris-reliability-before.log`.
- Post-fix result recorded in qualification.md after completion.

## Physical follow-up (not automated claims)
1. Three successive dictated requests with different distinctive final words;
   confirm retained/submitted words and exactly one reply each.
2. Start a harmless reply, press Stop before speech starts; ensure it stays
   silent. Cancel is separate and cannot undo already completed actions.
3. Start a harmless reply, background/reopen; verify reconnect, no old speech
   replay, and a fresh request succeeds.
4. Start a harmless longer request and verify useful interim/progress text;
   Cancel while pending, then verify a new turn works.

Spanish support is separately requested next. Do not claim physical reliability
complete or change language/voice settings as part of this pass.

## Updated acceptance — 2026-10-13, 18:59 MDT

User reports the remaining phone checklist complete except AirPods. The earlier
Spanish-next note is historical: Spanish input/output and voice selection are now
implemented and user-tested. AirPods remain unqualified; this acceptance does not
establish every Bluetooth route or phone-call interruption case.
