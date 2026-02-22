#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SCRIPT_DIR/NucleusNotificationBridge.c"
RESOURCE_DIR="$SCRIPT_DIR/../../resources/nucleus/native"
OUT_DIR_X64="$RESOURCE_DIR/linux-x64"
OUT_DIR_ARM64="$RESOURCE_DIR/linux-aarch64"

# Check for Java
if [ -z "${JAVA_HOME:-}" ]; then
    if command -v java &> /dev/null; then
        JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(which java)")")")
    fi
fi

if [ -z "${JAVA_HOME:-}" ]; then
    echo "ERROR: JAVA_HOME not set and java not found in PATH" >&2
    exit 1
fi

JNI_INCLUDE="$JAVA_HOME/include"
JNI_INCLUDE_LINUX="$JAVA_HOME/include/linux"

if [ ! -d "$JNI_INCLUDE" ]; then
    echo "ERROR: JNI headers not found at $JNI_INCLUDE" >&2
    exit 1
fi

# Check for required libraries
if ! command -v pkg-config &> /dev/null; then
    echo "ERROR: pkg-config not found. Install it to detect libraries." >&2
    exit 1
fi

# Get library flags
PKG_CFLAGS=$(pkg-config --cflags libnotify glib-2.0 gdk-pixbuf-2.0 2>/dev/null || echo "")
PKG_LIBS=$(pkg-config --libs libnotify glib-2.0 gdk-pixbuf-2.0 2>/dev/null || echo "")

if [ -z "$PKG_LIBS" ]; then
    echo "ERROR: Required libraries not found. Install with:" >&2
    echo "  sudo apt install libnotify-dev libglib2.0-dev libgdk-pixbuf2.0-dev" >&2
    exit 1
fi

# Create output directories
mkdir -p "$OUT_DIR_X64" "$OUT_DIR_ARM64"

echo "Building Linux native notification library..."
echo "  Source: $SRC"
echo "  Java: $JAVA_HOME"

# Common flags
COMMON_FLAGS=(
    -shared
    -fPIC
    -O2
    -Wall
    -Wextra
    -fvisibility=hidden
    -I"$JNI_INCLUDE"
    -I"$JNI_INCLUDE_LINUX"
    $PKG_CFLAGS
)

# Build for x86_64 (native) - always
echo ""
echo "Building for linux-x64..."
gcc "${COMMON_FLAGS[@]}" \
    -m64 \
    -o "$OUT_DIR_X64/libnucleus_notification.so" "$SRC" \
    $PKG_LIBS
strip -s "$OUT_DIR_X64/libnucleus_notification.so"
echo "  Built: $OUT_DIR_X64/libnucleus_notification.so"
ls -lh "$OUT_DIR_X64/libnucleus_notification.so"

# Check for ARM64 cross-compiler
BUILD_ARM64=false
if command -v aarch64-linux-gnu-gcc &> /dev/null; then
    BUILD_ARM64=true
fi

# Also check CI environment variable
if [ -n "${CI:-}" ]; then
    echo "  CI detected, attempting ARM64 build..."
    BUILD_ARM64=true
fi

if [ "$BUILD_ARM64" = true ]; then
    echo ""
    echo "Building for linux-aarch64 (ARM64)..."
    
    # Try cross-compile with aarch64-linux-gnu-gcc
    aarch64-linux-gnu-gcc "${COMMON_FLAGS[@]}" \
        -march=armv8-a \
        -o "$OUT_DIR_ARM64/libnucleus_notification.so" "$SRC" \
        $PKG_LIBS 2>/dev/null || {
        echo "  WARNING: ARM64 cross-compile failed - this is expected on non-ARM64 hosts without proper sysroot"
        rm -rf "$OUT_DIR_ARM64"
        echo "  ARM64 build skipped (handled in CI)"
    }
    
    if [ -f "$OUT_DIR_ARM64/libnucleus_notification.so" ]; then
        aarch64-linux-gnu-strip -s "$OUT_DIR_ARM64/libnucleus_notification.so"
        echo "  Built: $OUT_DIR_ARM64/libnucleus_notification.so"
        ls -lh "$OUT_DIR_ARM64/libnucleus_notification.so"
    fi
else
    echo ""
    echo "Note: ARM64 build skipped (not needed for x64 host)"
    echo "      ARM64 cross-compilation handled in CI pipeline"
    rm -rf "$OUT_DIR_ARM64"
fi

echo ""
echo "Build complete!"
echo ""
echo "Output files:"
ls -lh "$OUT_DIR_X64/libnucleus_notification.so" 2>/dev/null || true
ls -lh "$OUT_DIR_ARM64/libnucleus_notification.so" 2>/dev/null || true
