#!/usr/bin/env bash
# Installs a headless Android build toolchain (JDK 17 + SDK platform 35 / build-tools 35) on Debian/Ubuntu.
# Idempotent: re-running only downloads what is missing. Writes local.properties with sdk.dir.
#
#   scripts/bootstrap-env.sh            # install to /opt (needs sudo) or $HOME/android-toolchain
#   source <(scripts/bootstrap-env.sh --env)   # print exports only
set -euo pipefail

PREFIX="${TOOLCHAIN_PREFIX:-/opt}"
if [[ ! -w "$PREFIX" ]] && ! sudo -n true 2>/dev/null; then PREFIX="$HOME/android-toolchain"; fi
JDK_DIR="$PREFIX/jdk17"
SDK_DIR="$PREFIX/android-sdk"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"

if [[ "${1:-}" == "--env" ]]; then
  echo "export JAVA_HOME=$JDK_DIR"
  echo "export ANDROID_HOME=$SDK_DIR"
  echo "export PATH=$JDK_DIR/bin:$SDK_DIR/platform-tools:\$PATH"
  exit 0
fi

mk() { if [[ -w "$(dirname "$1")" ]]; then mkdir -p "$1"; else sudo mkdir -p "$1" && sudo chown "$(id -u):$(id -g)" "$1"; fi; }
mk "$JDK_DIR"; mk "$SDK_DIR"; mk "$PREFIX/dl"

if [[ ! -x "$JDK_DIR/bin/java" ]]; then
  echo ">> downloading Temurin JDK 17"
  curl -sSL -o "$PREFIX/dl/jdk17.tar.gz" "$JDK_URL"
  tar -xzf "$PREFIX/dl/jdk17.tar.gz" -C "$JDK_DIR" --strip-components=1
fi
export JAVA_HOME="$JDK_DIR" PATH="$JDK_DIR/bin:$PATH"

if [[ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]]; then
  echo ">> downloading Android command-line tools"
  curl -sSL -o "$PREFIX/dl/cmdline-tools.zip" "$CMDLINE_TOOLS_URL"
  rm -rf "$SDK_DIR/tmp" && unzip -q -o "$PREFIX/dl/cmdline-tools.zip" -d "$SDK_DIR/tmp"
  mkdir -p "$SDK_DIR/cmdline-tools/latest"
  mv "$SDK_DIR/tmp/cmdline-tools/"* "$SDK_DIR/cmdline-tools/latest/" && rm -rf "$SDK_DIR/tmp"
fi
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null

cd "$(dirname "$0")/.."
grep -q '^sdk.dir=' local.properties 2>/dev/null || echo "sdk.dir=$SDK_DIR" >> local.properties

echo
echo "toolchain ready:"
echo "  JAVA_HOME=$JDK_DIR"
echo "  ANDROID_HOME=$SDK_DIR"
echo "run:  export JAVA_HOME=$JDK_DIR ANDROID_HOME=$SDK_DIR PATH=$JDK_DIR/bin:\$PATH && ./gradlew assembleDebug"
