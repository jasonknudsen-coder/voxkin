# Installer hardening — October 14, 2026

Local source changes only; no gateway restart, deployment or package publication.

- Explicit experimental Linux x86_64 setup opt-in; macOS default preserved.
- Optional container timestamp normalization after SHA-256 verification; strict runtime receipt check preserved.
- Optional real English/Spanish synthesis check before new receipt issuance; existing venv Python version checked.
- Package preparation fixes public distribution files to 0644 for nonroot service readability.
- Documented native-library TMPDIR requirements and read-only runtime probe distinction.

Validation: five installer regression tests passed; plugin Node suite 37 passed,
two opt-in host tests skipped; real Heart/Dora macOS worker synthesis passed
without changing the installed receipt; npm dry pack all 17 files readable by
service users; diff whitespace check passed. The revised setup has not been run
on a fresh Linux image. Existing Linux evidence uses the older build harness.
Next: qualify the shared installer in a disposable Debian x86_64 image before
promoting Linux beyond experimental. No local Docker executable was available.

One read-only reviewer overlapped implementation; no implementation lane.
One concrete packaging defect found; five recommendations accepted (native smoke,
timestamp ordering, venv version, permission normalization, read-only probe docs).
Overlap duration was not measured. Pilot start/finish tools rejected voxkin
as not allowlisted; no cockpit policy change or completion record fabricated.

## October15 follow-up

The shared installer now passed fresh Debian Bookworm and Ubuntu24.04 x86_64
container qualification, including final read-only/offline nonroot EN/ES synthesis
and restart receipt stability. See [qualification evidence](linux-qualification.md).
Historical validation above is retained; Windows and wider Linux coverage remain open.
