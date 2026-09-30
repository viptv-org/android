#!/usr/bin/env python3
"""Finite public-UI lifecycle check on a signed-in isolated native QA emulator.

The private config supplies control_origin and ca for an owned fault proxy in
front of the actual backend. Controls affect transport only, not API payloads.
Start on a stable signed-in route with no other background requests pending.
"""
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
    parser.add_argument('--artifacts', type=Path, required=True)
    parser.add_argument('--phase', choices=['basic', 'extended', 'rotation', 'revoke'], default='basic')
    args = parser.parse_args()
    config = json.loads(args.config.read_text())
    tls = ssl.create_default_context(cafile=config['ca'])
    adb = ['/home/vynxc/Android/Sdk/platform-tools/adb', '-s', args.serial]
    args.artifacts.mkdir(mode=0o700, parents=True, exist_ok=True)

    def device(*values, binary=False):
        return subprocess.check_output(adb + list(values), text=not binary, timeout=20)

    def tree():
        for attempt in range(3):
            try:
                device('shell', 'uiautomator', 'dump', '/sdcard/viptv-foreground.xml')
                break
            except subprocess.CalledProcessError:
                if attempt == 2:
                    raise
                time.sleep(.3)
        raw = device('exec-out', 'cat', '/sdcard/viptv-foreground.xml')
        result = ET.fromstring(raw)
        assert any(n.get('package') == 'org.viptv.app' for n in result.iter('node')), 'VIPTV must be foreground'
        return result, raw

    def texts(root):
        return [n.get('text') or n.get('content-desc') or '' for n in root.iter('node')]

    def focus(root):
        return [(texts(n), n.get('bounds')) for n in root.iter('node') if n.get('focused') == 'true']

    def capture(name):
        root, raw = tree()
        (args.artifacts / (name + '.xml')).write_text(raw)
        (args.artifacts / (name + '.png')).write_bytes(device('exec-out', 'screencap', '-p', binary=True))
        return root

    def control(value=None):
        request = urllib.request.Request(config['control_origin'] + '/__control',
            data=None if value is None else json.dumps(value).encode(), headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, context=tls, timeout=5) as response:
            return json.load(response)

    def wait(predicate, timeout=40):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if predicate():
                return
            time.sleep(.2)
        raise AssertionError('Expected lifecycle outcome did not arrive')

    def background_return():
        activities = device('shell', 'dumpsys', 'activity', 'activities')
        task = re.search(r'topResumedActivity=.*org\.viptv\.app/\.MainActivity t(\d+)', activities).group(1)
        device('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        wait(lambda: bool(re.search(r'mActivityComponent=org\.viptv\.app/\.MainActivity[\s\S]*?state=STOPPED',
            device('shell', 'dumpsys', 'activity', 'activities'))), 15)
        device('shell', 'am', 'task', 'focus', task)

    def activate(label):
        if args.serial == 'emulator-5576':
            for _ in range(40):
                root, _ = tree()
                focused = next((n for n in root.iter('node') if n.get('focused') == 'true'), None)
                if focused is not None and label in texts(focused):
                    device('shell', 'input', 'keyevent', 'KEYCODE_DPAD_CENTER')
                    return
                device('shell', 'input', 'keyevent', 'KEYCODE_TAB')
            raise AssertionError('TV action was not keyboard/remote reachable: ' + label)
        root, _ = tree()
        node = next(n for n in root.iter('node') if n.get('text') == label or n.get('content-desc') == label)
        parents = {child: parent for parent in root.iter() for child in parent}
        while node.get('clickable') != 'true' and node in parents:
            node = parents[node]
        assert node.get('clickable') == 'true'
        left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
        assert right - left >= 44 and bottom - top >= 44
        device('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))

    initial = capture('foreground-before')
    assert not any(value in ['Sign in to VIPTV', 'Who’s watching?'] for value in texts(initial))
    if args.phase == 'rotation':
        before = control({'failIdentityOnce401': True, 'rejectRefresh401': False, 'delayIdentity': 0, 'delayRefreshResponse': 10000})
        background_return()
        wait(lambda: control()['successfulRefreshes'] > before['successfulRefreshes'], 10)
        committed = capture('foreground-rotation-committed')
        assert not any(value in ['Sign in to VIPTV', 'Who’s watching?', 'Try again'] for value in texts(committed))
        background_return()
        time.sleep(12)
        restored = capture('foreground-rotation-returned')
        assert not any(value in ['Sign in to VIPTV', 'Who’s watching?', 'Try again'] for value in texts(restored)), 'Canceled accepted refresh discarded the usable grant'
        assert config['profile_name'] + ' profile' in texts(restored)
        assert control()['pairingStarts'] == before['pairingStarts']
        assert control()['successfulRefreshes'] == before['successfulRefreshes'] + 1, 'Accepted rotation was repeated'
        control({'delayRefreshResponse': 0, 'failIdentityOnce401': False})
        print('PASS: accepted real token rotation survives native cancellation and return')
        return
    if args.phase == 'extended':
        before = control({'failIdentityOnce401': True, 'rejectRefresh401': False, 'delayIdentity': 0})
        background_return()
        wait(lambda: not control()['failIdentityOnce401'], 10)
        time.sleep(3)
        refreshed = capture('foreground-token-refreshed')
        assert not any(value in ['Sign in to VIPTV', 'Who’s watching?', 'Try again'] for value in texts(refreshed))
        assert focus(refreshed) == focus(initial)
        assert control()['pairingStarts'] == before['pairingStarts']

        before = control({'delayIdentity': 12000})
        background_return()
        wait(lambda: control()['identityCalls'] > before['identityCalls'], 10)
        activate('Search')
        wait(lambda: 'Search movies and series' in texts(tree()[0]), 10)
        capture('foreground-canceled-search')
        control({'delayIdentity': 0})
        time.sleep(13)
        assert 'Search movies and series' in texts(capture('foreground-late-search'))
        device('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        if args.serial == 'emulator-5574' and 'Home' not in texts(tree()[0]):
            device('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        wait(lambda: 'Home' in texts(tree()[0]), 10)

        before = control({'delayIdentity': 12000})
        background_return()
        wait(lambda: control()['identityCalls'] > before['identityCalls'], 10)
        activate(config['profile_name'] + ' profile')
        wait(lambda: 'Who’s watching?' in texts(tree()[0]), 10)
        control({'delayIdentity': 0})
        activate(config['alternate_profile_name'])
        wait(lambda: config['alternate_profile_name'] + ' profile' in texts(tree()[0]), 20)
        time.sleep(13)
        assert config['alternate_profile_name'] + ' profile' in texts(capture('foreground-replaced-profile'))
        activate(config['alternate_profile_name'] + ' profile')
        wait(lambda: 'Who’s watching?' in texts(tree()[0]), 10)
        activate(config['profile_name'])
        wait(lambda: config['profile_name'] + ' profile' in texts(tree()[0]), 20)
        control({'delayIdentity': 0})
        print('PASS: actual authenticated refresh, canceled route work and late profile-response rejection')
        return
    if args.phase == 'revoke':
        control({'failIdentityOnce401': True, 'rejectRefresh401': True, 'delayIdentity': 0})
        background_return()
        wait(lambda: 'Sign in to VIPTV' in texts(tree()[0]), 20)
        expired = capture('foreground-session-expired')
        assert config['profile_name'] + ' profile' not in texts(expired)
        assert 'Your session expired. Sign in again.' in texts(expired)
        control({'rejectRefresh401': False, 'failIdentityOnce401': False})
        print('PASS: actual rejected refresh clears protected presentation to sign-in')
        return
    before = control({'delayIdentity': 6000, 'offlineIdentity': False})
    background_return()
    wait(lambda: control()['identityCalls'] > before['identityCalls'], 10)
    pending = capture('foreground-pending')
    assert not any(value in ['Sign in to VIPTV', 'Who’s watching?', 'Try again'] for value in texts(pending))
    assert focus(pending) == focus(initial), 'Pending validation changed the focused control'
    assert not any(n.get('class') == 'android.widget.ProgressBar' for n in pending.iter('node'))
    time.sleep(6)
    restored = capture('foreground-restored')
    assert not any('Could not reconnect' in value for value in texts(restored))
    assert focus(restored) == focus(initial), 'Successful validation changed the focused control'
    assert control()['pairingStarts'] == before['pairingStarts'], 'Foreground restarted pairing'

    control({'delayIdentity': 35000})
    background_return()
    wait(lambda: 'Try again' in texts(tree()[0]), 40)
    failed = capture('foreground-timeout')
    assert 'Could not reconnect to VIPTV. Try again.' in texts(failed)
    control({'delayIdentity': 0})
    activate('Try again')
    wait(lambda: 'Try again' not in texts(tree()[0]), 10)
    capture('foreground-retried')
    assert control()['pairingStarts'] == before['pairingStarts']
    control({'delayIdentity': 0, 'offlineIdentity': False})
    print('PASS: silent foreground validation, bounded failure, native Retry, no repeated pairing')


if __name__ == '__main__':
    main()
