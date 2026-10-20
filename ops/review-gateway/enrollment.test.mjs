import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm,readFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {randomBytes,createHash} from 'node:crypto';
import {AccessStore} from '../../plugin/access/store.js';
import {reviewEnrollment} from './review-plugin/enrollment.mjs';

test('review enrollment: independent grants, cap, revoke, expiry, hash-only persistence',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'voxkin-review-test-'));
 let time=Date.now();const secret=randomBytes(32).toString('base64url');
 const c={bootstrapHash:createHash('sha256').update(secret).digest('hex'),expiresAt:time+60000,now:()=>time};
 const store=await new AccessStore({stateDir:dir,limits:{maxDevices:2},now:()=>time}).initialize();
 try {
  const review=reviewEnrollment(store,c);
  await assert.rejects(review.redeemInvite({secret:'x'.repeat(43)}));
  await assert.rejects(review.redeemInvite({secret:{}}));
  const [a,b]=await Promise.all([review.redeemInvite({secret}),review.redeemInvite({secret})]);
  assert.notEqual(a.deviceSecret,b.deviceSecret);assert.notEqual(a.id,b.id);
  assert.equal(review.authenticate(a.deviceSecret).agentId,'review');
  await assert.rejects(review.redeemInvite({secret}));
  await store.revokeDevice({deviceId:a.id});assert.throws(()=>review.authenticate(a.deviceSecret));
  const data=await readFile(join(dir,'access.json'),'utf8');
  for(const value of [secret,a.deviceSecret,b.deviceSecret]) assert.ok(!data.includes(value));
  time=c.expiresAt;assert.throws(()=>review.authenticate(b.deviceSecret));await assert.rejects(review.redeemInvite({secret}));
  assert.throws(()=>reviewEnrollment(store,{...c,expiresAt:time+31*86400_000}));
 }finally{await store.close();await rm(dir,{recursive:true,force:true});}
});

test('additional Play invitation preserves Apple credentials, expiry and hash-only storage',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'voxkin-review-grants-'));
 let time=Date.now();const apple=randomBytes(32).toString('base64url'),play=randomBytes(32).toString('base64url');
 const hash=s=>createHash('sha256').update(s).digest('hex');
 const config={bootstrapHash:hash(apple),expiresAt:time+60000,now:()=>time};
 const store=await new AccessStore({stateDir:dir,now:()=>time}).initialize();
 try {
  const prior=reviewEnrollment(store,config);
  const existing=await prior.redeemInvite({secret:apple});
  const review=reviewEnrollment(store,{...config,additionalBootstrapHashes:[hash(play)]});
  assert.equal(review.authenticate(existing.deviceSecret).agentId,'review');
  const [a,b]=await Promise.all([review.redeemInvite({secret:apple}),review.redeemInvite({secret:play})]);
  assert.notEqual(a.deviceSecret,b.deviceSecret);
  assert.equal(review.authenticate(b.deviceSecret).agentId,'review');
  await assert.rejects(prior.redeemInvite({secret:play}));
  await assert.rejects(review.redeemInvite({secret:'x'.repeat(43)}));
  const persisted=await readFile(join(dir,'access.json'),'utf8');
  for(const value of [apple,play,a.deviceSecret,b.deviceSecret])assert.ok(!persisted.includes(value));
  // Removing an invitation prevents fresh enrollment, not revocation of already-enrolled devices.
  assert.equal(prior.authenticate(b.deviceSecret).agentId,'review');
  time=config.expiresAt;
  for(const credential of [existing.deviceSecret,a.deviceSecret,b.deviceSecret])assert.throws(()=>review.authenticate(credential));
  for(const secret of [apple,play])await assert.rejects(review.redeemInvite({secret}));
 }finally{await store.close();await rm(dir,{recursive:true,force:true});}
});

test('additional review grant configuration fails closed',()=>{
 const hash='a'.repeat(64),c={bootstrapHash:hash,expiresAt:Date.now()+60000};
 for(const additionalBootstrapHashes of [null,{},'b'.repeat(64),[null],['bad'],[hash],['b'.repeat(64),'b'.repeat(64)],['b','c','d','e'].map(s=>s.repeat(64))]){
  assert.throws(()=>reviewEnrollment({}, {...c,additionalBootstrapHashes}));
 }
});
