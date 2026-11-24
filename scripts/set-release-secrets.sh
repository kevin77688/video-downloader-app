#!/usr/bin/env bash
# Upload the signing key from keystore.properties to the repo's GitHub Actions secrets, so the
# Release workflow signs APKs with the same certificate as local builds. Values are piped straight
# into `gh secret set` and never printed.
#
#   scripts/set-release-secrets.sh [owner/repo]
#
# Uses whatever account `gh` is authenticated as; set GH_TOKEN to pick a specific one.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
props=keystore.properties
[ -f "$props" ] || { echo "error: $props not found" >&2; exit 1; }
repo_args=()
[ -n "${1:-}" ] && repo_args=(--repo "$1")

prop() { sed -n "s/^$1=//p" "$props" | head -1; }
store_file=$(prop storeFile)
[ -f "$store_file" ] || { echo "error: keystore '$store_file' not found" >&2; exit 1; }

base64 < "$store_file" | tr -d '\n' | gh secret set KEYSTORE_BASE64 "${repo_args[@]}"
prop storePassword | tr -d '\n' | gh secret set KEYSTORE_PASSWORD "${repo_args[@]}"
prop keyAlias | tr -d '\n' | gh secret set KEY_ALIAS "${repo_args[@]}"
prop keyPassword | tr -d '\n' | gh secret set KEY_PASSWORD "${repo_args[@]}"
echo "Signing secrets uploaded: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD"
