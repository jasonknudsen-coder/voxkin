# Clean shared-installer qualification; no gateway configuration or public ports.
FROM node:24-bookworm-slim@sha256:d6aa754f16b3197301076f047b5def2f02ea1dbbc2ca920407d46d7ec7f87b20 AS node
FROM python:3.13-slim-bookworm@sha256:a1165e272e578941b84abc79e4ab38a0305cd12803a5c4247979ac7655f4d641
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
 && apt-get update && apt-get install -y --no-install-recommends ca-certificates libsndfile1 \
 && rm -rf /var/lib/apt/lists/*
COPY plugin /opt/source/plugin
RUN cd /opt/source/plugin && npm pack --pack-destination /tmp \
 && npm install --prefix /opt/qualification --omit=dev --ignore-scripts --legacy-peer-deps /tmp/voxkin-openclaw-local-voice-*.tgz
RUN groupadd -g 10001 voxkin && useradd -u 10001 -g voxkin -m voxkin \
 && install -d -o 10001 -g 10001 /opt/voxkin/voice
COPY --chmod=0644 ops/linux-qualification/probe.mjs /opt/qualification/probe.mjs
ENV HOME=/home/voxkin OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1
USER 10001:10001
RUN python3.13 /opt/qualification/node_modules/@voxkin/openclaw-local-voice/setup.py \
 --runtime-dir /opt/voxkin/voice --experimental-linux --install --smoke-test --container-timestamps
WORKDIR /opt/qualification
CMD ["node", "probe.mjs"]
