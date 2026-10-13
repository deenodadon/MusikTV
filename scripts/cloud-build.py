#!/usr/bin/env python3
"""Build in a prepared Codex cloud snapshot, preserving TLS verification."""
import os
from pathlib import Path
import subprocess
import sys
from urllib.parse import urlparse

root=Path(__file__).resolve().parents[1]
workspace=root.parent
env=dict(os.environ)
sdk=workspace/'tools/android-sdk'
jdk=workspace/'tools/jdk'
if sdk.exists(): env['ANDROID_HOME']=str(sdk)
if jdk.exists(): env['JAVA_HOME']=str(jdk)
if not env.get('ANDROID_HOME') and not (root/'local.properties').exists():
    sys.exit('Android SDK missing: install SDK 35 and build-tools 35.0.0, then set ANDROID_HOME.')
env['GRADLE_USER_HOME']=str(workspace/'.gradle')
env['ANDROID_USER_HOME']=str(workspace/'.android')
env['PIP_CACHE_DIR']=str(workspace/'.cache/pip')
for path in ['.gradle','.android','.cache/pip']:(workspace/path).mkdir(parents=True,exist_ok=True)
options=[]
proxy=urlparse(env.get('HTTPS_PROXY',''))
if proxy.hostname:
    for scheme in ['http','https']:
        options.extend([f'-D{scheme}.proxyHost={proxy.hostname}',f'-D{scheme}.proxyPort={proxy.port or 80}'])
trust=workspace/'tools/cacerts'
if trust.exists():options.extend([f'-Djavax.net.ssl.trustStore={trust}','-Djavax.net.ssl.trustStorePassword=changeit'])
env['JAVA_TOOL_OPTIONS']=' '.join([env.get('JAVA_TOOL_OPTIONS','')]+options)
args=sys.argv[1:] or ['assembleDebug','testDebugUnitTest','lintDebug']
sys.exit(subprocess.call([str(root/'gradlew')]+args,cwd=root,env=env))
