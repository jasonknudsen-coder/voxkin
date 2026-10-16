// Public SDK only. Capture typed terminal text before command JSON projection
// discards reasoning/commentary flags. Hooks never publish to a phone directly.
export function createWindowsIngress(api, {loadIngress=()=>import('openclaw/plugin-sdk/agent-runtime')}={}) {
  if(typeof api.on!=='function') throw new Error('Windows ingress requires supported hooks.');
  const runs=new Map();
  const owned=(ctx,runId=ctx?.runId)=>{
    const r=runs.get(runId);
    return r && ctx?.agentId===r.agentId && ctx?.sessionKey===r.sessionKey ? r : undefined;
  };
  api.on('llm_output',(event,ctx)=>{
    const r=owned(ctx,event.runId);if(!r)return;
    r.final=undefined;
    const m=event.lastAssistant;
    if(!m || m.role!=='assistant' || !['stop','end_turn'].includes(m.stopReason) ||
      (m.phase && m.phase!=='final') || !Array.isArray(m.content) ||
      m.content.some(b=>b.type==='toolCall'||b.type==='tool_use'))return;
    const text=m.content.filter(b=>b.type==='text'&&typeof b.text==='string').map(b=>b.text).join('');
    if(text.trim())r.final=text;
  });
  api.on('before_tool_call',(_event,ctx)=>owned(ctx)?{block:true,blockReason:'Tools are disabled for restricted Voxkin turns.'}:undefined);
  return async ({device,text,sessionKey,peerId,runId,signal,emit})=>{
    if(runs.has(runId))throw new Error('Duplicate restricted run.');
    const record={agentId:device.agentId,sessionKey};runs.set(runId,record);
    try{
      signal?.throwIfAborted();
      const {agentCommandFromIngress}=await loadIngress();
      let failed=false;
      const result=await agentCommandFromIngress({
        message:text,agentId:device.agentId,sessionKey,runId,
        messageChannel:'voxkin',accountId:'restricted',to:device.id,
        runContext:{messageChannel:'voxkin',accountId:'restricted',senderId:device.id,chatId:peerId},
        allowModelOverride:false,senderIsOwner:false,toolsAllow:[],disableMessageTool:true,
        deliver:false,json:true,verbose:'off',bootstrapContextMode:'lightweight',abortSignal:signal,
        onResultErrorPayload:()=>{failed=true;},
      },{log(){},error(){},exit(){throw new Error('Unexpected ingress exit');}});
      signal?.throwIfAborted();
      if(failed || result?.meta?.error || result?.meta?.aborted || result?.payloads?.some(p=>p.isError))throw new Error('Restricted turn failed.');
      // Exact correspondence rejects prefixes, status payloads and ambiguous
      // projections rather than accidentally exposing reasoning or commentary.
      if(!record.final || !(result?.payloads??[]).some(p=>!p.isError&&!p.isReasoning&&!p.isCommentary&&p.text===record.final))throw new Error('No verified final reply.');
      emit(record.final.slice(0,32_000),true);
      return result;
    }finally{runs.delete(runId);}
  };
}
