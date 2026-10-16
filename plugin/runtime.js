import { spawn } from 'node:child_process';
import { access, mkdtemp, readFile, rm, stat } from 'node:fs/promises';
import { readFileSync, constants } from 'node:fs';
import { join, isAbsolute } from 'node:path';
import { homedir, tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
export const venvPython = (root, platform = process.platform) => join(root, '.venv', platform === 'win32' ? 'Scripts/python.exe' : 'bin/python');
export class PublicError extends Error {}
export const VOICES = Object.freeze(JSON.parse(readFileSync(new URL('./voices.json', import.meta.url), 'utf8')).map(Object.freeze));
export function validateRequest(params, voices) {
  if (!params || typeof params !== 'object' || Array.isArray(params) ||
      Object.keys(params).some(k => !['text','language','voice'].includes(k))) throw new PublicError('Invalid speech request.');
  if (typeof params.text !== 'string' || !params.text.trim() || [...params.text].length > 4000) throw new PublicError('Expected 1–4000 text characters.');
  const voice = voices.find(v => v.id === params.voice && v.language === params.language);
  if (!voice) throw new PublicError('Language/voice is not enabled.');
  return {text: params.text, language: voice.language, voice: voice.id};
}
export function createRuntime(config = {}, runner = runWorker) {
  const root = config.runtimeDir ?? join(homedir(), '.local', 'share', 'voxkin', 'kokoro');
  if (!isAbsolute(root)) throw new PublicError('runtimeDir must be absolute.');
  const enabled = config.voices ?? VOICES.map(v => v.id);
  if (!Array.isArray(enabled) || !enabled.length || enabled.some(id => !VOICES.some(v => v.id === id))) throw new PublicError('Invalid enabled voices.');
  const voices = VOICES.filter(v => enabled.includes(v.id));
  let active = 0;
  let retired = false;
  const jobs = new Set();
  const inventory = async () => {
    try {
      if (retired) return null;
      if (!(await stat(venvPython(root))).isFile()) return null;
      await access(venvPython(root), process.platform === 'win32' ? constants.R_OK : constants.X_OK);
      const receiptPath = join(root,'verified.json');
      if ((await stat(receiptPath)).size > 4096) return null;
      const receipt = JSON.parse(await readFile(receiptPath, 'utf8'));
      if (receipt.version !== 1 || !Array.isArray(receipt.voices) || receipt.voices.some(v => typeof v !== 'string')) return null;
      for (const file of ['kokoro-v1.0.onnx','voices-v1.0.bin']) {
        const info = await stat(join(root,file));
        if (!info.isFile() || info.size !== receipt.files?.[file]?.size || Math.abs(info.mtimeMs - receipt.files[file].mtimeMs) > 1) return null;
      }
      return voices.filter(voice => receipt.voices.includes(voice.id));
    } catch { return null; }
  };
  return {
    async close() {
      retired = true;
      for (const job of jobs) job.abort();
      await Promise.allSettled([...jobs].map(job => job.settled));
    },
    async catalog(params = {}) {
      if (!params || Array.isArray(params) || Object.keys(params).length) throw new PublicError('Catalog takes no parameters.');
      const installed = await inventory();
      const available = installed !== null && installed.length > 0;
      return {version:1, provider:'voxkin-local', ready:available, local:true,
        voices:available ? installed : [], status:available ? 'ready' : 'setup-required'};
    },
    async speak(params, signal) {
      if (signal?.aborted || retired) throw new PublicError("Local synthesis cancelled.");
      validateRequest(params, voices);
      const installed = await inventory();
      if (!installed?.length) throw new PublicError('Local voice setup is incomplete. Run plugin setup on the gateway host.');
      const request = validateRequest(params, installed);
      if (active >= 2) throw new PublicError('Local speech is busy. Try again shortly.');
      if (signal?.aborted || retired) throw new PublicError("Local synthesis cancelled.");
      active++;
      const controller = new AbortController();
      let settle; controller.settled = new Promise(resolve => { settle = resolve });
      jobs.add(controller);
      const abort = () => controller.abort();
      signal?.addEventListener('abort', abort, {once:true});
      try {
        const data = await runner(root, request, controller.signal);
        if (controller.signal.aborted) throw new PublicError('Local synthesis cancelled.');
        if (!Buffer.isBuffer(data) || data.length < 44 || data.length > 6_000_000 ||
            data.toString('ascii',0,4) !== 'RIFF' || data.toString('ascii',8,12) !== 'WAVE') throw new PublicError('Invalid local audio.');
        return {audioBase64:data.toString('base64'), mimeType:'audio/wav', provider:'voxkin-local', language:request.language, voice:request.voice};
      } finally { active--; jobs.delete(controller); settle(); signal?.removeEventListener("abort", abort); }
    }
  };
}
async function runWorker(root, request, signal) {
  const directory = await mkdtemp(join(tmpdir(), 'voxkin-local-voice-'));
  const output = join(directory, 'speech.wav');
  try {
    await new Promise((resolve, reject) => {
      if (signal.aborted) { reject(new PublicError('Local synthesis cancelled.')); return; }
      const child = spawn(venvPython(root), ['-X', 'utf8', fileURLToPath(new URL('./worker.py', import.meta.url)), '--models', root, '--output', output], {stdio:['pipe','ignore','ignore']});
      const abort = () => child.kill('SIGKILL');
      signal.addEventListener('abort', abort, {once:true});
      const timeout = setTimeout(() => child.kill('SIGKILL'), 45_000);
      child.once('error', () => { clearTimeout(timeout); signal.removeEventListener('abort', abort); reject(new PublicError('Could not start local synthesis.')); });
      child.once('close', code => { clearTimeout(timeout); signal.removeEventListener('abort', abort); code === 0 ? resolve() : reject(new PublicError('Local synthesis failed or timed out.')); });
      child.stdin.on('error', () => {});
      child.stdin.end(JSON.stringify(request));
    });
    if ((await stat(output)).size > 6_000_000) throw new PublicError('Local audio exceeded size limit.');
    return await readFile(output);
  } finally { await rm(directory, {recursive:true, force:true}); }
}
