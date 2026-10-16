# Complete Ubuntu gateway candidate — October 15, 2026

**Ubuntu is LIVE. Maintainer confirmed physical iPhone English, Spanish and background/reopen reconnect. Superseded Debian review containers/images retired October 15.**

Immutable candidate: `sha256:e889a93bdc8f04b379621571f3a2db64279a0ad420616783f79de325812d8e47`.
Built on the qualified Ubuntu24.04/Python3.13.16 runtime with fresh
OpenClaw2026.9.7 installation and plugin0.4.1 source. See
`ops/review-gateway/Dockerfile.ubuntu`; the build explicitly verified the local
base image ID before using its tag (BuildKit attempted registry resolution for
its local digest reference).

## Live private evidence

- All three service checks passed: relay, gateway and HTTP ingress.
- Unauthenticated access denied; independent reviewer device enrollments passed.
- Real model responses, followed by real Heart English and Dora Spanish WAV.
- Cross-device run polling and history access rejected.
- Conversation create, persisted assistant history, rename and delete passed.
- HTTP ingress separately passed enrollment, catalog and both voice responses;
  unrelated path rejected with403 (not404).
- Gateway network disabled, read-only/nonroot, bounded executable voice tmpfs;
  no published candidate ports. Only the model relay had network egress.
- Existing review budget preserved, increased from5 to7 for the two model tests.
  Fixed expiry unchanged. Original state/key/invitation untouched.

Evidence: `.tmp/ubuntu-gateway/live-proof.log`, `ingress-proof.log`, and the
prepared `activation-plan.md`. Private invitation/device credentials are not
included in documentation. Private test containers are stopped after proof.

## Corrections and limits

The first fixture invitation used the wrong length; corrected to43-character
base64url. The historical live probe omitted the now-required conversation ID;
updated it to exercise the new history contract. The HTTP negative-path assertion
was corrected to match the intentional403. No product defect was patched.
The requested read-only review child failed authorization before reviewing this
integration; no independent integration approval is claimed. Earlier Linux
component review remains separate. Lead inspected integration and live proof.

The public review stack remains stopped and preserved. Candidate activation must
preserve its state, keys, budget and expiry, and retain the old image for rollback.
An actual iPhone session through the public Ubuntu gateway remains outstanding.

## Approved activation — October15

Maintainer provided the production phrase. Existing review stack now runs the exact
qualified Ubuntu image. All three services report healthy; public HTTPS returns
the expected401/DENIED without credentials. Existing keys, enrolled-device records,
budget and expiry were compared to pre-activation backup and are unchanged.
Protected old env/state backup and old image retained. Private qualification
containers remain stopped. Historical pending-activation text above is superseded
by this receipt: `.tmp/ubuntu-gateway/activation-receipt.json`. Phone acceptance
is the next step using the existing review connection.

## Debian retirement and Apple access — October 15

At Maintainer’s explicit request, removed the three stopped Debian private review
containers plus the old candidate/trial images. Live Ubuntu services stayed
healthy; state, credentials, accounting, expiry and protected backups were not
changed. Old image rollback is no longer available without rebuilding. Debian
installer qualification artifacts are separate and were not removed.

Apple private TestFlight review notes and App Review details now contain the
existing invitation and verified current Settings labels, the unchanged public
hostname, automatic review-only approval explanation and expiry. Both were read
back and matched exactly. No build submission or release was performed.
See `app-store/review-access.md` and `.tmp/debian-retirement/receipt.json`.
