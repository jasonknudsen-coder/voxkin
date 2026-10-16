import {createHash, timingSafeEqual} from 'node:crypto';

// Review appliance ONLY. Never imported by the distributable family plugin.
export function reviewEnrollment(store, {bootstrapHash, expiresAt, now=Date.now}) {
  if (!/^[a-f0-9]{64}$/.test(bootstrapHash ?? '') || !Number.isSafeInteger(expiresAt) ||
      expiresAt <= now() || expiresAt-now() > 30*86400_000) throw new Error('Invalid review window');
  let queue=Promise.resolve();
  const checkWindow=()=>{if(now()>=expiresAt) throw new Error('Review window closed');};
  return {
    authenticate(secret) {checkWindow();return store.authenticate(secret);},
    redeemInvite({secret}={}) {
      const task=queue.then(async()=>{
        checkWindow();
        if(typeof secret!=='string' || !/^[A-Za-z0-9_-]{43}$/.test(secret) ||
            !timingSafeEqual(createHash('sha256').update(secret).digest(),Buffer.from(bootstrapHash,'hex'))) throw new Error('Denied');
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
