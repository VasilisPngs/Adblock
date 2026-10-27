#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"
exec 9>/tmp/android-toolchain.lock
flock 9

jdk_feature=$(grep -oP "java-version: '\K[0-9]+" .github/workflows/release.yml)
java_home=/opt/jdk$jdk_feature
android_home=/opt/android-sdk
android_cli=$HOME/.local/bin/android
compile_sdk=$(sed -n '/compileSdk {/,/^    }/p' app/build.gradle.kts)
api=$(grep -oP 'release\(\K[0-9]+' <<<"$compile_sdk")
minor=$(grep -oP 'minorApiLevel = \K[0-9]+' <<<"$compile_sdk" || true)
platform=android-$api${minor:+.$minor}
skills="adaptive agp-9-upgrade android-cli android-intent-security android-permissions-security anr-audit-and-fix edge-to-edge jetpack-compose-performance navigation-3 navigation-event r8-analyzer testing-setup"

if [ ! -x "$java_home/bin/java" ]; then
  mkdir -p "$java_home"
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/$jdk_feature/ga/linux/x64/jdk/hotspot/normal/eclipse" | tar -xz -C "$java_home" --strip-components=1
fi

if [ ! -x "$android_cli" ]; then
  curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash >/dev/null
fi

if [ ! -d "$android_home/platforms/$platform" ] || [ ! -d "$android_home/platform-tools" ]; then
  "$android_cli" sdk install --no-metrics --sdk="$android_home" platform-tools "platforms/$platform" >/dev/null
fi

for skill in $skills; do
  if [ ! -d "$HOME/.claude/skills/$skill" ]; then
    "$android_cli" skills add --no-metrics --agent=claude-code "$skill" >/dev/null
  fi
done

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export JAVA_HOME=$java_home ANDROID_HOME=$android_home PATH=$java_home/bin:$HOME/.local/bin:\$PATH" >> "$CLAUDE_ENV_FILE"
fi
