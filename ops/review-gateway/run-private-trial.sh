#!/bin/sh
# Run on the trial host after building the image. Never publishes a port.
set -eu
image=${VOXKIN_TRIAL_IMAGE:-voxkin-review-trial:20261014}
mode=${1:-voice}
case "$mode" in
  voice) set -- node --input-type=module -e 'await import("/opt/voxkin/voice-probe.mjs"); const fs=await import("node:fs/promises"); console.log("memoryPeakBytes="+(await fs.readFile("/sys/fs/cgroup/memory.peak","utf8")).trim());' ;;
  gateway) set -- node --test /opt/voxkin/plugin/restricted/host-load.test.js ;;
  containment) set -- node --input-type=module -e 'import assert from "node:assert/strict"; import fs from "node:fs/promises"; import os from "node:os"; assert.equal(process.getuid(),10001); assert.deepEqual(Object.keys(os.networkInterfaces()),["lo"]); const s=await fs.readFile("/proc/self/status","utf8"); assert.match(s,/CapEff:\s+0+\n/); assert.match(s,/NoNewPrivs:\s+1/); await assert.rejects(fs.writeFile("/opt/voxkin/write-probe","x"),e=>e.code==="EROFS"||e.code==="EACCES"); await assert.rejects(fs.stat("/var/run/docker.sock"),e=>e.code==="ENOENT"); assert.equal((await fs.readFile("/sys/fs/cgroup/memory.max","utf8")).trim(),"3221225472"); assert.equal((await fs.readFile("/sys/fs/cgroup/cpu.max","utf8")).trim(),"100000 100000"); console.log("Containment checks passed: nonroot, loopback only, zero capabilities, no-new-privileges, read-only root, no Docker socket, 3GiB/1CPU limits");' ;;
  *) echo 'Expected voice, gateway or containment' >&2; exit 2 ;;
esac
docker run --rm --network none --read-only --user 10001:10001 \
  --cap-drop ALL --security-opt no-new-privileges \
  --memory 3g --memory-swap 3g --cpus 1 --pids-limit 128 \
  --tmpfs /tmp:rw,noexec,nosuid,size=512m,mode=1777 \
  --tmpfs /run/voice-tmp:rw,exec,nosuid,nodev,size=64m,uid=10001,gid=10001,mode=0700 \
  -e TMPDIR=/run/voice-tmp \
  --tmpfs /home/voxkin:rw,nosuid,size=16m,uid=10001,gid=10001,mode=0700 \
  -e VOXKIN_HOST_LOAD_TEST=1 -e OPENCLAW_TEST_CLI=/usr/local/bin/openclaw \
  "$image" "$@"
