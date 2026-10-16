#!/bin/sh
# Private qualification only: NO published ports or proxy configuration.
set -eu
root=${1:?absolute isolated trial directory required}
case "$root" in /tmp/voxkin-review-candidate-*) ;; *) exit 2;; esac
image=voxkin-review-candidate:20261014
[ "$(docker image inspect voxkin-review-trial:20261014 --format '{{.Id}}')" = 'sha256:3244e62ed69c31b09d9008461454e7bd4bab7e4374f97f3610e5ad8edffd5f10' ]
docker build --network none -f "$root/source/ops/review-gateway/Dockerfile.candidate" -t "$image" "$root/source"
sudo install -d -m 700 -o 10001 -g 10001 "$root/egress" "$root/ingress" "$root/budget" "$root/state"
sudo chown 10001:10001 "$root/openai.key" "$root/reviewer.key"
sudo chmod 600 "$root/openai.key" "$root/reviewer.key"
docker run -d --name voxkin-review-relay-private --stop-timeout 130 --read-only --user 10001:10001 \
 --cap-drop ALL --security-opt no-new-privileges --memory 128m --memory-swap 128m --cpus .25 --pids-limit 32 \
 --mount "type=bind,src=$root/egress,dst=/egress" --mount "type=bind,src=$root/budget,dst=/budget" \
 --mount "type=bind,src=$root/openai.key,dst=/run/secrets/openai.key,readonly" \
 "$image" node /opt/voxkin/ops/review-gateway/appliance.mjs relay
[ -f "$root/expires-at" ] || (umask 077; date -u -d '+14 days' +%s > "$root/expires-at")
expires=$(cat "$root/expires-at")000
docker run -d --name voxkin-review-gateway-private --stop-timeout 360 --network none --read-only --user 10001:10001 \
 --cap-drop ALL --security-opt no-new-privileges --memory 3g --memory-swap 3g --cpus 1 --pids-limit 128 \
 --tmpfs /home/voxkin:rw,noexec,nosuid,nodev,size=128m,uid=10001,gid=10001,mode=0700 \
 --tmpfs /tmp:rw,noexec,nosuid,nodev,size=512m,mode=1777 \
 --tmpfs /run/voice-tmp:rw,exec,nosuid,nodev,size=64m,uid=10001,gid=10001,mode=0700 \
 -e TMPDIR=/run/voice-tmp -e REVIEW_EXPIRES_AT="$expires" \
 --mount "type=bind,src=$root/egress,dst=/egress,readonly" \
 --mount "type=bind,src=$root/ingress,dst=/ingress" --mount "type=bind,src=$root/state,dst=/home/voxkin/review" \
 --mount "type=bind,src=$root/reviewer.key,dst=/run/secrets/reviewer.key,readonly" "$image"
