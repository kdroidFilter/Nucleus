#!/usr/bin/env bash
# publish-release.sh — Create/update a GitHub Release and upload all artifacts.
#
# Usage: publish-release.sh <artifacts-dir> <tag> [release-type]
#   artifacts-dir : directory containing downloaded CI artifacts and update-yml/
#   tag           : git tag (e.g. v1.2.3)
#   release-type  : "release" (default), "draft", or "prerelease"
#
# Requires: gh CLI authenticated with GITHUB_TOKEN

set -euo pipefail

ARTIFACTS_DIR="${1:?Usage: publish-release.sh <artifacts-dir> <tag> [release-type]}"
TAG="${2:?Usage: publish-release.sh <artifacts-dir> <tag> [release-type]}"
RELEASE_TYPE="${3:-release}"

# File extensions to skip uploading (already included in update-yml)
SKIP_PATTERN='\.(yml|yaml)$'

# Build gh release create flags
CREATE_FLAGS=("--title" "$TAG" "--generate-notes")
case "$RELEASE_TYPE" in
  draft)      CREATE_FLAGS+=("--draft") ;;
  prerelease) CREATE_FLAGS+=("--prerelease") ;;
  release)    ;; # default, no extra flags
  *)          echo "Unknown release type: $RELEASE_TYPE"; exit 1 ;;
esac

# Create the release if it doesn't exist
if gh release view "$TAG" >/dev/null 2>&1; then
  echo "Release $TAG already exists, will upload assets to it."
else
  echo "Creating release $TAG..."
  gh release create "$TAG" "${CREATE_FLAGS[@]}"
fi

# Collect all binary files to upload
UPLOAD_FILES=()

# Binaries from build artifacts
for asset_dir in "$ARTIFACTS_DIR"/release-assets-*/; do
  [ -d "$asset_dir" ] || continue
  while IFS= read -r -d '' file; do
    [ -f "$file" ] || continue
    basename_file="$(basename "$file")"

    # Skip yml/yaml metadata (we upload our consolidated ones instead)
    if echo "$basename_file" | grep -qE "$SKIP_PATTERN"; then
      continue
    fi

    UPLOAD_FILES+=("$file")
  done < <(find "$asset_dir" -type f -print0 | sort -z)
done

# Consolidated update-yml files
if [ -d "$ARTIFACTS_DIR/update-yml" ]; then
  for yml_file in "$ARTIFACTS_DIR"/update-yml/*.yml; do
    [ -f "$yml_file" ] || continue
    UPLOAD_FILES+=("$yml_file")
  done
fi

if [ ${#UPLOAD_FILES[@]} -eq 0 ]; then
  echo "No files to upload!"
  exit 1
fi

echo "Uploading ${#UPLOAD_FILES[@]} files to release $TAG..."
gh release upload "$TAG" "${UPLOAD_FILES[@]}" --clobber

echo "Release $TAG published with ${#UPLOAD_FILES[@]} assets."
