"""Start on the paired remote using the dedicated 5574 HTTPS fixtures only."""
import json
from pathlib import Path
import re
import ssl
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

adb = ['/home/vynxc/Android/Sdk/platform-tools/adb', '-s', 'emulator-5574']
tls = ssl.create_default_context(cafile=str(Path(__file__).parent / 'fixtures/tls/server.crt'))
def device(*args):
    return subprocess.check_output(adb + list(args), text=True)
def labels():
    device('shell', 'uiautomator', 'dump', '/sdcard/viptv-reconnect-check.xml')
    tree = ET.fromstring(device('exec-out', 'cat', '/sdcard/viptv-reconnect-check.xml'))
    assert any(n.get('package') == 'org.viptv.app' for n in tree.iter('node'))
    return [n.get('text') or n.get('content-desc') or '' for n in tree.iter('node')]
def control(value=None):
    request = urllib.request.Request('https://127.0.0.1:7345/__control', data=None if value is None else json.dumps(value).encode(), headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(request, context=tls, timeout=5) as response:
        return json.load(response)
def wait(text):
    deadline=time.monotonic()+20
    while time.monotonic()<deadline:
        if any(text in value for value in labels()): return
    raise AssertionError('Missing state: '+text)
task=re.search(r'topResumedActivity=.*org\.viptv\.app/\.MainActivity t(\d+)',device('shell','dumpsys','activity','activities')).group(1)
def resume():
    device('shell','input','keyevent','KEYCODE_HOME')
    deadline=time.monotonic()+15
    while time.monotonic()<deadline:
        if re.search(r'mActivityComponent=org\.viptv\.app/\.MainActivity[\s\S]*?state=STOPPED',device('shell','dumpsys','activity','activities')): break
        time.sleep(.2)
    else: raise AssertionError('Android did not complete background transition')
    device('shell','am','task','focus',task)

wait('Connected ·')
before=control({'authDelay':3000})
resume()
snapshot=labels()
assert 'Open VIPTV on TV' in snapshot
assert not any("Can't reach" in label or label=='Pair again' for label in snapshot), 'Premature offline recovery during reconnect'
wait('Connected ·')
assert control()['authChecks'] > before['authChecks'], 'Resume did not verify the TV'
assert control()['starts'] == before['starts'], 'Reconnect restarted pairing'
control({'offline':True,'authDelay':0})
resume()
wait("Can't reach Fixture TV")
failed=control()['authChecks']
time.sleep(1)
assert control()['authChecks']==failed, 'Offline verification retried without another lifecycle event'
control({'offline':False})
resume()
wait('Connected ·')
assert control()['starts'] == before['starts']
print('PASS: silent resume verification, preserved pairing, honest failure and automatic recovery')
