import {createWindowsIngress} from './windows-dispatch.js';
import {RestrictedHistory} from './history.js';
import { AccessStore } from '../access/store.js';
import { createRestrictedServer, createHttpHandler } from './server.js';
import { createRestrictedDispatch } from './dispatch.js';

export function registerRestricted(api, runtime) {
  const config = api.pluginConfig?.access;
  if (!config?.enabled) return;
  if(process.platform==='win32' && api.config?.plugins?.entries?.['voxkin-local']?.hooks?.allowConversationAccess!==true) throw new Error('Windows restricted access requires plugins.entries.voxkin-local.hooks.allowConversationAccess=true for verified final replies.');
  const windowsIngress=process.platform==='win32'?createWindowsIngress(api):undefined;
  let store, server, handler, startupError;
  function allowedAgents() {
    const entries = api.config?.agents?.entries ?? {};
    const known = new Set([...Object.keys(entries), ...(api.config?.agents?.list ?? []).map(a=>a.id)]);
    return (config.allowedAgents ?? []).filter(id => id !== 'main' && known.has(id));
  }
  async function ready() {
    if (!server || startupError) throw new Error('Restricted access unavailable. Check plugin setup.');
  }
  api.registerService({id:'voxkin-restricted-access',async start() {
    try {
      if (!config.stateDir || !allowedAgents().length) throw new Error('Configure a dedicated access stateDir and non-main allowedAgents.');
      const {runDetachedWebhookWork} = await import('openclaw/plugin-sdk/webhook-request-guards');
      const dispatch = createRestrictedDispatch(api,{runDetachedWebhookWork,windowsIngress});
      store = new AccessStore({stateDir:config.stateDir}); await store.initialize();
      const history = await new RestrictedHistory({stateDir:config.stateDir}).initialize();
      server = createRestrictedServer({store,runtime,dispatch,allowedAgents,history});
      handler = createHttpHandler({store,server});
    } catch { startupError = true; api.logger?.error('Voxkin restricted access disabled: SDK, agent policy, or state initialization failed.'); if(store) await store.close(); throw new Error("Voxkin restricted access initialization failed; consult setup documentation."); }
  },async stop() { if(server) await server.close(); if(store) await store.close(); }});
  api.registerHttpRoute({path:'/voxkin/v1',auth:'plugin',match:'exact',handler:async(req,res)=> {
    if(!handler || startupError) { res.statusCode=503; res.setHeader('Cache-Control','no-store');res.end('Restricted access unavailable');return true; }
    return handler(req,res);
  }});
  for (const action of ['list','invite','approve','revoke','cancel']) {
    api.registerGatewayMethod('voxkin.access.'+action,async({params,respond})=> {
      try {
        await ready();
        const keys = {list:[],invite:['agentId','label'],approve:['deviceId'],revoke:['deviceId'],cancel:['inviteId']}[action];
        if (!params || Object.keys(params).some(k=>!keys.includes(k))) throw new Error();
        let result;
        if(action==='list') result={...store.list(),agents:allowedAgents()};
        else if(action==='invite') { if(!allowedAgents().includes(params.agentId)) throw new Error(); result=await store.createInvite(params); }
        else if(action==='approve') {
          const device=store.list().devices.find(d=>d.id===params.deviceId);
          if(!device || !allowedAgents().includes(device.agentId)) throw new Error();
          result=await store.approveDevice(params);
        } else if(action==='revoke') { result=await store.revokeDevice(params);server.revoke(params.deviceId); }
        else result=await store.cancelInvite(params);
        respond(true,result ?? {});
      } catch { respond(false,undefined,{code:'INVALID_REQUEST',message:'Access operation rejected. Check admin authorization, policy, and plugin availability.'}); }
    },{scope:'operator.admin'});
  }
}
