import {validConversationId} from './history.js';
// Supported SDK channel ingress, deliberately not a gateway-RPC proxy.
// The owner must assign a separate agent with a private, non-sensitive workspace:
// disabling tools does not sanitize bootstrap files, model-side memory, or hooks.
// This first release is text-only, without tools, proactive delivery or attachments.
export function createRestrictedDispatch(api, {runDetachedWebhookWork, windowsIngress} = {}) {
  const channel = api?.runtime?.channel;
  if (typeof channel?.inbound?.buildContext !== 'function' ||
      typeof channel?.inbound?.dispatch !== 'function' ||
      typeof channel?.routing?.buildAgentSessionKey !== 'function' ||
      typeof runDetachedWebhookWork !== 'function') {
    throw new Error('Restricted access requires supported channel ingress and detached-work SDK APIs.');
  }
  return function dispatch({device, text, conversationId, runId, signal, emit}) {
    if (!validConversationId(conversationId) || !device || !/^[a-zA-Z0-9_-]+$/.test(device.id ?? '') ||
        !/^[a-zA-Z0-9_-]+$/.test(device.agentId ?? '') ||
        typeof text !== 'string' || !text.trim() || /^\s*\//.test(text)) {
      throw new Error('Invalid restricted turn. Slash commands are unavailable.');
    }
    // Admission is acquired synchronously, before the HTTP handler acknowledges.
    return runDetachedWebhookWork(async () => {
      signal?.throwIfAborted();
      const accountId = 'restricted';
      const peerId = `${device.id}-${conversationId}`;
      const dmScope = 'per-account-channel-peer';
      const sessionKey = channel.routing.buildAgentSessionKey({
        agentId:device.agentId, channel:'voxkin', accountId,
        peer:{kind:'direct',id:peerId}, dmScope,
      });
      if (typeof sessionKey !== 'string' || !sessionKey.startsWith(`agent:${device.agentId}:`) ||
          sessionKey === `agent:${device.agentId}:main`) throw new Error('Unsafe restricted session route.');
      if(windowsIngress)return windowsIngress({device,text,sessionKey,peerId,runId,signal,emit});
      const route = {agentId:device.agentId, sessionKey, dmScope};
      const ctxPayload = channel.inbound.buildContext({
        channel:'voxkin', accountId, from:device.id,
        sender:{id:device.id}, conversation:{kind:'direct',id:peerId},
        route:{agentId:device.agentId,routeSessionKey:sessionKey,dmScope,accountId},
        reply:{to:device.id,sourceReplyDeliveryMode:'direct'},
        message:{rawBody:text,body:text,bodyForAgent:text,inboundEventKind:'user_request'},
        access:{commands:{authorized:false}}, channelIngress:'unsupported',
      });
      // No global events: only this dispatch's own callback can publish replies.
      // Do not forward reasoning, tool arguments/results, local paths or media URLs.
      let finalText = '';
      const cfg = {...api.config, commands:{...api.config?.commands,text:false}};
      const result = await channel.inbound.dispatch({
        cfg, channel:'voxkin', accountId, route, ctxPayload,
        toolsAllow:[],
        replyOptions:{runId,abortSignal:signal,disableTools:true,toolsAllow:[],
          bootstrapContextMode:'lightweight'},
        delivery:{durable:false,deliver:async (payload,info) => {
          signal?.throwIfAborted();
          if (info?.kind !== 'final' || typeof payload?.text !== 'string') return;
          finalText = [finalText,payload.text].filter(Boolean).join('\n\n').slice(0,32_000);
          emit(finalText,true);
        }},
      });
      signal?.throwIfAborted();
      if (result?.dispatched === false) throw new Error('Restricted turn was not dispatched.');
      return result;
    });
  };
}
