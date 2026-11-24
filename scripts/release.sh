#!/usr/bin/env bash
# Cut a release: bump appVersion, commit, create an annotated tag, optionally push.
#
#   scripts/release.sh 2.1.0            # stable release  -> tag v2.1.0
#   scripts/release.sh 2.1.0-rc.1       # pre-release     -> tag v2.1.0-rc.1
#   scripts/release.sh 2.1.0 --push     # also push the commit + tag (triggers .github/workflows/release.yml)
#
# Rules (see CLAUDE.md "Versioning & tags"): tags are only created by this script, from a clean
# main branch, and the new version must sort after the current one (versionCode must increase).
set -euo pipefail

die() { echo "error: $*" >&2; exit 1; }

version="${1:-}"
push=false
[ "${2:-}" = "--push" ] && push=true
[ -n "$version" ] || die "usage: scripts/release.sh <X.Y.Z | X.Y.Z-rc.N> [--push]"

cd "$(git rev-parse --show-toplevel)"
props=gradle.properties

# Same formula as app/build.gradle.kts: X.Y.Z-rc.N -> X*1e6 + Y*1e4 + Z*100 + N ; X.Y.Z -> ... + 99
version_code() {
  local re='^([0-9]+)\.([0-9]+)\.([0-9]+)(-rc\.([0-9]+))?$'
  [[ "$1" =~ $re ]] || return 1
  local major=${BASH_REMATCH[1]} minor=${BASH_REMATCH[2]} patch=${BASH_REMATCH[3]} rc=${BASH_REMATCH[5]:-}
  (( 10#$minor < 100 && 10#$patch < 100 )) || return 1
  if [ -n "$rc" ]; then (( 10#$rc >= 1 && 10#$rc <= 98 )) || return 1; else rc=99; fi
  echo $(( 10#$major * 1000000 + 10#$minor * 10000 + 10#$patch * 100 + 10#$rc ))
}

new_code=$(version_code "$version") || die "'$version' is not X.Y.Z or X.Y.Z-rc.N (minor/patch < 100, rc 1..98)"
current=$(sed -n 's/^appVersion=//p' "$props")
current_code=$(version_code "$current") || die "current appVersion '$current' in $props is invalid"
(( new_code > current_code )) || die "$version must be newer than current $current"

branch=$(git rev-parse --abbrev-ref HEAD)
[ "$branch" = "main" ] || die "releases are cut from main (on '$branch')"
[ -z "$(git status --porcelain)" ] || die "working tree is not clean — commit or stash first"
tag="v$version"
git rev-parse -q --verify "refs/tags/$tag" >/dev/null && die "tag $tag already exists"
if git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1; then die "tag $tag already exists on origin"; fi

changelog=CHANGELOG.md
unreleased_lines=$(awk '/^## \[Unreleased\]/{f=1; next} /^## \[/{f=0} f && NF' "$changelog" | wc -l | tr -d ' ')
(( unreleased_lines > 0 )) || die "$changelog has nothing under '## [Unreleased]' — describe the changes first"

perl -pi -e "s/^appVersion=.*/appVersion=$version/" "$props"
git add "$props"
# Stable releases move the Unreleased notes under a dated version heading; pre-releases leave them.
if [[ "$version" != *-rc.* ]]; then
  perl -pi -e "s/^## \\[Unreleased\\]\$/## [Unreleased]\n\n## [$version] - $(date +%Y-%m-%d)/" "$changelog"
  git add "$changelog"
fi
git commit -q -m "chore(release): $tag"
git tag -a "$tag" -m "Release $tag"
echo "Created commit $(git rev-parse --short HEAD) and tag $tag (versionCode $new_code)."

if $push; then
  git push --atomic origin HEAD "$tag"
  echo "Pushed. The Release workflow will build, sign and publish $tag."
else
  echo "Not pushed. To publish:  git push --atomic origin HEAD $tag"
  echo "To undo:                 git tag -d $tag && git reset --hard HEAD~1"
fi
