// Private test: start a bounded synthesis request, then let the operator stop container.
import http from 'node:http';
import {readFile} from 'node:fs/promises';
const post=(body,key)=>new Promise((resolve,reject)=>{
 const r=http.request({socketPath:'/ingress/review.sock',path:'/voxkin/v1',method:'POST',headers:{'content-type':'application/json',...(key?{authorization:'Bearer '+key}:{})}},res=>{
  const chunks=[];res.on('data',c=>chunks.push(c));res.on('end',()=>resolve({status:res.statusCode,body:JSON.parse(Buffer.concat(chunks))}));
 });r.on('error',reject);r.end(JSON.stringify(body));
});
const secret=(await readFile('/run/secrets/reviewer.key','utf8')).trim();
const device=await post({op:'redeem',secret});
if(device.status!==200)throw new Error('Test enrollment failed');
const request=post({op:'speak',language:'en',voice:'af_heart',text:'This is a private shutdown qualification sentence. '.repeat(25)},device.body.deviceSecret).catch(()=>({status:'connection-closed'}));
await new Promise(r=>setTimeout(r,1000));console.log('VOICE_IN_FLIGHT');
const result=await request;console.log('VOICE_REQUEST_SETTLED '+result.status);
