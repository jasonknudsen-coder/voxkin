import {createHash, timingSafeEqual} from 'node:crypto';

// Review appliance ONLY. Never imported by the distributable family plugin.
export function reviewEnrollment(store, {bootstrapHash, additionalBootstrapHashes=[], permanentPlayBootstrapHash, expiresAt, now=Date.now}) {
  const validHash=h=>typeof h==='string' && /^[a-f0-9]{64}$/.test(h);
  const permanent=permanentPlayBootstrapHash!==undefined;
  if (permanent && !validHash(permanentPlayBootstrapHash)) throw new Error('Invalid permanent Play grant');
  if (!validHash(bootstrapHash) || !Number.isSafeInteger(expiresAt) || expiresAt<=0 ||
      (!permanent && expiresAt<=now()) || expiresAt-now()>30*86400_000) throw new Error('Invalid review window');
  if (!Array.isArray(additionalBootstrapHashes) || additionalBootstrapHashes.length>3 ||
      additionalBootstrapHashes.some(h=>!validHash(h)) ||
      new Set([bootstrapHash,...additionalBootstrapHashes,...(permanent?[permanentPlayBootstrapHash]:[])]).size!==additionalBootstrapHashes.length+1+Number(permanent))
    throw new Error('Invalid additional review grants');
  // Hash-bound persisted labels distinguish Play devices across process restarts.
  // Rotating/removing this grant revokes its devices without extending Apple access.
  const playLabel=permanent?'Play reviewer:'+permanentPlayBootstrapHash:null;
  const grants=[bootstrapHash,...additionalBootstrapHashes].map(hash=>({hash:Buffer.from(hash,'hex'),label:'App reviewer',permanent:false}));
  if(permanent)grants.push({hash:Buffer.from(permanentPlayBootstrapHash,'hex'),label:playLabel,permanent:true});
  let queue=Promise.resolve();
  const checkWindow=grant=>{if(!grant.permanent && now()>=expiresAt) throw new Error('Review window closed');};
  return {
    authenticate(secret) {
      const device=store.authenticate(secret);
      if(permanent && device.label===playLabel)return device;
      if(device.label!=='App reviewer')throw new Error('Review grant revoked');
      checkWindow({permanent:false});return device;
    },
    redeemInvite({secret}={}) {
      const task=queue.then(async()=>{
        if(typeof secret!=='string' || !/^[A-Za-z0-9_-]{43}$/.test(secret)) throw new Error('Denied');
        const digest=createHash('sha256').update(secret).digest();
        // Evaluate every fixed-length hash; no grant-index-dependent early return.
        let grant;
        for(const candidate of grants)if(timingSafeEqual(digest,candidate.hash))grant=candidate;
        if(!grant) throw new Error('Denied');
        checkWindow(grant);
        const invite=await store.createInvite({agentId:'review',label:grant.label,ttlSeconds:60});
        let device;
        try {
          device=await store.redeemInvite({secret:invite.inviteSecret});
          await store.approveDevice({deviceId:device.id});
          checkWindow(grant);
          return {...device,status:'active'};
        } catch(error) {
          if(device) await store.revokeDevice({deviceId:device.id});
          else await store.cancelInvite({inviteId:invite.id}).catch(()=>{});
          throw error;
        }
      });
      queue=task.catch(()=>{});return task;
    },
  };
}
