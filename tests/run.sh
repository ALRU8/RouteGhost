#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p /tmp/routeghost-unit-classes
javac -d /tmp/routeghost-unit-classes \
  tests/stubs/org/json/*.java \
  overlay/app/organicmaps/routeghost/RouteData.java \
  overlay/app/organicmaps/routeghost/SimulationEngine.java \
  tests/RunEngineTests.java
java -cp /tmp/routeghost-unit-classes RunEngineTests
python3 -m py_compile apply_patch.py
python3 tests/test_patch_geometry.py
python3 tests/test_java_ui.py
