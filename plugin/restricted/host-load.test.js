import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import {createServer} from 'node:net';
import {randomBytes} from 'node:crypto';

// Explicit opt-in only: runs a separate loopback gateway with a fresh HOME,
// state/config, synthetic agent, no inherited tokens, and no model requests.
test('isolated gateway loads source plugin and initializes restricted SDK import',
  {skip:process.env.VOXKIN_HOST_LOAD_TEST!=='1',timeout:100_000},async()=>{
  const root=await mkdtemp(join(tmpdir(),'voxkin-host-load-'));
  let child,exitPromise,log='';
  try {
    const probe=createServer();await new Promise(r=>probe.listen(0,'127.0.0.1',r));
    const port=probe.address().port;await new Promise(r=>probe.close(r));
    const home=join(root,'home'),state=join(root,'state'),workspace=join(root,'workspace');
    await Promise.all([home,state,workspace].map(p=>mkdir(p,{recursive:true})));
    const configPath=join(state,'openclaw.json');
    const config={gateway:{mode:'local',bind:'loopback',port,auth:{mode:'token',token:randomBytes(32).toString('hex')},controlUi:{enabled:false}},
      agents:{defaults:{heartbeat:{every:'0m'}},entries:{guest:{workspace}}},
      discovery:{mdns:{mode:'off'}},cron:{enabled:false},
      plugins:{allow:['voxkin-local'],load:{paths:[fileURLToPath(new URL('../',import.meta.url))]},
        slots:{memory:'none'},entries:{'voxkin-local':{enabled:true,config:{runtimeDir:join(root,'no-voice-runtime'),access:{enabled:true,stateDir:join(root,'access'),allowedAgents:['guest']}}}}}};
    await writeFile(configPath,JSON.stringify(config));
    child=spawn(process.env.OPENCLAW_TEST_CLI??'openclaw',['gateway','run','--port',String(port),'--bind','loopback','--tailscale','off'],{
      env:{PATH:process.env.PATH,HOME:home,TMPDIR:root,OPENCLAW_STATE_DIR:state,OPENCLAW_CONFIG_PATH:configPath,OPENCLAW_NO_RESPAWN:'1',OPENCLAW_TEST:'1'},
      stdio:['ignore','pipe','pipe'],
    });
    child.stdout.on('data',d=>log+=d);child.stderr.on('data',d=>log+=d);
    exitPromise=new Promise((resolve,reject)=>{child.once('exit',(code,signal)=>resolve({code,signal}));child.once('error',reject);});
    let status;
    const deadline=Date.now()+75_000;
    while(Date.now()<deadline && child.exitCode===null) {
      try {status=(await fetch('http://127.0.0.1:'+port+'/voxkin/v1',{signal:AbortSignal.timeout(1000)})).status;if(status===405)break;}catch{}
      await new Promise(r=>setTimeout(r,300));
    }
    assert.equal(status,405,'restricted route must be initialized, not 503. Gateway log: '+log);
    const unauthorized=await fetch('http://127.0.0.1:'+port+'/voxkin/v1',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({op:'hello'}),signal:AbortSignal.timeout(2000)});
    assert.equal(unauthorized.status,401);
    assert.equal((await unauthorized.json()).error,'DENIED');
  } finally {
    if(child && child.exitCode===null) {
      child.kill('SIGTERM');
      let stopTimer;
      const stopped=await Promise.race([exitPromise,new Promise(r=>{stopTimer=setTimeout(()=>r(null),8000);})]);
      clearTimeout(stopTimer);
      if(!stopped) {child.kill('SIGKILL');await exitPromise;}
    }
    await rm(root,{recursive:true,force:true});
  }
});
