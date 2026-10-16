import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
import plugin from './node_modules/@voxkin/openclaw-local-voice/index.js';
import { createRuntime } from './node_modules/@voxkin/openclaw-local-voice/runtime.js';
assert.equal(process.getuid(),10001);
assert.ok(plugin);
const receipt='/opt/voxkin/voice/verified.json';
const hash=()=>crypto.createHash('sha256').update(fs.readFileSync(receipt)).digest('hex');
const before=hash();
const runtime=createRuntime({runtimeDir:'/opt/voxkin/voice',voices:['af_heart','ef_dora']});
try {
 const catalog=await runtime.catalog();
 console.log(JSON.stringify({phase:'catalog',catalog}));
 for(const [language,voice,text] of [['en','af_heart','Hello. This is a clean Linux qualification.'],['es','ef_dora','Hola. Esta es una prueba de instalación.']]) {
  const result=await runtime.speak({language,voice,text});
  const wav=Buffer.from(result.audioBase64,'base64');
  assert.equal(wav.toString('ascii',0,4),'RIFF');
  assert.equal(wav.toString('ascii',8,12),'WAVE');
  assert.ok(wav.length>1000);
  assert.equal(wav.readUInt32LE(24),24000);
  console.log(JSON.stringify({language,voice,bytes:wav.length,sampleRate:24000,validWav:true}));
 }
 assert.equal(hash(),before);
 console.log(JSON.stringify({nonroot:true,pluginImported:true,receiptUnchanged:true,receiptSHA256:before}));
} finally { await runtime.close(); }
