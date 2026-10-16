import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {publicIPv4,relay,bridge} from './transport.mjs';
const listen=s=>new Promise(r=>s.listen(0,'127.0.0.1',()=>r('http://127.0.0.1:'+s.address().port)));
const stop=s=>new Promise(r=>{s.closeAllConnections();s.close(r);});
const post=(url,b)=>fetch(url,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(b)});
test('relay refuses private/reserved DNS addresses',()=>{
 for(const ip of ['127.0.0.1','10.0.0.1','169.254.169.254','172.18.0.1','192.168.1.1','100.64.0.1','::1','::ffff:127.0.0.1','0.0.0.0','224.0.0.1','198.18.0.1'])assert.equal(publicIPv4(ip),false,ip);
 assert.equal(publicIPv4('1.1.1.1'),true);
});
test('relay denies wrong path/model/tools; reconstructs bounded upstream request',async()=>{
 let observed,calls=0;
 const upstream=http.createServer(async(req,res)=>{const chunks=[];for await(const c of req)chunks.push(c);observed={body:JSON.parse(Buffer.concat(chunks)),headers:req.headers};res.end('{"ok":true}');});
 await listen(upstream);
 const s=relay({key:'test-only-key',model:'review-model',request:(options,cb)=>{calls++;assert.equal(options.hostname,'api.openai.com');assert.equal(options.path,'/v1/chat/completions');return http.request({...options,hostname:'127.0.0.1',port:upstream.address().port,lookup:undefined},cb);}});
 const url=await listen(s);const valid={model:'review-model',messages:[{role:'user',content:'hello'}]};
 try{
  for(const [path,b] of [['/v1/responses',valid],['/v1/chat/completions',{...valid,model:'expensive'}],['/v1/chat/completions',{...valid,tools:[{}]}],['/v1/chat/completions',{...valid,messages:[{role:'user',content:[{image_url:'http://private'}]}]}]])assert.equal((await post(url+path,b)).status,403);
  assert.equal(calls,0);
  const r=await post(url+'/v1/chat/completions',{...valid,base_url:'http://private',max_completion_tokens:99999});assert.equal(r.status,200);await r.text();
  assert.equal(observed.body.max_completion_tokens,1024);assert.equal(observed.body.store,false);assert.equal(observed.body.base_url,undefined);assert.equal(observed.headers.authorization,'Bearer test-only-key');
  const textParts=await post(url+'/v1/chat/completions',{...valid,messages:[{role:'system',content:'review only'},{role:'user',content:[{type:'text',text:'hello'}]}]});
  assert.equal(textParts.status,200);await textParts.text();assert.equal(observed.body.messages[1].content,'hello');
 }finally{await stop(s);await stop(upstream);}
});
test('Unix ingress bridge forwards only exact POST route and authorization',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'voxkin-bridge-'));const sock=join(dir,'socket');
 const target=http.createServer((req,res)=>res.end(JSON.stringify({path:req.url,auth:req.headers.authorization})));
 await new Promise(r=>target.listen(sock,r));const s=bridge({socketPath:sock,path:'/voxkin/v1'});const url=await listen(s);
 try{
  for(const path of ['/','/voxkin/v1?x=1','/api/config','/voxkin/v1/'])assert.equal((await post(url+path,{})).status,403);
  assert.equal((await fetch(url+'/voxkin/v1')).status,403);
  const r=await fetch(url+'/voxkin/v1',{method:'POST',headers:{'content-type':'application/json',authorization:'Bearer test'},body:'{"op":"hello"}'});assert.deepEqual(await r.json(),{path:'/voxkin/v1',auth:'Bearer test'});
 }finally{await stop(s);await stop(target);await rm(dir,{recursive:true,force:true});}
});
test('relay lifetime budget persists across restarts and fails closed',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'voxkin-budget-'));const budgetPath=join(dir,'budget.json');
 const upstream=http.createServer((req,res)=>{req.resume();res.end('{}');});await listen(upstream);
 const request=(o,cb)=>http.request({...o,hostname:'127.0.0.1',port:upstream.address().port,lookup:undefined},cb);
 const options={key:'test-only',model:'review',budgetPath,maxRequests:1,request};
 let s=relay(options),url=await listen(s);
 const body={model:'review',messages:[{role:'user',content:'hi'}]};
 try{
  const first=await post(url+'/v1/chat/completions',body);assert.equal(first.status,200);await first.text();
  await stop(s);s=relay(options);url=await listen(s);
  assert.equal((await post(url+'/v1/chat/completions',body)).status,429);
 }finally{await stop(s);await stop(upstream);await rm(dir,{recursive:true,force:true});}
});
test('bridge carries a valid large voice envelope without truncation',async()=>{
 const payload=JSON.stringify({audioBase64:'A'.repeat(8_000_000)});
 const upstream=http.createServer((req,res)=>{req.resume();res.end(payload);});await listen(upstream);
 const s=bridge({port:upstream.address().port,path:'/voxkin/v1'});const url=await listen(s);
 try{const r=await post(url+'/voxkin/v1',{});assert.equal((await r.text()).length,payload.length);}
 finally{await stop(s);await stop(upstream);}
});
test('relay rejects simultaneous work and releases capacity after completion',async()=>{
 let finish,entered;const enteredPromise=new Promise(r=>entered=r);
 const upstream=http.createServer((req,res)=>{req.resume();finish=()=>res.end('{}');entered();});await listen(upstream);
 const s=relay({key:'test-only',model:'review',request:(o,cb)=>http.request({...o,hostname:'127.0.0.1',port:upstream.address().port,lookup:undefined},cb)});
 const url=await listen(s),body={model:'review',messages:[{role:'user',content:'hi'}]};
 try{
  const first=post(url+'/v1/chat/completions',body);await enteredPromise;
  assert.equal((await post(url+'/v1/chat/completions',body)).status,429);
  finish();assert.equal((await first).status,200);
 }finally{await stop(s);await stop(upstream);}
});
test('model bridge preserves SSE framing and content type',async()=>{
 const upstream=http.createServer((req,res)=>{req.resume();res.writeHead(200,{'content-type':'text/event-stream'});res.end('data: {"ok":true}\n\ndata: [DONE]\n\n');});await listen(upstream);
 const s=bridge({port:upstream.address().port,path:'/v1/chat/completions'});const url=await listen(s);
 try{const r=await post(url+'/v1/chat/completions',{});assert.equal(r.headers.get('content-type'),'text/event-stream');assert.match(await r.text(),/data: \[DONE\]/);}
 finally{await stop(s);await stop(upstream);}
});
