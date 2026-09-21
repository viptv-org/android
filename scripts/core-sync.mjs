import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync, rmSync, readdirSync } from 'node:fs';
import { resolve, dirname, relative } from 'node:path';
const root=resolve(import.meta.dirname,'..'), dest=resolve(root,'vendor/core');
const hash=data=>createHash('sha256').update(data).digest('hex');
const safe=p=>typeof p==='string'&&!p.startsWith('/')&&!p.split('/').some(x=>!x||x==='.'||x==='..');
const git=(repo,args)=>execFileSync('git',['-C',repo,...args],{maxBuffer:16*1024*1024});
const mode=process.argv[2]??'check';
if(mode==='sync') {
 const source=resolve(process.argv[3]??'../core');
 if(git(source,['status','--porcelain']).toString().trim())throw Error('Commit core before adoption');
 const revision=git(source,['rev-parse','HEAD']).toString().trim();
 const names=git(source,['ls-tree','-r','--name-only',revision]).toString().trim().split('\n').filter(p=>
 ['Cargo.toml','Cargo.lock','DESIGN_REF'].includes(p)||p.startsWith('crates/')||p.startsWith('tests/')||p.startsWith('generated/kotlin/')||p.startsWith('generated/native-kotlin/')||p.startsWith('generated/kotlin-wire/')||p.startsWith('adapters/android/src/'));
 const imports=names.map(path=>({path,data:git(source,['show',`${revision}:${path}`])}));
 const previous=existsSync(resolve(dest,'lock.json'))?JSON.parse(readFileSync(resolve(dest,'lock.json'),'utf8')).files:{};
 const files={};
 for(const {path,data} of imports){if(!safe(path))throw Error('Invalid source path');files[path]=hash(data);}
 for(const path of Object.keys(previous)){if(!safe(path))throw Error('Invalid preceding path');if(!files[path])rmSync(resolve(dest,path),{force:true});}
 for(const {path,data} of imports){mkdirSync(dirname(resolve(dest,path)),{recursive:true});writeFileSync(resolve(dest,path),data);}
 writeFileSync(resolve(dest,'lock.json'),JSON.stringify({repository:'viptv-org/core',revision,files},null,2)+'\n');
 writeFileSync(resolve(root,'CORE_REF'),revision+'\n');
 console.log(`Imported core ${revision}`);
} else if(mode==='check') {
 const lock=JSON.parse(readFileSync(resolve(dest,'lock.json'),'utf8'));
 if(lock.repository!=='viptv-org/core'||!/^[a-f0-9]{40}$/.test(lock.revision)||readFileSync(resolve(root,'CORE_REF'),'utf8').trim()!==lock.revision)throw Error('Core pin mismatch');
 for(const [path,expected] of Object.entries(lock.files))if(!safe(path)||hash(readFileSync(resolve(dest,path)))!==expected)throw Error(`Core artifact mismatch: ${path}`);
 const inspect=dir=>{for(const entry of readdirSync(dir,{withFileTypes:true})){const file=resolve(dir,entry.name);if(entry.isDirectory())inspect(file);else if(!lock.files[relative(dest,file)])throw Error(`Unpinned core source: ${relative(dest,file)}`);}};
 for(const dir of ['crates','generated','adapters','tests'])if(existsSync(resolve(dest,dir)))inspect(resolve(dest,dir));
 console.log(`Core integrity passed: ${lock.revision}`);
} else throw Error('Use sync <core-checkout> or check');
