#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
set -euo pipefail
project_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
jar="$project_dir/jd2-adapter/target/NativeTray.jar"
dbus-run-session -- env JD_TRAY_PRIVATE_BUS=1 timeout 90s \
    java -Djava.awt.headless=true -cp "$jar" org.jdownloader.extensions.nativetray.ProtocolProbe
