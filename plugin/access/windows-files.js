import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {join} from 'node:path';
const exec=promisify(execFile);
// Windows mode bits do not describe NTFS privacy. Fail closed on an untrusted
// ACL/owner or any reparse-point ancestor before opening restricted state.
export async function assertWindowsPrivate(file) {
  if(process.platform!=='win32')return;
  const script=`$ErrorActionPreference='Stop'
try {
 $p=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('${Buffer.from(file).toString('base64')}'))
 $item=Get-Item -LiteralPath $p -Force
 $walk=$item
 while($null -ne $walk){
  if(($walk.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'reparse'}
  if($walk -is [IO.DirectoryInfo]){$walk=$walk.Parent}else{$walk=$walk.Directory}
 }
 $acl=Get-Acl -LiteralPath $p
 $trusted=@([Security.Principal.WindowsIdentity]::GetCurrent().User.Value,'S-1-5-18','S-1-5-32-544')
 $owner=$acl.GetOwner([Security.Principal.SecurityIdentifier]).Value
 if($owner -notin $trusted){throw 'owner'}
 foreach($rule in $acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])){
  if($rule.AccessControlType -eq 'Allow' -and $rule.IdentityReference.Value -notin $trusted){throw 'acl'}
 }
 Write-Output 'PRIVATE'
}catch{exit 1}`;
  try {
    const shell=join(process.env.SystemRoot || 'C:\\Windows','System32','WindowsPowerShell','v1.0','powershell.exe');
    const {stdout}=await exec(shell,['-NoProfile','-NonInteractive','-EncodedCommand',Buffer.from(script,'utf16le').toString('base64')],{timeout:10000,maxBuffer:4096,windowsHide:true});
    if(stdout.trim()!=='PRIVATE')throw new Error();
  }catch{throw new Error('Unsafe Windows state permissions or reparse point');}
}
export function privateStat(stat){return process.platform==='win32' || ((stat.mode&0o077)===0 && (!process.getuid || stat.uid===process.getuid()));}
