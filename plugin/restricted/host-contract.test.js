import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawn} from 'node:child_process';
import {pathToFileURL} from 'node:url';

// Opt-in contract test against an installed SDK. No gateway, provider, real
// profile, or network is used. Session metadata lives only in the temp profile.
const sdkDir=process.env.OPENCLAW_SDK_DIR;
test('installed SDK: actual context, routing, detached admission and reply pipeline', {skip:!sdkDir,timeout:45_000},async()=>{
  const root=await mkdtemp(join(tmpdir(),'voxkin-host-contract-'));
  const configPath=join(root,'openclaw.json');
  await writeFile(configPath,JSON.stringify({agents:{list:[{id:'guest',workspace:root}]},session:{store:join(root,'sessions','sessions.json')}}));
  const script=`
    import assert from 'node:assert/strict';
    import {readFile} from 'node:fs/promises';
    import {createRestrictedDispatch} from ${JSON.stringify(new URL('./dispatch.js',import.meta.url).href)};
    const sdk=${JSON.stringify(pathToFileURL(sdkDir+'/').href)};
    const {buildChannelInboundEventContext,dispatchChannelInboundTurn}=await import(sdk+'channel-inbound.js');
    const {buildAgentSessionKey}=await import(sdk+'routing.js');
    const {runDetachedWebhookWork}=await import(sdk+'webhook-request-guards.js');
    let resolved=0,observed;
    const cfg=JSON.parse(await readFile(process.env.OPENCLAW_CONFIG_PATH,'utf8'));
    const api={config:cfg,runtime:{channel:{routing:{buildAgentSessionKey},inbound:{
      buildContext:buildChannelInboundEventContext,
      dispatch:params=>dispatchChannelInboundTurn({...params,replyResolver:async(ctx,opts)=>{
        resolved++;observed={ctx,opts};
        assert.equal(ctx.SessionKey,'agent:guest:voxkin:restricted:direct:phone-123-12345678-1234-4234-8234-123456789012');
        assert.equal(opts.disableTools,true);assert.deepEqual(opts.toolsAllow,[]);
        return {text:'Hola desde el contrato'};
      }}),
    }}}};
    const emit=[];
    const dispatch=createRestrictedDispatch(api,{runDetachedWebhookWork});
    const pending=dispatch({device:{id:'phone-123',agentId:'guest'},conversationId:'12345678-1234-4234-8234-123456789012',text:'Hola',runId:'contract-run',signal:new AbortController().signal,emit:(...args)=>emit.push(args)});
    assert.equal(typeof pending.then,'function');
    const result=await pending;
    assert.equal(resolved,1);assert.equal(result.dispatched,true);
    assert.equal(observed.ctx.CommandAuthorized,false);
    assert.deepEqual(emit,[['Hola desde el contrato',true]]);
    console.log('HOST_CONTRACT_OK');
  `;
  try {
    const output=await new Promise((resolve,reject)=>{
      const child=spawn(process.execPath,['--input-type=module'],{env:{...process.env,OPENCLAW_STATE_DIR:root,OPENCLAW_CONFIG_PATH:configPath,OPENCLAW_TEST:'1'},stdio:['pipe','pipe','pipe']});
      let text='';child.stdout.on('data',d=>text+=d);child.stderr.on('data',d=>text+=d);
      const timer=setTimeout(()=>{child.kill();reject(new Error('Host contract timed out: '+text));},40_000);
      child.on('error',e=>{clearTimeout(timer);reject(e);});
      child.on('exit',code=>{clearTimeout(timer);code===0?resolve(text):reject(new Error('Host contract failed: '+text));});
      child.stdin.end(script);
    });
    assert.match(output,/HOST_CONTRACT_OK/);
  } finally {await rm(root,{recursive:true,force:true});}
});
