import test from 'node:test';
import assert from 'node:assert/strict';
import {registerRestricted} from './register.js';
function api(enabled=true) {
 const methods=[],routes=[],services=[];
 return {pluginConfig:{access:{enabled}},config:{},methods,routes,services,
 registerGatewayMethod:(...args)=>methods.push(args),registerHttpRoute:r=>routes.push(r),registerService:s=>services.push(s)};
}
test('restricted mode is opt-in; old voice installation registers no access surface',()=>{
 const a=api(false);registerRestricted(a,{});assert.equal(a.methods.length+a.routes.length+a.services.length,0);
});
test('every owner mutation and read requires admin; restricted route uses plugin auth',()=>{
 const a=api();registerRestricted(a,{});
 assert.deepEqual(a.methods.map(m=>m[0]),['voxkin.access.list','voxkin.access.invite','voxkin.access.approve','voxkin.access.revoke','voxkin.access.cancel']);
 assert.ok(a.methods.every(m=>m[2].scope==='operator.admin'));
 assert.equal(a.routes.length,1);assert.equal(a.routes[0].path,'/voxkin/v1');assert.equal(a.routes[0].auth,'plugin');
});
test('failed initialization rejects startup and leaves endpoint unavailable',async()=>{
 const a=api();registerRestricted(a,{});
 await assert.rejects(a.services[0].start(),/initialization failed/);
 const res={setHeader(){},end(){},statusCode:0};
 await a.routes[0].handler({},res);assert.equal(res.statusCode,503);
 let ok;await a.methods[0][1]({params:{},respond:v=>{ok=v;}});assert.equal(ok,false);
});
