#!/usr/bin/env bash
# One-time setup of everything needed to build the Android app (run in Git Bash):
#   JDK 17, the Android SDK and Bubblewrap's project generator.
# Everything goes into %LOCALAPPDATA%\attendance-android-tools - nothing is installed system-wide
# and nothing is added to this repository. Re-running skips the parts that are already there.
# Running it means accepting the Android SDK licence (https://developer.android.com/studio/terms).
set -e
T="$LOCALAPPDATA/attendance-android-tools"
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$T" && cd "$T"

echo "[1/5] JDK 17 (Android's build tools do not run on newer Java versions)"
if [ ! -d jdk17 ]; then
  curl -sSL -o jdk17.zip "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"
  unzip -q jdk17.zip -d jdk17-tmp && mv jdk17-tmp/* jdk17 && rmdir jdk17-tmp && rm jdk17.zip
fi

echo "[2/5] Android command-line tools"
if [ ! -d sdk/cmdline-tools/latest ]; then
  NAME=$(curl -s https://dl.google.com/android/repository/repository2-3.xml | grep -o 'commandlinetools-win-[0-9]*_latest.zip' | head -1)
  curl -sSL -o clt.zip "https://dl.google.com/android/repository/$NAME"
  mkdir -p sdk/cmdline-tools && unzip -q clt.zip -d sdk/cmdline-tools
  mv sdk/cmdline-tools/cmdline-tools sdk/cmdline-tools/latest && rm clt.zip
fi

echo "[3/5] Android SDK packages"
export JAVA_HOME="$T/jdk17"
# sdkmanager.bat splits arguments at ';', so the package names go in a file
printf "platform-tools\nplatforms;android-36\nbuild-tools;35.0.0\n" > packages.txt
yes | "$T/sdk/cmdline-tools/latest/bin/sdkmanager.bat" --sdk_root="$T/sdk" \
  --package_file="$(cygpath -w "$T/packages.txt")" > sdk-install.log 2>&1

echo "[4/5] Bubblewrap project generator"
mkdir -p bubblewrap && cp "$HERE/generate-project.js" bubblewrap/
( cd bubblewrap && [ -d node_modules/@bubblewrap/core ] || { npm init -y > /dev/null && npm install --no-fund --no-audit @bubblewrap/core@1.27.0; } )

echo "[5/5] Android project from android/twa-manifest.json"
rm -rf "$T/project"
( cd bubblewrap && node generate-project.js "$(cygpath -w "$HERE/twa-manifest.json")" "$(cygpath -w "$T/project")" )
printf "sdk.dir=%s\n" "$(cygpath -m "$T/sdk")" > "$T/project/local.properties"   # C:/... form, no escaping needed
# Our launcher icon (android/res) replaces Bubblewrap's, which showed blank with themed icons
cp -r "$HERE/res/." "$T/project/app/src/main/res/"

echo "Done. Tools and project are in $T"
