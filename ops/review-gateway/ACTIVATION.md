# Review gateway activation runbook

Review endpoint: `https://review.example.com/voxkin/v1`.
Purpose: Apple beta review only; family gateway/config/auth remain unchanged.

## Approval boundary

Record the frozen source commit and qualified immutable Linux image ID. Public
activation requires the owner's configured production confirmation for this
review service; the prior website approval does not cover it. The project is not
allowlisted in the approval cockpit: do not invent a grant or change the allowlist.

## After approval

1. Recheck image/source and host health. Create a protected application root with
   state/ingress/egress/budget directories owned by10001,mode0700. Never mount
   personal workspace/auth or Docker socket.
2. Install the authorized dedicated API key in protected secrets storage. Generate
   a NEW review invitation and fixed14-day expiry; do not reuse qualification
   credentials, devices, sessions or accounting. Only its hash enters config.
3. Set `VOXKIN_REVIEW_IMAGE` to the qualified immutable image ID and
   `REVIEW_EXPIRES_AT` to fixed epoch milliseconds. Start `compose.candidate.yaml`
   using the already-loaded image; check all three healthchecks and private
   conversation/audio proof. Confirm18149 is unused: only ingress is published,
   on host127.0.0.1:18149. Operator gateway ports are never published.
4. Add the approved DNS record and dedicated `review.caddy` route. Validate the
   combined Caddy config before reloading. Test TLS, denied credentials, exact
   route restriction, text/voice and unrelated-site health.
5. Disconnect any existing phone gateway before opening the review invitation.
   Test the uploaded build on a physical iPhone against the public endpoint: enrollment,
   English/Spanish playback, reconnect and second-device isolation. Server WAV
   generation does NOT satisfy that physical test. Wi-Fi is sufficient when it
   reaches the same public endpoint; cellular is optional, not a separate gate.
6. Save invitation, URL, expiry and concise steps in Apple's PRIVATE beta review
   field, verify contacts, then submit external TestFlight review. Public App
   Store release and CarPlay runtime qualification remain separate.

## Operations and rollback

- Trial budget:100 total requests,60/hour,1,024 maximum completion tokens. Read
  `budget/usage.json` for count; provider dashboard is authoritative for billing.
  Never reset accounting automatically or call it a verified dollar cap.
- Healthchecks detect unavailable routes. First candidate is operator-managed
  (`restart: no`). Install executable `health-check` at the application's
  `ops/health-check`; configure an operator-managed timer to invoke the hook every15 minutes
  and deliver failure/recovery alerts. Keep the fixed expiry
  in `expires-at` (epoch seconds) alongside state. The hook checks all three
  routes, warns before85/100 requests and within24hours of expiry. Confirm the
  existing monitor's alert delivery/recovery coverage before unattended review.
- Stop ingress, then gateway (360-second allowance), then relay (130seconds).
  Preserve reviewer state/accounting for clean restart. An unclean-shutdown writer
  lock fails closed: verify no prior writer remains before documented manual
  recovery. Never weaken the AccessStore lock checks.
- Rollback removes only this dedicated Caddy route (validate/reload), then stops
  this Compose project. Retain protected state for diagnosis; leave the static
  Voxkin site, other apps and personal gateway untouched.
- At expiry deactivate the review service and revoke its API key when no longer
  needed. Renewal is a new explicit fixed review window.
