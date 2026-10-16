#!/bin/sh
set -eu
image=${1:?Pass the immutable qualification image ID}
name=${2:-voxkin-linux-qualification-20261015}
case "$image" in sha256:*) ;; *) echo 'Use an immutable sha256 image ID' >&2; exit 2;; esac
docker run --name "$name" --network none --read-only --user 10001:10001 \
 --cap-drop ALL --security-opt no-new-privileges --pids-limit 128 --memory 3g --memory-swap 3g --cpus 1 \
 --tmpfs /tmp:rw,noexec,nosuid,nodev,size=64m,mode=1777 \
 --tmpfs /voice-tmp:rw,exec,nosuid,nodev,size=64m,uid=10001,gid=10001,mode=0700 \
 --env TMPDIR=/voice-tmp "$image"
# Same image and unchanged receipt in a fresh process, no installer rerun.
docker start --attach "$name"
test "$(docker inspect --format '{{.State.ExitCode}}' "$name")" = 0
docker inspect --format 'exit={{.State.ExitCode}} oom={{.State.OOMKilled}} readonly={{.HostConfig.ReadonlyRootfs}} network={{.HostConfig.NetworkMode}} image={{.Image}}' "$name"
