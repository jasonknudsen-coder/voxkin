Superseded by the integrated `plugin/restricted/windows-dispatch.js` adapter.
This directory preserves the original feasibility spike; do not deploy its
unverified JSON reply filtering. Current evidence is in `docs/windows-qualification.md`.

# Native Windows public agent-ingress spike — not a release implementation

The exported `agentCommandFromIngress` from
`openclaw/plugin-sdk/agent-runtime` bypasses the failing channel-reply path on
OpenClaw 2026.9.7. It is not a CLI subprocess, owner RPC proxy, private dist
import, or runtime monkeypatch. The public ingress sanitizer forces
`senderIsOwner: false` and strips gateway-only authority fields.

## Experiment

`dispatch.js` is a disposable replacement for `package/restricted/dispatch.js`
on the isolated Windows test installation only. `proof.mjs` runs from that
installation root, beside `package/`, with `prepare` then `resume` separated by
a gateway restart. It saves temporary test credentials only to the existing
protected `secrets/` directory; never publish that file. The standard native E2E
probe is also required. Keep original plugin and worker module backups.

Host worker was restored and hash-verified against its original backup before
testing. EN/ES model turns, WAV synthesis, history CRUD, pending-device denial,
cross-device isolation, and revocation passed. Two-turn random-code recall passed.

## Why this is not shipped yet

- Agent command JSON projection drops `isReasoning` / `isCommentary` flags.
  Filtering those flags in this spike is therefore NOT a sufficient final-only
  guarantee. Inspect upstream projection/generation and harden before adopting.
- Explicitly force verbosity off, reject error/meta failure results, and verify
  behavior across configured model harnesses (only the test model was exercised).
- Empty `toolsAllow` and `disableMessageTool` are forwarded by the host attempt
  executor; validate the effective tool list and malicious tool requests too.
- Keep synchronous detached admission, device/conversation-derived session keys,
  cancellation, approved-agent checks and per-device history isolation.
- Add focused adapter tests and lifecycle/revocation tests before integration.
- No shared macOS/Linux route changes are needed to prove a Windows-only adapter.

Independent reviewer failed to start (provider 403); source tracing and live
checks were performed by the lead agent, not independently reviewed.

The random-code recall also passed after a gateway process restart. An immediate
abort returned without a final reply in a subsequent poll. This does not yet
prove cancellation at every execution stage or absence of late side effects.
