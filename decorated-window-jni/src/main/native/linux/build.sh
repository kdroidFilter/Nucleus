#!/bin/bash
# Compiles nucleus_linux_window.c into per-architecture shared libraries (x64 + aarch64).
# The outputs are placed in the JAR resources so they ship with the library.
#
# Prerequisites: gcc, libX11-dev (or libx11-dev), JDK with JNI headers.
# Usage: ./build.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SCRIPT_DIR/nucleus_linux_window.c"
RESOURCE_DIR="$SCRIPT_DIR/../../resources/nucleus/native"
OUT_DIR_X64="$RESOURCE_DIR/linux-x64"
OUT_DIR_AARCH64="$RESOURCE_DIR/linux-aarch64"

# Detect JAVA_HOME for JNI headers
if [ -z "${JAVA_HOME:-}" ]; then
    # Try common locations
    for jdk in /usr/lib/jvm/java-*-openjdk-* /usr/lib/jvm/default-java; do
        if [ -d "$jdk/include" ]; then
            JAVA_HOME="$jdk"
            break
        fi
    done
fi
if [ -z "${JAVA_HOME:-}" ]; then
    echo "ERROR: JAVA_HOME not set and could not auto-detect a JDK." >&2
    exit 1
fi

JNI_INCLUDE="$JAVA_HOME/include"
JNI_INCLUDE_LINUX="$JAVA_HOME/include/linux"

if [ ! -d "$JNI_INCLUDE" ]; then
    echo "ERROR: JNI headers not found at $JNI_INCLUDE" >&2
    exit 1
fi

HOST_ARCH="$(uname -m)"

COMMON_FLAGS=(
    -shared
    -fPIC
    -I"$JNI_INCLUDE" -I"$JNI_INCLUDE_LINUX"
    -lX11
    -O2
    -fvisibility=hidden
    -s
    -Wall -Wextra -Wno-unused-parameter
)

# Build for the host architecture
if [ "$HOST_ARCH" = "x86_64" ]; then
    mkdir -p "$OUT_DIR_X64"
    gcc "${COMMON_FLAGS[@]}" \
        -o "$OUT_DIR_X64/libnucleus_linux_jni.so" "$SRC"
    echo "Built x64:"
    ls -lh "$OUT_DIR_X64/libnucleus_linux_jni.so"
elif [ "$HOST_ARCH" = "aarch64" ]; then
    mkdir -p "$OUT_DIR_AARCH64"
    gcc "${COMMON_FLAGS[@]}" \
        -o "$OUT_DIR_AARCH64/libnucleus_linux_jni.so" "$SRC"
    echo "Built aarch64:"
    ls -lh "$OUT_DIR_AARCH64/libnucleus_linux_jni.so"
else
    echo "WARNING: Unsupported host architecture: $HOST_ARCH" >&2
    exit 1
fi

# Attempt cross-compilation for the other architecture (optional, non-fatal)
if [ "$HOST_ARCH" = "x86_64" ]; then
    if command -v aarch64-linux-gnu-gcc &>/dev/null; then
        mkdir -p "$OUT_DIR_AARCH64"
        aarch64-linux-gnu-gcc "${COMMON_FLAGS[@]}" \
            -o "$OUT_DIR_AARCH64/libnucleus_linux_jni.so" "$SRC" || \
            echo "WARNING: aarch64 cross-compilation failed (non-fatal)."
        if [ -f "$OUT_DIR_AARCH64/libnucleus_linux_jni.so" ]; then
            echo "Built aarch64 (cross):"
            ls -lh "$OUT_DIR_AARCH64/libnucleus_linux_jni.so"
        fi
    else
        echo "NOTE: aarch64-linux-gnu-gcc not found, skipping aarch64 cross-build."
    fi
elif [ "$HOST_ARCH" = "aarch64" ]; then
    if command -v x86_64-linux-gnu-gcc &>/dev/null; then
        mkdir -p "$OUT_DIR_X64"
        x86_64-linux-gnu-gcc "${COMMON_FLAGS[@]}" \
            -o "$OUT_DIR_X64/libnucleus_linux_jni.so" "$SRC" || \
            echo "WARNING: x64 cross-compilation failed (non-fatal)."
        if [ -f "$OUT_DIR_X64/libnucleus_linux_jni.so" ]; then
            echo "Built x64 (cross):"
            ls -lh "$OUT_DIR_X64/libnucleus_linux_jni.so"
        fi
    else
        echo "NOTE: x86_64-linux-gnu-gcc not found, skipping x64 cross-build."
    fi
fi
