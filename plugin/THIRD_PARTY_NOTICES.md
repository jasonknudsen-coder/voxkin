# Third-party components — pre-release inventory

This source package does not bundle model weights, Python wheels or eSpeak binaries.
Setup explicitly downloads upstream artifacts onto the gateway host. This is not
an exemption from component licensing; a full release distribution review is pending.

- Kokoro model/voice assets: upstream model card declares Apache-2.0.
  https://huggingface.co/hexgrad/Kokoro-82M
- kokoro-onnx: MIT, copyright 2025 github.com/thewh1teagle.
  https://github.com/thewh1teagle/kokoro-onnx/blob/main/LICENSE
- phonemizer and eSpeak NG: GPL-3.0 family licenses; preserve applicable notices
  and satisfy corresponding-source obligations when distributing covered binaries.
  https://github.com/bootphon/phonemizer/blob/master/LICENSE
  https://github.com/espeak-ng/espeak-ng/blob/master/COPYING
- Model card includes attribution to Koniwa (CC BY 3.0) and SIWIS (CC BY 4.0).
  Preserve the upstream attribution when distributing model assets.

See [runtime inventory](https://github.com/jasonknudsen-coder/voxkin/blob/main/docs/runtime-licenses.md) for all pinned dependencies and remaining verification items.
The app talks to this gateway plugin over a protocol and bundles none of this stack.
Project source is Apache-2.0; the repository remains private. This inventory is not legal clearance or authorization to redistribute third-party binaries.

Shell onboarding adds `qrcode` 1.5.4 (MIT), installed through npm with its own
upstream license and transitive dependency notices; it is not vendored here.
Development-only QR round-trip tests use `jsqr` 1.4.0 (Apache-2.0) and `pngjs`
5.0.0 (MIT). Exact dependency resolution is recorded in package-lock.json.
