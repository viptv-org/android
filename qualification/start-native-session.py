#!/usr/bin/env python3
"""Sign into the actual isolated backend through native phone/TV UI only."""
import argparse
import json
from pathlib import Path
import re
import ssl
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', choices=['emulator-5574', 'emulator-5576'], required=True)
    parser.add_argument('--config', type=Path, required=True)
    args = parser.parse_args()
    config = json.loads(args.config.read_text())
    adb = ['/home/vynxc/Android/Sdk/platform-tools/adb', '-s', args.serial]

    def device(*values):
        try:
            return subprocess.check_output(adb + list(values), text=True, timeout=20, stderr=subprocess.PIPE)
        except subprocess.SubprocessError:
            raise RuntimeError('Native UI operation failed; fixture credentials are not included in diagnostics') from None

    def tree():
        for attempt in range(3):
            try:
                device('shell', 'uiautomator', 'dump', '/sdcard/viptv-session.xml')
                break
            except RuntimeError:
                if attempt == 2:
                    raise
                time.sleep(.3)
        return ET.fromstring(device('exec-out', 'cat', '/sdcard/viptv-session.xml'))

    def labels(root):
        return [n.get('text') or n.get('content-desc') or '' for n in root.iter('node')]

    def wait(label, timeout=45):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            root = tree()
            if label in labels(root):
                return root
            time.sleep(.2)
        raise AssertionError('Native session did not reach expected public state')

    def tap(label):
        root = wait(label)
        node = next(n for n in root.iter('node') if n.get('text') == label or n.get('content-desc') == label)
        parents = {child: parent for parent in root.iter() for child in parent}
        while node.get('clickable') != 'true' and node in parents:
            node = parents[node]
        left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
        device('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))

    device('shell', 'am', 'start', '-n', 'org.viptv.app/.MainActivity', '--es', 'preview-origin', config['origin'])
    deadline = time.monotonic() + 45
    while True:
        existing = labels(tree())
        if any(label in existing for label in ['Home', 'Who’s watching?', 'Username', 'Sign in to VIPTV']):
            break
        assert time.monotonic() < deadline, 'Native startup did not reach an authentication state'
    if 'Home' in existing:
        print('PASS: retained authenticated native session')
        return
    if 'Who’s watching?' in existing:
        pass
    elif args.serial == 'emulator-5574':
        wait('Username')
        for label, value in [('Username', config['username']), ('Password', config['password'])]:
            assert re.fullmatch(r'[A-Za-z0-9_-]+', value), 'Fixture text must not require shell escaping'
            tap(label)
            device('shell', 'input', 'text', value)
        device('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        tap('Sign in')
    else:
        wait('Sign in to VIPTV')
        request = urllib.request.Request(config['control_origin'] + '/__control',
            data=json.dumps({'approvePairing': True}).encode(), headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, context=ssl.create_default_context(cafile=config['ca']), timeout=10) as response:
            assert response.status == 200
    root = wait('Who’s watching?')
    assert config['profile_name'] in labels(root)
    if args.serial == 'emulator-5574':
        tap(config['profile_name'])
    else:
        for _ in range(30):
            root = tree()
            focused = next((n for n in root.iter('node') if n.get('focused') == 'true'), None)
            if focused is not None and config['profile_name'] in labels(focused):
                device('shell', 'input', 'keyevent', 'KEYCODE_DPAD_CENTER')
                break
            device('shell', 'input', 'keyevent', 'KEYCODE_TAB')
        else:
            raise AssertionError('Configured profile was not remotely reachable')
    wait('Home')
    print('PASS: actual native authentication and server-confirmed profile selection')


if __name__ == '__main__':
    main()
