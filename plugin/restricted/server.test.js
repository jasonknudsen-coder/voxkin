import test from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import { AccessStore } from '../access/store.js';
import { createRestrictedServer, createHttpHandler } from './server.js';

const rejected = code => error => error.code === code;
const flush = () => new Promise(resolve => setImmediate(resolve));
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
async function fixture(t, options = {}) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'voxkin-restricted-test-'));
  const store = await new AccessStore({ stateDir: path.join(root, 'state') }).initialize();
  const jobs = [], calls = [];
  let allowed = ['alice', 'bob'];
  const runtime = options.runtime ?? {
    async catalog() { calls.push('catalog'); return { voices: [] }; },
    async speak(input) { calls.push(['speak', input]); return { audio: 'audio' }; },
  };
  const server = createRestrictedServer({
    store, runtime, allowedAgents: () => allowed,
    dispatch: options.dispatch ?? (input => {
      const task = deferred();
      input.signal.addEventListener('abort', () => task.resolve(), { once: true });
      jobs.push({ ...input, finish: task.resolve, fail: task.reject });
      return task.promise;
    }),
  });
  const rawRequest=server.request.bind(server), conversations=new Map();
  server.request=async(secret,body)=> {
    if(body?.op==='send') {
      if(!conversations.has(secret)) conversations.set(secret,(await rawRequest(secret,{op:'history.create'})).id);
      body={conversationId:conversations.get(secret),...body};
    }
    return rawRequest(secret,body);
  };
  let listener;
  t.after(async () => {
    if (listener) { listener.closeAllConnections(); await new Promise(resolve => listener.close(resolve)); }
    await server.close(); await store.close(); await fs.rm(root, { recursive: true, force: true });
  });
  async function phone(agentId) {
    const invite = await store.createInvite({ agentId, label: `${agentId} phone` });
    const device = await store.redeemInvite({ secret: invite.inviteSecret });
    await store.approveDevice({ deviceId: device.id }); return device;
  }
  async function startHttp() {
    const handler = createHttpHandler({ store, server });
    listener = http.createServer((req, res) => { void handler(req, res); });
    await new Promise(resolve => listener.listen(0, '127.0.0.1', resolve));
    return `http://127.0.0.1:${listener.address().port}/voxkin/v1`;
  }
  return { store, server, jobs, calls, phone, startHttp, allow: values => { allowed = values; } };
}
const sendBody = (requestId = 'request-id-00000001', text = 'Hello') => ({ op: 'send', requestId, text });

test('all broker operations require approved credentials before invoking runtime or dispatch', async t => {
  const f = await fixture(t);
  const invite = await f.store.createInvite({ agentId: 'alice', label: 'pending' });
  const pending = await f.store.redeemInvite({ secret: invite.inviteSecret });
  for (const secret of ['bogus', pending.deviceSecret]) {
    for (const body of [{ op: 'hello' }, { op: 'catalog' }, { op: 'speak', text: 'secret' }, sendBody(), { op: 'poll', runId: 'unknown' }, { op: 'abort', runId: 'unknown' }]) {
      await assert.rejects(f.server.request(secret, body), rejected('unauthorized'));
    }
  }
  assert.equal(f.jobs.length, 0); assert.equal(f.calls.length, 0);
});

test('unknown agent/session/identity injection fields are rejected, including on voice calls', async t => {
  const f = await fixture(t); const a = await f.phone('alice');
  for (const extra of [{ agentId: 'main' }, { sessionKey: 'agent:main:main' }, { deviceId: 'other' }, { permissions: ['admin'] }]) {
    for (const body of [sendBody(), { op: 'hello' }, { op: 'catalog' }, { op: 'speak', text: 'hello' }]) {
      await assert.rejects(f.server.request(a.deviceSecret, { ...body, ...extra }), rejected('INVALID_REQUEST'));
    }
  }
  assert.equal(f.jobs.length, 0); assert.equal(f.calls.length, 0);
  await f.server.request(a.deviceSecret, sendBody()); await flush();
  assert.equal(f.jobs[0].device.agentId, 'alice'); assert.equal(f.jobs[0].device.id, a.id);
  assert.equal(f.jobs[0].sessionKey, undefined);
});

test('two phones cannot poll or abort each other, and revoking one leaves the other usable', async t => {
  const f = await fixture(t); const a = await f.phone('alice'); const b = await f.phone('bob');
  const ra = await f.server.request(a.deviceSecret, sendBody());
  const rb = await f.server.request(b.deviceSecret, sendBody()); await flush();
  const ja = f.jobs.find(j => j.runId === ra.runId), jb = f.jobs.find(j => j.runId === rb.runId);
  ja.emit('Alice private'); jb.emit('Bob private');
  for (const op of ['poll', 'abort']) {
    await assert.rejects(f.server.request(a.deviceSecret, { op, runId: rb.runId }), rejected('DENIED'));
    await assert.rejects(f.server.request(b.deviceSecret, { op, runId: ra.runId }), rejected('DENIED'));
  }
  assert.equal(ja.signal.aborted, false); assert.equal(jb.signal.aborted, false);
  assert.equal((await f.server.request(a.deviceSecret, { op: 'poll', runId: ra.runId })).text, 'Alice private');
  await f.store.revokeDevice({ deviceId: a.id }); f.server.revoke(a.id);
  assert.equal(ja.signal.aborted, true); assert.equal(jb.signal.aborted, false);
  await assert.rejects(f.server.request(a.deviceSecret, { op: 'poll', runId: ra.runId }), rejected('unauthorized'));
  assert.equal((await f.server.request(b.deviceSecret, { op: 'poll', runId: rb.runId })).text, 'Bob private');
});

test('voice and catalog results are withheld if credentials are revoked while work is pending', async t => {
  for (const op of ['speak', 'catalog']) {
    const hold = deferred();
    const f = await fixture(t, { runtime: { speak: () => hold.promise, catalog: () => hold.promise } });
    const a = await f.phone('alice');
    const result = f.server.request(a.deviceSecret, op === 'speak' ? { op, text: 'private audio' } : { op });
    await f.store.revokeDevice({ deviceId: a.id });
    hold.resolve({ sensitive: true });
    await assert.rejects(result, rejected('unauthorized'));
  }
});

test('removing an assigned agent from the allowlist immediately denies access', async t => {
  const f = await fixture(t); const a = await f.phone('alice');
  assert.equal((await f.server.request(a.deviceSecret, { op: 'hello' })).agentId, 'alice');
  f.allow(['bob']);
  await assert.rejects(f.server.request(a.deviceSecret, { op: 'hello' }), rejected('DENIED'));
});

test('single active run, same-payload idempotency, and changed-payload replay rejection', async t => {
  const f = await fixture(t); const a = await f.phone('alice');
  const first = await f.server.request(a.deviceSecret, sendBody());
  const repeat = await f.server.request(a.deviceSecret, sendBody());
  assert.deepEqual(first, repeat); await flush(); assert.equal(f.jobs.length, 1);
  await assert.rejects(f.server.request(a.deviceSecret, sendBody('request-id-00000001', 'Changed text')), rejected('INVALID_REQUEST'));
  await assert.rejects(f.server.request(a.deviceSecret, sendBody('request-id-00000002')), rejected('BUSY'));
  f.jobs[0].emit('Final text', true);
  // Final output is not permission to overlap execution: wait for dispatch settlement.
  await assert.rejects(f.server.request(a.deviceSecret, sendBody('request-id-00000002')), rejected('BUSY'));
  f.jobs[0].finish(); await flush();
  assert.equal((await f.server.request(a.deviceSecret, { op: 'poll', runId: first.runId })).state, 'final');
  await f.server.request(a.deviceSecret, sendBody('request-id-00000002')); await flush(); assert.equal(f.jobs.length, 2);
});

test('abort does not allow another run until the aborted dispatch settles', async t => {
  const hold = deferred(); let input;
  const f = await fixture(t, { dispatch: value => { input = value; return hold.promise; } });
  const a = await f.phone('alice'); const run = await f.server.request(a.deviceSecret, sendBody()); await flush();
  const result = await f.server.request(a.deviceSecret, { op: 'abort', runId: run.runId });
  assert.equal(result.state, 'aborted'); assert.equal(input.signal.aborted, true);
  try {
    await assert.rejects(f.server.request(a.deviceSecret, sendBody('request-id-00000002')), rejected('BUSY'));
  } finally { hold.resolve(); }
  await flush();
  await f.server.request(a.deviceSecret, sendBody('request-id-00000002'));
});

test('HTTP link previews/GET never redeem invitations; explicit POST can redeem only once', async t => {
  const f = await fixture(t); const url = await f.startHttp();
  const i = await f.store.createInvite({ agentId: 'alice', label: 'Remote phone' });
  for (const suffix of ['', `?op=redeem&secret=${i.inviteSecret}`]) {
    const res = await fetch(url + suffix);
    assert.equal(res.status, 405); assert.equal(f.store.list().invites.length, 1);
  }
  const options = { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ op: 'redeem', secret: i.inviteSecret }) };
  const redeemed = await fetch(url, options); assert.equal(redeemed.status, 200);
  assert.equal(redeemed.headers.get('cache-control'), 'no-store');
  const pending = await redeemed.json(); assert.equal(pending.status, 'pending');
  assert.throws(() => f.store.authenticate(pending.deviceSecret), rejected('unauthorized'));
  assert.equal((await fetch(url, options)).status, 403);
});

test('HTTP refuses missing auth, excessive body, unknown enrollment fields, and browser origins', async t => {
  const f = await fixture(t); const url = await f.startHttp(); const a = await f.phone('alice');
  const post = (body, headers = {}) => fetch(url, { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) });
  assert.equal((await post({ op: 'catalog' })).status, 401);
  assert.equal((await post({ op: 'catalog' }, { authorization: 'Bearer wrong' })).status, 401);
  assert.equal((await post({ op: 'speak', text: 'x'.repeat(70001) }, { authorization: `Bearer ${a.deviceSecret}` })).status, 403);
  const i = await f.store.createInvite({ agentId: 'alice', label: 'Phone' });
  assert.equal((await post({ op: 'redeem', secret: i.inviteSecret, agentId: 'main' })).status, 403);
  assert.equal(f.store.list().invites.length, 1);
  assert.equal((await post({ op: 'redeem', secret: i.inviteSecret }, { origin: 'https://example.invalid' })).status, 405);
  assert.equal(f.store.list().invites.length, 1); assert.equal(f.calls.length, 0);
});
