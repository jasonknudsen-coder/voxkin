import http from 'node:http';
import https from 'node:https';
import dns from 'node:dns';
import {readFileSync,writeFileSync,renameSync} from 'node:fs';
import {BlockList,isIP} from 'node:net';

export async function bodyJSON(req,limit=256*1024) {
 if(!/^application\/json(?:;|$)/i.test(req.headers['content-type']??'')) throw new Error('Invalid type');
 const chunks=[];let size=0;
 for await(const c of req){size+=c.length;if(size>limit)throw new Error('Too large');chunks.push(c);}
 return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}
function reject(res,status=403){res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end('{"error":"REVIEW_REQUEST_REJECTED"}');}
export function secureServer(handler) {
 const s=http.createServer({requestTimeout:15000,headersTimeout:10000,maxHeaderSize:8192},(req,res)=>{
  Promise.resolve(handler(req,res)).catch(()=>{if(!res.headersSent)reject(res);else res.destroy();});
 });
 s.on('upgrade',(_req,socket)=>socket.destroy());s.on('connect',(_req,socket)=>socket.destroy());
 return s;
}
export function bridge({socketPath,port,path,limit=70000}) {
 return secureServer(async(req,res)=>{
  if(req.method!=='POST'||req.url!==path||req.headers.origin){reject(res);return;}
  const body=JSON.stringify(await bodyJSON(req,limit));
  const headers={'content-type':'application/json','content-length':Buffer.byteLength(body)};
  if(req.headers.authorization)headers.authorization=req.headers.authorization;
  const upstream=http.request({...socketPath?{socketPath}:{hostname:'127.0.0.1',port},path,method:'POST',headers},r=>{
   res.writeHead(r.statusCode,{'content-type':r.headers['content-type']?.startsWith('text/event-stream')?'text/event-stream':'application/json','cache-control':'no-store'});
   let bytes=0;r.on('data',c=>{bytes+=c.length;if(bytes>8_100_000){r.destroy();res.destroy();}else if(!res.write(c))r.pause();});
   res.on('drain',()=>r.resume());r.on('end',()=>res.end());r.on('error',()=>res.destroy());
  });
  upstream.setTimeout(125000,()=>upstream.destroy());
  upstream.on('error',()=>{if(!res.headersSent)reject(res,502);else res.destroy();});
  res.on('close',()=>upstream.destroy());upstream.end(body);
 });
}
const blocked=new BlockList();
for(const [ip,prefix] of [['0.0.0.0',8],['10.0.0.0',8],['100.64.0.0',10],['127.0.0.0',8],['169.254.0.0',16],['172.16.0.0',12],['192.0.0.0',24],['192.0.2.0',24],['192.168.0.0',16],['198.18.0.0',15],['198.51.100.0',24],['203.0.113.0',24],['224.0.0.0',4],['240.0.0.0',4]])blocked.addSubnet(ip,prefix,'ipv4');
export const publicIPv4=ip=>isIP(ip)===4&&!blocked.check(ip,'ipv4');
function lookup(host,options,callback){
 if(host!=='api.openai.com'){callback(new Error('Host denied'));return;}
 dns.resolve4(host,(error,addresses)=>{
  if(error||!addresses?.length||addresses.some(ip=>!publicIPv4(ip))){callback(new Error('Address denied'));return;}
  if(options.all)callback(null,addresses.map(address=>({address,family:4})));else callback(null,addresses[0],4);
 });
}
export function relay({key,model,now=Date.now,request=https.request,budgetPath,maxRequests=100,diagnostics=false}) {
 if(typeof key!=='string'||!key.trim()||typeof model!=='string'||!model)throw new Error('Relay credentials and model required');
 let busy=false,requests=0,window=now(),total=0;
 if(budgetPath){
  try { const b=JSON.parse(readFileSync(budgetPath,'utf8'));total=b.total;
   if(!Number.isSafeInteger(total)||total<0)throw new Error('Invalid budget');
  }catch(e){if(e.code!=='ENOENT')throw e;}
 }
 const charge=()=>{
  const next=total+1;
  if(budgetPath){writeFileSync(budgetPath+'.next',JSON.stringify({total:next}),{mode:0o600});renameSync(budgetPath+'.next',budgetPath);}
  total=next;
 };
 return secureServer(async(req,res)=>{
  if(req.method!=='POST'||req.url!=='/v1/chat/completions'||req.headers.origin){reject(res);return;}
  const b=await bodyJSON(req);
  if(diagnostics) console.log(JSON.stringify({event:'MODEL_REQUEST_SHAPE',model:b?.model,keys:Object.keys(b??{}),toolCount:b?.tools?.length??0,toolChoice:b?.tool_choice,messages:b?.messages?.map(m=>({role:m?.role,kind:Array.isArray(m?.content)?'array':typeof m?.content,length:typeof m?.content==='string'?m.content.length:undefined,parts:Array.isArray(m?.content)?m.content.map(p=>({type:p?.type,length:typeof p?.text==='string'?p.text.length:undefined})):undefined}))}));
  // OpenClaw emits OpenAI text-part arrays for inbound user messages.
  // Flatten text only; never accept image, file, URL, or tool parts.
  if(Array.isArray(b?.messages)) b.messages=b.messages.map(m=>{
   if(Array.isArray(m?.content)&&m.content.length>0&&m.content.every(p=>p?.type==='text'&&typeof p.text==='string'&&Object.keys(p).every(k=>['type','text'].includes(k))))return {...m,content:m.content.map(p=>p.text).join('\n')};
   return m;
  });
  if(b?.model!==model||!Array.isArray(b.messages)||!b.messages.length||b.messages.length>64||
     b.messages.reduce((n,m)=>n+(typeof m?.content==='string'?m.content.length:16001),0)>16000||
     b.messages.some(m=>!['system','developer','user','assistant'].includes(m.role)||typeof m.content!=='string'||m.content.length>32000)||
     (b.tools?.length)||b.functions||b.tool_choice||b.function_call||b.n>1){reject(res);return;}
  if(now()-window>=3600000){window=now();requests=0;}
  if(busy||requests>=60||total>=maxRequests){reject(res,429);return;}charge();busy=true;requests++;
  // Reconstruct, never forward arbitrary URLs, tools, headers or provider options.
  const body=JSON.stringify({model,messages:b.messages.map(({role,content})=>({role,content})),stream:b.stream===true,store:false,max_completion_tokens:1024});
  const up=request({hostname:'api.openai.com',port:443,path:'/v1/chat/completions',method:'POST',lookup,
   headers:{'authorization':'Bearer '+key.trim(),'content-type':'application/json','content-length':Buffer.byteLength(body)}},r=>{
    if(r.statusCode!==200){r.resume();reject(res,502);return;}
    res.writeHead(200,{'content-type':b.stream?'text/event-stream':'application/json','cache-control':'no-store'});
    let size=0;r.on('data',c=>{size+=c.length;if(size>1024*1024){r.destroy();res.destroy();}else if(!res.write(c))r.pause();});
    res.on('drain',()=>r.resume());r.on('end',()=>res.end());r.on('error',()=>res.destroy());
   });
  const timer=setTimeout(()=>up.destroy(),120000);timer.unref();
  up.on('error',()=>{if(!res.headersSent)reject(res,502);else res.destroy();});
  res.once('close',()=>{clearTimeout(timer);busy=false;up.destroy();});up.end(body);
 });
}
