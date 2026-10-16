// Drain/cancel work before releasing the credential writer, including failed drains.
export async function closeReviewResources({server,runtime,store}) {
 const results=await Promise.allSettled([
  Promise.resolve().then(()=>server?.close()),
  Promise.resolve().then(()=>runtime?.close()),
 ]);
 try{await store?.close();}catch(error){results.push({status:'rejected',reason:error});}
 const failure=results.find(r=>r.status==='rejected');if(failure)throw failure.reason;
}
