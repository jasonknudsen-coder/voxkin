import test from 'node:test';
import assert from 'node:assert/strict';
import {closeReviewResources} from './review-plugin/lifecycle.mjs';
test('failed drain still cancels runtime and releases credential writer',async()=>{
 const calls=[];const failure=new Error('drain timeout');
 await assert.rejects(closeReviewResources({server:{close(){calls.push('server');throw failure;}},runtime:{async close(){calls.push('runtime');}},store:{async close(){calls.push('store');}}}),e=>e===failure);
 assert.deepEqual(calls,['server','runtime','store']);
});
test('runtime failure does not skip writer release',async()=>{
 let closed=false;
 await assert.rejects(closeReviewResources({runtime:{async close(){throw new Error('runtime');}},store:{async close(){closed=true;}}}),/runtime/);
 assert.equal(closed,true);
});
