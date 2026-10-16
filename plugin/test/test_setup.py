import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import wave

spec = importlib.util.spec_from_file_location('setup', Path(__file__).parents[1] / 'setup.py')
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)

class SetupTests(unittest.TestCase):
    def test_platform_opt_in(self):
        setup.validate_platform('Darwin', 'arm64', False)
        setup.validate_platform('Linux', 'x86_64', True)
        for system, machine, opt in [('Linux', 'x86_64', False), ('Linux', 'aarch64', True), ('Windows', 'AMD64', True)]:
            with self.assertRaises(ValueError):
                setup.validate_platform(system, machine, opt)

    def test_windows_opt_in_and_interpreter(self):
        setup.validate_platform('Windows', 'AMD64', False, True)
        with self.assertRaises(ValueError):
            setup.validate_platform('Windows', 'ARM64', False, True)
        self.assertEqual(setup.venv_python(Path('/runtime'), 'Windows'), Path('/runtime/.venv/Scripts/python.exe'))
        self.assertEqual(setup.venv_python(Path('/runtime'), 'Linux'), Path('/runtime/.venv/bin/python'))

    def test_container_timestamps_preserve_content(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in setup.ARTIFACTS:
                (root / name).write_bytes(b'verified bytes')
                setup.os.utime(root / name, ns=(1700000000123456789, 1700000000123456789))
            setup.normalize_timestamps(root)
            for name in setup.ARTIFACTS:
                self.assertEqual((root / name).read_bytes(), b'verified bytes')
                self.assertEqual((root / name).stat().st_mtime_ns, 1700000000000000000)

    def test_smoke_both_languages_and_cleanup(self):
        outputs, languages = [], []
        def run(command, **kwargs):
            output = Path(command[-1]); outputs.append(output)
            languages.append(json.loads(kwargs['input'])['language'])
            self.assertEqual(kwargs['timeout'], 45)
            with wave.open(str(output), 'wb') as audio:
                audio.setnchannels(1); audio.setsampwidth(2); audio.setframerate(24000)
                audio.writeframes(b'\0\0' * 24)
            return type('Result', (), {'returncode': 0})()
        with patch.object(setup.subprocess, 'run', side_effect=run):
            setup.smoke_test(Path('/runtime'), ['af_heart', 'ef_dora'])
        self.assertEqual(languages, ['en', 'es'])
        self.assertTrue(all(not p.parent.exists() for p in outputs))

    def test_smoke_fails_on_native_error(self):
        with patch.object(setup.subprocess, 'run', return_value=type('Result', (), {'returncode': 1})()):
            with self.assertRaisesRegex(RuntimeError, 'TMPDIR'):
                setup.smoke_test(Path('/runtime'), ['af_heart', 'ef_dora'])

    def test_smoke_rejects_invalid_audio(self):
        def run(command, **kwargs):
            with wave.open(command[-1], 'wb') as audio:
                audio.setnchannels(1); audio.setsampwidth(2); audio.setframerate(8000)
                audio.writeframes(b'\0\0')
            return type('Result', (), {'returncode': 0})()
        with patch.object(setup.subprocess, 'run', side_effect=run):
            with self.assertRaisesRegex(RuntimeError, 'invalid audio'):
                setup.smoke_test(Path('/runtime'), ['af_heart', 'ef_dora'])
