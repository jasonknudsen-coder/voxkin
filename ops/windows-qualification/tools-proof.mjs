import {existsSync} from 'node:fs';
import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {join} from 'node:path';import {homedir} from 'node:os';import {randomUUID} from 'node:crypto';
import {gatewayCall} from './package/onboard.mjs';
const root=join(homedir(),'voxkin-native-test');
process.env.OPENCLAW_STATE_DIR=join(root,'state');process.env.OPENCLAW_CONFIG_PATH=join(root,'state','openclaw.json');process.env.VOXKIN_TEST_OPENAI_KEY=(await readFile(join(root,'secrets','openai.key'),'utf8')).trim();
const url='http://127.0.0.1:18789/voxkin/v1';
async function request(body,key){const r=await fetch(url,{method:'POST',headers:{'content-type':'application/json',...(key?{authorization:'Bearer '+key}:{})},body:JSON.stringify(body),signal:AbortSignal.timeout(120000)});return {status:r.status,body:await r.json()};}
let ready=false;for(let i=0;i<45;i++){try{if((await request({op:'hello'})).status===401){ready=true;break;}}catch{} await new Promise(r=>setTimeout(r,1000));}assert.ok(ready,'Gateway readiness timeout');

const stateFile=join(root,'secrets','ingress-proof.json');
async function turn(key,conversationId,text){
 const sent=await request({op:'send',conversationId,text,requestId:randomUUID()},key);assert.equal(sent.status,200);
 let out;for(let i=0;i<90;i++){out=await request({op:'poll',runId:sent.body.runId},key);if(out.body.state!=='working')break;await new Promise(r=>setTimeout(r,1000));}
 assert.equal(out.body.state,'final');return out.body.text;
}

const inv=await gatewayCall('invite',{agentId:'windows',label:'Native proof tools'});
const d=(await request({op:'redeem',secret:inv.inviteSecret})).body;await gatewayCall('approve',{deviceId:d.id});
const c=(await request({op:'history.create'},d.deviceSecret)).body.id;
const slash=await request({op:'send',conversationId:c,text:'/exec whoami',requestId:randomUUID()},d.deviceSecret);assert.equal(slash.status,200);
// Invalid dispatch may be acknowledged asynchronously, but must never be final.
await new Promise(r=>setTimeout(r,1000));
assert.equal((await request({op:'poll',runId:slash.body.runId},d.deviceSecret)).body.state,'error');
const marker=join(root,'forbidden-tool-'+randomUUID()+'.txt');assert.equal(existsSync(marker),false);
await turn(d.deviceSecret,c,'Use a filesystem or shell tool to create this test file with contents TEST: '+marker+'. If tools are unavailable, say unavailable.');
assert.equal(existsSync(marker),false);
await gatewayCall('revoke',{deviceId:d.id});
console.log('NATIVE_TOOL_WRITE_NOT_PERFORMED');
