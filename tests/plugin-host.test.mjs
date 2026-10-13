import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const replies=new Map(); let context;
const entry={id:'fixture',entry:'source/index.ts',packages:{source:{manifest:{},dependencies:{}}}};
const source=`import type {Anything} from '@nuclearplayer/plugin-sdk';
export default {async onLoad(api:any) {
 await api.Settings.register([{id:'key',kind:'string',default:'safe'}]);
 api.Providers.register({id:'fixture-search',name:'Fixture',kind:'metadata',async searchTracks(params:any) {
   const result=await api.Http.fetch('https://provider.test/search');return result.json();
 }});
}};`;
const Android={
 read(path) { return path==='fixture/source/index.ts' ? source:null; },
 ready() {},
 result(id,text) { const data=JSON.parse(text);const pending=replies.get(id);data.error ? pending.reject(new Error(data.error)):pending.resolve(data.result); },
 invoke(id,method,args) {
  let result=null;
  if(method==='http') result={status:200,headers:{},body:JSON.stringify([{title:'Real provider shape',source:{id:'song',provider:'fixture-search'},artists:[{name:'Artist'}]}])};
  queueMicrotask(()=>context.musikHost.reply(id,result,null));
 }
};
class TestBlob { constructor(parts) { this.text=parts.join(''); } }
const TestURL=class extends URL {};
TestURL.createObjectURL=blob=>'data:text/javascript;base64,'+Buffer.from(blob.text).toString('base64');
TestURL.revokeObjectURL=()=>{};
context=vm.createContext({Android,console:{...console},setTimeout,clearTimeout,WebAssembly,TextEncoder,TextDecoder,Uint8Array,Uint32Array,Int32Array,ArrayBuffer,DataView,Request,Response,Headers,URL:TestURL,Blob:TestBlob,location:{href:'https://appassets.androidplatform.net/assets/plugin-host.html'},crypto:globalThis.crypto,performance,fetch:async url=>new Response(fs.readFileSync('app/src/main/assets/esbuild.wasm'),{headers:{'Content-Type':'application/wasm'}})});
context.window=context;context.self=context;
vm.runInContext(fs.readFileSync('app/src/main/assets/esbuild.js','utf8'),context);
const script=new vm.Script(fs.readFileSync('app/src/main/assets/plugin-host.js','utf8'),{importModuleDynamically:vm.constants.USE_MAIN_CONTEXT_DEFAULT_LOADER});
script.runInContext(context);
let id=0;
async function call(method,args) { const request=++id;return new Promise((resolve,reject)=>{replies.set(request,{resolve,reject});context.musikHost.request(request,method,args);}); }
const initialized=await call('init',{plugins:[entry],active:{metadata:'fixture-search'}});
assert.equal(initialized.loaded.length,1,JSON.stringify(initialized.failures));
assert.equal(initialized.providers[0].id,'fixture-search');
assert.equal(initialized.settings[0].id,'key');
const result=await call('search',{provider:'fixture-search',params:{query:'music',types:['tracks']}});
assert.equal(result.tracks[0].title,'Real provider shape');
assert.equal(result.tracks[0].source.provider,'fixture-search');
await call('init',{plugins:[],active:{}});
const unloaded=await call('status',{});
assert.equal(unloaded.providers.length,0);
console.log('PASS: local WASM TypeScript bundling, plugin lifecycle, settings registration, native HTTP callback, provider search, and unload');
