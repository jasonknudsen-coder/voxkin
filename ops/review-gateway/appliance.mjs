// Private appliance entry point. Public listener is deliberately absent.
import {readFile,mkdir,chmod,lstat,unlink,access,writeFile} from 'node:fs/promises';
import {spawn} from 'node:child_process';
import {randomBytes,createHash} from 'node:crypto';
import {bridge,relay} from './transport.mjs';
const root='/home/voxkin/review';
const mode=process.argv[2];
async function clearSocket(path){
 try{const info=await lstat(path);if(!info.isSocket()||info.uid!==process.getuid())throw new Error('Unsafe socket path');await unlink(path);}catch(e){if(e.code!=='ENOENT')throw e;}
}

if(mode==='relay'){
 const key=await readFile('/run/secrets/openai.key','utf8');
 const server=relay({key,model:'gpt-4.1-mini',budgetPath:'/budget/usage.json',maxRequests:100,diagnostics:process.env.REVIEW_DIAGNOSTICS==='1'});
 await clearSocket('/egress/model.sock');
 server.listen('/egress/model.sock',async()=>{await chmod('/egress/model.sock',0o600);console.log('MODEL_RELAY_READY');});
 process.once('SIGTERM',()=>server.close(()=>process.exit(0)));
}else if(mode==='ingress'){
 const server=bridge({socketPath:'/ingress/review.sock',path:'/voxkin/v1'});
 server.listen(8080,'0.0.0.0');
 process.once('SIGTERM',()=>server.close(()=>process.exit(0)));
}else if(mode==='gateway'){
 await mkdir(root,{recursive:true});
 await mkdir(root+'/workspace',{recursive:true});
 await writeFile(root+'/workspace/AGENTS.md',await readFile('/opt/voxkin/ops/review-gateway/workspace/AGENTS.md'),{mode:0o600});
 const invite=await readFile('/run/secrets/reviewer.key','utf8');
 const config={
  gateway:{mode:'local',bind:'loopback',port:18789,auth:{mode:'token',token:randomBytes(32).toString('hex')},controlUi:{enabled:false}},
  agents:{defaults:{model:{primary:'review-relay/gpt-4.1-mini'},heartbeat:{every:'0m'},skipBootstrap:true},list:[{id:'main',workspace:root+'/unused-bootstrap'},{id:'review',workspace:root+'/workspace'}]},
  models:{providers:{'review-relay':{api:'openai-completions',baseUrl:'http://127.0.0.1:18790/v1',apiKey:{source:'env',provider:'default',id:'REVIEW_RELAY_PLACEHOLDER'},models:[{id:'gpt-4.1-mini',name:'Review model',reasoning:false,input:['text'],contextWindow:32768,maxTokens:1024}]}}},
  tools:{deny:['*']},commands:{text:false},discovery:{mdns:{mode:'off'}},cron:{enabled:false},
  plugins:{allow:['voxkin-review-only'],load:{paths:['/opt/voxkin/ops/review-gateway/review-plugin']},slots:{memory:'none'},entries:{'voxkin-review-only':{enabled:true,config:{runtimeDir:'/opt/voxkin/voice',stateDir:root+'/access',bootstrapHash:createHash('sha256').update(invite.trim()).digest('hex'),expiresAt:Number(process.env.REVIEW_EXPIRES_AT)}}}}
 };
 const env={...process.env,OPENCLAW_STATE_DIR:root+'/state',OPENCLAW_CONFIG_PATH:root+'/state/openclaw.json',REVIEW_RELAY_PLACEHOLDER:'local-socket-not-provider-key',VOXKIN_ISOLATED_REVIEW:'1',OPENCLAW_NO_RESPAWN:'1'};
 let initialized=false;try{await access(root+'/initialized');initialized=true;}catch{}
 if(!initialized){
 const batch=Object.entries(config).map(([path,value])=>({path,value}));
 const configure=spawn('openclaw',['config','set','--replace','--batch-json',JSON.stringify(batch)],{env,stdio:['ignore','pipe','pipe']});
 let output='';configure.stdout.on('data',d=>output+=d);configure.stderr.on('data',d=>output+=d);
 const code=await new Promise(r=>configure.on('exit',r));
 if(code!==0){console.error(output);process.exit(1);}
 const migrate=spawn('openclaw',['doctor','--fix','--non-interactive'],{env,stdio:['ignore','pipe','pipe']});
 let migrationLog='';migrate.stdout.on('data',d=>migrationLog+=d);migrate.stderr.on('data',d=>migrationLog+=d);
 if(await new Promise(r=>migrate.on('exit',r))!==0){console.error(migrationLog);process.exit(1);}
 const removeDefault=spawn('openclaw',['agents','delete','main','--force','--json'],{env,stdio:['ignore','pipe','pipe']});
 let removeLog='';removeDefault.stdout.on('data',d=>removeLog+=d);removeDefault.stderr.on('data',d=>removeLog+=d);
 if(await new Promise(r=>removeDefault.on('exit',r))!==0){console.error(removeLog);process.exit(1);}
 await writeFile(root+'/initialized','review-only-v1\n',{mode:0o600});
 }
const authored=JSON.parse(await readFile(env.OPENCLAW_CONFIG_PATH,'utf8'));
 if(authored.agents?.entries?.review?.workspace!==root+'/workspace'){
  const patch=spawn('openclaw',['config','set','--batch-json',JSON.stringify([{path:'agents.entries.review.workspace',value:root+'/workspace'},{path:'agents.defaults.skipBootstrap',value:true}])],{env,stdio:'ignore'});
  if(await new Promise(r=>patch.on('exit',r))!==0)throw new Error('Review workspace configuration failed');
 }
 const modelBridge=bridge({socketPath:'/egress/model.sock',path:'/v1/chat/completions',limit:256*1024});
 await new Promise(r=>modelBridge.listen(18790,'127.0.0.1',r));
 await clearSocket('/ingress/review.sock');
 const ingress=bridge({port:18789,path:'/voxkin/v1'});
 await new Promise(r=>ingress.listen('/ingress/review.sock',r));await chmod('/ingress/review.sock',0o600);
 const child=spawn('openclaw',['gateway','run','--port','18789','--bind','loopback','--tailscale','off'],{env,stdio:'inherit'});
 let stopping=false;const stop=()=>{if(stopping)return;stopping=true;ingress.close();child.kill('SIGTERM');};
 process.on('SIGTERM',stop);process.on('SIGINT',stop);
 child.on('exit',code=>process.exit(code??1));
}else throw new Error('Expected relay or gateway mode');
