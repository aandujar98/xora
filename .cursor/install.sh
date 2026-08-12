#!/usr/bin/env bash
# XOrA Cloud Agent bootstrap.
#
# Installs the Android SDK components the Gradle build needs and points the build
# at them through local.properties. Safe to run repeatedly: every step is a no-op
# when the component is already present.
set -euo pipefail

# Version of Google's command-line tools bundle to fetch when none is installed.
CMDLINE_VERSION="11076708"

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_HOME

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

CMDLINE_DIR="$ANDROID_HOME/cmdline-tools/latest"
SDKMANAGER="$CMDLINE_DIR/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  echo "Installing Android command-line tools..."
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/cmdtools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VERSION}_latest.zip"
  unzip -q -o "$tmp/cmdtools.zip" -d "$tmp"
  rm -rf "$CMDLINE_DIR"
  mv "$tmp/cmdline-tools" "$CMDLINE_DIR"
  rm -rf "$tmp"
fi

# Accept licenses (idempotent) and install the exact components the modules pin:
# platform 37 (compileSdk), build-tools 37, both NDKs (core:launcher pins 27 to
# match the Discord AAR prefab, core:libretro uses the AGP default 28) and
# CMake 3.22.1 for the two native hosts.
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" --install \
  "platform-tools" \
  "platforms;android-37.0" \
  "platforms;android-37.1" \
  "build-tools;37.0.0" \
  "ndk;27.0.12077973" \
  "ndk;28.2.13676358" \
  "cmake;3.22.1"

# Gradle discovers the SDK from local.properties (gitignored); keep it in sync.
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$REPO_ROOT/local.properties"

echo "Android SDK ready at $ANDROID_HOME"
