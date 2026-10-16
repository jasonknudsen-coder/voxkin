import {assertWindowsPrivate,privateStat} from './windows-files.js';
import { randomBytes, createHash, timingSafeEqual } from 'node:crypto';
import { promises as fs, constants } from 'node:fs';
import path from 'node:path';

export class AccessError extends Error {
  constructor(code, message = code) { super(message); this.name = 'AccessError'; this.code = code; }
}
const fail = (code) => { throw new AccessError(code); };
const digest = value => createHash('sha256').update(value).digest('hex');
const secret = () => randomBytes(32).toString('base64url');
const id = () => randomBytes(16).toString('hex');
const validSecret = value => typeof value === 'string' && /^[A-Za-z0-9_-]{43}$/.test(value);
const equal = (a, b) => timingSafeEqual(Buffer.from(a, 'hex'), Buffer.from(b, 'hex'));
const registries = new Set();
const defaults = { maxInvites: 100, maxDevices: 100, inviteTtlSeconds: 600, maxInviteTtlSeconds: 3600, pendingTtlSeconds: 3600 };
function name(value, field) {
  if (typeof value !== 'string' || value.length < 1 || value.length > (field === 'agentId' ? 64 : 80) || value !== value.trim() || /[\x00-\x1f\x7f]/.test(value)) fail('invalid_input');
  if (field === 'agentId' && !/^[A-Za-z0-9][A-Za-z0-9_-]*$/.test(value)) fail('invalid_input');
  return value;
}
function publicDevice(d) { const { credentialHash, ...record } = d; return record; }
function publicInvite(d) { const { secretHash, ...record } = d; return record; }

/** One instance owns a dedicated stateDir. close() releases its lifetime writer lock. */
export class AccessStore {
  constructor({ stateDir, now = Date.now, limits = {} } = {}) {
    if (typeof stateDir !== 'string' || !path.isAbsolute(stateDir)) fail('invalid_state_dir');
    this.dir = path.resolve(stateDir); this.file = path.join(this.dir, 'access.json');
    this.now = now; this.limits = { ...defaults, ...limits };
    for (const [key, value] of Object.entries(this.limits)) {
      if (!(key in defaults) || !Number.isSafeInteger(value) || value < 1 || value > (key.startsWith('max') && !key.includes('Ttl') ? 10000 : 86400)) fail('invalid_limits');
    }
    this.queue = Promise.resolve(); this.ready = false; this.closing = false;
  }
  async initialize() {
    if (this.ready) return this;
    if (registries.has(this.dir)) fail('state_locked');
    registries.add(this.dir);
    try {
      await fs.mkdir(this.dir, { recursive: true, mode: 0o700 });
      await assertWindowsPrivate(this.dir);
      const stat = await fs.lstat(this.dir);
      if (!stat.isDirectory() || !privateStat(stat)) fail('unsafe_state_dir');
      // A lifetime lock intentionally fails closed after an unclean shutdown. The owner
      // must verify the previous process is stopped before removing a stale lock.
      this.lock = await fs.open(path.join(this.dir, 'writer.lock'), constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600);
      await this.lock.writeFile(JSON.stringify({ pid: process.pid }));
      try {
        await fs.lstat(this.file);
        await assertWindowsPrivate(this.file);
        const handle = await fs.open(this.file, constants.O_RDONLY | constants.O_NOFOLLOW);
        try {
          const stat = await handle.stat();
          if (!stat.isFile() || stat.size > 8 * 1024 * 1024 || !privateStat(stat)) fail('unsafe_state_file');
          this.state = JSON.parse(await handle.readFile('utf8'));
        } finally { await handle.close(); }
        this.#validate(this.state);
      } catch (error) {
        if (error.code !== 'ENOENT') throw error;
        this.state = { version: 1, invites: [], devices: [] };
        await this.#save(this.state);
      }
      this.closing = false; this.ready = true; return this;
    } catch (error) {
      if (this.lock) { await this.lock.close(); this.lock = null; await fs.unlink(path.join(this.dir, 'writer.lock')).catch(() => {}); }
      registries.delete(this.dir);
      throw error instanceof AccessError ? error : new AccessError(error.code === 'EEXIST' ? 'state_locked' : 'storage_error');
    }
  }
  #validate(s) {
    if (s?.version !== 1 || !Array.isArray(s.invites) || !Array.isArray(s.devices) || s.invites.length > 10000 || s.devices.length > 10000) fail('invalid_state');
    const ids = new Set();
    for (const [records, type] of [[s.invites, 'invite'], [s.devices, 'device']]) for (const r of records) {
      if (!r || !/^[a-f0-9]{32}$/.test(r.id) || ids.has(r.id)) fail('invalid_state');
      ids.add(r.id); name(r.agentId, 'agentId'); name(r.label, 'label');
      if (!Number.isSafeInteger(r.createdAt) || !Number.isSafeInteger(r.expiresAt) || r.expiresAt <= r.createdAt || !/^[a-f0-9]{64}$/.test(type === 'invite' ? r.secretHash : r.credentialHash)) fail('invalid_state');
      if (type === 'device' && !['pending', 'active', 'revoked'].includes(r.status)) fail('invalid_state');
    }
  }
  #require() { if (!this.ready) fail('not_ready'); }
  async #save(state) {
    await assertWindowsPrivate(this.dir);
    const tmp = path.join(this.dir, `.access-${id()}.tmp`);
    let h;
    try {
      h = await fs.open(tmp, constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600);
      await h.writeFile(JSON.stringify(state)); await h.sync(); await h.close(); h = null;
      await fs.rename(tmp, this.file);
    } catch {
      if (h) await h.close().catch(() => {});
      await fs.unlink(tmp).catch(() => {}); fail('storage_error');
    }
  }
  #mutate(fn) {
    if (this.closing) return Promise.reject(new AccessError('not_ready'));
    const result = this.queue.then(async () => {
      this.#require(); const state = structuredClone(this.state); const now = this.now();
      state.invites = state.invites.filter(i => i.expiresAt > now);
      state.devices = state.devices.filter(d => d.status !== 'revoked' && (d.status !== 'pending' || d.expiresAt > now));
      const output = fn(state, now);
      await this.#save(state); this.state = state; return output;
    });
    this.queue = result.catch(() => {}); return result;
  }
  createInvite({ agentId, label, ttlSeconds = this.limits.inviteTtlSeconds } = {}) {
    return this.#mutate((state, now) => {
      name(agentId, 'agentId'); name(label, 'label');
      if (!Number.isSafeInteger(ttlSeconds) || ttlSeconds < 1 || ttlSeconds > this.limits.maxInviteTtlSeconds) fail('invalid_input');
      if (state.invites.length >= this.limits.maxInvites) fail('invite_capacity');
      const inviteSecret = secret();
      const record = { id: id(), agentId, label, createdAt: now, expiresAt: now + ttlSeconds * 1000, secretHash: digest(inviteSecret) };
      state.invites.push(record); return { ...publicInvite(record), inviteSecret };
    });
  }
  redeemInvite({ secret: invitation } = {}) {
    return this.#mutate((state, now) => {
      if (!validSecret(invitation)) fail('invalid_invite');
      const hash = digest(invitation); const index = state.invites.findIndex(i => equal(i.secretHash, hash));
      if (index < 0) fail('invalid_invite');
      if (state.devices.length >= this.limits.maxDevices) fail('device_capacity');
      const invite = state.invites[index]; const deviceSecret = secret();
      const record = { id: id(), agentId: invite.agentId, label: invite.label, createdAt: now, expiresAt: now + this.limits.pendingTtlSeconds * 1000, status: 'pending', credentialHash: digest(deviceSecret) };
      state.invites.splice(index, 1); state.devices.push(record); return { ...publicDevice(record), deviceSecret };
    });
  }
  approveDevice({ deviceId } = {}) {
    return this.#mutate((state, now) => {
      const d = state.devices.find(d => d.id === deviceId);
      if (!d) fail('device_not_found');
      if (d.status !== 'pending') fail('invalid_device_state');
      d.status = 'active'; d.approvedAt = now; return publicDevice(d);
    });
  }
  revokeDevice({ deviceId } = {}) {
    return this.#mutate((state, now) => {
      const d = state.devices.find(d => d.id === deviceId); if (!d) fail('device_not_found');
      d.status = 'revoked'; d.revokedAt = now; return publicDevice(d);
    });
  }
  cancelInvite({ inviteId } = {}) {
    return this.#mutate(state => {
      const i = state.invites.findIndex(i => i.id === inviteId); if (i < 0) fail('invite_not_found');
      state.invites.splice(i, 1); return { id: inviteId, cancelled: true };
    });
  }
  authenticate(credential) {
    this.#require(); if (!validSecret(credential)) fail('unauthorized');
    const hash = digest(credential);
    const device = this.state.devices.find(d => equal(d.credentialHash, hash));
    if (!device || device.status !== 'active') fail('unauthorized');
    return { deviceId: device.id, agentId: device.agentId, label: device.label };
  }
  list() {
    this.#require(); const now = this.now();
    return { invites: this.state.invites.filter(i => i.expiresAt > now).map(publicInvite), devices: this.state.devices.filter(d => d.status !== 'pending' || d.expiresAt > now).map(publicDevice) };
  }
  close() {
    if (this.closing) return this.closePromise;
    this.closing = true;
    this.closePromise = (async () => {
      await this.queue;
      if (!this.ready) return;
      this.ready = false; await this.lock.close(); this.lock = null;
      await fs.unlink(path.join(this.dir, 'writer.lock')); registries.delete(this.dir);
    })();
    return this.closePromise;
  }
}
