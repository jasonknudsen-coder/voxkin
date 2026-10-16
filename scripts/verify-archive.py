#!/usr/bin/env python3
"""Validate packaged mode, not just source plist; never reads signing secrets."""
import plistlib
import sys
from pathlib import Path

archive = Path(sys.argv[1])
carplay = len(sys.argv) > 2 and sys.argv[2] == '1'
app = archive / 'Products/Applications/Voxkin.app'
info = plistlib.loads((app / 'Info.plist').read_bytes())
assert info['CFBundleIdentifier'] == 'com.iris.private.voice'
assert (app / 'PrivacyInfo.xcprivacy').is_file()
roles = info.get('UIApplicationSceneManifest', {}).get('UISceneConfigurations', {})
role = 'CPTemplateApplicationSceneSessionRoleApplication'
if carplay:
    assert info.get('UIBackgroundModes') == ['audio']
    assert roles.get(role), 'Xcode erased CarPlay scene: disable scene-manifest generation for CarPlay build'
    assert roles[role][0]['UISceneDelegateClassName'] == 'Voxkin.CarPlaySceneDelegate'
else:
    assert not info.get('UIBackgroundModes'), 'Ordinary beta unexpectedly requests background audio'
    assert role not in roles, 'Ordinary beta unexpectedly registers CarPlay scene'
print('Archive metadata verified:', 'CarPlay' if carplay else 'ordinary phone')
