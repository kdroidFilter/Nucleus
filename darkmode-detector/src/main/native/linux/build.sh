#!/bin/bash
# Compiles nucleus_linux_theme.c into a shared library for the host architecture.
# The output is placed in the JAR resources so it ships with the library.
#
# Prerequisites: gcc, libdbus-1-dev (apt-get install libdbus-1-dev)
# Usage: ./build.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SCRIPT_DIR/nucleus_linux_theme.c"
RESOURCE_DIR="$SCRIPT_DIR/../../resources/nucleus/native"

# Detect host architecture
MACHINE="$(uname -m)"
case "$MACHINE" in
    x86_64)  ARCH="x64" ;;
    aarch64) ARCH="aarch64" ;;
    *)
        echo "ERROR: Unsupported architecture: $MACHINE" >&2
        exit 1
        ;;
esac

OUT_DIR="$RESOURCE_DIR/linux-$ARCH"

# Detect JAVA_HOME for JNI headers
if [ -z "${JAVA_HOME:-}" ]; then
    for candidate in /usr/lib/jvm/java-21-openjdk-amd64 /usr/lib/jvm/java-21-openjdk-arm64 /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/default-java; do
        if [ -d "$candidate/include" ]; then
            JAVA_HOME="$candidate"
            break
        fi
    done
fi
if [ -z "${JAVA_HOME:-}" ]; then
    echo "ERROR: JAVA_HOME not set and could not auto-detect." >&2
    exit 1
fi

JNI_INCLUDE="$JAVA_HOME/include"
JNI_INCLUDE_LINUX="$JAVA_HOME/include/linux"

if [ ! -d "$JNI_INCLUDE" ]; then
    echo "ERROR: JNI headers not found at $JNI_INCLUDE" >&2
    exit 1
fi

# Get dbus-1 compiler flags
DBUS_CFLAGS=$(pkg-config --cflags dbus-1)
DBUS_LIBS=$(pkg-config --libs dbus-1)

mkdir -p "$OUT_DIR"

echo "Compiling for $MACHINE ($ARCH)..."
gcc -o "$OUT_DIR/libnucleus_linux_theme.so" "$SRC" \
    -shared -fPIC \
    -I"$JNI_INCLUDE" -I"$JNI_INCLUDE_LINUX" \
    $DBUS_CFLAGS \
    -Os                     \
    -flto                   \
    -fvisibility=hidden     \
    -Wl,--gc-sections       \
    -ffunction-sections     \
    -fdata-sections         \
    -s                      \
    -lpthread               \
    $DBUS_LIBS

echo "Built linux-$ARCH .so:"
ls -lh "$OUT_DIR/libnucleus_linux_theme.so"
