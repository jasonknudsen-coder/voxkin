# Runtime license inventory — 2026-10-13

Scope: all 16 direct/pinned entries in plugin/requirements.txt. Source package
contains no model weights, Python wheels, eSpeak or libsndfile binaries. Setup
fetches upstream artifacts separately; that does not erase upstream obligations.
The following is installed-distribution metadata plus existing upstream references,
not verification of every file in every wheel.

- attrs 26.1.0 — MIT
- cffi 2.1.1 — MIT-0
- cloudpickle 3.1.2 — BSD-3-Clause
- dlinfo 2.0.0 — MIT
- espeakng-loader 0.2.4 — wheel license metadata absent; inspect loader license and
  bundled eSpeak binary/source/notice obligations before any binary distribution.
- flatbuffers 25.12.19 — Apache-2.0
- joblib 1.6.0 — BSD-3-Clause
- kokoro-onnx 0.6.1 — wheel metadata absent; upstream MIT per linked license in
  plugin/THIRD_PARTY_NOTICES.md; verify exact distributed version when packaging.
- numpy 2.5.4 — composite BSD/MIT/Zlib/CC0 notices; retain wheel license inventory.
- onnxruntime 1.31.0 — MIT; inspect included third-party notices if redistributing.
- packaging 26.3 — Apache-2.0 OR BSD
- phonemizer 3.4.0 — GPL-3.0 family; retain exact upstream license.
- protobuf 7.36.2 — BSD-3-Clause
- pycparser 3.11 — BSD-3-Clause
- soundfile 0.14.0 — BSD-3-Clause wrapper; bundled libsndfile obligations are separate.
- typing_extensions 4.16.0 — PSF-2.0

Model and voice assets: upstream Kokoro model card declares Apache-2.0 and includes
Koniwa CC BY 3.0 / SIWIS CC BY 4.0 attribution. Preserve upstream attribution when
redistributing those assets. eSpeak NG has GPL-family terms; libsndfile has separate
license requirements. Do not infer a uniform Apache license for the runtime.

## Still required for binary/model distribution

Verify exact artifact licenses, embedded notices and source-offer/source delivery
obligations, package the applicable texts/attribution, and review modified binaries.
This project currently distributes none of these artifacts. Runtime dependencies
are version-pinned but not wheel-hash locked; model downloads are checksum-verified.
A future installer/binary release needs its own supply-chain and license review.
