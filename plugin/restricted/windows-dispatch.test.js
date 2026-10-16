import test from 'node:test';
import assert from 'node:assert/strict';
import {createWindowsIngress} from './windows-dispatch.js';
const input=()=>({device:{id:'phone',agentId:'windows'},text:'Hi',sessionKey:'agent:windows:voxkin:phone-chat',peerId:'phone-chat',runId:'run',signal:new AbortController().signal,emit(){}});
function fixture(action){const hooks={};let args;const fn=createWindowsIngress({on:(n,f)=>hooks[n]=f},{loadIngress:async()=>({agentCommandFromIngress:async p=>{args=p;return action(hooks,{runId:p.runId,agentId:p.agentId,sessionKey:p.sessionKey},p);}})});return {fn,hooks,get args(){return args;}};}
const output=(h,c,m={})=>h.llm_output({runId:c.runId,lastAssistant:{role:'assistant',stopReason:'stop',content:[{type:'thinking',thinking:'secret'},{type:'text',text:'Hello'}],...m}},c);
test('uses nonowner capped ingress and publishes only verified terminal text',async()=>{
 const f=fixture((h,c)=>{output(h,c);assert.equal(h.before_tool_call({},c).block,true);assert.equal(h.before_tool_call({},{...c,sessionKey:'other'}),undefined);return {payloads:[{text:'reasoning leaked by JSON'},{text:'Hello',mediaUrl:'secret'}]};});const seen=[];await f.fn({...input(),emit:t=>seen.push(t)});assert.deepEqual(seen,['Hello']);assert.deepEqual(f.args.toolsAllow,[]);assert.equal(f.args.allowModelOverride,false);assert.equal(f.args.senderIsOwner,false);assert.equal(f.args.deliver,false);assert.equal(f.args.disableMessageTool,true);assert.equal(f.hooks.before_tool_call({},{runId:'run',agentId:'windows',sessionKey:input().sessionKey}),undefined);
});
for(const [name,m] of [['commentary',{phase:'commentary'}],['tool turn',{stopReason:'toolUse'}],['tool block',{content:[{type:'text',text:'Hello'},{type:'toolCall'}]}],['error',{stopReason:'error'}]])test('rejects '+name,async()=>{const f=fixture((h,c)=>{output(h,c,m);return {payloads:[{text:'Hello'}]};});await assert.rejects(f.fn(input()),/verified/);});
test('rejects mismatched scope, errors and transformed output',async()=>{
 for(const mode of ['scope','error','changed']){const f=fixture((h,c)=>{output(h,mode==='scope'?{...c,sessionKey:'other'}:c);return {payloads:[{text:mode==='changed'?'prefix Hello':'Hello',isError:mode==='error'}]};});await assert.rejects(f.fn(input()));}
});
test('cancellation suppresses even a captured final and clears hooks',async()=>{const ctrl=new AbortController();const f=fixture((h,c)=>{output(h,c);ctrl.abort();return {payloads:[{text:'Hello'}]};});await assert.rejects(f.fn({...input(),signal:ctrl.signal}));assert.equal(f.hooks.before_tool_call({},{runId:'run',agentId:'windows',sessionKey:input().sessionKey}),undefined);});
test('concurrent runs cannot borrow another run final',async()=>{const f=fixture(async(h,c)=>{if(c.runId==='one')output(h,c);return {payloads:[{text:'Hello'}]};});const r=await Promise.allSettled([f.fn({...input(),runId:'one'}),f.fn({...input(),runId:'two'})]);assert.equal(r[0].status,'fulfilled');assert.equal(r[1].status,'rejected');});
