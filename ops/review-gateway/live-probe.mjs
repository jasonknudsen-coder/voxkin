import http from 'node:http';
import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
const request=(body,key)=>new Promise((resolve,reject)=>{
 const data=JSON.stringify(body);
 const r=http.request({socketPath:'/ingress/review.sock',path:'/voxkin/v1',method:'POST',headers:{'content-type':'application/json',...(key?{authorization:'Bearer '+key}:{})}},res=>{
  const chunks=[];res.on('data',c=>chunks.push(c));res.on('end',()=>{try{resolve({status:res.statusCode,body:JSON.parse(Buffer.concat(chunks))});}catch{reject(new Error('Invalid gateway response'));}});
 });r.on('error',reject);r.setTimeout(125000,()=>r.destroy(new Error('Timeout')));r.end(data);
});
let ready=false;
for(let i=0;i<240;i++){
 try{const r=await request({op:'hello'});if(r.status===401){ready=true;break;}}catch{}
 await new Promise(r=>setTimeout(r,1000));
}
assert.ok(ready,'Gateway did not initialize');console.log('Unauthenticated hello denied');
assert.equal((await request({op:'redeem',secret:'x'.repeat(43)})).status,403);
const secret=(await readFile('/run/secrets/reviewer.key','utf8')).trim();
const a=await request({op:'redeem',secret}),b=await request({op:'redeem',secret});
assert.equal(a.status,200);assert.equal(b.status,200);assert.notEqual(a.body.deviceSecret,b.body.deviceSecret);
const key=a.body.deviceSecret;
assert.equal((await request({op:'hello'},key)).body.agentId,'review');
const catalog=await request({op:'catalog'},key);assert.equal(catalog.body.ready,true);
console.log('Independent reviewer enrollment and local voice catalog passed');
for(const [language,voice,prompt] of [['en','af_heart','Reply with exactly: Hello from the isolated review assistant.'],['es','ef_dora','Responde exactamente: Hola desde el asistente de prueba.']]){
 const created=await request({op:'history.create'},key);assert.equal(created.status,200);
 const conversationId=created.body.id;
 assert.equal((await request({op:'history.messages',conversationId},b.body.deviceSecret)).status,403);
 const sent=await request({op:'send',conversationId,text:prompt,requestId:randomUUID()},key);assert.equal(sent.status,200);
 const runId=sent.body.runId;
 assert.equal((await request({op:'poll',runId},b.body.deviceSecret)).status,403);
 let result;
 for(let i=0;i<120;i++){
  result=await request({op:'poll',runId},key);
  if(result.body.state!=='working')break;
  await new Promise(r=>setTimeout(r,1000));
 }
 assert.equal(result.body.state,'final','Model turn did not complete');
 assert.ok(result.body.text?.includes(language==='en'?'Hello from':'Hola desde'),'Expected model response missing');
 const audio=await request({op:'speak',text:result.body.text,language,voice},key);
 assert.equal(audio.status,200);const wav=Buffer.from(audio.body.audioBase64,'base64');
 assert.equal(wav.toString('ascii',0,4),'RIFF');assert.equal(wav.toString('ascii',8,12),'WAVE');
 await writeFile('/home/voxkin/review/probe-'+language+'.wav',wav,{mode:0o600});
 const history=await request({op:'history.messages',conversationId},key);assert.equal(history.status,200);assert.ok(history.body.messages.some(m=>m.role==='assistant'));
 const renamed=await request({op:'history.rename',conversationId,title:'Ubuntu qualification'},key);assert.equal(renamed.status,200);
 assert.equal((await request({op:'history.delete',conversationId},key)).status,200);
 console.log(JSON.stringify({language,modelReply:true,audioBytes:wav.length,crossDevicePollDenied:true,historyActions:true}));
}
console.log('PRIVATE_CONVERSATION_PROOF_OK');
