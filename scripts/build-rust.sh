#!/usr/bin/env bash
# Builds libts_mobile.so for every ABI and its Kotlin bindings (design §9, §16.2).
#
#   scripts/build-rust.sh [path-to-website-repo]
#
# Uses the website checkout given (default ../tamilscripture.com). CI checks out the
# commit pinned in rust/website.ref first. Needs: rustup targets for Android,
# cargo-ndk, and ANDROID_NDK_HOME (or the NDK under ANDROID_HOME).
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
web="${1:-$here/../tamilscripture.com}"
out="$here/core/rust/src/main"
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
  ANDROID_NDK_HOME="$(ls -d "$sdk"/ndk/* | sort -V | tail -1)"
  export ANDROID_NDK_HOME
fi
cd "$web"
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -P 26 -o "$out/jniLibs" build -p ts-mobile --release
rm -rf "$out/kotlin/uniffi"
cargo run -q -p ts-mobile --bin uniffi-bindgen -- generate \
  --library "$out/jniLibs/arm64-v8a/libts_mobile.so" --language kotlin --out-dir "$out/kotlin" --no-format
echo "ts-mobile built from $(git -C "$web" rev-parse --short HEAD)"
