#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
set -euo pipefail
mode=publish
if [ "${1:-}" = --validate ]; then
    mode=validate
    shift
fi
if (( $# > 1 )); then
    printf 'Usage: %s [--validate] [artifact directory]\n' "$0" >&2
    exit 2
fi
: "${GITHUB_REF:?}" "${GITHUB_SHA:?}" "${GITHUB_REPOSITORY:?}" "${RELEASE_TAG:?}"
test "$GITHUB_REF" = refs/heads/main || { echo 'Select main when running the release workflow.' >&2; exit 1; }
[[ "$RELEASE_TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+([.-][0-9A-Za-z]+)*)?$ ]] || {
    echo 'Use a version tag such as v0.1.0 or v0.1.0-beta.1.' >&2
    exit 1
}
tags=$(gh api "repos/$GITHUB_REPOSITORY/git/matching-refs/tags/$RELEASE_TAG")
python3 -c 'import json, os, sys; tags=json.load(sys.stdin); assert not any(t["ref"] == "refs/tags/" + os.environ["RELEASE_TAG"] for t in tags), "Release tag already exists"' <<< "$tags"
if [ "$mode" = validate ]; then
    exit 0
fi
assets=${1:-.local-build/ci/artifact}
(
    cd -- "$assets"
    sha256sum --check SHA256SUMS
    python3 -c 'import json, os; data=json.load(open("SourceBuild.json")); assert data["source_commit"] == os.environ["GITHUB_SHA"], "Artifact does not match the selected main commit"'
)
options=()
if [ "${PRERELEASE:-false}" = true ]; then
    options+=(--prerelease)
fi
gh release create "$RELEASE_TAG" "$assets/NativeTray.jar" "$assets/SHA256SUMS" "$assets/SourceBuild.json" \
    --repo "$GITHUB_REPOSITORY" --target "$GITHUB_SHA" --title "$RELEASE_TAG" --generate-notes "${options[@]}"
