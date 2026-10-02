#!/usr/bin/env bash
set -euo pipefail
project_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
jd_dir=${1:-"$HOME/.var/app/org.jdownloader.JDownloader/data/jdownloader"}
mkdir -p "$project_dir/.local-build/provided"
for jd_jar in Core JDownloader; do
    cp -- "$jd_dir/$jd_jar.jar" "$project_dir/.local-build/provided/$jd_jar.jar"
done
for jd_jar in JDUtils JDGUI jna jna_platform; do
    cp -- "$jd_dir/libs/$jd_jar.jar" "$project_dir/.local-build/provided/$jd_jar.jar"
done
cd -- "$project_dir"
mvn -Dmaven.repo.local=.local-build/m2 package
