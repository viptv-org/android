#!/usr/bin/env bash
set -euo pipefail

air_repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
air_java_home="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
air_output_dir="$air_repo_dir/build/native/linux-x64"
air_output="$air_output_dir/libair_jawt_window.so"
air_compiler="${CC:-cc}"

test -f "$air_java_home/include/jni.h"
test -f "$air_java_home/include/jawt.h"
test -f "$air_java_home/include/linux/jawt_md.h"
mkdir -p "$air_output_dir"

"$air_compiler" \
    -std=c11 \
    -O2 \
    -fPIC \
    -fvisibility=hidden \
    -Wall \
    -Wextra \
    -Werror \
    -I"$air_java_home/include" \
    -I"$air_java_home/include/linux" \
    -shared \
    "$air_repo_dir/src/jvmMain/native/linux/air_jawt_window.c" \
    -L"$air_java_home/lib" \
    -Wl,-rpath,"$air_java_home/lib" \
    -ljawt \
    -o "$air_output"

printf '%s\n' "$air_output"
