import http from 'node:http';
import {stat} from 'node:fs/promises';
const mode=process.argv[2];
if(mode==='relay'){
 try{process.exit((await stat('/egress/model.sock')).isSocket()?0:1);}catch{process.exit(1);}
}
const target=mode==='gateway'?{socketPath:'/ingress/review.sock'}:{hostname:'127.0.0.1',port:8080};
const timer=setTimeout(()=>process.exit(1),5000);
const req=http.request({...target,path:'/voxkin/v1',method:'POST',headers:{'content-type':'application/json'}},res=>{
 let body='';res.on('data',c=>{body+=c;if(body.length>1024)process.exit(1);});
 res.on('end',()=>{clearTimeout(timer);try{process.exit(res.statusCode===401&&JSON.parse(body).error==='DENIED'?0:1);}catch{process.exit(1);}});
});req.on('error',()=>process.exit(1));req.end('{"op":"hello"}');
