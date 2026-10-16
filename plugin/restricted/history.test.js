import test from 'node:test';
import assert from 'node:assert/strict';
import {promises as fs} from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {RestrictedHistory} from './history.js';
import {createRestrictedServer} from './server.js';
const a={id:'a',agentId:'alice'},b={id:'b',agentId:'alice'};
const rejected=code=>e=>e.code===code;
async function fixture(t) {const dir=await fs.mkdtemp(path.join(os.tmpdir(),'voxkin-history-'));t.after(()=>fs.rm(dir,{recursive:true,force:true}));return {dir,h:await new RestrictedHistory({stateDir:dir,now:()=>1000}).initialize()};}
test('persistent, text-only history pages have stable tie ordering and device plus agent isolation',async t=>{
  const {h,dir}=await fixture(t);
  for(let i=0;i<23;i++)await h.create(a);
  const first=h.list(a);assert.equal(first.conversations.length,20);
  const rest=h.list(a,{before:first.nextCursor});assert.equal(rest.conversations.length,3);assert.equal(rest.nextCursor,undefined);
  assert.equal(new Set([...first.conversations,...rest.conversations].map(c=>c.id)).size,23);
  const id=first.conversations[0].id;
  for(let i=0;i<55;i++)await h.append(a,id,i%2?'assistant':'user',`Text ${i}`);
  const reopened=await new RestrictedHistory({stateDir:dir}).initialize();
  const page=reopened.messages(a,{conversationId:id});assert.equal(page.messages.length,50);assert.equal(page.messages[0].text,'Text 5');assert.equal(page.messages.at(-1).text,'Text 54');
  const older=reopened.messages(a,{conversationId:id,before:page.nextCursor});assert.equal(older.messages.length,5);assert.equal(older.nextCursor,undefined);
  assert.deepEqual(Object.keys(page.messages[0]).sort(),['createdAt','id','role','text']);
  assert.deepEqual(h.list(b),{conversations:[]});
  for(const device of [b,{...a,agentId:'bob'}])assert.throws(()=>h.messages(device,{conversationId:id}),rejected('DENIED'));
  assert.throws(()=>h.list(b,{before:id}),rejected('DENIED'));
  assert.equal((await fs.stat(path.join(dir,'history.json'))).mode&0o777,0o600);
});
test('malformed conversation, cursor, bounds and cross-conversation message IDs rejected',async t=>{
  const {h}=await fixture(t),c=await h.create(a),d=await h.create(a);
  const m=await h.append(a,d.id,'user','Other');
  for(const id of ['../access','agent:main:main','',null])assert.throws(()=>h.messages(a,{conversationId:id}),rejected('INVALID_REQUEST'));
  assert.throws(()=>h.messages(a,{conversationId:c.id,before:m.id}),rejected('DENIED'));
  for(const limit of [0,51,-1,1.2,'1'])assert.throws(()=>h.messages(a,{conversationId:c.id,limit}),rejected('INVALID_REQUEST'));
  assert.throws(()=>h.list(a,{limit:21}),rejected('INVALID_REQUEST'));
});
test('server requires conversation, routes new/resumed turns separately, persists only successful final output and denies revoked history',async t=>{
  const {h}=await fixture(t);let revoked=false;const routes=[];
  const store={authenticate:s=>{if(revoked)throw Object.assign(new Error(),{code:'unauthorized'});return s==='a'?a:b;}};
  const server=createRestrictedServer({store,history:h,runtime:{},allowedAgents:()=>['alice'],dispatch:async input=>{routes.push(input.conversationId);input.emit('Final answer',true);}});t.after(()=>server.close());
  const c=await server.request('a',{op:'history.create'}),d=await server.request('a',{op:'history.create'});
  const send=(id,n)=>({op:'send',conversationId:id,text:'Question',requestId:`request-000000000${n}`});
  await assert.rejects(server.request('a',{op:'send',text:'No id',requestId:'request-000000001'}),rejected('INVALID_REQUEST'));
  await assert.rejects(server.request('b',send(c.id,0)),rejected('DENIED'));
  for(const [id,n]of [[c.id,1],[d.id,2],[c.id,3]]){await server.request('a',send(id,n));await new Promise(r=>setTimeout(r,10));}
  assert.deepEqual(routes,[c.id,d.id,c.id]);assert.equal(h.messages(a,{conversationId:c.id}).messages.length,4);
  revoked=true;
  for(const body of [{op:'history.list'},{op:'history.create'},{op:'history.messages',conversationId:c.id}])await assert.rejects(server.request('a',body),rejected('unauthorized'));
});
test('corrupt or insecure persistent history fails closed',async t=>{
  const {dir}=await fixture(t),file=path.join(dir,'history.json');
  await fs.writeFile(file,'{"version":1,"conversations":[{}]}',{mode:0o600});
  await assert.rejects(new RestrictedHistory({stateDir:dir}).initialize());
  await fs.writeFile(file,'{"version":1,"conversations":[]}');await fs.chmod(file,0o644);
  await assert.rejects(new RestrictedHistory({stateDir:dir}).initialize(),rejected('UNAVAILABLE'));
});
test('failed archive writes never publish an unpersisted conversation or dispatch a turn',async t=>{
  const {h,dir}=await fixture(t);const c=await h.create(a);
  await fs.rename(path.join(dir,'history.json'),path.join(dir,'saved.json'));
  await fs.mkdir(path.join(dir,'history.json'));
  await assert.rejects(h.create(a));assert.equal(h.list(a).conversations.length,1);
  let dispatched=false;
  const server=createRestrictedServer({store:{authenticate:()=>a},history:h,runtime:{},allowedAgents:()=>['alice'],dispatch:async()=>{dispatched=true;}});t.after(()=>server.close());
  await assert.rejects(server.request('a',{op:'send',conversationId:c.id,text:'Do not send',requestId:'request-0000000001'}));
  assert.equal(dispatched,false);assert.deepEqual(h.messages(a,{conversationId:c.id}),{messages:[]});
});
test('long per-turn preference envelope is absent from archive, title and preview but retained for dispatch',async t=>{
  const {h,dir}=await fixture(t);let dispatchedText;
  const server=createRestrictedServer({store:{authenticate:()=>a},history:h,runtime:{},allowedAgents:()=>['alice'],dispatch:async input=>{dispatchedText=input.text;}});t.after(()=>server.close());
  const c=await server.request('a',{op:'history.create'});
  const question='What should I pack for a weekend hiking trip?';
  const text='[Voxkin reply preference for this turn only: Keep your answer brief and conversational. For quick questions, answer directly without acknowledgment or preamble. Only if work will take time, send at most one short natural contextual opening through the supported user-visible source-reply channel (under 180 characters), describing only work you are actually starting. Do not say Got it or narrate internal reasoning. Respond in English unless the user explicitly requests another language.]\n\n'+question;
  await server.request('a',{op:'send',conversationId:c.id,text,requestId:'request-0000000001'});
  await new Promise(r=>setImmediate(r));
  assert.equal(dispatchedText,text);
  const summary=h.list(a).conversations[0];assert.equal(summary.title,question);assert.equal(summary.preview,question);
  assert.equal(h.messages(a,{conversationId:c.id}).messages[0].text,question);
  const disk=JSON.parse(await fs.readFile(path.join(dir,'history.json'),'utf8'));assert.equal(disk.conversations[0].messages[0].text,question);
  const literal='Discuss [Voxkin reply preference for this turn only: short]\n\n literally';
  await h.append(a,c.id,'user',literal);assert.equal(h.messages(a,{conversationId:c.id}).messages.at(-1).text,literal);
  await h.append(a,c.id,'assistant',text);assert.equal(h.messages(a,{conversationId:c.id}).messages.at(-1).text,text);
});
test('assistant archive capacity failure never reports an unpersisted final answer',async t=>{
  const {h}=await fixture(t),append=h.append.bind(h);let resolveWrite;
  const writeBlocked=new Promise(resolve=>{resolveWrite=resolve;});
  h.append=async(...args)=>{if(args[2]==='assistant'){await writeBlocked;throw Object.assign(new Error('Full'),{code:'BUSY'});}return append(...args);};
  const server=createRestrictedServer({store:{authenticate:()=>a},history:h,runtime:{},allowedAgents:()=>['alice'],dispatch:async input=>input.emit('Final must be durable',true)});t.after(()=>server.close());
  const c=await server.request('a',{op:'history.create'});
  const run=await server.request('a',{op:'send',conversationId:c.id,text:'Question',requestId:'request-0000000001'});
  await new Promise(r=>setImmediate(r));assert.equal((await server.request('a',{op:'poll',runId:run.runId})).state,'working');
  resolveWrite();await new Promise(r=>setImmediate(r));
  const result=await server.request('a',{op:'poll',runId:run.runId});assert.equal(result.state,'error');assert.equal(result.text,'');
  assert.deepEqual(h.messages(a,{conversationId:c.id}).messages.map(m=>m.role),['user']);
});
test('abort during a pending final archive write cannot be overwritten by final completion',async t=>{
  const {h}=await fixture(t),append=h.append.bind(h);let resolveWrite,writeStarted;
  const started=new Promise(resolve=>{writeStarted=resolve;});
  const blocked=new Promise(resolve=>{resolveWrite=resolve;});
  h.append=async(...args)=>{if(args[2]==='assistant'){writeStarted();await blocked;}return append(...args);};
  const server=createRestrictedServer({store:{authenticate:()=>a},history:h,runtime:{},allowedAgents:()=>['alice'],dispatch:async input=>input.emit('Completed text',true)});t.after(()=>server.close());
  const c=await server.request('a',{op:'history.create'});
  const run=await server.request('a',{op:'send',conversationId:c.id,text:'Question',requestId:'request-0000000001'});
  await started;
  assert.equal((await server.request('a',{op:'abort',runId:run.runId})).state,'aborted');
  resolveWrite();await h.queue;await new Promise(r=>setTimeout(r,20));
  const result=await server.request('a',{op:'poll',runId:run.runId});assert.equal(result.state,'aborted');assert.equal(result.text,'');
});
test('per-phone archive quota prevents one device exhausting the shared budget without deleting old text',async()=>{
  const h=new RestrictedHistory();const c=await h.create(a),other=await h.create(b);
  // Seed a near-cap valid archive to exercise boundary without hundreds of disk writes.
  for(let i=0;i<131;i++)await h.append(a,c.id,'user','x'.repeat(31900));
  const count=h.owned(a,c.id).messages.length;
  await assert.rejects(h.append(a,c.id,'user','x'.repeat(31900)),rejected('BUSY'));
  assert.equal(h.owned(a,c.id).messages.length,count);
  await h.append(b,other.id,'user','Other phone still works');
  assert.equal(h.messages(b,{conversationId:other.id}).messages[0].text,'Other phone still works');
});
test('titles choose meaningful early user text, preserve Unicode, and manual names persist',async t=>{
  const {h,dir}=await fixture(t),c=await h.create(a);
  await h.append(a,c.id,'user','Test 1234');
  await h.append(a,c.id,'assistant','Ready');
  await h.append(a,c.id,'user','[Voxkin reply preference for this turn only: short]\n\n Weather   in Denver?');
  assert.equal(h.list(a).conversations[0].title,'Weather in Denver?');
  await h.rename(a,{conversationId:c.id,title:'  My\n weather   notes  '});
  await h.append(a,c.id,'user','Something else');
  const reopened=await new RestrictedHistory({stateDir:dir}).initialize();
  assert.equal(reopened.list(a).conversations[0].title,'My weather notes');
  assert.equal(reopened.list(a).conversations[0].customTitle,'My weather notes');
  for(const title of ['', ' \n ', null, 4, '🌈'.repeat(81)])assert.throws(()=>h.rename(a,{conversationId:c.id,title}),rejected('INVALID_REQUEST'));
  await h.rename(a,{conversationId:c.id,title:'🌈'.repeat(80)});
  assert.equal([...h.list(a).conversations[0].title].length,80);
  const u=await h.create(a);await h.append(a,u.id,'user','🌈'.repeat(90));
  assert.equal([...h.list(a).conversations.find(x=>x.id===u.id).title].length,80);
});
test('rename and delete isolate device/agent, persist deletion, reject late append',async t=>{
  const {h,dir}=await fixture(t),c=await h.create(a),other=await h.create(b);
  for(const device of [b,{...a,agentId:'bob'}]) {
    await assert.rejects(h.rename(device,{conversationId:c.id,title:'Stolen'}),rejected('DENIED'));
    await assert.rejects(h.delete(device,{conversationId:c.id}),rejected('DENIED'));
  }
  await h.delete(a,{conversationId:c.id});
  await assert.rejects(h.append(a,c.id,'assistant','Late result'),rejected('DENIED'));
  const reopened=await new RestrictedHistory({stateDir:dir}).initialize();
  assert.deepEqual(reopened.list(a).conversations,[]);
  assert.equal(reopened.list(b).conversations[0].id,other.id);
});
test('failed rename/delete writes do not publish, queued revoked mutation cannot commit',async t=>{
  const {h,dir}=await fixture(t),c=await h.create(a);
  await fs.rename(path.join(dir,'history.json'),path.join(dir,'saved.json'));await fs.mkdir(path.join(dir,'history.json'));
  await assert.rejects(h.rename(a,{conversationId:c.id,title:'Lost name'}));
  await assert.rejects(h.delete(a,{conversationId:c.id}));
  assert.equal(h.list(a).conversations[0].title,'New conversation');
  let unblock;h.queue=new Promise(r=>{unblock=r;});let revoked=false;
  const pending=h.rename(a,{conversationId:c.id,title:'Forbidden'},()=>{if(revoked)throw Object.assign(new Error(),{code:'DENIED'});});
  revoked=true;unblock();await assert.rejects(pending,rejected('DENIED'));
  assert.equal(h.list(a).conversations[0].title,'New conversation');
});
test('server blocks active and aborted-unsettled mutations, serializes delete/send and purges finished buffers',async t=>{
  const {h}=await fixture(t);let finish;
  const work=new Promise(r=>{finish=r;});
  const server=createRestrictedServer({store:{authenticate:()=>a},history:h,runtime:{},allowedAgents:()=>['alice'],dispatch:async input=>{await work;input.emit('Finished',true);}});t.after(()=>server.close());
  assert.equal((await server.request('a',{op:'hello'})).historyActions,true);
  const c=await server.request('a',{op:'history.create'});
  const send={op:'send',conversationId:c.id,text:'Question',requestId:'request-0000000001'};
  const run=await server.request('a',send);
  for(const op of ['history.rename','history.delete'])await assert.rejects(server.request('a',{op,conversationId:c.id,...(op==='history.rename'?{title:'New'}:{})}),rejected('BUSY'));
  await server.request('a',{op:'abort',runId:run.runId});
  await assert.rejects(server.request('a',{op:'history.delete',conversationId:c.id}),rejected('BUSY'));
  finish();await new Promise(r=>setImmediate(r));
  let unblock;h.queue=new Promise(r=>{unblock=r;});
  const deleting=server.request('a',{op:'history.delete',conversationId:c.id});
  await assert.rejects(server.request('a',{...send,requestId:'request-0000000002'}),rejected('BUSY'));
  unblock();assert.deepEqual(await deleting,{deleted:true});
  await assert.rejects(server.request('a',{op:'poll',runId:run.runId}),rejected('DENIED'));
  await assert.rejects(server.request('a',send),rejected('DENIED'));
});
test('server rechecks revoked access inside queued delete before publishing',async t=>{
  const {h}=await fixture(t);let revoked=false;
  const server=createRestrictedServer({store:{authenticate:()=>{if(revoked)throw Object.assign(new Error(),{code:'DENIED'});return a;}},history:h,runtime:{},allowedAgents:()=>['alice'],dispatch:async()=>{}});t.after(()=>server.close());
  const c=await server.request('a',{op:'history.create'});let unblock;h.queue=new Promise(r=>{unblock=r;});
  const deleting=server.request('a',{op:'history.delete',conversationId:c.id});
  revoked=true;unblock();await assert.rejects(deleting,rejected('DENIED'));
  assert.equal(h.list(a).conversations.length,1);
});

test('revocation during persistence is checked again before atomic replacement',async t=>{
  const {h,dir}=await fixture(t),c=await h.create(a);
  let checks=0;
  await assert.rejects(h.delete(a,{conversationId:c.id},()=>{if(++checks===2)throw Object.assign(new Error(),{code:'DENIED'});}),rejected('DENIED'));
  assert.equal(checks,2);assert.equal(h.list(a).conversations.length,1);
  const reopened=await new RestrictedHistory({stateDir:dir}).initialize();assert.equal(reopened.list(a).conversations.length,1);
  assert.deepEqual((await fs.readdir(dir)).sort(),['history.json']);
});

test('automatic titles skip greeting and audio checks and never expose partial preference envelopes',async()=>{
  const h=new RestrictedHistory();
  for(const greeting of ['hello there','hola','can you hear me?','are you there','1234']) {
    const c=await h.create(a);await h.append(a,c.id,'user',greeting);
    await h.append(a,c.id,'user','What is the weather in Denver?');
    assert.equal(h.list(a).conversations.find(row=>row.id===c.id).title,'What is the weather in Denver?');
  }
  const c=await h.create(a);await h.append(a,c.id,'user','[Voxkin reply preference for this turn only: unfinished');
  assert.equal(h.list(a).conversations.find(row=>row.id===c.id).title,'New conversation');
  await h.append(a,c.id,'user','Plan my hiking trip');
  assert.equal(h.list(a).conversations.find(row=>row.id===c.id).title,'Plan my hiking trip');
});
