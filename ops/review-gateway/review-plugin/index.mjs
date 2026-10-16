import {AccessStore} from './lib/access/store.js';
import {createRuntime} from './lib/runtime.js';
import {createRestrictedServer,createHttpHandler} from './lib/restricted/server.js';
import {createRestrictedDispatch} from './lib/restricted/dispatch.js';
import {closeReviewResources} from './lifecycle.mjs';
import {reviewEnrollment} from './enrollment.mjs';
export default {
  id:'voxkin-review-only',name:'Voxkin isolated review appliance',
  register(api) {
    const c=api.pluginConfig;
    let store,runtime,server,handler;
    api.registerService({id:'voxkin-review-only',async start(){
      // Refuse accidental installation into a family gateway.
      const agents=api.config?.agents?.entries?Object.entries(api.config.agents.entries).map(([id,c])=>({...c,id})):api.config?.agents?.list;
      if(process.env.VOXKIN_ISOLATED_REVIEW!=='1' || agents?.length!==1 || agents[0].id!=='review' ||
         api.config?.plugins?.entries?.['voxkin-local']?.enabled) throw new Error('Isolated review agent required');
      try {
        const {runDetachedWebhookWork}=await import('openclaw/plugin-sdk/webhook-request-guards');
        store=await new AccessStore({stateDir:c.stateDir,limits:{maxDevices:20}}).initialize();
        const access=reviewEnrollment(store,c);
        runtime=createRuntime({runtimeDir:c.runtimeDir});
        const dispatch=createRestrictedDispatch(api,{runDetachedWebhookWork});
        let busy=false;
        server=createRestrictedServer({store:access,runtime,allowedAgents:()=>['review'],dispatch:async args=>{
          if(busy) throw new Error('Review capacity reached');
          busy=true;try{return await dispatch(args);}catch(error){api.logger.error('Review dispatch failed: '+String(error?.message??error).replace(/sk-[A-Za-z0-9_-]+/g,'[redacted]').slice(0,500));throw error;}finally{busy=false;}
        }});
        handler=createHttpHandler({store:access,server});
      } catch(e) {await closeReviewResources({server,runtime,store}).catch(()=>{});throw e;}
    },async stop(){handler=null;await closeReviewResources({server,runtime,store});}});
    api.registerHttpRoute({path:'/voxkin/v1',auth:'plugin',match:'exact',handler:async(req,res)=>{
      if(!handler){res.statusCode=503;res.end('Unavailable');return true;}return handler(req,res);
    }});
  }
};
