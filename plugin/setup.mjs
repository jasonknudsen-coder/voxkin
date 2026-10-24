#!/usr/bin/env node
// Local terminal wizard: never downloads or changes configuration on plugin startup.
import { spawn } from 'node:child_process';
import { realpath } from 'node:fs/promises';
import { homedir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createInterface } from 'node:readline/promises';
import { nativeCli, run as onboard, validateGateway } from './onboard.mjs';

export const help = `Voxkin guided setup (run on the gateway computer)
  openclaw voxkin setup|doctor|repair [options]  (enabled plugin)
  node /path/to/voxkin-local/setup.mjs [setup|doctor|repair] [options]
  voxkin-setup [setup|doctor|repair] [options]  (when installed with an npm bin)

setup   Check prerequisites, offer download, test speech, configure, reload.
doctor  Read-only checks; no downloads, config changes, or receipt writes.
repair  Explicitly reinstall dependencies and repair model files, then test/reload.

--enable                    Explicitly enable a disabled installed plugin.
--download                  Consent to model/voice/dependency downloads (~400 MB,
                            plus Python dependencies). Otherwise prompted on a TTY.
--runtime-dir ABSOLUTE_PATH  New runtime location (existing configured path wins).
--experimental-linux        Opt in to Linux x86_64 (not fully qualified).
--experimental-windows      Opt in to Windows AMD64 (not fully qualified).
--pair --gateway wss://HOST  Offer owner QR in this private terminal after success.
--help                      Show this help.

Python 3.13 and OpenClaw 2026.10.6 are required. Run as your gateway OS user,
with the same OpenClaw profile/environment. Review THIRD_PARTY_NOTICES.md first.
Existing voice allowlists, access controls, and default TTS are never replaced.
QR pairing needs iOS 1.0.1 / Android 0.2.20 or newer; older apps can use manual setup.
Do not run --pair in a shared terminal or capture its output in logs.
`;
export function parseArgs(args) {
  const result = {mode:'setup'};
  if (args.includes('--help')) return {mode:'help'};
  args = [...args];
  if (args[0] && !args[0].startsWith('--')) result.mode = args.shift();
  if (!['setup','doctor','repair'].includes(result.mode)) throw new Error('Choose setup, doctor, or repair.');
  while (args.length) {
    const key = args.shift();
    if (!['--enable','--download','--runtime-dir','--experimental-linux','--experimental-windows','--pair','--gateway'].includes(key) || key in result) throw new Error('Unknown or repeated option. Use --help.');
    if (['--runtime-dir','--gateway'].includes(key)) {
      const value = args.shift();
      if (!value || value.startsWith('--')) throw new Error('Missing option value.');
      result[key] = value;
    } else result[key] = true;
  }
  if (result.mode === 'doctor' && (result['--enable'] || result['--download'] || result['--pair'])) throw new Error('Doctor cannot download or pair.');
  if (Boolean(result['--pair']) !== Boolean(result['--gateway'])) throw new Error('Use --pair and --gateway together.');
  if (result['--gateway']) validateGateway(result['--gateway']);
  return result;
}

// Only relay our own fixed progress markers; dependency/CLI output can contain
// environment-specific credentials. Never embed child stderr in error messages.
export function execute(command, args, {progress = () => {}, timeout = 120000, killGrace = 1500} = {}) {
  return new Promise((accept, reject) => {
    // A private POSIX process group includes pip/native grandchildren. Killing
    // only the Python parent would leave them writing after a reported timeout.
    const child = spawn(command, args, {stdio:['ignore','pipe','pipe'], shell:false, detached:process.platform !== 'win32'});
    let stdout = '', stderr = '', ended = false, closed = false, closeCode;
    let failure, cleanupDone = false, graceTimer, cleanupTimer;
    const interrupted = signal => {
      const error = new Error('Setup interrupted. Process tree stopped; inspect any retained setup lock before retrying.');
      error.code = 'VOXKIN_INTERRUPTED'; error.signal = signal; stop(error);
    };
    const onInterrupt = () => interrupted('SIGINT');
    const onTerminate = () => interrupted('SIGTERM');
    process.on('SIGINT', onInterrupt); process.on('SIGTERM', onTerminate);
    const timer = setTimeout(() => stop(new Error('Operation timed out. Process tree stopped; inspect any retained setup lock before retrying.')), timeout);
    function finish(error) {
      if (ended) return;
      ended = true; process.removeListener('SIGINT', onInterrupt); process.removeListener('SIGTERM', onTerminate); clearTimeout(timer); clearTimeout(graceTimer); clearTimeout(cleanupTimer);
      error ? reject(error) : accept(stdout);
    }
    function settle() {
      if (!closed || (failure && !cleanupDone)) return;
      finish(failure || (closeCode === 0 ? null : new Error('Operation failed. Check prerequisites, network/disk space, and gateway permissions; retry with repair.')));
    }
    function signalGroup(signal) {
      try { process.kill(-child.pid, signal); } catch(error) { if(error.code !== 'ESRCH') return false; }
      return true;
    }
    function stop(error) {
      if (ended || failure) return;
      failure = error; clearTimeout(timer);
      if (!child.pid) { cleanupDone = true; settle(); return; }
      cleanupTimer = setTimeout(() => finish(new Error('Process-tree shutdown could not be confirmed. Do not retry until all setup processes have stopped; retain the runtime lock.')), 10000);
      if (process.platform === 'win32') {
        // Use the OS tree terminator directly, never a command shell.
        const killer = spawn('taskkill.exe',['/PID',String(child.pid),'/T','/F'],{stdio:'ignore',shell:false,windowsHide:true});
        killer.on('error', () => { /* bounded cleanup timer reports uncertainty */ });
        killer.on('close', code => { if(code === 0) { cleanupDone = true; settle(); } });
      } else {
        signalGroup('SIGTERM');
        // Wait the full grace period even if the parent exits first: a child
        // may ignore TERM and close inherited output while still writing files.
        graceTimer = setTimeout(() => {
          if (signalGroup('SIGKILL')) { cleanupDone = true; settle(); }
        }, killGrace);
      }
    }
    child.on('error', () => { failure = new Error('Required executable unavailable. Check OpenClaw and Python 3.13 on PATH.'); cleanupDone = true; });
    child.stdout.on('data', data => {
      if (failure) return;
      stdout += data;
      if (stdout.length > 4*1024*1024) stop(new Error('Unexpectedly large tool response. Process tree stopped; inspect any retained setup lock before retrying.'));
    });
    child.stderr.on('data', data => {
      if (failure) return;
      stderr += data;
      let split;
      while ((split = stderr.indexOf('\n')) >= 0) {
        const line = stderr.slice(0, split); stderr = stderr.slice(split+1);
        if (line.startsWith('VOXKIN_PROGRESS ')) progress(line.slice(16));
      }
      if (stderr.length > 65536) stderr = '';
    });
    child.on('close', code => { closed = true; closeCode = code; settle(); });
  });
}

function json(text) { try { return JSON.parse(text); } catch { throw new Error('Unexpected host response; no readiness claim can be made.'); } }
export function configurationPlan(plugins, runtime, voices) {
  const config = plugins?.entries?.['voxkin-local']?.config || {};
  const changes = [];
  if (config.runtimeDir === undefined) changes.push(['runtimeDir', runtime]);
  if (config.voices === undefined) changes.push(['voices', voices]);
  return changes;
}
export async function run(options, dependencies = {}) {
  const write = dependencies.write || (line => process.stdout.write(line+'\n'));
  const exec = dependencies.execute || execute;
  const cli = dependencies.cli || await nativeCli();
  const invoke = async args => exec(cli[0], [...cli[1],...args]);
  const ask = dependencies.ask || (async question => {
    if (!process.stdin.isTTY || !process.stdout.isTTY) return false;
    const rl = createInterface({input:process.stdin, output:process.stdout});
    try { return /^(y|yes)$/i.test((await rl.question(question+' [y/N] ')).trim()); } finally { rl.close(); }
  });
  write('Checking OpenClaw installation and Python 3.13…');
  const host = await invoke(['--version']);
  if (!/\b2026\.10\.6\b/.test(host)) throw new Error('Guided configuration requires OpenClaw 2026.10.6. Use INSTALL.md for manual setup on older hosts.');
  const info = json(await invoke(['plugins','inspect','voxkin-local','--json']));
  if (!info.plugin?.rootDir) throw new Error('Install voxkin-local first; no installation was found.');
  const root = await (dependencies.realpath || realpath)(info.plugin.rootDir);
  // The host may import a staged module copy. Compare against its trusted
  // registration root, not the cache directory of that transformed module.
  const self = await (dependencies.realpath || realpath)(dependencies.pluginRoot || dirname(fileURLToPath(import.meta.url)));
  if (root !== self && !dependencies.allowTestRoot) throw new Error('Run setup.mjs from the installed plugin directory reported by openclaw plugins inspect voxkin-local.');
  if (!info.plugin.enabled) {
    if (options.mode === 'doctor') throw new Error('Plugin is disabled. Doctor changed nothing; run setup --enable to deliberately enable it.');
    if (!options['--enable'] && !await ask('Enable the installed Voxkin plugin?')) throw new Error('Plugin remains disabled. Rerun setup --enable when ready.');
    await invoke(['plugins','enable','voxkin-local']);
  }
  const plugins = json(await invoke(['config','get','plugins','--json']));
  const existing = plugins?.entries?.['voxkin-local']?.config || {};
  if (existing.runtimeDir && options['--runtime-dir'] && resolve(existing.runtimeDir) !== resolve(options['--runtime-dir'])) throw new Error('A different runtime is already configured. No path was changed; omit --runtime-dir to repair the existing runtime.');
  const runtime = existing.runtimeDir || resolve(options['--runtime-dir'] || resolve(homedir(),'.local/share/voxkin/kokoro'));
  const python = process.platform === 'win32' ? ['py',['-3.13']] : ['python3.13',[]];
  if ((await exec(python[0],[...python[1],'-I','-c','import sys; print("%d.%d" % sys.version_info[:2])'])).trim() !== '3.13') throw new Error('Python 3.13 is required; install it and rerun setup.');
  const flags = ['--experimental-linux','--experimental-windows'].filter(flag=>options[flag]);
  const base = [...python[1],'-I','-B','-X','utf8',resolve(root,'setup.py'),'--runtime-dir',runtime,...flags];
  const verify = () => exec(python[0],[...base,'--check'],{progress:write});
  let verified;
  try { verified = json(await verify()); } catch(error) {
    if (error.code === 'VOXKIN_INTERRUPTED') throw error;
    if (options.mode === 'doctor') throw new Error('Local runtime needs repair. Review prerequisites and run setup or repair; doctor changed nothing.');
  }
  if (options.mode !== 'doctor') {
    let download = options['--download'];
    if ((!verified || options.mode === 'repair') && !download) download = await ask('Download/repair local model, voices and Python dependencies? Review THIRD_PARTY_NOTICES.md.');
    if ((!verified || options.mode === 'repair') && !download) throw new Error('Download consent not provided. Nothing downloaded; rerun with --download when ready.');
    write(download ? 'Preparing local voices; verified models are reused…' : 'Testing existing local voices…');
    verified = json(await exec(python[0],[...base,...(download?['--install']:[]),...(options.mode==='repair'?['--repair-dependencies']:[]),'--smoke-test'],{progress:write,timeout:30*60*1000}));
    if (!verified.ready || !Array.isArray(verified.pluginConfig?.voices) || !verified.pluginConfig.voices.length) throw new Error('Runtime did not verify successfully; configuration unchanged.');
    for (const [key,value] of configurationPlan(plugins,runtime,verified.pluginConfig.voices)) {
      // Compare-and-set on each missing leaf: never overwrite a concurrent edit.
      await invoke(['config','set',`plugins.entries["voxkin-local"].config.${key}`,JSON.stringify(value),'--strict-json','--expect-current-absent']);
    }
    write('Local speech passed. Reloading the gateway plugin…');
    try { await invoke(['plugins','reload','voxkin-local','--wait','--json']); }
    catch(error) { if (error.code === 'VOXKIN_INTERRUPTED') throw error; throw new Error('Local speech is verified, but gateway reload did not complete. Check operator.admin authorization and plugin status, then rerun setup. Gateway readiness is not confirmed.'); }
  }
  const catalog = json(await invoke(['gateway','call','voxkin.catalog','--params','{}','--json']));
  if (!catalog.ready) throw new Error('Local checks completed, but the live gateway is not ready. Check plugin reload/status and rerun doctor.');
  write(options.mode === 'doctor' ? 'Doctor: local runtime verified and live gateway reports ready. No changes made.' : 'Ready: local English/Spanish speech passed and live gateway reports ready. Existing access controls and default TTS preserved.');
  if (options['--pair']) {
    if (!process.stdin.isTTY || !process.stdout.isTTY) throw new Error('Pairing requires a private interactive terminal; no QR was printed.');
    if (await ask('Show the reusable owner credential as a QR in this private terminal?')) await (dependencies.onboard || onboard)({mode:'owner',gateway:options['--gateway'],config:process.env.OPENCLAW_CONFIG_PATH || resolve(process.env.OPENCLAW_STATE_DIR || resolve(homedir(),'.openclaw'),'openclaw.json')});
  }
  return {ready:true};
}
// Host command registration is lazy: normal gateway startup does no setup work.
export function registerSetupCli(program, {pluginRoot} = {}) {
  const group = program.command('voxkin').description('Set up, diagnose, and repair local Voxkin voices');
  for (const mode of ['setup','doctor','repair']) {
    const command = group.command(mode).description(mode === 'doctor' ? 'Read-only runtime and gateway checks' : 'Guided local speech setup and verification');
    command.option('--runtime-dir <path>', 'New runtime location; preserve existing configuration')
      .option('--experimental-linux', 'Opt in to experimental Linux support')
      .option('--experimental-windows', 'Opt in to experimental Windows support');
    if (mode !== 'doctor') command.option('--enable','Explicitly enable the installed plugin')
      .option('--download','Consent to voice/model/dependency downloads')
      .option('--pair','Offer owner QR privately after setup')
      .option('--gateway <url>','Phone-reachable wss:// gateway origin for pairing');
    command.action(async options => {
      const args = [mode];
      const names = {runtimeDir:'runtime-dir',experimentalLinux:'experimental-linux',experimentalWindows:'experimental-windows',enable:'enable',download:'download',pair:'pair',gateway:'gateway'};
      for (const [key,value] of Object.entries(options)) if (names[key] && value) { args.push('--'+names[key]); if(typeof value === 'string') args.push(value); }
      try { await run(parseArgs(args), {pluginRoot}); }
      catch(error) { process.stderr.write(`Voxkin setup: ${error.message}\n`); process.exitCode = error.code === 'VOXKIN_INTERRUPTED' ? (error.signal === 'SIGINT' ? 130 : 143) : 1; }
    });
  }
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try { const options = parseArgs(process.argv.slice(2)); if(options.mode==='help') process.stdout.write(help); else await run(options); }
  catch(error) { process.stderr.write(`Voxkin setup: ${error.message}\n`); process.exitCode = error.code === 'VOXKIN_INTERRUPTED' ? (error.signal === 'SIGINT' ? 130 : 143) : 1; }
}
