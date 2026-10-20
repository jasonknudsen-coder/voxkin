import {createHash, timingSafeEqual} from 'node:crypto';

// Review appliance ONLY. Never imported by the distributable family plugin.
export function reviewEnrollment(store, {bootstrapHash, additionalBootstrapHashes=[], expiresAt, now=Date.now}) {
  if (!/^[a-f0-9]{64}$/.test(bootstrapHash ?? '') || !Number.isSafeInteger(expiresAt) ||
      expiresAt <= now() || expiresAt-now() > 30*86400_000) throw new Error('Invalid review window');
  if (!Array.isArray(additionalBootstrapHashes) || additionalBootstrapHashes.length>3 ||
      additionalBootstrapHashes.some(h=>typeof h!=='string' || !/^[a-f0-9]{64}$/.test(h)) ||
      new Set([bootstrapHash,...additionalBootstrapHashes]).size!==additionalBootstrapHashes.length+1)
    throw new Error('Invalid additional review grants');
  const hashes=[bootstrapHash,...additionalBootstrapHashes].map(h=>Buffer.from(h,'hex'));
  let queue=Promise.resolve();
  const checkWindow=()=>{if(now()>=expiresAt) throw new Error('Review window closed');};
  return {
    authenticate(secret) {checkWindow();return store.authenticate(secret);},
    redeemInvite({secret}={}) {
      const task=queue.then(async()=>{
        checkWindow();
        if(typeof secret!=='string' || !/^[A-Za-z0-9_-]{43}$/.test(secret)) throw new Error('Denied');
        const digest=createHash('sha256').update(secret).digest();
        // Evaluate every fixed-length hash; no grant-index-dependent early return.
        let matched=0;
        for(const hash of hashes) matched |= Number(timingSafeEqual(digest,hash));
        if(!matched) throw new Error('Denied');
        const invite=await store.createInvite({agentId:'review',label:'App reviewer',ttlSeconds:60});
        let device;
        try {
          device=await store.redeemInvite({secret:invite.inviteSecret});
          await store.approveDevice({deviceId:device.id});
          checkWindow();
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
