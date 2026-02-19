#!/usr/bin/env bash

set -e

repo="kdroidFilter/Nucleus"
app_name="nucleusdemo"

# Resolve latest version tag from GitHub
echo "Checking latest version ..."
version=$(curl -sI "https://github.com/${repo}/releases/latest" | grep -i '^location:' | sed 's/.*tag\///' | tr -d '\r\n')

if [ -z "$version" ]; then
  echo "Failed to determine latest version."
  exit 1
fi

# Strip leading 'v' for the filename (v1.1.1 -> 1.1.1)
version_number="${version#v}"
echo "Latest version: $version (${version_number})"

arch=$(arch)
if [[ "$arch" == "i386" ]]; then
  mac_zip_url="https://github.com/${repo}/releases/download/${version}/${app_name}-${version_number}-mac-x64.zip"
elif [[ "$arch" == "arm64" ]]; then
  mac_zip_url="https://github.com/${repo}/releases/download/${version}/${app_name}-${version_number}-mac-arm64.zip"
else
  echo "Unsupported CPU architecture: $arch"
  exit 1
fi

tmpdir="$(mktemp -d -t download)"
tmpfile="$tmpdir/app.zip"

cleanup() {
  cd "$HOME" || true
  if [ -e "$tmpfile" ]; then
    rm -f "$tmpfile"
  fi
  rmdir "$tmpdir" 2>/dev/null || true
}
trap cleanup EXIT

echo "Downloading $app_name ..."
curl -L --output "$tmpfile" --progress-bar "$mac_zip_url"

if [ -w /Applications ]; then
  cd /Applications
else
  if [ ! -d "$HOME/Applications" ]; then
    mkdir "$HOME/Applications"
  fi
  cd "$HOME/Applications"
fi

if [ -d "${app_name}.app" ]; then
  echo "App folder ${app_name}.app already exists, removing."
  rm -rf "${app_name}.app"
fi

echo "Extracting to $PWD ..."
ditto -x -k "$tmpfile" .

# Remove quarantine attribute
if command -v xattr >/dev/null 2>&1; then
  xattr -r -d com.apple.quarantine "${app_name}.app" 2>/dev/null || true
fi

open "${app_name}.app"

cleanup
exit 0
