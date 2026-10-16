import { registerRestricted } from './restricted/register.js';
import { createRuntime, PublicError } from './runtime.js';
export default {
  id:'voxkin-local', name:'Voxkin Local',
  description:'Offline language/voice catalog and speech synthesis',
  register(api) {
    const runtime = createRuntime(api.pluginConfig ?? {});
    registerRestricted(api, runtime);
    const handler = action => async ({params, respond, signal}) => {
      try { respond(true, await action(params, signal)); }
      catch (error) { respond(false, undefined, {code:'INVALID_REQUEST', message:error instanceof PublicError ? error.message : 'Local voice operation failed. Check setup on the gateway host.'}); }
    };
    api.registerService({id:'voxkin-local-workers', start() {}, stop: () => runtime.close()});
    api.registerGatewayMethod('voxkin.catalog', handler(params => runtime.catalog(params)), {scope:'operator.read'});
    api.registerGatewayMethod('voxkin.speak', handler((params, signal) => runtime.speak(params, signal)), {scope:'operator.write'});
  }
};
