#!/usr/bin/env python3
"""Dependency-free, deterministic Xcode app project; VoiceCore is a local Swift package."""
from pathlib import Path
import hashlib
root = Path(__file__).resolve().parents[1]
def ident(s): return hashlib.sha256(s.encode()).hexdigest()[:24].upper()
objects = {}
def add(name, body): objects[ident(name)] = body; return ident(name)
files = []
app_sources = sorted(list((root/'App').glob('*.swift')) + list((root/'App').glob('*.m')))
for p in app_sources:
    filetype = 'sourcecode.c.objc' if p.suffix == '.m' else 'sourcecode.swift'
    ref = add(str(p.name), f'isa = PBXFileReference; lastKnownFileType = {filetype}; path = App/{p.name}; sourceTree = SOURCE_ROOT;')
    files.append(add('build'+p.name, f'isa = PBXBuildFile; fileRef = {ref};'))
product = add('product','isa = PBXFileReference; explicitFileType = wrapper.application; path = Voxkin.app; sourceTree = BUILT_PRODUCTS_DIR;')
package = add('package','isa = XCLocalSwiftPackageReference; relativePath = .;')
dep = add('dep',f'isa = XCSwiftPackageProductDependency; package = {package}; productName = VoiceCore;')
link = add('link',f'isa = PBXBuildFile; productRef = {dep};')
sources = add('sources','isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = ('+','.join(files)+'); runOnlyForDeploymentPostprocessing = 0;')
frameworks = add('frameworks',f'isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = ({link}); runOnlyForDeploymentPostprocessing = 0;')
assetRef = add('assets', 'isa = PBXFileReference; lastKnownFileType = folder.assetcatalog; path = App/Assets.xcassets; sourceTree = SOURCE_ROOT;')
assetBuild = add('assetBuild', f'isa = PBXBuildFile; fileRef = {assetRef};')
privacyRef = add('privacy', 'isa = PBXFileReference; lastKnownFileType = text.xml; path = App/PrivacyInfo.xcprivacy; sourceTree = SOURCE_ROOT;')
privacyBuild = add('privacyBuild', f'isa = PBXBuildFile; fileRef = {privacyRef};')
resources = add('resources',f'isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = ({assetBuild},{privacyBuild}); runOnlyForDeploymentPostprocessing = 0;')
group = add('group',f'isa = PBXGroup; children = ({product},{assetRef},'+','.join(ident(p.name) for p in app_sources)+'); sourceTree = "<group>";')
configs=[]; projectConfigs=[]
for mode in ('Debug','Release'):
    settings = '''ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon; ENABLE_TESTABILITY = YES; ONLY_ACTIVE_ARCH = YES; PRODUCT_BUNDLE_IDENTIFIER = com.iris.private.voice; PRODUCT_NAME = Voxkin; SWIFT_VERSION = 5.0; SWIFT_OBJC_BRIDGING_HEADER = "App/Voxkin-Bridging-Header.h"; CLANG_ENABLE_OBJC_ARC = YES; CLANG_ENABLE_OBJC_ARC_EXCEPTIONS = YES; IPHONEOS_DEPLOYMENT_TARGET = 17.0; TARGETED_DEVICE_FAMILY = 1; GENERATE_INFOPLIST_FILE = YES; INFOPLIST_FILE = App/Info.plist; INFOPLIST_KEY_CFBundleDisplayName = "Voxkin"; INFOPLIST_KEY_NSMicrophoneUsageDescription = "Dictate a message locally, then review before sending to your gateway."; INFOPLIST_KEY_NSSpeechRecognitionUsageDescription = "Transcribe your voice using on-device recognition only."; INFOPLIST_KEY_UIApplicationSceneManifest_Generation = YES; INFOPLIST_KEY_UILaunchScreen_Generation = YES; INFOPLIST_KEY_UISupportedInterfaceOrientations = UIInterfaceOrientationPortrait; CODE_SIGN_STYLE = Automatic; CURRENT_PROJECT_VERSION = 13; MARKETING_VERSION = 1.0; SDKROOT = iphoneos; SUPPORTED_PLATFORMS = "iphoneos iphonesimulator"; SUPPORTS_MACCATALYST = NO;'''
    configs.append(add('app'+mode,f'isa = XCBuildConfiguration; name = {mode}; buildSettings = {{ {settings} }};'))
    projectConfigs.append(add('project'+mode,f'isa = XCBuildConfiguration; name = {mode}; buildSettings = {{ CLANG_ENABLE_MODULES = YES; SWIFT_OPTIMIZATION_LEVEL = "'+ ('-Onone' if mode=='Debug' else '-O') +'"; };'))
def configlist(name, values): return add(name, 'isa = XCConfigurationList; buildConfigurations = ('+','.join(values)+'); defaultConfigurationIsVisible = 0; defaultConfigurationName = Debug;')
appConfig=configlist('appconfigs',configs); projConfig=configlist('projconfigs',projectConfigs)
target=add('target',f'isa = PBXNativeTarget; buildConfigurationList = {appConfig}; buildPhases = ({sources},{frameworks},{resources}); buildRules = (); dependencies = (); name = Voxkin; productName = Voxkin; productReference = {product}; productType = "com.apple.product-type.application"; packageProductDependencies = ({dep});')
testRef=add('testRef','isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = UITests/VoxkinUITests.swift; sourceTree = SOURCE_ROOT;')
testBuild=add('testBuild',f'isa = PBXBuildFile; fileRef = {testRef};')
testSources=add('testSources',f'isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = ({testBuild}); runOnlyForDeploymentPostprocessing = 0;')
testProduct=add('testProduct','isa = PBXFileReference; explicitFileType = wrapper.cfbundle; path = VoxkinUITests.xctest; sourceTree = BUILT_PRODUCTS_DIR;')
testConfigs=[]
for mode in ('Debug','Release'):
    testConfigs.append(add('test'+mode,f'isa = XCBuildConfiguration; name = {mode}; buildSettings = {{ PRODUCT_BUNDLE_IDENTIFIER = studio.builderofthings.voxkin.uitests; PRODUCT_NAME = VoxkinUITests; SWIFT_VERSION = 5.0; IPHONEOS_DEPLOYMENT_TARGET = 17.0; TARGETED_DEVICE_FAMILY = 1; GENERATE_INFOPLIST_FILE = YES; SDKROOT = iphoneos; TEST_TARGET_NAME = Voxkin; CODE_SIGN_STYLE = Automatic; }};'))
testConfig=configlist('testconfigs',testConfigs)
testDependency=add('testDependency',f'isa = PBXTargetDependency; target = {target};')
testTarget=add('testTarget',f'isa = PBXNativeTarget; buildConfigurationList = {testConfig}; buildPhases = ({testSources}); buildRules = (); dependencies = ({testDependency}); name = VoxkinUITests; productName = VoxkinUITests; productReference = {testProduct}; productType = "com.apple.product-type.bundle.ui-testing";')
unitRef=add('unitRef','isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = AppTests/VoiceModelTests.swift; sourceTree = SOURCE_ROOT;')
unitBuild=add('unitBuild',f'isa = PBXBuildFile; fileRef = {unitRef};')
nativeUnitBuilds = []
for p in sorted((root/'AppTests').glob('*.m')):
    ref = add('test-'+p.name, f'isa = PBXFileReference; lastKnownFileType = sourcecode.c.objc; path = AppTests/{p.name}; sourceTree = SOURCE_ROOT;')
    nativeUnitBuilds.append(add('test-build-'+p.name, f'isa = PBXBuildFile; fileRef = {ref};'))
unitSources=add('unitSources',f'isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = ({unitBuild},{",".join(nativeUnitBuilds)}); runOnlyForDeploymentPostprocessing = 0;')
unitProduct=add('unitProduct','isa = PBXFileReference; explicitFileType = wrapper.cfbundle; path = VoxkinTests.xctest; sourceTree = BUILT_PRODUCTS_DIR;')
unitConfigs=[]
for mode in ('Debug','Release'):
    unitConfigs.append(add('unit'+mode,f'isa = XCBuildConfiguration; name = {mode}; buildSettings = {{ CLANG_ENABLE_OBJC_ARC = YES; CLANG_ENABLE_OBJC_ARC_EXCEPTIONS = YES; PRODUCT_BUNDLE_IDENTIFIER = studio.builderofthings.voxkin.tests; PRODUCT_NAME = VoxkinTests; SWIFT_VERSION = 5.0; IPHONEOS_DEPLOYMENT_TARGET = 17.0; TARGETED_DEVICE_FAMILY = 1; GENERATE_INFOPLIST_FILE = YES; SDKROOT = iphoneos; TEST_HOST = "$(BUILT_PRODUCTS_DIR)/Voxkin.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/Voxkin"; BUNDLE_LOADER = "$(TEST_HOST)"; CODE_SIGN_STYLE = Automatic; }};'))
unitConfig=configlist('unitconfigs',unitConfigs)
unitTarget=add('unitTarget',f'isa = PBXNativeTarget; buildConfigurationList = {unitConfig}; buildPhases = ({unitSources}); buildRules = (); dependencies = ({testDependency}); name = VoxkinTests; productName = VoxkinTests; productReference = {unitProduct}; productType = "com.apple.product-type.bundle.unit-test";')
project=add('project',f'isa = PBXProject; attributes = {{ LastUpgradeCheck = 2700; }}; buildConfigurationList = {projConfig}; compatibilityVersion = "Xcode 14.0"; developmentRegion = en; hasScannedForEncodings = 0; knownRegions = (en,Base); mainGroup = {group}; projectDirPath = ""; projectRoot = ""; targets = ({target},{testTarget},{unitTarget}); packageReferences = ({package});')
out=root/'Voxkin.xcodeproj'; out.mkdir(exist_ok=True)
(out/'project.pbxproj').write_text('// !$*UTF8*$!\n{ archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n'+ '\n'.join(k+' = { '+v+' };' for k,v in objects.items())+'\n}; rootObject = '+project+'; }\n')
print('Generated Voxkin.xcodeproj')

schemes=out/'xcshareddata'/'xcschemes'; schemes.mkdir(parents=True,exist_ok=True)
def buildable(targetID, name, product): return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{targetID}" BuildableName="{product}" BlueprintName="{name}" ReferencedContainer="container:Voxkin.xcodeproj"/>'
(schemes/'Voxkin.xcscheme').write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="2700" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{buildable(target,'Voxkin','Voxkin.app')}</BuildActionEntry></BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO">{buildable(testTarget,'VoxkinUITests','VoxkinUITests.xctest')}</TestableReference><TestableReference skipped="NO">{buildable(unitTarget,'VoxkinTests','VoxkinTests.xctest')}</TestableReference></Testables></TestAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{buildable(target,'Voxkin','Voxkin.app')}</BuildableProductRunnable></LaunchAction>
<ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{buildable(target,'Voxkin','Voxkin.app')}</BuildableProductRunnable></ProfileAction>
<AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/></Scheme>''')
