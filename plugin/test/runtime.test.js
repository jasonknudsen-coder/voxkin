import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm, stat, chmod } from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import plugin from '../index.js';
import {createRuntime, validateRequest, VOICES, workerEnvironment} from '../runtime.js';
test('allowlist rejects mismatched language, oversized and extra params', () => {
 for (const p of [{text:'hola',language:'es',voice:'af_heart'},{text:'',language:'en',voice:'af_heart'}, {text:'x'.repeat(4001),language:'en',voice:'af_heart'}, {text:'x',language:'en',voice:'af_heart',path:'/tmp/x'}]) assert.throws(()=>validateRequest(p,VOICES));
 assert.equal(validateRequest({text:'hola',language:'es',voice:'ef_dora'},VOICES).text,'hola');
});
test('registers existing non-admin scopes', () => {
 const entries=[]; plugin.register({pluginConfig:{},registerService:()=>{},registerGatewayMethod:(...args)=>entries.push(args)});
 assert.deepEqual(entries.map(([name,,options])=>[name,options.scope]),[['voxkin.catalog','operator.read'],['voxkin.speak','operator.write']]);
});
test('missing runtime advertises no voices',async()=>{
 const runtime=createRuntime({runtimeDir:'/nonexistent-voxkin-test'});
 assert.equal((await runtime.catalog()).ready,false);
 assert.deepEqual((await runtime.catalog()).voices,[]);
 await assert.rejects(runtime.speak({text:'x',language:'en',voice:'af_heart'}));
});
test('concurrency is bounded and capacity restored after failure',async()=>{
 const root=await mkdtemp(join(tmpdir(),'voxkin-test-'));
 try {
 await mkdir(join(root,'.venv/bin'),{recursive:true});
 for(const path of ['.venv/bin/python','kokoro-v1.0.onnx','voices-v1.0.bin']) await writeFile(join(root,path),'test');
 await chmod(join(root,'.venv/bin/python'),0o700);
 const files={}; for(const f of ['kokoro-v1.0.onnx','voices-v1.0.bin']) {const st=await stat(join(root,f));files[f]={size:st.size,mtimeMs:st.mtimeMs}}
 await writeFile(join(root,'verified.json'),JSON.stringify({version:1,files,voices:['af_heart','ef_dora']}));
 const pending=[]; const runtime=createRuntime({runtimeDir:root},()=>new Promise((resolve,reject)=>pending.push({resolve,reject})));
 const p={text:'hello',language:'en',voice:'af_heart'};
 const first=runtime.speak(p), second=runtime.speak(p);
 while(pending.length<2) await new Promise(resolve=>setTimeout(resolve,1));
 await assert.rejects(runtime.speak(p),/busy/);
 const fails=Promise.allSettled([first,second]); pending.forEach(x=>x.reject(new Error('test'))); await fails;
 assert.equal((await runtime.catalog()).ready,true);
 }finally{await rm(root,{recursive:true,force:true})}
});

test('worker abort kills the owned process and retirement rejects new work', async()=>{
 const root=await mkdtemp(join(tmpdir(),'voxkin-worker-test-'));
 try {
  await mkdir(join(root,'.venv/bin'),{recursive:true});
  await writeFile(join(root,'.venv/bin/python'), '#!/bin/sh\nprintf "%s" "$$" > "'+root+'/pid"\nexec /bin/sleep 30\n',{mode:0o700});
  const files={};
  for(const f of ['kokoro-v1.0.onnx','voices-v1.0.bin']) {await writeFile(join(root,f),'test');const st=await stat(join(root,f));files[f]={size:st.size,mtimeMs:st.mtimeMs}}
  await writeFile(join(root,'verified.json'),JSON.stringify({version:1,files,voices:['af_heart','ef_dora']}));
  const runtime=createRuntime({runtimeDir:root});
  const pending=runtime.speak({text:'hello',language:'en',voice:'af_heart'});
  const rejected=assert.rejects(pending,/failed|cancelled/);
  const {readFile}=await import('node:fs/promises');
  let pid;
  for(let i=0;i<100;i++){try{pid=Number(await readFile(join(root,'pid'),'utf8'));if(pid)break}catch{} await new Promise(r=>setTimeout(r,10))}
  assert.ok(pid,'worker started');
  await runtime.close(); await rejected;
  assert.throws(()=>process.kill(pid,0),{code:'ESRCH'});
  await assert.rejects(runtime.speak({text:'hello',language:'en',voice:'af_heart'}),/cancelled/);
  assert.equal((await runtime.catalog()).ready,false);
 }finally{await rm(root,{recursive:true,force:true})}
});
test('catalog rejects params and unverified files stay unavailable',async()=>{
 const runtime=createRuntime({runtimeDir:'/nonexistent-voxkin-test'});
 await assert.rejects(runtime.catalog({path:'/private/path'}),/no parameters/);
 const entries=[];plugin.register({pluginConfig:{runtimeDir:'/nonexistent-voxkin-test'},registerService:()=>{},registerGatewayMethod:(...args)=>entries.push(args)});
 let response; await entries[1][1]({params:{text:'hi',language:'en',voice:'af_heart'},respond:(...args)=>{response=args}});
 assert.equal(response[0],false);assert.equal(response[2].message.includes('/nonexistent'),false);
});

test('catalog intersects installed inventory and operator allowlist; absent voice cannot synthesize',async()=>{
 const root=await mkdtemp(join(tmpdir(),'voxkin-catalog-test-'));
 try{
  await mkdir(join(root,'.venv/bin'),{recursive:true});
  await writeFile(join(root,'.venv/bin/python'),'test',{mode:0o700});
  const files={};for(const f of ['kokoro-v1.0.onnx','voices-v1.0.bin']){await writeFile(join(root,f),'test');const st=await stat(join(root,f));files[f]={size:st.size,mtimeMs:st.mtimeMs}}
  await writeFile(join(root,'verified.json'),JSON.stringify({version:1,files,voices:['af_heart','bf_emma','ef_dora','not_a_supported_voice']}));
  const r=createRuntime({runtimeDir:root});
  assert.deepEqual((await r.catalog()).voices.map(v=>v.id),['af_heart','bf_emma','ef_dora']);
  assert.equal((await r.catalog()).voices.find(v=>v.id==='bf_emma').locale,'en-GB');
  await assert.rejects(r.speak({text:'hello',language:'en',voice:'am_michael'}),/not enabled/);
  const restricted=createRuntime({runtimeDir:root,voices:['af_heart','am_michael']});
  assert.deepEqual((await restricted.catalog()).voices.map(v=>v.id),['af_heart']);
  await writeFile(join(root,'verified.json'),JSON.stringify({version:1,files}));
  assert.equal((await r.catalog()).ready,false);
 }finally{await rm(root,{recursive:true,force:true})}
});
test('manifest and shared metadata expose exactly31 supported voices',async()=>{
 const {readFile}=await import('node:fs/promises');
 const manifest=JSON.parse(await readFile(new URL('../openclaw.plugin.json',import.meta.url),'utf8'));
 assert.deepEqual(manifest.configSchema.properties.voices.items.enum,VOICES.map(v=>v.id));
 assert.equal(VOICES.length,31);assert.equal(new Set(VOICES.map(v=>v.id)).size,31);
 assert.equal(VOICES.filter(v=>v.language==='es').length,3);
 assert.equal(VOICES.filter(v=>v.locale==='en-GB').length,8);
});


test('worker environment allows native OS essentials but strips secrets and Python/loader overrides', () => {
 const env=workerEnvironment({HOME:'/synthetic/home',Path:'C:\\Windows',SystemRoot:'C:\\Windows',TMPDIR:'/synthetic/tmp',LANG:'en_US.UTF-8',OPENCLAW_GATEWAY_TOKEN:'sentinel',OPENAI_API_KEY:'sentinel',PYTHONPATH:'/evil',PYTHONHOME:'/evil',LD_PRELOAD:'/evil',DYLD_INSERT_LIBRARIES:'/evil',AWS_SECRET_ACCESS_KEY:'sentinel'});
 assert.deepEqual(env,{HOME:'/synthetic/home',Path:'C:\\Windows',SystemRoot:'C:\\Windows',TMPDIR:'/synthetic/tmp',LANG:'en_US.UTF-8'});
});
