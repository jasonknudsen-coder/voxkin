#!/usr/bin/env node
// Local-only setup QR rendering. Never send setup credentials to a QR service.
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { readFile, realpath } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { homedir } from 'node:os';
import { resolve, delimiter } from 'node:path';
import { pathToFileURL } from 'node:url';
import QRCode from 'qrcode';

const exec = promisify(execFile);
export const help = `Voxkin gateway-host onboarding
  node onboard.mjs owner --gateway wss://phone-reachable-host --token-stdin
  node onboard.mjs owner --gateway wss://phone-reachable-host --config [path]
  node onboard.mjs invite --gateway wss://phone-reachable-host --agent ID --label NAME
  node onboard.mjs list
  node onboard.mjs approve --device ID
  node onboard.mjs cancel --invite ID

Restricted operations use OpenClaw's configured gateway/authentication. Optional
--expect-url ws://127.0.0.1:18789 verifies that CLI target without changing auth.
Owner QR contains the existing gateway bootstrap token: treat it like a password.
It is NOT a single-use invitation; normal gateway device pairing still applies.
Scan privately, review in Voxkin, then explicitly Connect. Nothing auto-connects.
No gateway configuration or permissions are changed by generating an owner QR.
`;

export function parseArgs(argv) {
  if (!argv.length || argv.includes('--help')) return { mode: 'help' };
  const [mode, ...rest] = argv;
  const allowed = { owner: ['gateway','token-stdin','config'], invite: ['gateway','agent','label','expect-url'], list: ['expect-url'], approve: ['device','expect-url'], cancel: ['invite','expect-url'] }[mode];
  if (!allowed) throw new Error('Choose owner, invite, list, approve, or cancel.');
  const options = { mode };
  while (rest.length) {
    const key = rest.shift().replace(/^--/, '');
    if (!allowed.includes(key) || key in options) throw new Error('Unknown or repeated option. Use --help.');
    if (key === 'token-stdin') options[key] = true;
    else if (key === 'config' && (!rest.length || rest[0].startsWith('--'))) options[key] = process.env.OPENCLAW_CONFIG_PATH || resolve(homedir(), '.openclaw/openclaw.json');
    else {
      const value = rest.shift();
      if (!value || value.startsWith('--')) throw new Error('Missing option value.');
      options[key] = value;
    }
  }
  for (const key of ({owner:['gateway'], invite:['gateway','agent','label'],approve:['device'],cancel:['invite']}[mode] || [])) if (!options[key]) throw new Error(`Missing --${key}.`);
  if (mode === 'owner' && Boolean(options['token-stdin']) === Boolean(options.config)) throw new Error('Choose exactly one token source: --token-stdin or --config.');
  if (options.gateway) validateGateway(options.gateway);
  return options;
}

export function validateGateway(value) {
  let url;
  try { url = new URL(value); } catch { throw new Error('A valid wss:// gateway URL is required.'); }
  if (url.protocol !== 'wss:' || !url.hostname || url.username || url.password || url.search || url.hash || (url.pathname !== '/' && url.pathname !== '')) throw new Error('Use a wss:// gateway origin without credentials, path, query, or fragment.');
  return value;
}
export function setupLink(mode, gateway, credential) {
  if (!['owner','invite'].includes(mode)) throw new Error('Unknown setup mode.');
  validateGateway(gateway);
  if (mode === 'invite' && (typeof credential !== 'string' || !/^[A-Za-z0-9_-]{32,256}$/.test(credential))) throw new Error('Invalid invitation credential.');
  if (typeof credential !== 'string' || !credential.trim() || Buffer.byteLength(credential, 'utf8') > 4096 || /[\x00-\x1f\x7f-\x9f]/.test(credential)) throw new Error('Invalid setup credential.');
  // A non-URI owner code cannot be dispatched to a competing custom-scheme app.
  return (mode === 'owner' ? 'VOXKIN OWNER 1 ' : 'voxkin://join#') + new URLSearchParams({gateway, [mode === 'owner' ? 'token' : 'secret']: credential}).toString().replace(/\+/g, '%20');
}
export async function nativeCli() {
  if(process.platform !== 'win32') return ['openclaw',[]];
  for(const dir of (process.env.PATH || '').split(delimiter).filter(Boolean)){
    try {return [process.execPath,[await realpath(resolve(dir,'node_modules/openclaw/openclaw.mjs'))]];} catch {}
  }
  throw new Error('Native OpenClaw CLI not found on PATH.');
}
export async function gatewayCall(action, params, expectedURL, run = exec) {
  const args = ['gateway','call',`voxkin.access.${action}`,'--json','--params',JSON.stringify(params)];
  if (expectedURL) args.push('--expect-url',expectedURL);
  let stdout;
  try { const [command,prefix]=await nativeCli(); ({stdout} = await run(command,[...prefix,...args],{timeout:20000,maxBuffer:1024*1024})); }
  catch { throw new Error('Gateway operation failed. Check configured authentication, operator.admin approval, and restricted plugin setup.'); }
  try { return JSON.parse(stdout); } catch { throw new Error('Gateway returned invalid JSON; no QR generated.'); }
}
async function stdinToken() {
  if (process.stdin.isTTY) throw new Error('Pipe the token on stdin, or use --config. Never put a token in command arguments.');
  let token = '';
  for await (const chunk of process.stdin) {
    token += chunk;
    if (token.length > 4096) throw new Error('Token input too long.');
  }
  return token.trim();
}
export async function loadSecretResolver() {
  // Use the installed host resolver, including its file/provider safety checks.
  const candidates = [import.meta.url];
  for (const dir of (process.env.PATH || '').split(delimiter).filter(Boolean)) {
    try { candidates.push(await realpath(resolve(dir, process.platform === 'win32' ? 'node_modules/openclaw/openclaw.mjs' : 'openclaw'))); } catch { }
  }
  for (const base of candidates) {
    try {
      const sdk = createRequire(base).resolve('openclaw/plugin-sdk/secret-ref-runtime');
      const module = await import(pathToFileURL(sdk).href);
      if (typeof module.resolveSecretRefValues === 'function') return module.resolveSecretRefValues;
    } catch { }
  }
  throw new Error('Installed OpenClaw secret resolver unavailable; use --token-stdin.');
}
export async function configToken(config, load = loadSecretResolver) {
  const token = config?.gateway?.auth?.token;
  if (typeof token === 'string' && !token.includes('${')) return token;
  if (token && ['file','env'].includes(token.source) && typeof token.provider === 'string' && typeof token.id === 'string') {
    try {
      const resolver = await load();
      const values = await resolver([token], {config});
      if (values.size !== 1) throw new Error();
      const value = values.values().next().value;
      if (typeof value !== 'string') throw new Error();
      return value;
    } catch { throw new Error('Cannot resolve gateway token using the installed OpenClaw provider. Check provider access or use --token-stdin.'); }
  }
  throw new Error('Unsupported gateway token source; use --token-stdin with the resolved token.');
}
export async function run(options, dependencies = {}) {
  const call = dependencies.call || gatewayCall;
  const render = dependencies.render || (value => QRCode.toString(value, {type:'terminal',small:true,errorCorrectionLevel:'M'}));
  const write = dependencies.write || (value => process.stdout.write(value));
  if (options.mode === 'help') { write(help); return; }
  if (options.mode === 'owner') {
    let token;
    if (options.config) {
      let config;
      try { config = JSON.parse(await (dependencies.read || readFile)(options.config,'utf8')); }
      catch { throw new Error('Cannot read strict-JSON config; use --token-stdin for JSON5.'); }
      token = await configToken(config, dependencies.loadResolver);
    } else token = await (dependencies.stdin || stdinToken)();
    const qr = await render(setupLink('owner', options.gateway, token));
    write('OWNER SETUP — sensitive gateway token, not a single-use invite.\nStandard device pairing/approval still applies; scan inside Voxkin Settings (not the system camera), then explicitly Connect.\nRequires a Voxkin app supporting non-link owner codes; update the app first.\n' + qr);
    return;
  }
  const params = options.mode === 'invite' ? {agentId:options.agent,label:options.label} : options.mode === 'approve' ? {deviceId:options.device} : options.mode === 'cancel' ? {inviteId:options.invite} : {};
  const result = await call(options.mode,params,options['expect-url']);
  if (options.mode === 'invite') {
    const qr = await render(setupLink('invite',options.gateway,result?.inviteSecret));
    write('Single-use restricted invitation. Scan privately; then list and approve the pending device.\n' + qr);
    write(JSON.stringify({id:result.id,expiresAt:result.expiresAt}) + '\n');
  } else write(JSON.stringify(result,null,2) + '\n');
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try { await run(parseArgs(process.argv.slice(2))); }
  catch (error) { process.stderr.write(`Voxkin setup: ${error.message}\n`); process.exitCode = 1; }
}
