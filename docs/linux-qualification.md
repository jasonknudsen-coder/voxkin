# Linux installer qualification — October 15, 2026

## Result

Passed clean environment tests on **Debian Bookworm/glibc x86_64** and
**Ubuntu 24.04/glibc x86_64** containers on an Ubuntu host. Ubuntu is
the primary documented user-facing target; Debian is the baseline.

- Current plugin0.4.1 source snapshot packaged with npm; nonroot plugin import passed.
- Actual shared installer with experimental Linux, install, smoke-test and
  container-timestamp flags; no reuse of the old review runtime or models.
- Python3.13.16; Node24.21.0. Ubuntu CPython built from checksum-verified upstream
  source rather than copying a Debian Python environment. Host Python unchanged.
- Build-time Heart English and Dora Spanish synthesis passed on both images.
- Exported images passed real EN/ES synthesis twice each, including restart:
  eight final-sandbox voice results, all nonempty 24kHz WAV.
- UID10001, read-only root, no network/ports/bind mounts/secrets, dropped
  capabilities, no-new-privileges, one CPU, 3GiB memory+swap, 128 PIDs. General
  tmpfs noexec; separate private64MiB executable voice tmpfs.
- Receipts unchanged between runs. Both final containers exited0, no OOM.

## Evidence and repeatability

Harness: `ops/linux-qualification/` (pinned base digests, CPython source hash,
shared-installer invocations, immutable image enforcement and probe).
Local evidence: `.tmp/linux-qualification/receipt.json`, distro runtime logs,
image IDs and frozen final source snapshot. Initial snapshot was copied from
current source, not presented as a clean git commit; unrelated app changes were
not sent to the host.

Initial harness failures were fixed: probe source inherited mode0600; Ubuntu
custom PATH omitted sbin account tools. Added explicit readable COPY, correct
PATH/account tooling, strict swap cap and immutable image-ID enforcement.
No plugin/installer product defect was found or patched.
One independent read-only reviewer overlapped build work; two final harness
recommendations accepted. Pilot recorder refused non-allowlisted project; no
cockpit policy bypass or fabricated record.

## Limits and operational state

This qualifies installer/package/native speech runtime in these containers. It
is **not** a new full OpenClaw host RPC or physical iPhone-to-Ubuntu test, a clean
Ubuntu desktop installation, every voice, ARM, Alpine/musl, or Windows/WSL test.
The explicit `--experimental-linux` guard remains; no broad platform promise.
Ubuntu's system Python3.12 alone does not meet the Python3.13 prerequisite.

The existing three-container review service was cleanly stopped with exit0 and
preserved, including state, images and accounting. No public route, credential,
production image or other app was changed. Qualification containers are stopped.
The review endpoint remains unavailable until a deliberate restart; don't
silently replace it with a qualification image. Windows route/access awaits Maintainer.
