#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
sdk_tmp="$(mktemp -d)"
curl --fail --location --retry 3 --max-time 120 \
  https://cdn.cnbj3-fusion.fds.api.mi-img.com/quickapp-vela/interconnect_dev_test_demo.zip \
  --output "$sdk_tmp/demo.zip"
echo "8e3d74eebda558e2bef45e32f0965c5b77ef939a06cdc55136929f9427a95ca7  $sdk_tmp/demo.zip" | sha256sum --check
mkdir -p "$project_dir/android/app/libs"
unzip -p "$sdk_tmp/demo.zip" interconnect_dev_test_demo/libs/xms-wearable-lib_1.4_release.aar \
  > "$project_dir/android/app/libs/xms-wearable-lib_1.4_release.aar"
test -s "$project_dir/android/app/libs/xms-wearable-lib_1.4_release.aar"
