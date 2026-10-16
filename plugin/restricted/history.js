import {assertWindowsPrivate,privateStat} from '../access/windows-files.js';
import {randomUUID} from 'node:crypto';
import {promises as fs, constants} from 'node:fs';
import path from 'node:path';
const fail = (code='INVALID_REQUEST') => { throw Object.assign(new Error(code), {code}); };
export const validConversationId = value => typeof value === 'string' && /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(value);
// Strip only the app's exact leading per-turn metadata envelope, never arbitrary
// bracketed user text or assistant replies. Dispatch retains the original input.
export function historyUserText(text) {
  const prefix='[Voxkin reply preference for this turn only: ';
  if(!text.startsWith(prefix)) return text;
  const end=text.indexOf(']\n\n',prefix.length);
  return end<0 ? text : text.slice(end+3);
}
const displayText = m => m?.role==='user' ? historyUserText(m.text) : m?.text;
const cleanTitle = text => text.replace(/\s+/gu,' ').trim();
const excerpt = (text,limit) => [...text].slice(0,limit).join('');
function automaticTitle(c) {
  const candidates=c.messages.filter(m=>m.role==='user').slice(0,3).map(m=>{const text=displayText(m);return text.startsWith('[Voxkin reply preference for this turn only: ') ? '' : cleanTitle(text);}).filter(Boolean);
  const meaningful=candidates.find(text=>! /^(?:(?:hi|hello|hey)(?: there)?|hola|test(?:ing)?[ 0-9]*|[0-9 ]+|can you hear me|are you there)[.!?]*$/iu.test(text));
  return excerpt(meaningful ?? candidates[0] ?? 'New conversation',80);
}
const summary = c => ({id:c.id,title:c.title ?? automaticTitle(c),...(c.title!==undefined?{customTitle:c.title}:{}),preview:displayText(c.messages.at(-1))?.slice(0,160) ?? '',createdAt:c.createdAt,updatedAt:c.updatedAt});
/** Owned by AccessStore's lifetime state-directory lock. Never opens OpenClaw transcripts. */
export class RestrictedHistory {
  constructor({stateDir,now=Date.now}={}) { this.file=stateDir ? path.join(stateDir,'history.json') : null;this.now=now;this.state={version:1,conversations:[]};this.queue=Promise.resolve(); }
  async initialize() {
    if(!this.file) return this;
    let h;
    try {
      await fs.lstat(this.file);
      await assertWindowsPrivate(this.file);
      h=await fs.open(this.file,constants.O_RDONLY|constants.O_NOFOLLOW);
      const s=await h.stat();
      if(!s.isFile() || s.size>32*1024*1024 || !privateStat(s)) fail('UNAVAILABLE');
      const state=JSON.parse(await h.readFile('utf8'));
      if(state?.version!==1 || !Array.isArray(state.conversations)) fail('UNAVAILABLE');
      const ids=new Set();
      for(const c of state.conversations) {
        if(!validConversationId(c.id)||ids.has(c.id)||typeof c.deviceId!=='string'||typeof c.agentId!=='string'||!Number.isSafeInteger(c.createdAt)||!Number.isSafeInteger(c.updatedAt)||!Array.isArray(c.messages)) fail('UNAVAILABLE');
        if(c.title!==undefined && (typeof c.title!=='string'||!cleanTitle(c.title)||[...c.title].length>80))fail('UNAVAILABLE');
        ids.add(c.id);const mids=new Set();
        for(const m of c.messages) {if(!validConversationId(m.id)||mids.has(m.id)||!['user','assistant'].includes(m.role)||typeof m.text!=='string'||m.text.length>32000||!Number.isSafeInteger(m.createdAt))fail('UNAVAILABLE');mids.add(m.id);}
      }
      this.state=state;
    } catch(e) {if(e.code!=='ENOENT')throw e;} finally {await h?.close();}
    return this;
  }
  async mutate(fn,authorize=()=>{}) {
    const result=this.queue.then(async()=>{
      authorize();
      const state=structuredClone(this.state);const result=fn(state);
      // A single phone cannot exhaust the shared archive budget. Fail closed;
      // neither quota silently drops or expires existing conversations.
      const deviceBytes=new Map();
      for(const c of state.conversations) {
        const bytes=(deviceBytes.get(c.deviceId)??0)+Buffer.byteLength(JSON.stringify(c));
        if(bytes>4*1024*1024)fail('BUSY');deviceBytes.set(c.deviceId,bytes);
      }
      const json=JSON.stringify(state);if(Buffer.byteLength(json)>32*1024*1024)fail('BUSY');
      if(this.file) {
        await assertWindowsPrivate(path.dirname(this.file));
        const tmp=this.file+'.'+randomUUID()+'.tmp';let h;
        try {h=await fs.open(tmp,constants.O_WRONLY|constants.O_CREAT|constants.O_EXCL|constants.O_NOFOLLOW,0o600);await h.writeFile(json);await h.sync();await h.close();h=null;authorize();await fs.rename(tmp,this.file);}
        catch(e){await h?.close();await fs.unlink(tmp).catch(()=>{});throw e;}
      }
      this.state=state;return result;
    });this.queue=result.catch(()=>{});return result;
  }
  owned(device,id,state=this.state) {
    if(!validConversationId(id))fail();
    const c=state.conversations.find(c=>c.id===id && c.deviceId===(device.id??device.deviceId) && c.agentId===device.agentId);
    if(!c)fail('DENIED');return c;
  }
  create(device,authorize) {return this.mutate(s=>{const c={id:randomUUID(),deviceId:device.id??device.deviceId,agentId:device.agentId,createdAt:this.now(),updatedAt:this.now(),messages:[]};s.conversations.push(c);return summary(c);},authorize);}
  list(device,{before,limit=20}={}) {
    if(!Number.isInteger(limit)||limit<1||limit>20)fail();
    const all=this.state.conversations.filter(c=>c.deviceId===(device.id??device.deviceId)&&c.agentId===device.agentId).sort((a,b)=>b.createdAt-a.createdAt||b.id.localeCompare(a.id));
    let start=0;if(before!==undefined){this.owned(device,before);start=all.findIndex(c=>c.id===before)+1;}
    const rows=all.slice(start,start+limit);return {conversations:rows.map(summary),...(start+rows.length<all.length?{nextCursor:rows.at(-1).id}:{})};
  }
  messages(device,{conversationId,before,limit=50}) {
    if(!Number.isInteger(limit)||limit<1||limit>50)fail();
    const c=this.owned(device,conversationId);let end=c.messages.length;
    if(before!==undefined){if(!validConversationId(before))fail();end=c.messages.findIndex(m=>m.id===before);if(end<0)fail('DENIED');}
    const start=Math.max(0,end-limit);const messages=c.messages.slice(start,end).map(m=>({id:m.id,role:m.role,text:displayText(m),createdAt:m.createdAt}));return {messages,...(start>0?{nextCursor:messages[0].id}:{})};
  }
  rename(device,{conversationId,title},authorize) {
    if(typeof title!=='string')fail();
    title=cleanTitle(title);if(!title || [...title].length>80)fail();
    return this.mutate(s=>{const c=this.owned(device,conversationId,s);c.title=title;c.updatedAt=this.now();return summary(c);},authorize);
  }
  delete(device,{conversationId},authorize) {
    return this.mutate(s=>{const c=this.owned(device,conversationId,s);s.conversations=s.conversations.filter(item=>item!==c);return {deleted:true};},authorize);
  }
  append(device,id,role,text,authorize) {return this.mutate(s=>{const c=this.owned(device,id,s);if(!['user','assistant'].includes(role)||typeof text!=='string'||text.length>32000)fail();const m={id:randomUUID(),role,text:role==='user'?historyUserText(text):text,createdAt:this.now()};c.messages.push(m);c.updatedAt=m.createdAt;return m;},authorize);}
}
