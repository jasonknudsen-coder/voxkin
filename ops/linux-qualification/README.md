# Clean Linux shared-installer qualification

These isolated images exercise the shipped `plugin/setup.py`, not the historical
review-gateway build harness. They contain no gateway config, invitation, API key,
private transcript or host bind mount. Do not use them to replace the review service.

## Build

From a source snapshot containing `plugin/` and this directory:

```sh
docker build -f ops/linux-qualification/Dockerfile -t voxkin-linux-qualification:test .
docker build -f ops/linux-qualification/Dockerfile.ubuntu -t voxkin-ubuntu-qualification:test .
```

Use `--no-cache` for the initial clean install. The Debian image supplies Python
3.13 from its pinned official image. Ubuntu 24.04 builds the matching CPython
3.13.16 from checksum-verified python.org source under `/opt/cpython`, without
replacing system Python. Both use Node from the same pinned official Node image.
Ubuntu's default Python 3.12 is not sufficient for this installer.

The plugin is packed and installed as a package, then the shared installer runs
as UID10001 with `--experimental-linux --install --smoke-test
--container-timestamps`. Models are freshly downloaded and SHA-256 checked; the
result is baked into the image rather than borrowed from the old service.

## Final sandbox

```sh
sh ops/linux-qualification/run.sh "$(docker image inspect voxkin-linux-qualification:test --format '{{.Id}}')" unique-test-container
```

Runs twice (including a stopped-container restart): UID10001, read-only root,
network none, no host mounts or published ports, dropped capabilities, no new
privileges, one CPU, 3GiB memory+swap cap, 128 PIDs, noexec general temporary space,
and a private bounded executable voice tmpfs. It preserves the stopped test
container and image as evidence. No installer rerun repairs the final receipt.

The probe imports the packaged plugin, requests the runtime catalog, synthesizes
Heart English and Dora Spanish, checks nonempty 24kHz WAV, and asserts the receipt
is unchanged. This is installer/package/native-runtime qualification, **not** a
live OpenClaw host RPC, remote phone playback, native Windows, ARM, or Alpine test.

## Review notes

Independent read-only review caught no product defect. Harness corrections:
explicit probe readability (COPY mode0644), strict memory+swap cap and immutable
image-ID enforcement. The cockpit pilot recorder rejected `voxkin` as not
allowlisted; no registry/allowlist was modified. One read-only helper overlapped
build work; overlap timing was not measured.
