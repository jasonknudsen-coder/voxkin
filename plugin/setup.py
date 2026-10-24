#!/usr/bin/env python3
"""Explicit host setup. No gateway configuration changes or automatic downloads at startup."""
import argparse
from contextlib import contextmanager, nullcontext
import secrets
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import tempfile
import urllib.request
import wave

ARTIFACTS = {
    'kokoro-v1.0.onnx': 'beb0d1848dee9a49da392cc3df26958d46cfa35d321edf434f52949153f0df3a',
    'voices-v1.0.bin': 'bca610b8308e8d99f32e6fe4197e7ec01679264efed0cac9140fe9c29f1fbf7d',
}
BASE = 'https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.1/'
def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()
def venv_python(root, system=None):
    return root / (".venv/Scripts/python.exe" if (system or platform.system()) == "Windows" else ".venv/bin/python")

def validate_platform(system, machine, experimental_linux, experimental_windows=False):
    if system == 'Darwin':
        return
    if system == 'Linux' and machine in ('x86_64', 'AMD64') and experimental_linux:
        return
    if system == 'Windows' and machine in ('AMD64', 'x86_64') and experimental_windows:
        return
    raise ValueError('Windows AMD64 requires --experimental-windows. Setup supports macOS; Linux x86_64 requires --experimental-linux. Other platforms are not qualified.')


def smoke_test(root, available):
    """Exercise real native loading and EN/ES synthesis without logging speech."""
    samples = {'af_heart': ('en', 'Hello.'), 'ef_dora': ('es', '¿Cómo estás? Mañana hablamos español.')}
    with tempfile.TemporaryDirectory(prefix='voxkin-setup-') as directory:
        output = Path(directory) / 'probe.wav'
        for voice, (language, text) in samples.items():
            if voice not in available:
                raise RuntimeError('Smoke test requires Heart and Dora.')
            result = subprocess.run([
                str(venv_python(root)), '-X', 'utf8', str(Path(__file__).with_name('worker.py')),
                '--models', str(root), '--output', str(output)],
                input=json.dumps({'voice': voice, 'language': language, 'text': text}),
                capture_output=True, text=True, encoding="utf-8", timeout=45)
            if result.returncode:
                raise RuntimeError('Voice smoke test failed. On hardened Linux, ensure gateway TMPDIR permits native library loading; see README.md.')
            with wave.open(str(output), 'rb') as audio:
                if audio.getframerate() != 24000 or audio.getnframes() == 0:
                    raise RuntimeError('Voice smoke test produced invalid audio.')
            output.unlink()


def normalize_timestamps(root):
    # Opt-in for container layer export; never relax the runtime verifier.
    for name in ARTIFACTS:
        target = root / name
        info = target.stat()
        os.utime(target, ns=(info.st_atime_ns, (info.st_mtime_ns // 1_000_000_000) * 1_000_000_000))


def progress(message):
    print('VOXKIN_PROGRESS ' + message, file=sys.stderr, flush=True)


@contextmanager
def runtime_lock(root):
    """Fail closed on another setup, including stale locks; never auto-unlock."""
    path = root / '.setup.lock'
    token = secrets.token_hex(24)
    try:
        descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError:
        raise RuntimeError('Another setup or retained setup lock exists. Stop all setup processes and inspect .setup.lock before manually removing a stale lock.') from None
    identity = os.fstat(descriptor)
    try:
        with os.fdopen(descriptor, 'w') as stream:
            stream.write(json.dumps({'pid': os.getpid(), 'owner': token}))
        yield
    finally:
        # Release only this acquisition, never a replacement by another owner.
        try:
            current = path.lstat()
            if current.st_ino == identity.st_ino and current.st_dev == identity.st_dev and json.loads(path.read_text()).get('owner') == token:
                path.unlink()
        except (FileNotFoundError, ValueError, OSError):
            pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime-dir', type=Path, default=Path.home()/'.local/share/voxkin/kokoro')
    parser.add_argument('--check', action='store_true', help='Read-only verification; do not install or write a receipt')
    parser.add_argument('--repair-dependencies', action='store_true', help='With --install, force reinstall pinned dependencies to repair damaged packages')
    parser.add_argument('--install', action='store_true', help='Explicitly install dependencies and download verified model artifacts')
    parser.add_argument('--experimental-linux', action='store_true', help='Opt in to Linux x86_64 setup; not general Linux qualification')
    parser.add_argument('--experimental-windows', action='store_true', help='Opt in to native Windows AMD64 voice setup; restricted gateway support is not yet qualified')
    parser.add_argument('--container-timestamps', action='store_true', help='Normalize verified model mtimes before container layer export')
    parser.add_argument('--smoke-test', action='store_true', help='Verify actual offline Heart/Dora synthesis before writing receipt')
    args = parser.parse_args()
    if args.repair_dependencies and not args.install:
        parser.error('--repair-dependencies requires --install')
    if args.check and (args.install or args.smoke_test or args.container_timestamps):
        parser.error('--check cannot be combined with modifying options')
    try:
        validate_platform(platform.system(), platform.machine(), args.experimental_linux, args.experimental_windows)
    except ValueError as error:
        parser.error(str(error))
    if sys.version_info[:2] != (3, 13): parser.error('Run using Python 3.13.')
    root = args.runtime_dir.expanduser().resolve()
    if args.install:
        root.mkdir(mode=0o700, parents=True, exist_ok=True)
    if args.check and (root / '.setup.lock').exists():
        raise RuntimeError('Setup lock present: runtime verification deferred until setup finishes. Doctor changed nothing.')
    with nullcontext() if args.check else runtime_lock(root):
        if args.install:
            progress('Preparing Python environment')
            root.mkdir(mode=0o700, parents=True, exist_ok=True)
            python = venv_python(root)
            if not python.exists(): subprocess.run([sys.executable,'-m','venv',str(root/'.venv')],check=True)
            progress('Installing speech dependencies (this may take several minutes)')
            subprocess.run([str(python),'-m','pip','install',*(['--force-reinstall'] if args.repair_dependencies else []),'--requirement',str(Path(__file__).with_name('requirements.txt'))],check=True, stdout=sys.stderr)
            for name, expected in ARTIFACTS.items():
                target = root/name
                if target.exists() and digest(target) == expected:
                    progress('Verified existing ' + name)
                    continue
                progress('Downloading ' + name)
                temporary = None
                try:
                    with tempfile.NamedTemporaryFile(dir=root, prefix='.download-', delete=False) as output:
                        temporary = Path(output.name)
                        with urllib.request.urlopen(BASE+name, timeout=60) as response:
                            total = 0
                            reported = 0
                            while chunk := response.read(1024*1024):
                                total += len(chunk)
                                if total > 512*1024*1024: raise ValueError('Model exceeds download bound')
                                output.write(chunk)
                                if total // (16*1024*1024) > reported:
                                    reported = total // (16*1024*1024)
                                    progress(name + ': ' + str(total // (1024*1024)) + ' MiB downloaded')
                    if digest(temporary) != expected: raise ValueError('Model checksum mismatch')
                    os.replace(temporary,target); temporary=None
                finally:
                    if temporary is not None: temporary.unlink(missing_ok=True)
        progress('Verifying voices and native dependencies')
        ok = (venv_python(root)).exists() and all((root/name).exists() and digest(root/name)==sha for name,sha in ARTIFACTS.items())
        if not ok: raise RuntimeError('Runtime missing or unverified. Review THIRD_PARTY_NOTICES.md, then run with --install.')
        probe = subprocess.run([str(venv_python(root)), '-I', '-B', '-c',
            "import json,sys; assert sys.version_info[:2] == (3,13), 'Runtime venv requires Python 3.13'; import kokoro_onnx,soundfile,numpy; v=numpy.load(sys.argv[1],allow_pickle=False); print(json.dumps(v.files)); v.close()",
            str(root/'voices-v1.0.bin')], check=True, capture_output=True, text=True)
        installed = json.loads(probe.stdout)
        supported = json.loads(Path(__file__).with_name('voices.json').read_text())
        available = [voice['id'] for voice in supported if voice['id'] in installed]
        if not available: raise RuntimeError('No supported English/Spanish voices installed.')
        if args.smoke_test:
            progress('Testing English and Spanish speech')
            smoke_test(root, available)
        if args.container_timestamps: normalize_timestamps(root)
        if args.check:
            print(json.dumps({'ready': True, 'pluginConfig': {'runtimeDir': str(root), 'voices': available}}))
            return
        receipt = {'version':1, 'voices':available, 'files':{name:{'size':(root/name).stat().st_size,'mtimeMs':(root/name).stat().st_mtime*1000} for name in ARTIFACTS}}
        with tempfile.NamedTemporaryFile(dir=root, prefix='.verify-', mode='w', delete=False) as file:
            json.dump(receipt,file); receipt_path = Path(file.name)
        os.replace(receipt_path,root/'verified.json')
        print(json.dumps({'ready':True,'pluginConfig':{'runtimeDir':str(root),'voices':available}},indent=2))
if __name__ == '__main__':
    try: main()
    except Exception as error:
        print('Setup failed: '+str(error),file=sys.stderr); sys.exit(1)
