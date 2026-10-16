"""Build-time Linux-only qualification harness; does not change the macOS installer."""
import importlib.util
import json
import os
from pathlib import Path
import urllib.request
import numpy as np

plugin = Path('/opt/voxkin/plugin')
root = Path('/opt/voxkin/voice')
spec = importlib.util.spec_from_file_location('voice_setup', plugin / 'setup.py')
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)
for name, expected in setup.ARTIFACTS.items():
    target = root / name
    with urllib.request.urlopen(setup.BASE + name, timeout=90) as source, target.open('wb') as out:
        total = 0
        while chunk := source.read(1024*1024):
            total += len(chunk)
            if total > 512*1024*1024:
                raise RuntimeError('Artifact too large')
            out.write(chunk)
    if setup.digest(target) != expected:
        raise RuntimeError('Artifact hash mismatch')
    # Docker layer export normalizes timestamps to whole seconds.
    stamp = int(target.stat().st_mtime)
    os.utime(target, (stamp, stamp))
with np.load(root / 'voices-v1.0.bin', allow_pickle=False) as voices:
    supported = json.loads((plugin / 'voices.json').read_text())
    available = [v['id'] for v in supported if v['id'] in voices.files]
receipt = {'version': 1, 'voices': available, 'files': {
    name: {'size': (root/name).stat().st_size, 'mtimeMs': (root/name).stat().st_mtime*1000}
    for name in setup.ARTIFACTS}}
(root / 'verified.json').write_text(json.dumps(receipt))
print('Verified voice model hashes and inventory:', len(available))
