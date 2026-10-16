import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {join} from 'node:path';import {homedir} from 'node:os';import {randomUUID} from 'node:crypto';
import {gatewayCall} from './package/onboard.mjs';
const root=join(homedir(),'voxkin-native-test');
process.env.OPENCLAW_STATE_DIR=join(root,'state');process.env.OPENCLAW_CONFIG_PATH=join(root,'state','openclaw.json');process.env.VOXKIN_TEST_OPENAI_KEY=(await readFile(join(root,'secrets','openai.key'),'utf8')).trim();
const url='http://127.0.0.1:18789/voxkin/v1';
async function request(body,key){const r=await fetch(url,{method:'POST',headers:{'content-type':'application/json',...(key?{authorization:'Bearer '+key}:{})},body:JSON.stringify(body),signal:AbortSignal.timeout(120000)});return {status:r.status,body:await r.json()};}
let ready=false;for(let i=0;i<45;i++){try{if((await request({op:'hello'})).status===401){ready=true;break;}}catch{} await new Promise(r=>setTimeout(r,1000));}assert.ok(ready,'Gateway readiness timeout');

const devices=[];
for(let i=0;i<2;i++){
 const inv=await gatewayCall('invite',{agentId:'windows',label:'Native proof lifecycle '+i});
 const d=(await request({op:'redeem',secret:inv.inviteSecret})).body;
 await gatewayCall('approve',{deviceId:d.id});devices.push(d);
}
const [a,b]=devices;
const c=(await request({op:'history.create'},a.deviceSecret)).body.id;
assert.equal((await request({op:'send',conversationId:c,text:'Hi',requestId:randomUUID()},b.deviceSecret)).status,403);
const sent=await request({op:'send',conversationId:c,text:'Write a long story about a mountain.',requestId:randomUUID()},a.deviceSecret);assert.equal(sent.status,200);
assert.equal((await request({op:'send',conversationId:c,text:'Overlapping turn',requestId:randomUUID()},a.deviceSecret)).status,429);
assert.equal((await request({op:'poll',runId:sent.body.runId},b.deviceSecret)).status,403);
await gatewayCall('revoke',{deviceId:a.id});
assert.equal((await request({op:'poll',runId:sent.body.runId},a.deviceSecret)).status,403);
assert.equal((await request({op:'send',conversationId:c,text:'Hi',requestId:randomUUID()},a.deviceSecret)).status,403);
await gatewayCall('revoke',{deviceId:b.id});
console.log('NATIVE_CONCURRENCY_CROSS_DEVICE_REVOKE_PASS');
