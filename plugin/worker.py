#!/usr/bin/env python3
"""Offline English/Spanish synthesis; explicit language and voice arrive as JSON on stdin."""
import argparse
import json
import logging
import os
from pathlib import Path
import sys
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--models', type=Path, default=Path.home() / 'Library/Application Support/Voxkin/kokoro')
    args = parser.parse_args()
    raw = sys.stdin.read(50001)
    if len(raw) > 50000: raise ValueError('Request too large')
    request = json.loads(raw)
    text = request['text']
    voices = {voice['id']: voice for voice in json.loads(Path(__file__).with_name('voices.json').read_text())}
    selected = voices[request['voice']]
    language = selected['language']
    locale = {'en-US': 'en-us', 'en-GB': 'en-gb', 'es-ES': 'es'}[selected['locale']]
    if request['language'] != language or not isinstance(text, str) or not text.strip() or len(text) > 4000:
        raise ValueError('Invalid speech request')
    logging.disable(logging.CRITICAL)
    import numpy as np
    import soundfile as sf
    from kokoro_onnx import Kokoro
    engine = Kokoro(str(args.models / 'kokoro-v1.0.onnx'), str(args.models / 'voices-v1.0.bin'))
    samples, rate = engine.create(text, voice=request['voice'], speed=1.0, lang=locale)
    if rate != 24000 or not len(samples) or not np.isfinite(samples).all():
        raise ValueError('Invalid synthesized audio')
    if len(samples) / rate > 360:
        raise ValueError('Audio exceeds duration limit')
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=args.output.parent, prefix='.voxkin-tts-', suffix='.wav', delete=False) as f:
            temporary = Path(f.name)
        sf.write(str(temporary), samples, rate, subtype='PCM_16')
        os.replace(temporary, args.output)
        temporary = None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Never echo private reply text, arguments or exception details.
        print('Local voice synthesis failed: ' + type(error).__name__, file=sys.stderr)
        sys.exit(1)
