#!/usr/bin/env bash
set -euo pipefail

python3 .github/scripts/generate-startup-profiles.py app/build/outputs/apk/profileGeneration/app-profileGeneration.apk
bash gradlew assemblePerformance bundlePerformance --stacktrace
python3 .github/scripts/verify-startup-profiles.py

mkdir -p app/build/profile-benchmark
python3 .github/scripts/benchmark-startup.py /tmp/til-profile-before.apk app/build/outputs/apk/performance/app-performance.apk --iterations 5
mv app/build/startup-benchmark app/build/profile-benchmark/uncompiled
python3 .github/scripts/benchmark-startup.py /tmp/til-profile-before.apk app/build/outputs/apk/performance/app-performance.apk --iterations 5 --compile-profile
mv app/build/startup-benchmark app/build/profile-benchmark/compiled

