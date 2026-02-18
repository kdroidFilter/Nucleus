#!/bin/bash
# Compiles NucleusMacBridge.m into a universal (arm64 + x86_64) dylib.
# The output is placed in the JAR resources so it ships with the library.
#
# Prerequisites: Xcode command-line tools (clang).
# Usage: ./build.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SCRIPT_DIR/NucleusMacBridge.m"
OUT_DIR="$SCRIPT_DIR/../../resources/nucleus/native/darwin-universal"
OUT_LIB="$OUT_DIR/libnucleus_macos.dylib"

# Detect JAVA_HOME for JNI headers
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
fi
if [ -z "${JAVA_HOME:-}" ]; then
    echo "ERROR: JAVA_HOME not set and /usr/libexec/java_home failed." >&2
    exit 1
fi

JNI_INCLUDE="$JAVA_HOME/include"
JNI_INCLUDE_DARWIN="$JAVA_HOME/include/darwin"

if [ ! -d "$JNI_INCLUDE" ]; then
    echo "ERROR: JNI headers not found at $JNI_INCLUDE" >&2
    exit 1
fi

mkdir -p "$OUT_DIR"

TMPDIR_BUILD="$(mktemp -d)"
trap 'rm -rf "$TMPDIR_BUILD"' EXIT

# Compile for arm64
clang -arch arm64 \
    -dynamiclib \
    -o "$TMPDIR_BUILD/libnucleus_macos_arm64.dylib" \
    -I"$JNI_INCLUDE" -I"$JNI_INCLUDE_DARWIN" \
    -framework Cocoa \
    -mmacosx-version-min=10.13 \
    -fobjc-arc \
    "$SRC"

# Compile for x86_64
clang -arch x86_64 \
    -dynamiclib \
    -o "$TMPDIR_BUILD/libnucleus_macos_x86_64.dylib" \
    -I"$JNI_INCLUDE" -I"$JNI_INCLUDE_DARWIN" \
    -framework Cocoa \
    -mmacosx-version-min=10.13 \
    -fobjc-arc \
    "$SRC"

# Create universal binary
lipo -create \
    "$TMPDIR_BUILD/libnucleus_macos_arm64.dylib" \
    "$TMPDIR_BUILD/libnucleus_macos_x86_64.dylib" \
    -output "$OUT_LIB"

echo "Built universal dylib:"
file "$OUT_LIB"
