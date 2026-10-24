import { randomUUID, createHash } from 'node:crypto';
import {RestrictedHistory} from './history.js';

// This is a channel ingress, never a proxy to operator RPC. All authority comes
// from the credential store; no client-supplied session key reaches OpenClaw.
export function createRestrictedServer({store, runtime, dispatch, allowedAgents, now = Date.now, history = new RestrictedHistory()}) {
  const runs = new Map();
  const mutations = new Set();
  let closed = false;
  const deny = (code = 'DENIED') => { throw Object.assign(new Error(code), {code}); };
  function identity(secret) {
    if (closed) deny('UNAVAILABLE');
    const d = store.authenticate(secret);
    if (!allowedAgents().includes(d.agentId)) deny();
    return d;
  }
  function prune() {
    for (const [id, r] of runs) if (r.settled && now() - r.updated > 300_000) runs.delete(id);
  }
  async function request(secret, body) {
    if (!body || typeof body !== 'object' || Array.isArray(body)) deny('INVALID_REQUEST');
    const d = identity(secret);
    const deviceId = d.id ?? d.deviceId;
    const keys = {hello:[],catalog:[],speak:['text','language','voice'],send:['text','requestId','conversationId'],'history.list':['before','limit'],'history.create':[],'history.rename':['conversationId','title'],'history.delete':['conversationId'],'history.messages':['conversationId','before','limit'],poll:['runId'],abort:['runId']}[body.op];
    if (!keys || Object.keys(body).some(k => k !== 'op' && !keys.includes(k))) deny('INVALID_REQUEST');
    switch (body.op) {
      case 'hello': return {version:1, agentId:d.agentId, deviceId, history:true, historyActions:true};
      case 'history.list': return history.list(d,body);
      case 'history.messages': return history.messages(d,body);
      case 'history.create': {const result=await history.create(d,()=>identity(secret));identity(secret);return result;}
      case 'history.rename': case 'history.delete': {
        history.owned(d,body.conversationId);
        const id=body.conversationId;
        if(mutations.has(id) || [...runs.values()].some(r=>r.conversationId===id && !r.settled)) deny('BUSY');
        mutations.add(id);
        try {
          const result=await history[body.op==='history.rename'?'rename':'delete'](d,body,()=>identity(secret));
          if(body.op==='history.delete') for(const [runId,r] of runs) if(r.conversationId===id) runs.delete(runId);
          identity(secret);return result;
        } finally { mutations.delete(id); }
      }
      case 'catalog': { const result = await runtime.catalog(); identity(secret); return result; }
      case 'speak': {
        const result = await runtime.speak({text:body.text, language:body.language, voice:body.voice});
        identity(secret); return result;
      }
      case 'send': {
        if (typeof body.text !== 'string' || !body.text.trim() || /^\s*\//.test(body.text) || body.text.length > 16_000 ||
            typeof body.requestId !== 'string' || !/^[a-zA-Z0-9-]{16,80}$/.test(body.requestId)) deny('INVALID_REQUEST');
        history.owned(d,body.conversationId);
        if(mutations.has(body.conversationId))deny('BUSY');
        prune();
        const previous = [...runs.values()].find(r => r.deviceId === deviceId && r.agentId === d.agentId && r.requestId === body.requestId);
        if (previous) { if (previous.conversationId !== body.conversationId || previous.inputHash !== createHash('sha256').update(body.text).digest('hex')) deny('INVALID_REQUEST'); await previous.admission; identity(secret); return {runId:previous.id}; }
        // Retention is an idempotency promise: never evict an unexpired result to
        // admit another turn. A full phone waits for its own five-minute window;
        // it cannot consume another phone's allocation. Active work has its own
        // ceiling; 4096 is a final multi-device memory overload guard.
        const retained = [...runs.values()];
        if (retained.some(r => r.deviceId === deviceId && !r.settled) ||
            retained.filter(r => r.deviceId === deviceId).length >= 32 ||
            retained.filter(r => !r.settled).length >= 128 || runs.size >= 4096) deny('BUSY');
        const r = {id:randomUUID(),deviceId,agentId:d.agentId,conversationId:body.conversationId,requestId:body.requestId,controller:new AbortController(),state:'working',text:'',done:false,settled:false,inputHash:createHash('sha256').update(body.text).digest('hex'),updated:now()};
        runs.set(r.id,r);
        r.admission = history.append(d,body.conversationId,'user',body.text,()=>identity(secret));
        try {await r.admission;identity(secret);} catch(error) {runs.delete(r.id);throw error;}
        const emit = (text, final = false) => {
          identity(secret);
          if (r.done) return;
          if (typeof text === 'string') r.text = text.slice(0,32_000);
          // Final is observable only after durable history write and dispatch settlement.
          r.state = 'working';
          r.updated = now();
        };
        const deadline = setTimeout(() => { r.controller.abort(); r.state='aborted'; r.done=true; r.text=''; }, 120_000); deadline.unref?.();
        let work;
        try { identity(secret); work = dispatch({device:{...d,id:deviceId},text:body.text,conversationId:r.conversationId,runId:r.id,signal:r.controller.signal,emit}); }
        catch (error) { work = Promise.reject(error); }
        r.task = Promise.resolve(work).then(async () => {
          if (!r.done) { identity(secret); if(r.text) await history.append(d,r.conversationId,'assistant',r.text,()=>identity(secret)); if(r.done || r.controller.signal.aborted) return; identity(secret); r.state = 'final'; r.done = true; r.updated = now(); }
        }).catch(() => { r.state = r.controller.signal.aborted ? 'aborted' : 'error'; r.text = ''; r.done = true; r.updated = now(); }).finally(() => { clearTimeout(deadline); r.settled=true; });
        return {runId:r.id};
      }
      case 'poll': case 'abort': {
        if (typeof body.runId !== 'string' || body.runId.length > 80) deny('INVALID_REQUEST');
        const r = runs.get(body.runId);
        if (!r || r.deviceId !== deviceId || r.agentId !== d.agentId) deny();
        if (body.op === 'abort' && !r.done) {
          r.controller.abort(); r.state = 'aborted'; r.text = ''; r.done = true; r.updated = now();
        }
        return {runId:r.id,state:r.state,text:r.text};
      }
    }
  }
  function revoke(deviceId) {
    for (const r of runs.values()) if (r.deviceId === deviceId) {
      r.controller.abort(); r.text = ''; r.state = 'aborted'; r.done = true; r.updated = now();
    }
  }
  return {request,revoke,async close() { closed = true; for (const r of runs.values()) r.controller.abort(); let timer; try { await Promise.race([Promise.allSettled([...runs.values()].flatMap(r=>[r.admission,r.task]).concat(history.queue)),new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('Restricted work did not drain; restart required.')),10_000);timer.unref?.();})]); runs.clear(); } finally {clearTimeout(timer);} }};
}

export function createHttpHandler({store, server, now = Date.now}) {
  let active = 0, enrollmentActive = 0, windowStart = now(), admissions = 0, enrollmentAdmissions = 0;
  const devices = new Map();
  return async (req,res) => {
    res.setHeader('Cache-Control','no-store');
    res.setHeader('Content-Type','application/json');
    res.setHeader('X-Content-Type-Options','nosniff');
    const send = (status,value) => { if (!res.writableEnded) { res.statusCode=status; res.end(JSON.stringify(value)); } };
    if (req.method !== 'POST' || req.url?.split('?')[0] !== '/voxkin/v1' || req.url?.includes('?') || req.headers.origin) { send(405,{error:'POST_REQUIRED'}); return true; }
    // Authenticate headers before reserving capacity or waiting on an attacker
    // controlled body. Enrollment has a separate, small pool. No client-supplied
    // forwarding header participates in identity or quota decisions.
    let secret, device;
    if (req.headers.authorization !== undefined) {
      const match = /^Bearer ([A-Za-z0-9_-]{32,256})$/.exec(req.headers.authorization);
      if (!match) { send(401,{error:'DENIED'}); return true; }
      try { device = store.authenticate(match[1]); secret = match[1]; }
      catch { send(401,{error:'DENIED'}); return true; }
    }
    if (now()-windowStart >= 60_000) {
      windowStart=now(); admissions=0; enrollmentAdmissions=0;
      for (const [id,q] of devices) { q.count=0; if (!q.active) devices.delete(id); }
    }
    let quota;
    if (device) {
      const id = device.id ?? device.deviceId;
      quota = devices.get(id);
      if (!quota) {
        if (devices.size >= 10_000) { send(429,{error:'BUSY'}); return true; }
        quota = {count:0,active:0}; devices.set(id,quota);
      }
      if (active >= 32 || quota.active >= 4 || quota.count >= 600 || admissions >= 6000) { send(429,{error:'BUSY'}); return true; }
      quota.count++; quota.active++; active++; admissions++;
    } else {
      if (enrollmentActive >= 4 || enrollmentAdmissions >= 60) { send(429,{error:'BUSY'}); return true; }
      enrollmentActive++; enrollmentAdmissions++;
    }
    const timer = setTimeout(()=>req.destroy(),10_000); timer.unref?.();
    try {
      if (!String(req.headers['content-type'] ?? '').startsWith('application/json')) throw new Error();
      let size=0; const chunks=[];
      for await (const chunk of req) { size+=chunk.length; if(size>70_000) throw new Error(); chunks.push(chunk); }
      clearTimeout(timer);
      const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
      let result;
      if(body?.op==='redeem' && !device) {
        if(Object.keys(body).some(k=>!['op','secret'].includes(k))) throw new Error();
        result=await store.redeemInvite({secret:body.secret});
      } else {
        if(!secret) { send(401,{error:'DENIED'}); return true; }
        result=await server.request(secret,body);
      }
      send(200,result);
    } catch(error) {
      const code = ['BUSY','UNAVAILABLE','INVALID_REQUEST'].includes(error.code) ? error.code : 'DENIED';
      send(code==='BUSY'?429:code==='UNAVAILABLE'?503:403,{error:code});
    } finally {clearTimeout(timer); if (quota) {active--;quota.active--;} else enrollmentActive--;}
    return true;
  };
}
