import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,chmod,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {spawnSync} from 'node:child_process';

test('monitor separates Apple expiry from persistent Play availability without relaxing budget',async()=>{
 const root=await mkdtemp(join(tmpdir(),'voxkin-health-monitor-'));
 try {
  await writeFile(join(root,'expires-at'),'1');
  const docker=join(root,'docker');
  await writeFile(docker,`#!/bin/sh
case "$*" in
 inspect*) echo true ;;
 *usage.json*) echo "\${TEST_COUNT:-13}" ;;
 *permanentPlayBootstrapHash*) echo "\${TEST_PLAY:-false}" ;;
esac
`);await chmod(docker,0o700);
  const run=(play,count)=>spawnSync('sh',[resolve('ops/review-gateway/health-check')],{encoding:'utf8',env:{...process.env,PATH:root+':'+process.env.PATH,GAME_DIR:root,TEST_PLAY:play,TEST_COUNT:String(count)}});
  assert.notEqual(run('false',13).status,0);
  const active=run('true',13);assert.equal(active.status,0);assert.match(active.stdout,/APPLE_REVIEW_WINDOW=expired/);assert.match(active.stdout,/VOXKIN_REVIEW_HEALTH=OK/);
  assert.notEqual(run('true',85).status,0);assert.notEqual(run('true',-1).status,0);
  assert.notEqual(run('null',13).status,0);
 }finally{await rm(root,{recursive:true,force:true});}
});
