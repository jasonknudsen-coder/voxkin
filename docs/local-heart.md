# Local Heart voice

`tts-local-cli` invokes `scripts/kokoro_tts.py` via an isolated Python environment in `~/Library/Application Support/Voxkin/kokoro/.venv`. It uses local `kokoro-v1.0.onnx` and `voices-v1.0.bin` from the upstream kokoro-onnx model-files-v1.1 release. Dependencies are pinned in `scripts/kokoro-requirements.txt`; the upstream kokoro-onnx library/model use MIT/Apache-2.0 respectively, but the runtime stack also includes GPL components such as phonemizer/eSpeak. See [third-party notices](../plugin/THIRD_PARTY_NOTICES.md); do not treat the complete runtime as MIT/Apache-only.

The adapter is newly written for runtime use, not the audition script. Text arrives on stdin, never command arguments. Voice is fixed to `af_heart`, speed 1.0, en-us. Output is validated 24 kHz PCM16 WAV written atomically with private temporary permissions. Empty/oversized inputs fail with generic diagnostics. No runtime downloads or cloud synthesis are used by this adapter. Gateway provider/fallback configuration remains a separate concern.

## Installation and invocation

Create a Python 3.13 venv at the location above, install the pinned requirements, and place the two model files alongside it. Gateway CLI command is the quoted absolute venv Python path; args are the absolute adapter path, `--output`, and `{{OutputPath}}`. Omit `{{Text}}` to use the plugin's stdin path. Keep outputFormat `wav` and timeoutMs 120000.

## Verification

Observed on this Mac (fresh process each time): short reply 2.75 seconds / 3.32 seconds audio; ~1100-character text 9.29 seconds / 66.37 seconds audio. Empty and 4001-character input rejected with no stdout. These are individual measurements, not percentiles. Read-only review found one raw-length validation edge case, corrected to check before trimming. Gateway sample rate 24000 distinguishes Heart adapter output from prior Samantha 22050 output; complete live verification must wait for deferred config reload.

Configuration affects shared gateway TTS, not only Voxkin. No iPhone rebuild is needed. `scripts/samantha-rollback.json` preserves the previous local provider config for an explicit rollback. No automatic voice fallback is introduced.
