#!/bin/bash
# Builds/exports locally; never uploads, submits for review, or changes the gateway.
set -euo pipefail
repo_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
: "${VOXKIN_DEVELOPMENT_TEAM:?Set your Apple Developer team ID}"
export DEVELOPER_DIR=${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}
build_number=${VOXKIN_BUILD_NUMBER:-1}
release_dir=${VOXKIN_RELEASE_DIR:-"$repo_dir/.tmp/testflight"}
mkdir -p "$release_dir"
python3 "$repo_dir/scripts/generate-project.py"
carplay_flags=(INFOPLIST_FILE=App/Info.plist CODE_SIGN_ENTITLEMENTS=)
if [[ ${VOXKIN_CARPLAY:-0} == 1 ]]; then
  carplay_flags=(INFOPLIST_FILE=App/Info-CarPlay.plist CODE_SIGN_ENTITLEMENTS=App/CarPlay.entitlements INFOPLIST_KEY_UIApplicationSceneManifest_Generation=NO)
fi
xcodebuild -project "$repo_dir/Voxkin.xcodeproj" -scheme Voxkin -configuration Release \
  -destination generic/platform=iOS -archivePath "$release_dir/Voxkin.xcarchive" \
  DEVELOPMENT_TEAM="$VOXKIN_DEVELOPMENT_TEAM" CURRENT_PROJECT_VERSION="$build_number" \
  "${carplay_flags[@]}" -allowProvisioningUpdates archive
python3 "$repo_dir/scripts/verify-archive.py" "$release_dir/Voxkin.xcarchive" "${VOXKIN_CARPLAY:-0}"
python3 - "$release_dir/ExportOptions.plist" "$VOXKIN_DEVELOPMENT_TEAM" <<'PY'
import plistlib,sys
with open(sys.argv[1],'wb') as f:
    plistlib.dump({'method':'app-store-connect','destination':'export','signingStyle':'automatic','teamID':sys.argv[2],'stripSwiftSymbols':True,'uploadSymbols':True},f)
PY
xcodebuild -exportArchive -archivePath "$release_dir/Voxkin.xcarchive" \
  -exportPath "$release_dir/export" -exportOptionsPlist "$release_dir/ExportOptions.plist" \
  -allowProvisioningUpdates
