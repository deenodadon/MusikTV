// Adapted from Musik nuclear_host.mjs, GPL-3.0-or-later.
for (const name of ['log','debug','info','warn','error']) console[name]=()=>{};
let buildReady;
function normalize(path) {
 const parts=[]; for(const part of path.split('/')) { if(part==='..') parts.pop(); else if(part && part!=='.') parts.push(part); } return parts.join('/');
}
function exported(value) {
 if(typeof value==='string') return value;
 if(value && typeof value==='object') for(const key of ['browser','import','default','require','node']) { const target=exported(value[key]); if(target) return target; }
}
async function loadPlugin(entry) {
 if(!buildReady) buildReady=esbuild.initialize({wasmURL:'/assets/esbuild.wasm',worker:false});
 await buildReady;
 const roots=Object.keys(entry.packages).sort((a,b)=>b.length-a.length);
 const read=path=>Android.read(entry.id+'/'+path);
 const exists=path=>read(path)!==null;
 const file=path=> {
   for(const ext of ['', '.ts','.tsx','.mjs','.cjs','.js','.jsx','.json','/index.ts','/index.js']) if(exists(path+ext)) return path+ext;
   throw new Error('Plugin file was not found: '+path);
 };
 const packageEntry=(root,sub='')=>{
   const m=entry.packages[root]?.manifest ?? {}; const exports=m.exports;
   let target=typeof exports==='string' ? (sub ? null:exports) : exported(exports?.['./'+sub] ?? (!sub ? exports?.['.'] ?? exports:null));
   if(!target && typeof exports==='object' && sub) for(const [key,value] of Object.entries(exports)) if(key.includes('*')) {
     const [head,tail]=key.split('*'); const spec='./'+sub;
     if(spec.startsWith(head) && spec.endsWith(tail)) target=exported(value)?.replace('*',spec.slice(head.length,tail ? -tail.length:undefined));
   }
   return file(normalize(root+'/'+(target ?? (sub || (typeof m.browser==='string' ? m.browser : m.module || m.main || 'index.js')))));
 };
 const result=await esbuild.build({entryPoints:[entry.entry],bundle:true,format:'esm',platform:'browser',target:'es2020',write:false,plugins:[{
   name:'verified-local-packages', setup(build) {
     build.onResolve({filter:/.*/},args=>{
       if(args.path==='@nuclearplayer/plugin-sdk') return {path:'sdk',namespace:'sdk'};
       if(args.path.startsWith('node:')) throw new Error('This plugin uses a desktop-only Node API: '+args.path);
       if(args.kind==='entry-point') return {path:file(args.path),namespace:'package'};
       if(args.path.startsWith('.')) return {path:file(normalize(args.importer.split('/').slice(0,-1).join('/')+'/'+args.path)),namespace:'package'};
       const packageRoot=roots.find(r=>args.importer.startsWith(r+'/')) ?? 'source';
       const pieces=args.path.split('/'); const name=args.path.startsWith('@') ? pieces.slice(0,2).join('/') : pieces[0];
       const sub=pieces.slice(args.path.startsWith('@') ? 2:1).join('/');
       const dep=entry.packages[packageRoot].dependencies[name];
       if(!dep) throw new Error('Plugin requires a desktop or undeclared dependency: '+name);
       return {path:packageEntry(dep,sub),namespace:'package'};
     });
     build.onLoad({filter:/.*/,namespace:'sdk'},()=>({contents:'export default {};',loader:'js'}));
     build.onLoad({filter:/.*/,namespace:'package'},args=>({contents:read(args.path),loader:args.path.endsWith('.json') ? 'json':args.path.endsWith('.tsx') ? 'tsx':args.path.endsWith('.ts') ? 'ts':'js'}));
   }
 }]});
 const blob=new Blob([result.outputFiles[0].text],{type:'text/javascript'}); const url=URL.createObjectURL(blob);
 try { return await import(url); } finally { URL.revokeObjectURL(url); }
}
let callbackId = 0;
const callbacks = new Map(), providers = new Map(), loaded = new Map();
const events = new Map(), subscriptions = new Map();
let definitions = new Map(), active = {}, failures = {};
function callback(method, args) {
  return new Promise((resolve, reject) => {
    const id = ++callbackId;
    const timer = setTimeout(() => { callbacks.delete(id); reject(new Error('Host request timed out')); }, 60000);
    callbacks.set(id, {resolve, reject, timer});
    Android.invoke(id, method, JSON.stringify(args));
  });
}
function subscribe(key, listener) {
  const list = subscriptions.get(key) ?? new Set();
  subscriptions.set(key, list); list.add(listener);
  return () => list.delete(listener);
}
async function notify(key, value) {
  await Promise.allSettled([...subscriptions.get(key) ?? []].map(fn => fn(value)));
}
function provider(id, kind) {
  const found = id ? providers.get(id) : providers.get(active[kind]) ?? [...providers.values()].find(p => p.kind === kind);
  if (!found || found.kind !== kind) throw new Error(`No ${kind} provider available`);
  return found;
}
async function invoke(id, kind, method, args=[]) {
  const p = provider(id, kind);
  if (typeof p[method] !== 'function') throw new Error(`${p.name} does not support ${method}`);
  return await p[method](...args);
}
async function search(params, id) {
  const p = provider(id, 'metadata'), result = {artists:[], albums:[], tracks:[], playlists:[]};
  for (const type of params.types ?? ['artists','albums','tracks']) {
    const method = `search${type[0].toUpperCase()}${type.slice(1)}`;
    if (typeof p[method] === 'function') result[type] = await p[method](params);
  }
  return result;
}
function apiFor(pluginId) {
  const key = id => `${pluginId}:${id}`;
  const api = {
    Providers: {
      register(p) {
        if (!p?.id || !p.kind) throw new Error('Invalid provider descriptor');
        if (providers.has(p.id)) throw new Error(`Duplicate provider ID: ${p.id}`);
        providers.set(p.id, {...p, pluginId}); notify('providers', null); return p.id;
      },
      unregister: id => providers.delete(id),
      list: kind => [...providers.values()].filter(p => !kind || p.kind === kind),
      get: (id, kind) => {try {return provider(id, kind);} catch {return undefined;}},
      subscribe: fn => subscribe('providers', fn),
    },
    Settings: {
      register: async defs => {
        for (const d of defs) definitions.set(key(d.id), {...d, pluginId});
        await callback('settings.register',[pluginId,defs]);
        return {registered:defs.map(d=>d.id)};
      },
      get: async id => callback('settings.get',[pluginId,id]),
      set: async (id, value) => {
        const d=definitions.get(key(id));
        if(!d) throw new Error('Unknown plugin setting');
        if(['string','enum'].includes(d.kind) && typeof value !== 'string') throw new Error('Setting requires a string');
        if(d.kind==='boolean' && typeof value !== 'boolean') throw new Error('Setting requires true or false');
        if(d.kind==='number' && (typeof value !== 'number' || !Number.isFinite(value) || (d.min!=null && value<d.min) || (d.max!=null && value>d.max))) throw new Error('Setting requires a number within its allowed range');
        if(d.kind==='enum' && !d.options?.some(option=>option.value===value)) throw new Error('Invalid setting option');
        await callback('settings.set',[pluginId,id,value]); await notify(key(id), value);
      },
      getGlobal: async id => callback('settings.get',['global',id]),
      setGlobal: async (id,value) => {await callback('settings.set',['global',id,value]); await notify(`global:${id}`, value);},
      subscribe: (id,fn) => subscribe(key(id), fn),
      registerWidget: () => {throw new Error('Custom React settings widgets require an Android settings adapter');},
      unregisterWidget: () => {},
    },
    Http: {fetch: async (input,init={}) => {
      const url = String(input instanceof Request ? input.url : input);
      const response = await callback('http', [url, {...init, headers:Object.fromEntries(new Headers(init.headers ?? {}))}]);
      return new Response([204,205,304].includes(response.status) ? null : response.body, {status:response.status,headers:response.headers});
    }},
    Ytdlp: Object.fromEntries(['search','getStream','getPlaylist'].map(method=>[method,(...args)=>callback(`ytdlp.${method}`,args)])),
    Logger: Object.fromEntries(['debug','info','warn','error'].map(name=>[name,()=>{}])),
    Events: {
      on: (event,fn) => {const list=events.get(event) ?? new Set(); events.set(event,list); list.add(fn); return ()=>list.delete(fn);},
      emit: (event,payload) => Promise.allSettled([...events.get(event) ?? []].map(fn=>fn(payload))),
    },
    Metadata: {search},
    Streaming: {
      resolveCandidatesForTrack: async track => {try {const p=provider(undefined,'streaming'); const candidates=p.searchForTrackV2 ? await p.searchForTrackV2(track) : await p.searchForTrack(track.artists?.map(a=>a.name).join(', ') ?? '',track.title,track.album?.title); return {success:true,candidates};} catch(e) {return {success:false,error:e.message};}},
      resolveStreamForCandidate: async candidate => {const p=provider(candidate.source?.provider,'streaming'); const stream=p.getStreamUrlV2 ? await p.getStreamUrlV2(candidate) : await p.getStreamUrl(candidate.id); return {...candidate,stream};},
    },
    Lyrics: {getLyricsForTrack: async (track,id) => {const p=provider(id,'lyrics'); const lyrics=await p.getLyrics(track,{}); return lyrics ? [{providerId:p.id,providerName:p.name,lyrics}] : [];}},
    Discovery: {getRecommendations: (context,options,id) => invoke(id,'discovery','getRecommendations',[context,options])},
    Shell: {openExternal: url => callback('shell.openExternal',[url])},
  };
  for (const method of ['fetchArtistBio','fetchArtistSocialStats','fetchArtistAlbums','fetchArtistTopTracks','fetchArtistPlaylists','fetchArtistRelatedArtists','fetchAlbumDetails']) api.Metadata[method] = (id,pid)=>invoke(pid,'metadata',method,[id]);
  api.Dashboard = {};
  for (const method of ['fetchTopTracks','fetchTopArtists','fetchTopAlbums','fetchEditorialPlaylists','fetchNewReleases']) api.Dashboard[method] = async id => {
    const ps=id ? [provider(id,'dashboard')] : [...providers.values()].filter(p=>p.kind==='dashboard' && typeof p[method]==='function');
    return await Promise.all(ps.map(async p=>({providerId:p.id,metadataProviderId:p.metadataProviderId,providerName:p.name,items:await p[method]()})));
  };
  // Desktop/player control requests are handled by Musik, never fake success.
  for (const [area,methods] of Object.entries({
    Queue:['getQueue','getCurrentItem','addToQueue','addNext','addAt','removeByIds','removeByIndices','clearQueue','reorder','updateItemState','goToNext','goToPrevious','goToIndex','goToId'],
    Playback:['getState','play','pause','stop','toggle','seekTo','getVolume','setVolume','isMuted','setMuted','isShuffleEnabled','setShuffleEnabled','getRepeatMode','setRepeatMode'],
    Playlists:['getIndex','getPlaylist','createPlaylist','deletePlaylist','addTracks','removeTracks','reorderTracks','importPlaylist','saveQueueAsPlaylist'],
    Favorites:['getTracks','getAlbums','getArtists','addTrack','removeTrack','isTrackFavorite','addAlbum','removeAlbum','isAlbumFavorite','addArtist','removeArtist','isArtistFavorite'],
  })) {
    api[area]=Object.fromEntries(methods.map(method=>[method,(...args)=>callback(`${area}.${method}`,args)]));
    api[area].subscribe=fn=>subscribe(area,fn);
    if (area==='Queue') api[area].subscribeToCurrentItem=fn=>subscribe('currentItem',fn);
  }
  return api;
}
// Asset reads remain local; provider fetch uses native HTTPS transport.
const assetFetch=globalThis.fetch.bind(globalThis);
globalThis.fetch=(input,init)=>{
 const url=new URL(String(input instanceof Request ? input.url:input),location.href);
 return url.hostname==='appassets.androidplatform.net' ? assetFetch(input,init):apiFor('musik').Http.fetch(input,init);
};
async function unload() {
  for (const [id,{plugin,api}] of [...loaded].reverse()) {
    try {await plugin.onDisable?.(api); await plugin.onUnload?.(api);} catch {}
    loaded.delete(id);
  }
  providers.clear(); events.clear(); subscriptions.clear(); definitions.clear(); failures={};
}
async function handle(method,args) {
  if (method==='init') {
    await unload(); active=args.active ?? {};
    for (const entry of args.plugins) {
      const api=apiFor(entry.id);
      const savedEvents = new Map([...events].map(([key,set])=>[key,new Set(set)]));
      const savedSubscriptions = new Map([...subscriptions].map(([key,set])=>[key,new Set(set)]));
      let plugin;
      try {
        const module=await loadPlugin(entry);
        plugin=module.default?.default ?? module.default;
        if (!plugin || !['onEnable','onLoad'].some(k=>typeof plugin[k]==='function')) throw new Error('No Nuclear plugin lifecycle entry point');
        await plugin.onLoad?.(api); await plugin.onEnable?.(api);
        
        loaded.set(entry.id,{plugin,api});
      } catch(e) {
        try {await plugin?.onDisable?.(api); await plugin?.onUnload?.(api);} catch {}
        events.clear(); for (const [key,set] of savedEvents) events.set(key,set);
        subscriptions.clear(); for (const [key,set] of savedSubscriptions) subscriptions.set(key,set);
        for (const [key,d] of definitions) if(d.pluginId===entry.id) definitions.delete(key);
        for (const [id,p] of providers) if(p.pluginId===entry.id) providers.delete(id);
        failures[entry.id]=String(e.message).slice(0,250);
      }
    }
    return handle('status',{});
  }
  if (method==='status') return {providers:[...providers.values()].map(p=>({id:p.id,name:p.name,kind:p.kind,pluginId:p.pluginId,streamingProviderId:p.streamingProviderId,metadataProviderId:p.metadataProviderId,methods:Object.keys(p).filter(k=>typeof p[k]==='function')})),loaded:[...loaded.keys()],failures,settings:[...definitions.values()]};
  if (method==='search') return search(args.params,args.provider);
  if (method==='invoke') return invoke(args.provider,args.kind,args.method,args.args);
  if (method==='stream') {
    const track=args.track, p=provider(args.provider ?? track.source?.provider,'streaming');
    let candidates;
    const metadata=providers.get(track.source?.provider);
    const explicit=track.streamCandidates?.filter(c=>c.source?.provider===p.id);
    if(explicit?.length) candidates=explicit;
    else if (track.source?.provider===p.id || metadata?.streamingProviderId===p.id) candidates=[{id:track.source.id,source:{...track.source,provider:p.id},title:track.title}];
    else candidates=p.searchForTrackV2 ? await p.searchForTrackV2(track) : await p.searchForTrack(track.artists?.map(a=>a.name).join(', ') ?? '',track.title,track.album?.title);
    for (const c of candidates ?? []) {
      try {const stream=p.getStreamUrlV2 ? await p.getStreamUrlV2(c) : await p.getStreamUrl(c.id); if(stream?.url) return stream;} catch {}
    }
    throw new Error('Plugin could not resolve a playable audio stream');
  }
  if (method==='lyrics') return apiFor('musik').Lyrics.getLyricsForTrack(args.track,args.provider);
  if (method==='setting') {await apiFor(args.plugin).Settings.set(args.id,args.value); return true;}
  if (method==='event') return apiFor('musik').Events.emit(args.event,args.payload);
  if (method==='notify') return notify(args.key,args.value);
  if (method==='close') {await unload();  return true;}
  throw new Error(`Unsupported host method: ${method}`);
}

window.musikHost={
 reply(id,result,error) { const pending=callbacks.get(id); if(!pending) return; callbacks.delete(id);clearTimeout(pending.timer); error ? pending.reject(new Error(error)) : pending.resolve(result); },
 async request(id,method,args) { try { Android.result(id,JSON.stringify({result:await handle(method,args) ?? null})); } catch(e) { Android.result(id,JSON.stringify({error:String(e.message).slice(0,300)})); } }
};
Android.ready();
