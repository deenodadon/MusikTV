"""Verified Nuclear releases and npm packages for Android's WebView/WASM host."""
import base64
import hashlib
import io
import json
import re
import shutil
import tarfile
import tempfile
import zipfile
from pathlib import Path
from urllib.parse import quote,urlparse
import requests
from advanced import UserError, Session
import advanced

REGISTRY='https://raw.githubusercontent.com/NuclearPlayer/plugin-registry/master/plugins.json'

def directory():
    p=advanced.root/'plugins';p.mkdir(exist_ok=True);return p

def config():
    p=directory()/'index.json'
    return json.loads(p.read_text()) if p.exists() else {'installed':{},'active':{}}

def save(c):
    p=directory()/'index.json';t=p.with_suffix('.tmp');t.write_text(json.dumps(c));t.replace(p)

def registry():
    r=Session().get(REGISTRY);r.raise_for_status();return r.json()['plugins']

def status():return config()

def valid(identity):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,100}',identity):raise UserError('Invalid plugin ID.')
    return identity

def unpack_zip(blob,dest):
    with zipfile.ZipFile(io.BytesIO(blob)) as z:
        entries=z.infolist()
        if len(entries)>5000 or sum(e.file_size for e in entries)>100_000_000:raise UserError('Plugin archive is too large.')
        for e in entries:
            p=(dest/e.filename.replace('\\','/')).resolve()
            if not p.is_relative_to(dest.resolve()) or ':' in e.filename or (e.external_attr>>16)&0o170000==0o120000:raise UserError('Plugin archive contains an unsafe path.')
        z.extractall(dest)
    manifests=[p for p in dest.rglob('package.json') if 'node_modules' not in p.parts]
    if (dest/'package.json').exists():return dest
    if len(manifests)!=1:raise UserError('Plugin must contain one root manifest.')
    return manifests[0].parent

def npm_version(meta, requirement):
    from semantic_version import NpmSpec,Version
    if requirement in meta.get('dist-tags',{}):return meta['dist-tags'][requirement]
    try:
        spec=NpmSpec(requirement)
        candidates=[Version(v) for v in meta['versions'] if spec.match(Version(v))]
        return str(max(candidates))
    except (ValueError,TypeError):raise UserError('Unsupported npm dependency version range.') from None

def install(identity):
    valid(identity)
    entry=next((e for e in registry() if e['id']==identity),None)
    if entry is None:raise UserError('Plugin is not in the Nuclear registry.')
    match=re.fullmatch(r'https://github.com/([^/]+/[^/]+)/releases/download/([^/]+)/([^/]+)',entry['downloadUrl'])
    if not match or match[1].casefold()!=entry['repo'].casefold():raise UserError('Plugin URL does not match the registry repository.')
    r=Session().get(f'https://api.github.com/repos/{entry["repo"]}/releases/tags/{match[2]}');r.raise_for_status()
    asset=next((a for a in r.json()['assets'] if a['name']==match[3]),{})
    digest=asset.get('digest','')
    if not re.fullmatch(r'sha256:[a-f0-9]{64}',digest):raise UserError('GitHub did not supply a verifiable SHA-256 release digest.')
    blob=download(entry['downloadUrl'],50_000_000)
    if hashlib.sha256(blob).hexdigest()!=digest[7:]:raise UserError('Plugin release checksum mismatch.')
    dest=directory()/identity
    with tempfile.TemporaryDirectory(dir=directory(),prefix='.install-') as temporary:
        temp=Path(temporary);source=unpack_zip(blob,temp/'release')
        manifest=json.loads((source/'package.json').read_text())
        main=source/manifest.get('main','index.js')
        if not main.resolve().is_relative_to(source.resolve()):raise UserError('Plugin entry point escapes its release.')
        if not main.is_file():main=next((source/p for p in ['src/index.ts','src/index.js','index.ts','index.js'] if (source/p).is_file()),main)
        if not main.is_file():raise UserError('Plugin entry point is missing.')
        # Retain complete release and licenses; build only JS/TS in the TV runtime.
        package=temp/'prepared';shutil.copytree(source,package/'source')
        packages={};resolved={};total=[0]
        def dependency(name,requirement):
            if name=='@nuclearplayer/plugin-sdk':return None
            if not re.fullmatch(r'(?:@[a-zA-Z0-9_.-]+/)?[a-zA-Z0-9_.-]+',name):raise UserError('Invalid npm dependency name.')
            r=Session().get('https://registry.npmjs.org/'+quote(name,safe='@'));r.raise_for_status();meta=r.json()
            version=npm_version(meta,requirement);key=name+'@'+version
            if key in resolved:return resolved[key]
            if len(resolved)>=150:raise UserError('Plugin dependency graph is too large.')
            folder='npm/'+hashlib.sha256(key.encode()).hexdigest()[:20]
            resolved[key]=folder
            m=meta['versions'][version];dist=m['dist'];url=dist['tarball']
            if urlparse(url).scheme!='https' or urlparse(url).hostname!='registry.npmjs.org':raise UserError('npm tarball must use the official HTTPS registry.')
            data=download(url,25_000_000);sri=dist.get('integrity','').split(' ')
            verified=False
            for item in sri:
                if item.startswith(('sha512-','sha256-')):
                    algorithm,expected=item.split('-',1)
                    verified|=base64.b64encode(hashlib.new(algorithm,data).digest()).decode()==expected
            if not verified:raise UserError('npm package integrity verification failed.')
            d=package/folder;d.mkdir(parents=True)
            with tarfile.open(fileobj=io.BytesIO(data),mode='r:gz') as archive:
                members=archive.getmembers()
                if len(members)>10000:raise UserError('npm package has too many files.')
                for member in members:
                    relative=Path(member.name).relative_to('package') if member.name.startswith('package/') else None
                    if relative is None:continue
                    target=(d/relative).resolve()
                    if not target.is_relative_to(d.resolve()) or member.issym() or member.islnk():raise UserError('npm archive contains an unsafe path.')
                    if not member.isfile():continue
                    total[0]+=member.size
                    if total[0]>100_000_000 or member.size>5_000_000:raise UserError('Plugin dependency payload exceeds its size limit.')
                    target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(archive.extractfile(member).read())
            details={'manifest':m,'dependencies':{}};packages[folder]=details
            for dep,req in m.get('dependencies',{}).items():details['dependencies'][dep]=dependency(dep,req)
            return folder
        packages['source']={'manifest':manifest,'dependencies':{}}
        for name,requirement in manifest.get('dependencies',{}).items():packages['source']['dependencies'][name]=dependency(name,requirement)
        installed={'id':identity,'name':entry.get('name',identity),'version':entry.get('version',manifest.get('version','')),'enabled':True,'entry':'source/'+str(main.relative_to(source)),'packages':packages}
        # Atomic swap preserves the prior release until the complete install verifies.
        backup=directory()/(identity+'.previous')
        if backup.exists():shutil.rmtree(backup)
        if dest.exists():dest.rename(backup)
        try:package.rename(dest)
        except Exception:
            if backup.exists():backup.rename(dest)
            raise
        if backup.exists():shutil.rmtree(backup)
        c=config();c['installed'][identity]=installed;save(c)
    return installed

def download(url,limit):
    with Session().get(url,stream=True) as r:
        r.raise_for_status();output=bytearray()
        for chunk in r.iter_content(65536):
            output.extend(chunk)
            if len(output)>limit:raise UserError('Download exceeds its size limit.')
    return bytes(output)

def uninstall(identity):
    valid(identity);c=config();c['installed'].pop(identity,None)
    c['active']={k:v for k,v in c['active'].items() if not v.startswith(identity+':')}
    p=directory()/identity
    if p.exists():shutil.rmtree(p)
    save(c);return True

def enable(identity,value):
    c=config();c['installed'][valid(identity)]['enabled']=value=='true';save(c);return True

def select(kind,provider):
    if kind not in ('metadata','streaming','lyrics','dashboard','playlists','discovery'):raise UserError('Unsupported provider kind.')
    c=config()
    if provider:c['active'][kind]=provider
    else:c['active'].pop(kind,None)
    save(c);return True


def http(url, init_json='{}'):
    if urlparse(url).scheme!='https':raise UserError('Provider HTTP requests must use HTTPS.')
    init=json.loads(init_json);headers=init.get('headers') or {}
    r=Session().request(init.get('method','GET'),url,headers=headers,data=init.get('body'))
    if len(r.content)>10_000_000:raise UserError('Provider response is too large.')
    return {'status':r.status_code,'headers':dict(r.headers),'body':r.text}
