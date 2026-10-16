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
 const inv=await gatewayCall('invite',{agentId:'windows',label:'Native proof '+i});
 const r=await request({op:'redeem',secret:inv.inviteSecret});assert.equal(r.status,200);
 assert.equal((await request({op:'hello'},r.body.deviceSecret)).status,403);
 await gatewayCall('approve',{deviceId:r.body.id});devices.push(r.body);
}
const key=devices[0].deviceSecret;
assert.equal((await request({op:'catalog'},key)).body.ready,true);
for(const [language,voice,text] of [['en','af_heart','Reply with exactly: Hello from Windows.'],['es','ef_dora','Responde exactamente: Hola desde Windows.']]){
 const c=await request({op:'history.create'},key);assert.equal(c.status,200);const conversationId=c.body.id;
 assert.equal((await request({op:'history.messages',conversationId},devices[1].deviceSecret)).status,403);
 const sent=await request({op:'send',conversationId,text,requestId:randomUUID()},key);assert.equal(sent.status,200);
 let out;for(let i=0;i<90;i++){out=await request({op:'poll',runId:sent.body.runId},key);if(out.body.state!=='working')break;await new Promise(r=>setTimeout(r,1000));}
 assert.equal(out.body.state,'final');assert.ok(out.body.text);
 const audio=await request({op:'speak',language,voice,text:out.body.text},key);assert.equal(audio.status,200);const wav=Buffer.from(audio.body.audioBase64,'base64');assert.equal(wav.toString('ascii',0,4),'RIFF');
 assert.equal((await request({op:'history.rename',conversationId,title:'Windows qualification'},key)).status,200);
 assert.equal((await request({op:'history.delete',conversationId},key)).status,200);
 console.log(JSON.stringify({language,model:true,audioBytes:wav.length,history:true,isolation:true}));
}
for(const d of devices)await gatewayCall('revoke',{deviceId:d.id});
console.log('NATIVE_GATEWAY_E2E_PASS');
