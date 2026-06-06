#!/usr/bin/env bash
# Offline unit-test runner for CurbRun's pure-JVM logic.
#
# CurbRun's unit tests exercise only the pure-Kotlin `domain` package and the
# `CurbAssetParsing` helper (no Android framework), so they can be compiled and
# run with the Kotlin compiler + JUnit jars that any local Gradle 8.x install
# already bundles -- no Android SDK, no network, and no working CI required.
# This is a convenience fallback; the canonical gate is
# `./gradlew testDebugUnitTest`.
#
# Usage: scripts/run_unit_tests_offline.sh
set -euo pipefail

# --- locate a Gradle lib directory that bundles the Kotlin compiler + JUnit ---
gradle_lib=""
if [[ -n "${GRADLE_HOME:-}" && -d "$GRADLE_HOME/lib" ]]; then
  gradle_lib="$GRADLE_HOME/lib"
elif command -v gradle >/dev/null 2>&1; then
  gradle_bin="$(readlink -f "$(command -v gradle)")"
  gradle_lib="$(dirname "$(dirname "$gradle_bin")")/lib"
fi
if [[ -z "$gradle_lib" || ! -d "$gradle_lib" ]]; then
  echo "error: could not find a Gradle lib dir; set GRADLE_HOME or put gradle on PATH" >&2
  exit 1
fi

pick() { ls "$gradle_lib"/$1 2>/dev/null | head -1; }
compiler="$(pick 'kotlin-compiler-embeddable-*.jar')"
stdlib="$(pick 'kotlin-stdlib-*.jar')"
junit="$(pick 'junit-4*.jar')"
hamcrest="$(pick 'hamcrest-core-*.jar')"
for name in compiler stdlib junit hamcrest; do
  if [[ -z "${!name}" ]]; then
    echo "error: could not find a $name jar in $gradle_lib (needs Gradle 8.x)" >&2
    exit 1
  fi
done

# Extra jars the embedded compiler needs on its own runtime classpath.
runcp="$compiler:$stdlib"
for extra in 'kotlin-reflect-*.jar' 'kotlin-script-runtime-*.jar' \
             'kotlinx-coroutines-core-jvm-*.jar' 'annotations-*.jar' \
             'trove4j-*.jar' 'fastutil-*-min.jar'; do
  jar="$(pick "$extra")"; [[ -n "$jar" ]] && runcp="$runcp:$jar"
done

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$(mktemp -d)"; trap 'rm -rf "$out"' EXIT
mkdir -p "$out/main" "$out/test"

main_src=$(find "$root/app/src/main/java/com/cuuper/sfpark/domain" \
  "$root/app/src/main/java/com/cuuper/sfpark/data/CurbAssetParsing.kt" \
  "$root/app/src/main/java/com/cuuper/sfpark/data/CurbRepository.kt" -name '*.kt')
test_src=$(find "$root/app/src/test/java/com/cuuper/sfpark" -name '*.kt')

kotlinc() { java -cp "$runcp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "$@"; }

echo "Compiling main (pure-JVM domain + parsing)..."
kotlinc -jvm-target 17 -no-stdlib -classpath "$stdlib" -d "$out/main" $main_src

echo "Compiling tests (-Xfriend-paths mirrors the AGP test/main friend setup)..."
kotlinc -jvm-target 17 -no-stdlib -Xfriend-paths="$out/main" \
  -classpath "$stdlib:$junit:$hamcrest:$out/main" -d "$out/test" $test_src

# Derive fully-qualified test class names from their source paths.
classes=$(echo "$test_src" | sed -e "s#^$root/app/src/test/java/##" -e 's#\.kt$##' -e 's#/#.#g')

echo "Running JUnit..."
java -cp "$out/main:$out/test:$stdlib:$junit:$hamcrest" org.junit.runner.JUnitCore $classes
