import test from 'node:test';
import assert from 'node:assert/strict';
import {createRestrictedDispatch} from './dispatch.js';
function fixture() {
  const calls=[]; let admitted=false;
  const api={config:{commands:{text:true}},runtime:{channel:{
    routing:{buildAgentSessionKey:p=>`agent:${p.agentId}:${p.channel}:${p.accountId}:direct:${p.peer.id}`},
    inbound:{buildContext:p=>({...p,SessionKey:p.route.routeSessionKey}),dispatch:async p=>{
      calls.push(p);
      await p.delivery.deliver({text:'secret tool data'},{kind:'tool'});
      await p.delivery.deliver({text:'Hello',mediaUrl:'/private/file'},{kind:'final'});
      return {dispatched:true};
    }},
  }}};
  const fn=createRestrictedDispatch(api,{runDetachedWebhookWork:work=>{admitted=true;return Promise.resolve().then(work);}});
  return {api,calls,fn,get admitted(){return admitted;}};
}
const request=(overrides={})=>({device:{id:'phone-123',agentId:'guest'},conversationId:'12345678-1234-4234-8234-123456789012',text:'Hola',runId:'run-1',signal:new AbortController().signal,emit:()=>{},...overrides});
test('fails closed without required SDK APIs',()=>assert.throws(()=>createRestrictedDispatch({})));
test('admission synchronous, private server route, tools disabled, final text only',async()=>{
  const f=fixture(), messages=[];
  const task=f.fn(request({emit:(...args)=>messages.push(args)}));
  assert.equal(f.admitted,true); await task;
  const p=f.calls[0];
  assert.equal(p.route.sessionKey,'agent:guest:voxkin:restricted:direct:phone-123-12345678-1234-4234-8234-123456789012');
  assert.deepEqual(p.toolsAllow,[]);assert.deepEqual(p.replyOptions.toolsAllow,[]);
  assert.equal(p.replyOptions.disableTools,true);assert.equal(p.cfg.commands.text,false);
  assert.equal(f.api.config.commands.text,true);
  assert.equal(p.ctxPayload.access.commands.authorized,false);
  assert.deepEqual(messages,[['Hello',true]]);
});
test('rejects command turns and malformed identity before admission',()=>{
  for(const patch of [{text:'/model admin'},{device:{id:'../main',agentId:'main'}}]) {
    const f=fixture();assert.throws(()=>f.fn(request(patch)));assert.equal(f.admitted,false);
  }
});
test('aborted work never dispatches',async()=>{
  const f=fixture(),c=new AbortController();c.abort();
  await assert.rejects(f.fn(request({signal:c.signal})));assert.equal(f.calls.length,0);
});
test('session helper cannot silently route to main',async()=>{
  const f=fixture();f.api.runtime.channel.routing.buildAgentSessionKey=()=> 'agent:guest:main';
  await assert.rejects(f.fn(request()),/Unsafe/);assert.equal(f.calls.length,0);
});
test('Windows adapter receives only validated server-derived route; channel path untouched',async()=>{
 const f=fixture();let received;
 const fn=createRestrictedDispatch(f.api,{runDetachedWebhookWork:w=>w(),windowsIngress:async p=>{received=p;}});
 await fn(request());assert.equal(f.calls.length,0);
 assert.equal(received.sessionKey,'agent:guest:voxkin:restricted:direct:phone-123-12345678-1234-4234-8234-123456789012');
 assert.equal(received.device.agentId,'guest');
});
