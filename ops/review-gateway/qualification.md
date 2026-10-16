# Private Linux container trial — October 14, 2026

## Result

Feasible for the next development stage; **not an end-to-end Apple review service**.
Image `sha256:3244e62ed69c31b09d9008461454e7bd4bab7e4374f97f3610e5ad8edffd5f10`
(size894150658 bytes) built on Linux x86_64 review host using digest-pinned Node24
and Python3.13 base images, OpenClaw2026.9.7 and the existing Python package pins.
Both model file hashes matched the source manifest;31 voices inventoried.

## Observed runtime evidence

- Voice test: real plugin runtime, network disabled, no model API key.
  Heart English short sentence:6693ms,177164-byte RIFF/WAVE.
  Dora Spanish short sentence:5804ms,115116-byte RIFF/WAVE.
  Cgroup peak memory525410304 bytes (~501MiB) for the voice probe.
  These are short-sentence cold-worker tests, not latency promises, quality
  listening tests, concurrent-user measurements or whole-gateway memory peaks.
- Real isolated OpenClaw host-load test passed in20.1seconds. Source plugin
  initialized; unauthenticated restricted hello returned401/DENIED. No model turn.
- Containment assertions passed: uid10001, loopback interface only, zero effective
  capabilities, no-new-privileges, root write rejected, no Docker socket,
  cgroup memory limit3GiB and CPU quota1vCPU. No bind mounts or published ports.
- Trial containers exited and were auto-removed. Image retained; no review server
  left running.13 pre-existing containers still running; edge health passes,
  zero failed systemd units, ~6.35GiB available memory, swap unused afterward.

## Corrections and limitations

1. Node22 was incompatible with package engine requirements. Disposable npm build
   step stopped (exit137 by our stop, not asserted OOM); rebuilt with Node24.
2. Docker layers rounded model mtimes to seconds, invalidating the strict receipt.
   Normalize timestamps before receipt generation; normal verifier unchanged.
3. Phonemizer copies a native library to temporary storage; noexec blocked loading.
   Keep /tmp noexec but provide dedicated64MiB /run/voice-tmp executable tmpfs
   (uid10001,mode0700,nosuid,nodev), with TMPDIR pointing there. This is an explicit
   writable/executable runtime exception, not a claim of a wholly noexec sandbox.
4. npm reported skipped dependency install scripts. Gateway and voice probes pass,
   but additional provider/native-package features are not qualified by that.
5. Independent read-only helper failed before review with backend403 authentication.
   Lead reviewed directly; no independent security signoff claimed. Existing pilot
   tooling does not allowlist this project; no allowlist change made.

## Repeat the private probes

Build using Dockerfile.trial with build CPU/memory limits, then on the host run:

```
sh ops/review-gateway/run-private-trial.sh voice
sh ops/review-gateway/run-private-trial.sh gateway
sh ops/review-gateway/run-private-trial.sh containment
```

## Remaining gates

Select/authorize dedicated model credential and cost limit; implement bounded model
only egress without access to review host services; durable review-only enrollment;
public route authentication/rate limits; full device conversation/audio test; idle
and concurrent load; then separately approve an immutable public deployment.
The current network-none trial says nothing about the safety of a future networked
configuration. Normal family enrollment and macOS setup are unchanged.

## Integrated private candidate — October14 follow-up

- Dedicated review API key stored outside Git and mounted only into the relay.
  Owner reports provider spend limits configured; they were not independently
  verified. Relay trial budget adds100 persistent requests,60/hour, one in flight,
 16k aggregate text characters and1,024 completion tokens. Text-only content parts
  emitted by the actual OpenClaw SDK are normalized; images/tools remain rejected.
- Real gateway starts with exactly one synthetic review agent; unauthenticated
  hello401, wrong invitation403, two independent active reviewer credentials,
  catalog readiness, and cross-device polling403 passed.
- Real model-to-audio proof passed for English and Spanish, then passed again after
  restart:123082-byte and102284-byte RIFF/WAVE responses. These are valid decoded
  audio artifacts, **not a claim of physical iPhone playback or listening QA**.
- Clean idle stop:exit0, no OOM, writer lock released. Active-speech shutdown:
 45.59seconds,exit0, no OOM, writer lock released. Gateway stop allowance is now
 360seconds to cover OpenClaw's325-second drain/shutdown budget; relay130seconds.
  Ingress admission closes before forwarding termination. Cleanup regression
  tests prove failed drains/runtime errors still attempt writer-lock release.
- Compose and dedicated Caddy candidate syntax validated without activation.
  Public DNS, Caddy activation, real off-network phone playback and Apple review
  submission remain pending. No personal gateway/default model changed.

### Runtime corrections found by live qualification

- Fresh config requires explicit default-agent cleanup: normal validated config,
  supported empty shared-auth migration with doctor, then agents delete. No
  personal state or credentials are present in that container.
- Review plugin dependencies must be included inside its captured package;
  nonroot-readable packaging is required. Bundle existing runtime beneath the
  review adapter instead of assuming escaping relative paths remain valid.
- Reply processing writes workspace metadata. Use a dedicated writable synthetic
  workspace in review state, not the immutable image workspace or family files.
- A15-second forced stop was insufficient and left a fail-closed writer lock.
  Recovery was performed only after the previous container was stopped; the stale
  lock was retained separately. No automatic stale credential-lock bypass added.

### Independent review / orchestration record

One read-only helper overlapped integration; no implementation files delegated.
Five concrete defects were identified (audio envelope, SSE type, stale sockets,
cleanup-on-error, shutdown allowance/admission), plus the restart-reset quota
caveat. Six recommendations were accepted and covered by fixes/tests. Exact
wall-clock overlap was not measured. Pilot/lane CLIs reject voxkin as not
allowlisted; no allowlist or cockpit policy was changed and no pilot completion
was fabricated.
