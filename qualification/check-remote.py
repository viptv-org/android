"""Run from the Choose your TV screen on the dedicated HTTPS-fixture emulator."""
import json
from pathlib import Path
import re
import ssl
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
adb = ['/home/vynxc/Android/Sdk/platform-tools/adb', '-s', 'emulator-5574']
tls = ssl.create_default_context(cafile=str(root / 'fixtures/tls/server.crt'))

def device(*args):
    return subprocess.check_output(adb + list(args), text=True)

def tree():
    device('shell', 'uiautomator', 'dump', '/sdcard/viptv-remote-check.xml')
    result = ET.fromstring(device('exec-out', 'cat', '/sdcard/viptv-remote-check.xml'))
    assert any(n.get('package') == 'org.viptv.app' for n in result.iter('node')), 'VIPTV must be foreground'
    return result

def find(label, snapshot=None):
    snapshot = snapshot if snapshot is not None else tree()
    return next((n for n in snapshot.iter('node') if n.get('text') == label or n.get('content-desc') == label), None)

def position(node):
    x, y, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
    return str((x + right) // 2), str((y + bottom) // 2)

def tap(label):
    node = find(label)
    assert node is not None, f'Missing control: {label}'
    device('shell', 'input', 'tap', *position(node))

def wait(label):
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        if find(label) is not None:
            return
        time.sleep(.1)
    raise AssertionError(f'Did not reach {label}')

def control(value=None):
    request = urllib.request.Request('https://127.0.0.1:7345/__control',
        data=None if value is None else json.dumps(value).encode(), headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, context=tls, timeout=5) as response:
        return json.load(response)

def until(predicate):
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        state = control()
        if predicate(state): return state
        time.sleep(.1)
    raise AssertionError('Synthetic TV state did not reach expected result')

wait('Fixture TV')
tap('Fixture TV')
wait('Enter the PIN on your TV')
before = until(lambda s: s['pairing'])
activity = device('shell', 'dumpsys', 'activity', 'activities')
task = re.search(r'topResumedActivity=.*org\.viptv\.app/\.MainActivity t(\d+)', activity)
assert task, 'Could not identify the dedicated test activity task'
device('shell', 'input', 'keyevent', 'KEYCODE_HOME')
device('shell', 'am', 'task', 'focus', task.group(1))
wait('Enter the PIN on your TV')
assert control()['starts'] == before['starts'], 'Background return started a second pairing'
tap('Cancel')
wait('Choose your TV')
cancelled = until(lambda s: not s['pairing'])
assert cancelled['cancels'] > before['cancels']
tap('Fixture TV')
wait('Enter the PIN on your TV')
paired = until(lambda s: s['starts'] > before['starts'])
tap('New PIN')
until(lambda s: s['starts'] > paired['starts'] and s['cancels'] > paired['cancels'])
tap('4-digit PIN')
device('shell', 'input', 'text', '1234')
wait('Connected to Fixture TV')
tap('Open the remote')
wait('Open VIPTV on TV')
tap('Open VIPTV on TV')
wait('The TV accepted the launch request. Check its screen to confirm VIPTV opened.')
tap('Buttons')

control({'keyDelay': 3000})
before_keys = len(control()['commands'])
snapshot = tree()
positions = [position(find(label, snapshot)) for label in ['Up', 'Right', 'Down', 'Left']]
for point in positions: device('shell', 'input', 'tap', *point)
until(lambda s: len(s['commands']) > before_keys)
snapshot = tree()
node = find('Open VIPTV on TV', snapshot)
assert node is not None
parents = {child: parent for parent in snapshot.iter() for child in parent}
while node is not None:
    assert node.get('enabled', 'true') == 'true', 'Key traffic disabled/faded the launch button'
    node = parents.get(node)
assert find('The TV accepted the launch request. Check its screen to confirm VIPTV opened.', snapshot) is not None
control({'keyDelay': 0})
keys = until(lambda s: len(s['commands']) >= before_keys + 4)['commands'][before_keys:before_keys + 4]
assert [entry[0]['CODE'] for entry in keys] == [8, 7, 0, 1], 'Rapid keys were lost or reordered'
print('PASS: Wi-Fi discovery, background PIN retention, cancellation, New PIN, ordered keys and stable launch button')
