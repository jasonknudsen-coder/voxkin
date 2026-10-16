import assert from 'node:assert/strict';
import { createRuntime } from './plugin/runtime.js';
const runtime = createRuntime({runtimeDir:'/opt/voxkin/voice',voices:['af_heart','ef_dora']});
try {
  console.log('Runtime API:',Object.keys(runtime));
  console.log(JSON.stringify(await runtime.catalog()));
  for (const request of [
    {text:'Hello. This is an isolated Voxkin review voice test.',language:'en',voice:'af_heart'},
    {text:'Hola. Esta es una prueba de voz de Voxkin.',language:'es',voice:'ef_dora'},
  ]) {
    const start=performance.now();
    const result=await runtime.speak(request);
    const wav=Buffer.from(result.audioBase64,'base64');
    assert.equal(wav.toString('ascii',0,4),'RIFF');
    assert.equal(wav.toString('ascii',8,12),'WAVE');
    assert.ok(wav.length>1000);
    console.log(JSON.stringify({language:request.language,elapsedMs:Math.round(performance.now()-start),bytes:wav.length,validWav:true}));
  }
} finally {await runtime.close();}
