#!/usr/bin/env bash
# Stores the upload key in the repository's Actions secrets, so a v* tag builds the
# signed release in CI (.github/workflows/release.yml, design §16.4).
#
#   scripts/set-release-secrets.sh [path-to-upload-keystore]
#
# Sets UPLOAD_KEYSTORE_B64 (the keystore, base64), UPLOAD_KEY_ALIAS, UPLOAD_KEYSTORE_PASSWORD
# and UPLOAD_KEY_PASSWORD with the GitHub CLI. Passwords are read without echo and piped
# to `gh`, never put on a command line or written to a file.
set -euo pipefail
keystore="${1:-$HOME/keys/tamilscripture-upload.jks}"
[ -f "$keystore" ] || { echo "No keystore at $keystore" >&2; exit 1; }
repo="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
echo "Setting the upload-key secrets of $repo from $keystore"

read -rp "Key alias [upload]: " alias
alias="${alias:-upload}"
read -rsp "Keystore password: " store_pw; echo
read -rsp "Key password (Enter if the same): " key_pw; echo
key_pw="${key_pw:-$store_pw}"

# Check the passwords open the key before storing them, so CI doesn't fail on a typo.
keytool="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
printf '%s\n' "$store_pw" | "$keytool" -list -keystore "$keystore" -alias "$alias" >/dev/null 2>&1 \
  || { echo "The keystore password or alias is wrong; nothing was stored." >&2; exit 1; }

base64 -w0 "$keystore" | gh secret set UPLOAD_KEYSTORE_B64
printf '%s' "$alias" | gh secret set UPLOAD_KEY_ALIAS
printf '%s' "$store_pw" | gh secret set UPLOAD_KEYSTORE_PASSWORD
printf '%s' "$key_pw" | gh secret set UPLOAD_KEY_PASSWORD
unset store_pw key_pw
gh secret list
