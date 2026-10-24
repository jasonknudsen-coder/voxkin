import test from 'node:test';
import assert from 'node:assert/strict';
import QRCode from 'qrcode';
import { parseArgs, setupLink, gatewayCall, run, configToken } from '../onboard.mjs';
const gateway='wss://example.test';
test('explicit owner credential source and strict gateway URL',()=>{
  for(const args of [['owner','--gateway',gateway],['owner','--gateway',gateway,'--token','secret'],['owner','--gateway',gateway,'--token-stdin','--config']]) assert.throws(()=>parseArgs(args));
  assert.equal(parseArgs(['owner','--gateway',gateway,'--token-stdin']).mode,'owner');
  for(const url of ['ws://example.test','wss://u:p@example.test','wss://example.test/path','wss://example.test?q=x']) assert.throws(()=>setupLink('owner',url,'secret'));
});
test('owner QR is non-URI text with exact credential encoding; guest links remain dispatchable',()=>{
  const payload=setupLink('owner',gateway,'a+b& c/=?!');
  assert.ok(payload.startsWith('VOXKIN OWNER 1 '));
  assert.throws(()=>new URL(payload));
  const fields=new URLSearchParams(payload.slice('VOXKIN OWNER 1 '.length));
  assert.equal(fields.get('gateway'),gateway);
  assert.equal(fields.get('token'),'a+b& c/=?!');
  assert.ok(!payload.includes('+'));
  const invite=new URL(setupLink('invite',gateway,'a'.repeat(43)));
  assert.equal(invite.host,'join');assert.equal(invite.search,'');
  for(const value of ['',undefined,'x\ny','x\ty','x\u0085y','é'.repeat(4096)]) assert.throws(()=>setupLink('owner',gateway,value));
});
test('safe subprocess argv preserves configured authentication and sanitizes failures',async()=>{
  let invocation;
  assert.deepEqual(await gatewayCall('invite',{agentId:'safe',label:'$(no-shell)'},'ws://localhost:18789',async(...args)=>{invocation=args;return {stdout:'{"id":"abc"}'};}),{id:'abc'});
  assert.equal(invocation[0],process.platform === 'win32' ? process.execPath : 'openclaw');assert.equal(invocation[2].shell,undefined);
  assert.ok(!invocation[1].includes('--token')); assert.ok(invocation[1].includes('--expect-url'));
  await assert.rejects(gatewayCall('list',{},undefined,async()=>{throw new Error('SECRET');}),e=>!e.message.includes('SECRET'));
});
test('owner rendering is local and never prints raw credentials',async()=>{
  let output='',payload;
  await run(parseArgs(['owner','--gateway',gateway,'--token-stdin']),{stdin:async()=> 'private-token',call:()=>{throw new Error('network forbidden');},render:async p=>{payload=p;return 'QR\n';},write:v=>output+=v});
  assert.equal(payload,setupLink('owner',gateway,'private-token'));assert.ok(!output.includes('private-token'));assert.ok(output.includes('pairing'));
});
test('config accepts literal tokens only',async()=>{
  const options=parseArgs(['owner','--gateway',gateway,'--config','fixture']);let payload;
  await run(options,{read:async()=>JSON.stringify({gateway:{auth:{token:'token'}}}),render:async p=>{payload=p;return '';},write:()=>{}});
  assert.equal(payload,setupLink('owner',gateway,'token'));
  await assert.rejects(run(options,{read:async()=>JSON.stringify({gateway:{auth:{token:{source:'env'}}}})}));
});
test('restricted invite/list/approve/cancel RPC contracts',async()=>{
  let output='',payload,called;
  await run(parseArgs(['invite','--gateway',gateway,'--agent','safe','--label','Guest']),{call:async(...args)=>{called=args;return {inviteSecret:'s'.repeat(32),id:'id',expiresAt:100};},render:async p=>{payload=p;return 'QR\n';},write:v=>output+=v});
  assert.deepEqual(called,['invite',{agentId:'safe',label:'Guest'},undefined]);assert.equal(payload,setupLink('invite',gateway,'s'.repeat(32)));assert.ok(!output.includes('secret'));
  for(const [args,expected] of [[['list'],{}],[['approve','--device','id'],{deviceId:'id'}],[['cancel','--invite','id'],{inviteId:'id'}]]) await run(parseArgs(args),{call:async(mode,params)=>{assert.deepEqual(params,expected);return {};},write:()=>{}});
});
test('real local QR encoding contains expected payload',async()=>{
  const payload=setupLink('owner',gateway,'fixture-token'),qr=QRCode.create(payload,{errorCorrectionLevel:'M'});
  assert.ok(qr.modules.size>=21);
  assert.equal(qr.segments.map(s=>Buffer.from(s.data).toString()).join(''),payload);
  assert.ok((await QRCode.toString(payload,{type:'terminal',small:true})).length>100);
});

test('invite credentials and setup modes reject invalid values',()=>{for(const value of ['secret','a'.repeat(257),'!'.repeat(32)]) assert.throws(()=>setupLink('invite',gateway,value));assert.throws(()=>setupLink('unknown',gateway,'secret'));});
test('generated QR PNG decodes to owner and restricted app payloads',async()=>{
  const {default:jsQR}=await import('jsqr');
  const {PNG}=await import('pngjs');
  for(const [mode,credential] of [['owner','a+b& c/=?!'],['invite','a'.repeat(43)]]) {
    const payload=setupLink(mode,gateway,credential);
    const png=PNG.sync.read(await QRCode.toBuffer(payload,{type:'png',scale:6,margin:4,errorCorrectionLevel:'M'}));
    const decoded=jsQR(new Uint8ClampedArray(png.data),png.width,png.height);
    assert.equal(decoded?.data,payload);
  }
});

test('file and env references use host resolver without disclosing failure details',async()=>{
  for(const source of ['file','env']) {
    const token={source,provider:'fixture',id:'/token'},config={gateway:{auth:{token}}};
    assert.equal(await configToken(config,async()=>async(refs,opts)=>{
      assert.deepEqual(refs,[token]);assert.equal(opts.config,config);
      return new Map([['key','synthetic-token']]);
    }),'synthetic-token');
    await assert.rejects(configToken(config,async()=>{throw Error('DO-NOT-PRINT-SECRET');}),e=>!e.message.includes('DO-NOT-PRINT'));
    await assert.rejects(configToken(config,async()=>async()=>new Map([['key',{}]])));
  }
  await assert.rejects(configToken({gateway:{auth:{token:{source:'exec',provider:'x',id:'x'}}}}));
});
