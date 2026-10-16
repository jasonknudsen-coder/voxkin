# Apple review gateway — isolated review service

Purpose: a fully functional isolated environment for Apple's external beta review,
with synthetic data and text/voice responses. No personal gateway connection,
owner credentials, account history, messaging channels, admin UI or tools.

## Current service — October 15, 2026

Ubuntu now serves the existing public review hostname. Physical iPhone English,
Spanish and background/reopen reconnect passed per Maintainer. Superseded Debian
review containers/images were removed at his request; live state and credentials
remain unchanged. Apple private TestFlight and App Review instructions were saved
and read-back verified. No review submission performed. See
`../../docs/app-store/review-access.md` for current steps. Earlier status below
is historical and superseded by this section.

## Findings (October 14, 2026)

- Qualification used Linux x86_64 with Docker. Provision sufficient resources
  for the gateway and local speech models.
- Existing voice setup.py explicitly accepts only macOS/Python3.13. Linux voice
  support is not established by the current automated host-load tests. Do not
  silently remove the platform guard and call the runtime qualified.
- Standard restricted enrollment uses a one-time10-minute invite and explicit
  owner approval. Apple may open review days after submission and use multiple
  devices. A static invitation pasted in review notes is not a reliable login.
- Restricted mode cannot paste a preissued device bearer directly into settings;
  it must enroll. Owner mode would grant general operator API access and is not
  an acceptable substitute on the personal gateway.

## Intended boundary

- Separate container/process, HOME, state, workspace, access store and model
  credential. Only the synthetic workspace here is mounted into the agent context.
- A dedicated hostname and restricted HTTPS endpoint, no public operator RPC or
  management UI. Administration remains local to the host.
- Tools/commands disabled; model API account/budget selected by owner. No copied
  personal model auth-profile file or personal OpenClaw configuration.
- CPU/memory/request/concurrency limits so review load cannot exhaust review host.
- Reviewer secret only in Apple's private review field and server secret storage;
  never a public website, Git repo, screenshot or chat log.

## Work required before submission

1. Confirm model account/usage and review contact details. Reviewer telephone
   should be entered privately in Apple, not posted in the project channel.
2. Qualify the real Linux voice runtime in an isolated build/test environment, or
   choose another isolated host that supports the existing qualified runtime.
3. Design review-only durable onboarding. Prefer an authenticated enrollment
   broker that issues fresh per-device restricted access without exposing owner
   credentials. Keep any automatic review approval isolated from normal family
   enrollment. Test wrong secrets, retries, revocation and cross-device isolation.
4. Real end-to-end model response and playable English/Spanish audio, followed by
   an off-network iPhone run using the uploaded build. Mocks do not satisfy this.
5. Prepare a concrete deployment candidate and obtain fresh production approval
   for this service; the earlier website-only authorization does not cover it.
6. Populate Apple review credentials/instructions, verify contact details and
   submit external beta review. No review has been submitted by this preparation.

## Current status — October 14, 2026

The isolated service was publicly activated with owner authorization. Public
HTTPS, authentication, English/Spanish model-to-audio probes, per-device isolation,
and host health checks passed. Maintainer confirmed voice preview and an English
conversation played successfully in the uploaded iPhone build over Wi-Fi.
Physical Spanish playback and background/reopen acceptance remain unconfirmed;
external Apple review has not been submitted. A poor-signal LTE request failed;
that observation alone does not establish the cause.

The findings and preparation checklist above describe the original deployment
work. See [qualification](qualification.md) for staged evidence and
[activation](ACTIVATION.md) for the deployment procedure.

## Fresh-install lessons

- Use the qualified Node24/Python3.13 container stack. Node22 did not meet the
  package engine requirements. This Linux container qualification does not make
  the macOS-only user installer a supported Linux installer.
- Generate runtime receipts after normalizing model timestamps for Docker layers;
  do not weaken checksum/receipt verification to work around packaging changes.
- Phonemizer needs a temporary executable native-library copy. Keep the dedicated
  bounded voice tmpfs exception; general /tmp can remain noexec.
- Package all plugin dependencies inside the captured plugin directory and make
  them readable by the nonroot runtime. Relative imports outside it do not survive
  OpenClaw package capture.
- Initialize through validated config and the supported empty shared-auth
  migration before deleting the disposable bootstrap agent. Verify the isolated
  configuration contains exactly the intended synthetic agent.
- Give the synthetic agent a dedicated writable workspace: reply processing
  writes metadata even when tools are disabled. Never solve this by mounting a
  personal workspace or making the entire image writable.
- Allow graceful gateway drain (360 seconds here). A short forced stop can leave
  a fail-closed writer lock; never automatically bypass that lock.
- Test monitoring as the actual restricted monitoring account. Its accounting
  check now reads through the relay container; protected state permissions were
  not loosened to make the health check pass.
- On the iPhone, disconnect the existing gateway before opening the review
  invitation. Verify the review endpoint is selected before testing.
- Test actual phone playback separately from server WAV generation. Either Wi-Fi
  or cellular can reach the public endpoint; cellular is not a mandatory extra
  gate when Wi-Fi uses that same public route. Still test background/reopen.
- Measure reply time and audio request time separately. One phone sample took
  11.4 seconds to complete text and 21.9 seconds for the audio request including
  transfer (33.4 seconds send-to-audio; playback setup 0.1 seconds). This is an
  observation, not a latency guarantee or proof of a CPU/network bottleneck.

## Private Linux qualification trial

`Dockerfile.trial` installs the exact existing Python dependency pins and model
hashes, alongside OpenClaw2026.9.7. `prepare-linux-trial.py` is a separate experiment;
it does not relax the macOS installer's platform guard. `voice-probe.mjs` checks
real English/Spanish synthesis through the normal plugin runtime, not canned WAVs.
Build access is needed for package/model downloads. The test runtime has **no
network**, no bind mounts, no credentials and no public/published ports.

This does not yet demonstrate model egress restrictions, public authentication,
reviewer onboarding, or an end-to-end iPhone review service. Those are distinct
qualification gates before any production activation.

Runtime exception: phonemizer requires a writable/executable temporary shared-library
copy. The trial grants a dedicated64MiB tmpfs for this, not execution in general /tmp.
See qualification.md for exact evidence and limitations.

## Private candidate (under qualification)

The review-only adapter is separate from the distributable family plugin. Its
14-day enrollment window grants up to20 independently revocable devices, each
assigned only to the synthetic review agent. The expiry is fixed in the trial
state, not renewed by ordinary container restarts.

`run-candidate.sh` builds on the verified trial image, checks its immutable image
ID, and starts two nonroot/read-only/resource-limited containers with **no published
ports**. The gateway has no network interface beyond loopback. Unix sockets carry
model requests to a separate relay and restricted HTTP requests into the gateway.
Only the relay receives the provider credential. Its fixed upstream is
`api.openai.com`, with public IPv4 resolution, no redirects, one model, no tools,
16,000 aggregate text characters, and1,024 maximum completion tokens. The relay
has ordinary Docker networking: its destination restriction is application-level,
**not a host egress firewall**. Do not represent this as kernel-enforced relay egress.

The relay allows one in-flight request,60/hour (process-local), and100 total
requests (persisted before sending; fails closed on invalid accounting). The
100-request allowance survives restarts and needs an explicit operator reset or
new qualification environment. It is a trial request allowance, not a dollar
budget; provider spend settings have not been independently verified.

Fresh isolated OpenClaw initialization goes through the validated config CLI,
then its supported empty shared-auth migration before deleting the disposable
bootstrap agent. Its synthetic workspace is copied into separate writable review
state because the real reply path writes workspace metadata, even with tools
disabled. The runtime guard requires exactly one review agent and the
explicit isolated-review marker. Normal personal gateway state is never mounted.

The Docker build bundles a copy of the existing plugin runtime beneath the
review-only package. OpenClaw captures plugin packages into a staging directory;
imports escaping that package are unavailable. No distributable family-plugin
source changes are needed. This adapter is not intended for a family gateway.

Independent read-only review identified the voice envelope limit, SSE forwarding,
persistent request accounting, and stale Unix socket handling; corrections and
regression tests were added. The cockpit pilot CLI rejected this project as not
allowlisted, so no cockpit registry or policy was changed. Local regression
results are not a substitute for live model/audio and physical-phone qualification.
