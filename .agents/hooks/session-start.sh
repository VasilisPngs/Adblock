#!/bin/bash
set -euo pipefail

[ -t 0 ] || cat >/dev/null 2>&1 || true

if [ "$(uname -s)" != "Linux" ]; then
  echo '{}'
  exit 0
fi

exec 3>&1 >/dev/null
cd "$(dirname "$0")/../.."
exec 9>/tmp/android-toolchain.lock
flock 9

jdk_feature=$(grep -oP "java-version: '\K[0-9]+" .github/workflows/release.yml)
java_home=$HOME/.local/jdk$jdk_feature
android_home=$HOME/.local/android-sdk
android_cli=$HOME/.local/bin/android
compile_sdk=$(sed -n '/compileSdk {/,/^    }/p' app/build.gradle.kts)
api=$(grep -oP 'release\(\K[0-9]+' <<<"$compile_sdk")
minor=$(grep -oP 'minorApiLevel = \K[0-9]+' <<<"$compile_sdk" || true)
platform=android-$api${minor:+.$minor}
skills="adaptive agp-9-upgrade android-cli android-intent-security android-permissions-security anr-audit-and-fix edge-to-edge jetpack-compose-performance navigation-3 navigation-event r8-analyzer testing-setup"

if [ "$(uname -m)" = "aarch64" ]; then
  jdk_arch=aarch64
  cli_arch=linux_arm64
else
  jdk_arch=x64
  cli_arch=linux_x86_64
fi

android_updated=false
update_android_cli() {
  if [ "$android_updated" = false ]; then
    "$android_cli" update --no-metrics 2>/dev/null || true
    android_updated=true
  fi
}

if [ ! -x "$java_home/bin/java" ]; then
  mkdir -p "$java_home"
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/$jdk_feature/ga/linux/$jdk_arch/jdk/hotspot/normal/eclipse" | tar -xz -C "$java_home" --strip-components=1
fi

if [ ! -x "$android_cli" ]; then
  mkdir -p "$(dirname "$android_cli")"
  curl -fsSL "https://dl.google.com/android/cli/latest/$cli_arch/android" -o "$android_cli.tmp"
  chmod +x "$android_cli.tmp"
  mv "$android_cli.tmp" "$android_cli"
  "$android_cli" update --no-metrics 2>/dev/null
  android_updated=true
fi

if [ ! -d "$android_home/platforms/$platform" ] || [ ! -d "$android_home/platform-tools" ]; then
  update_android_cli
  "$android_cli" sdk install --no-metrics --sdk="$android_home" platform-tools "platforms/$platform" 2>/dev/null
fi

for skill in $skills; do
  if [ ! -d "$HOME/.gemini/config/skills/$skill" ]; then
    update_android_cli
    "$android_cli" skills add --no-metrics --agent=antigravity "$skill" 2>/dev/null
  fi
done

touch local.properties
grep -q '^sdk\.dir=' local.properties && sed -i "s|^sdk\\.dir=.*|sdk.dir=$android_home|" local.properties || echo "sdk.dir=$android_home" >> local.properties

mkdir -p "$HOME/.gradle"
touch "$HOME/.gradle/gradle.properties"
grep -q '^org\.gradle\.java\.home=' "$HOME/.gradle/gradle.properties" && sed -i "s|^org\\.gradle\\.java\\.home=.*|org.gradle.java.home=$java_home|" "$HOME/.gradle/gradle.properties" || echo "org.gradle.java.home=$java_home" >> "$HOME/.gradle/gradle.properties"

msg="export JAVA_HOME=$java_home ANDROID_HOME=$android_home PATH=$java_home/bin:$HOME/.local/bin:$PATH"
msg=${msg//\\/\\\\}
msg=${msg//\"/\\\"}
printf '{"injectSteps":[{"systemMessage":{"systemMessage":"%s"}}]}\n' "$msg" >&3
