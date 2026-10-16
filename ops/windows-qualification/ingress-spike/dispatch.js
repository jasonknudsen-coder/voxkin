import {validConversationId} from './history.js';
// Supported SDK channel ingress, deliberately not a gateway-RPC proxy.
// The owner must assign a separate agent with a private, non-sensitive workspace:
// disabling tools does not sanitize bootstrap files, model-side memory, or hooks.
// This first release is text-only, without tools, proactive delivery or attachments.
export function createRestrictedDispatch(api, {runDetachedWebhookWork} = {}) {
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
      const {agentCommandFromIngress} = await import('openclaw/plugin-sdk/agent-runtime');
      const result = await agentCommandFromIngress({
        message:text, agentId:device.agentId, sessionKey, runId,
        messageChannel:'voxkin', accountId, to:device.id,
        runContext:{messageChannel:'voxkin',accountId,senderId:device.id,chatId:peerId},
        allowModelOverride:false, senderIsOwner:false, toolsAllow:[],
        disableMessageTool:true, deliver:false, json:true,
        bootstrapContextMode:'lightweight', abortSignal:signal,
      }, {log(){},error(){},exit(){throw new Error('Unexpected runtime exit');}});
      signal?.throwIfAborted();
      const textOut=(result?.payloads ?? []).filter(p=>!p.isError&&!p.isReasoning&&!p.isCommentary&&typeof p.text==='string').map(p=>p.text).join('\n\n').slice(0,32_000);
      if (!textOut) throw new Error('No final reply');
      emit(textOut,true);
      return result;
    });
  };
}
