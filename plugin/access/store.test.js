import test from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { AccessStore } from './store.js';

async function fixture(t, limits) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'voxkin-access-test-'));
  let time = 1800000000000;
  const stateDir = path.join(root, 'state');
  const store = new AccessStore({ stateDir, limits, now: () => time });
  await store.initialize();
  t.after(async () => { await store.close(); await fs.rm(root, { recursive: true, force: true }); });
  return { root, stateDir, store, tick: ms => { time += ms; }, now: () => time };
}
const invite = store => store.createInvite({ agentId: 'guest', label: 'Guest’s iPhone' });
const code = expected => error => error.code === expected;

test('credential remains unusable until owner approval; permission binding and revocation', async t => {
  const { store } = await fixture(t);
  const i = await invite(store);
  const d = await store.redeemInvite({ secret: i.inviteSecret });
  assert.equal(d.status, 'pending');
  assert.throws(() => store.authenticate(d.deviceSecret), code('unauthorized'));
  await store.approveDevice({ deviceId: d.id });
  assert.deepEqual(store.authenticate(d.deviceSecret), { deviceId: d.id, agentId: 'guest', label: 'Guest’s iPhone' });
  await store.revokeDevice({ deviceId: d.id });
  assert.throws(() => store.authenticate(d.deviceSecret), code('unauthorized'));
});

test('redemption is atomic and single-use under 30 concurrent requests', async t => {
  const { store } = await fixture(t); const i = await invite(store);
  const outcomes = await Promise.allSettled(Array.from({ length: 30 }, () => store.redeemInvite({ secret: i.inviteSecret })));
  assert.equal(outcomes.filter(r => r.status === 'fulfilled').length, 1);
  assert.equal(outcomes.filter(r => r.status === 'rejected' && r.reason.code === 'invalid_invite').length, 29);
  assert.equal(store.list().devices.length, 1);
});

test('disk and list never contain raw secrets; file ownership modes; reload preserves access', async t => {
  const { stateDir, store, now } = await fixture(t); const i = await invite(store);
  const d = await store.redeemInvite({ secret: i.inviteSecret }); await store.approveDevice({ deviceId: d.id });
  const data = await fs.readFile(path.join(stateDir, 'access.json'), 'utf8');
  assert.ok(!data.includes(i.inviteSecret)); assert.ok(!data.includes(d.deviceSecret));
  assert.ok(!JSON.stringify(store.list()).includes('Hash'));
  assert.equal((await fs.stat(path.join(stateDir, 'access.json'))).mode & 0o777, 0o600);
  assert.equal((await fs.stat(stateDir)).mode & 0o777, 0o700);
  await store.close();
  const reopened = await new AccessStore({ stateDir, now }).initialize();
  assert.equal(reopened.authenticate(d.deviceSecret).agentId, 'guest'); await reopened.close();
});

test('expired and cancelled invitations fail and pending approvals expire', async t => {
  const { store, tick } = await fixture(t, { inviteTtlSeconds: 1, pendingTtlSeconds: 2 });
  const expired = await invite(store); tick(1000);
  await assert.rejects(store.redeemInvite({ secret: expired.inviteSecret }), code('invalid_invite'));
  const cancelled = await invite(store); await store.cancelInvite({ inviteId: cancelled.id });
  await assert.rejects(store.redeemInvite({ secret: cancelled.inviteSecret }), code('invalid_invite'));
  const i = await invite(store); const d = await store.redeemInvite({ secret: i.inviteSecret }); tick(2000);
  await assert.rejects(store.approveDevice({ deviceId: d.id }), code('device_not_found'));
  assert.equal(store.list().devices.length, 0);
});

test('capacity is bounded and expired entries free capacity', async t => {
  const { store, tick } = await fixture(t, { maxInvites: 1, maxDevices: 1, pendingTtlSeconds: 1 });
  const i = await invite(store);
  await assert.rejects(invite(store), code('invite_capacity'));
  await store.redeemInvite({ secret: i.inviteSecret });
  const j = await invite(store);
  await assert.rejects(store.redeemInvite({ secret: j.inviteSecret }), code('device_capacity'));
  tick(1000); await store.redeemInvite({ secret: j.inviteSecret });
  assert.equal(store.list().devices.length, 1);
});

test('storage failure does not consume an invitation or return an unpersisted credential', async t => {
  const { root, stateDir, store } = await fixture(t); const i = await invite(store);
  const moved = path.join(root, 'moved'); await fs.rename(stateDir, moved);
  await assert.rejects(store.redeemInvite({ secret: i.inviteSecret }), code('storage_error'));
  assert.equal(store.list().invites.length, 1); assert.equal(store.list().devices.length, 0);
  await fs.rename(moved, stateDir);
  await store.redeemInvite({ secret: i.inviteSecret });
});

test('failed revoke does not pretend access was revoked', async t => {
  const { root, stateDir, store } = await fixture(t); const i = await invite(store);
  const d = await store.redeemInvite({ secret: i.inviteSecret }); await store.approveDevice({ deviceId: d.id });
  const moved = path.join(root, 'moved'); await fs.rename(stateDir, moved);
  await assert.rejects(store.revokeDevice({ deviceId: d.id }), code('storage_error'));
  assert.equal(store.authenticate(d.deviceSecret).deviceId, d.id);
  await fs.rename(moved, stateDir); await store.revokeDevice({ deviceId: d.id });
  assert.throws(() => store.authenticate(d.deviceSecret), code('unauthorized'));
});

test('strict input validation and separate invite/device credential domains', async t => {
  const { store } = await fixture(t);
  for (const agentId of ['', '../main', ' main', 'a'.repeat(65), 'main\n']) await assert.rejects(store.createInvite({ agentId, label: 'phone' }), code('invalid_input'));
  await assert.rejects(store.createInvite({ agentId: 'main', label: 'a'.repeat(81) }), code('invalid_input'));
  for (const ttlSeconds of [0, 3601, Infinity, '3']) await assert.rejects(store.createInvite({ agentId: 'main', label: 'phone', ttlSeconds }), code('invalid_input'));
  const i = await invite(store); assert.throws(() => store.authenticate(i.inviteSecret), code('unauthorized'));
  const d = await store.redeemInvite({ secret: i.inviteSecret });
  await assert.rejects(store.redeemInvite({ secret: d.deviceSecret }), code('invalid_invite'));
  assert.throws(() => store.authenticate('a'.repeat(100000)), code('unauthorized'));
});

test('second writer and stale lock fail closed', async t => {
  const { stateDir, store } = await fixture(t);
  await assert.rejects(new AccessStore({ stateDir }).initialize(), code('state_locked'));
  await store.close(); await fs.writeFile(path.join(stateDir, 'writer.lock'), '{}', { mode: 0o600 });
  await assert.rejects(new AccessStore({ stateDir }).initialize(), code('state_locked'));
});

test('unsafe state directory and symlink state file fail closed', async t => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'voxkin-access-test-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  const stateDir = path.join(root, 'state'); await fs.mkdir(stateDir, { mode: 0o755 });
  await fs.chmod(stateDir, 0o755);
  await assert.rejects(new AccessStore({ stateDir }).initialize(), code('unsafe_state_dir'));
  await fs.chmod(stateDir, 0o700);
  await fs.writeFile(path.join(root, 'other.json'), '{}');
  await fs.symlink(path.join(root, 'other.json'), path.join(stateDir, 'access.json'));
  await assert.rejects(new AccessStore({ stateDir }).initialize(), code('storage_error'));
});

test('shutdown drains accepted writes and refuses new writes', async t => {
  const { store } = await fixture(t);
  const pendingWrite = invite(store);
  const closing = store.close();
  await assert.rejects(invite(store), code('not_ready'));
  await pendingWrite; await closing; await store.close();
  assert.throws(() => store.list(), code('not_ready'));
});

test('two approved phones retain independent server-side assignments', async t => {
  const { store, tick } = await fixture(t);
  const a = await store.createInvite({ agentId: 'alice', label: 'A' });
  const b = await store.createInvite({ agentId: 'bob', label: 'B' });
  const da = await store.redeemInvite({ secret: a.inviteSecret });
  const db = await store.redeemInvite({ secret: b.inviteSecret });
  await store.approveDevice({ deviceId: da.id }); await store.approveDevice({ deviceId: db.id });
  tick(86400000);
  assert.equal(store.authenticate(da.deviceSecret).agentId, 'alice');
  assert.equal(store.authenticate(db.deviceSecret).agentId, 'bob');
  await store.revokeDevice({ deviceId: da.id });
  assert.equal(store.authenticate(db.deviceSecret).agentId, 'bob');
});
