#!/usr/bin/env bash
set -euo pipefail
project_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
# Compatibility shortcut; the Maven build now resolves JD2_INSTALL_DIR itself.
if (( $# > 1 )); then
    printf 'Usage: %s [JD2 installation directory]\n' "$0" >&2
    exit 2
fi
if (( $# == 1 )); then
    JD2_INSTALL_DIR=$(cd -- "$1" && pwd)
    export JD2_INSTALL_DIR
fi
cd -- "$project_dir"
exec mvn -Dmaven.repo.local="$project_dir/.local-build/m2" package
