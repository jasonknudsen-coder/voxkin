import test from 'node:test';
import assert from 'node:assert/strict';
import {configurationPlan,parseArgs,run,execute} from '../setup.mjs';
function harness({config={},missing=false,reloadFails=false,conflict=false,enabled=true}={}) {
  const calls=[], lines=[];
  return {calls,lines,dependencies:{cli:['openclaw',[]],allowTestRoot:true,realpath:async p=>p,write:s=>lines.push(s),ask:async()=>false,execute:async (cmd,args)=>{
    calls.push([cmd,args]);
    if(cmd==='python3.13'||cmd==='py') {
      if(args.includes('-c')) return '3.13\n';
      if(args.includes('--check')&&missing) throw Error('secret must not print');
      return JSON.stringify({ready:true,pluginConfig:{voices:['af_heart','ef_dora']}});
    }
    if(args[0]==='--version')return '2026.10.6';
    if(args[0]==='plugins'&&args[1]==='inspect')return JSON.stringify({plugin:{rootDir:'/fixture/plugin',enabled}});
    if(args[0]==='config'&&args[1]==='get')return JSON.stringify({entries:{'voxkin-local':{config}}});
    if(args[0]==='config'&&args[1]==='set'){if(conflict)throw Error('Concurrent config change');return '{}';}
    if(args[1]==='reload'&&reloadFails)throw Error('operator.admin required');
    if(args[0]==='gateway')return '{"ready":true}';
    return '{}';
  }}};
}
test('doctor rejects download/pair and never writes config, receipt, or reloads',async()=>{
  assert.throws(()=>parseArgs(['doctor','--download']));
  const h=harness();await run({mode:'doctor'},h.dependencies);
  assert(h.calls.some(([,a])=>a.includes('--check')));
  assert(!h.calls.some(([,a])=>a.includes('--install')||a.includes('--smoke-test')||a.includes('set')||a.includes('reload')));
});
test('missing runtime without explicit consent performs no installation or config changes',async()=>{
  const h=harness({missing:true});await assert.rejects(run({mode:'setup'},h.dependencies),/consent/);
  assert(!h.calls.some(([,a])=>a.includes('--install')||a.includes('set')));
});
test('existing allowlists/access/runtime preserved and no broad config replacement',async()=>{
  const config={runtimeDir:'/existing',voices:['ef_dora'],access:{enabled:true,allowedAgents:['test']}};
  assert.deepEqual(configurationPlan({entries:{'voxkin-local':{config}}},'/new',['af_heart']),[]);
  const h=harness({config});await run({mode:'setup'},h.dependencies);
  assert(!h.calls.some(([,a])=>a.includes('set')));
  assert(h.calls.some(([,a])=>a.includes('/existing')&&a.includes('--smoke-test')));
});
test('fresh setup uses only absent-leaf compare-and-set after actual smoke test',async()=>{
  const h=harness({missing:true});await run({mode:'setup','--download':true},h.dependencies);
  const mutations=h.calls.filter(([,a])=>a.includes('set'));
  assert.equal(mutations.length,2);
  assert(mutations.every(([,a])=>a.includes('--expect-current-absent')));
  assert(h.calls.findIndex(([,a])=>a.includes('--smoke-test'))<h.calls.findIndex(([,a])=>a.includes('set')));
});
test('concurrent config edit stops setup before reload',async()=>{
  const h=harness({conflict:true});await assert.rejects(run({mode:'setup'},h.dependencies),/Concurrent/);
  assert(!h.calls.some(([,a])=>a.includes('reload')));
});
test('failed reload never claims live readiness; rerun repair is deliberate',async()=>{
  const h=harness({reloadFails:true});await assert.rejects(run({mode:'repair','--download':true},h.dependencies),/readiness is not confirmed/);
  assert(!h.lines.some(s=>s.startsWith('Ready:')));
  assert(h.calls.some(([,a])=>a.includes('--install')&&a.includes('--repair-dependencies')));
});
test('path changes are refused, rather than replacing an existing runtime',async()=>{
  const h=harness({config:{runtimeDir:'/existing'}});
  await assert.rejects(run({mode:'setup','--runtime-dir':'/different'},h.dependencies),/No path was changed/);
});
test('subprocess failure does not expose raw dependency error or credential',async()=>{
  await assert.rejects(execute(process.execPath,['-e','console.error("SECRETfixture");process.exit(1)']),error=>!error.message.includes('SECRETfixture'));
});

test('disabled plugin requires separate enable consent; doctor never enables',async()=>{
  let h=harness({enabled:false});await assert.rejects(run({mode:'setup'},h.dependencies),/remains disabled/);
  assert(!h.calls.some(([,a])=>a.includes('enable')));
  h=harness({enabled:false});await assert.rejects(run({mode:'doctor'},h.dependencies),/disabled/);
  assert(!h.calls.some(([,a])=>a.includes('enable')));
  h=harness({enabled:false});await run({mode:'setup','--enable':true},h.dependencies);
  assert(h.calls.some(([,a])=>a[0]==='plugins'&&a[1]==='enable'));
});

test('root identity is checked before enabling a disabled plugin',async()=>{
  const h=harness({enabled:false});h.dependencies.allowTestRoot=false;
  await assert.rejects(run({mode:'setup','--enable':true},h.dependencies),/installed plugin directory/);
  assert(!h.calls.some(([,a])=>a.includes('enable')));
});

import {mkdtemp,readFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
for(const mode of ['timeout','output-bound']) test(`${mode} terminates a TERM-resistant writing grandchild before rejecting`,{skip:process.platform==='win32'},async()=>{
  const dir=await mkdtemp(join(tmpdir(),'voxkin-tree-'));
  const output=join(dir,'writer');
  const writer=`const fs=require('node:fs');process.on('SIGTERM',()=>{});setInterval(()=>fs.appendFileSync(process.argv[1],'x'),5);`;
  const parent=`const {spawn}=require('node:child_process');spawn(process.execPath,['-e',${JSON.stringify(writer)},${JSON.stringify(output)}],{stdio:'ignore'});process.on('SIGTERM',()=>{});${mode==='output-bound'?"setTimeout(()=>process.stdout.write('x'.repeat(5*1024*1024)),300);":''}setInterval(()=>{},100);`;
  try {
    await assert.rejects(execute(process.execPath,['-e',parent],{timeout:mode==='timeout'?350:5000,killGrace:100}),mode==='timeout'?/timed out/:/large tool response/);
    const before=await readFile(output,'utf8');assert(before.length>0);
    await new Promise(resolve=>setTimeout(resolve,150));
    assert.equal(await readFile(output,'utf8'),before);
  } finally {await rm(dir,{recursive:true,force:true});}
});

import {spawn} from 'node:child_process';
import {once} from 'node:events';
for (const signal of ['SIGINT','SIGTERM']) test(`${signal} forwarded to child tree and temporary listeners removed`,{skip:process.platform==='win32'},async()=>{
  const dir=await mkdtemp(join(tmpdir(),'voxkin-signal-'));
  const output=join(dir,'writer');
  const writer=`const fs=require('node:fs');process.on('SIGTERM',()=>{});setInterval(()=>fs.appendFileSync(process.argv[1],'x'),5);`;
  const parent=`const {spawn}=require('node:child_process');spawn(process.execPath,['-e',${JSON.stringify(writer)},${JSON.stringify(output)}],{stdio:'ignore'});process.on('SIGTERM',()=>{});setInterval(()=>{},100);`;
  const moduleURL = new URL('../setup.mjs',import.meta.url).href;
  const wrapper=`import {execute} from ${JSON.stringify(moduleURL)};const before=process.listenerCount('${signal}');try{await execute(process.execPath,['-e',${JSON.stringify(parent)}],{timeout:5000,killGrace:100});process.exitCode=99;}catch(error){console.log(JSON.stringify({code:error.code,listenersRestored:before===process.listenerCount('${signal}')}));process.exitCode=error.signal==='SIGINT'?130:143;}`;
  const probe=spawn(process.execPath,['--input-type=module','-e',wrapper],{stdio:['ignore','pipe','pipe']});
  let stdout='';probe.stdout.on('data',data=>stdout+=data);
  const completed=once(probe,'close');
  try {
    let started=false;
    for(let i=0;i<100;i++){try{if((await readFile(output,'utf8')).length){started=true;break;}}catch{}await new Promise(resolve=>setTimeout(resolve,10));}
    assert(started,'grandchild started before sending signal');
    probe.kill(signal);
    const [code]=await completed;assert.equal(code,signal==='SIGINT'?130:143);
    assert.deepEqual(JSON.parse(stdout),{code:'VOXKIN_INTERRUPTED',listenersRestored:true});
    const before=await readFile(output,'utf8');await new Promise(resolve=>setTimeout(resolve,150));
    assert.equal(await readFile(output,'utf8'),before);
  }finally{probe.kill('SIGKILL');await rm(dir,{recursive:true,force:true});}
});
test('interrupt during initial verification is not mistaken for a missing runtime',async()=>{
  const h=harness();const original=h.dependencies.execute;
  h.dependencies.execute=async (cmd,args)=>{if(args.includes('--check'))throw Object.assign(new Error('interrupted'),{code:'VOXKIN_INTERRUPTED'});return original(cmd,args);};
  await assert.rejects(run({mode:'setup','--download':true},h.dependencies),error=>error.code==='VOXKIN_INTERRUPTED');
  assert(!h.calls.some(([,args])=>args.includes('--install')));
});
test('trusted host registration root supports staged imports but rejects mismatched installation',async()=>{
  let h=harness();h.dependencies.allowTestRoot=false;h.dependencies.pluginRoot='/fixture/plugin';
  await run({mode:'doctor'},h.dependencies);
  h=harness({enabled:false});h.dependencies.allowTestRoot=false;h.dependencies.pluginRoot='/different/plugin';
  await assert.rejects(run({mode:'setup','--enable':true},h.dependencies),/installed plugin directory/);
  assert(!h.calls.some(([,args])=>args.includes('enable')));
});
