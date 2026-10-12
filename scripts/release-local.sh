#!/usr/bin/env bash
# Builds the signed release bundle (.aab) on this machine, for uploading to the Play
# Console by hand (design §16.4 does the same in CI once the upload-key secrets are set).
#
#   scripts/release-local.sh [path-to-upload-keystore]
#
# The version comes from version.properties. The keystore defaults to
# ~/keys/tamilscripture-upload.jks; its alias and passwords are asked for here and never
# written down. The native library should be built first from the pinned website commit
# (scripts/build-rust.sh with a checkout of rust/website.ref).
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
cd "$here"

export TS_KEYSTORE="${1:-$HOME/keys/tamilscripture-upload.jks}"
[ -f "$TS_KEYSTORE" ] || { echo "No keystore at $TS_KEYSTORE" >&2; exit 1; }
version="$(sed -n 's/^version=//p' version.properties | tr -d '\r')"
echo "Building v$version, signed with $TS_KEYSTORE"

read -rp "Key alias: " TS_KEY_ALIAS
read -rsp "Keystore password: " TS_KEYSTORE_PASSWORD; echo
read -rsp "Key password (Enter if the same): " TS_KEY_PASSWORD; echo
TS_KEY_PASSWORD="${TS_KEY_PASSWORD:-$TS_KEYSTORE_PASSWORD}"
export TS_KEY_ALIAS TS_KEYSTORE_PASSWORD TS_KEY_PASSWORD

./gradlew --quiet :app:bundleRelease

aab="app/build/outputs/bundle/release/app-release.aab"
# The build falls back to the debug key when the upload key is missing; Play would reject that.
signer="$("${JAVA_HOME:+$JAVA_HOME/bin/}keytool" -printcert -jarfile "$aab" | sed -n 's/^Owner: //p' | head -1)"
case "$signer" in
  *"Android Debug"*|"") echo "$aab is not signed with the upload key ($signer)" >&2; exit 1 ;;
esac
mkdir -p release
cp "$aab" "release/tamilscripture-v$version.aab"
cp app/build/outputs/mapping/release/mapping.txt "release/mapping-v$version.txt"
echo "Signed by: $signer"
echo "Ready to upload: release/tamilscripture-v$version.aab (and release/mapping-v$version.txt for deobfuscation)"
