#!/usr/bin/env bash
# 不依赖 Gradle 的最小构建脚本：aapt2 + javac + d8 + zipalign + apksigner。
# 依赖：JDK 17、Android SDK（platforms;android-36、build-tools;36.0.0）。
# 产物：out/hyperos-power-gpt-<version>.apk（自签名，v2+v3 签名）
set -euo pipefail
cd "$(dirname "$0")"

: "${ANDROID_HOME:=/opt/android}"
BT="$ANDROID_HOME/build-tools/36.0.0"
PLATFORM="$ANDROID_HOME/platforms/android-36/android.jar"
XPOSED_API="libs/api-82.jar"          # 只用于编译（compileOnly），绝不打包进 APK
VERSION_CODE=1
VERSION_NAME=1.0.0
MIN_SDK=34                            # Android 14（HyperOS 1.x）起
TARGET_SDK=36
# 签名：读取本地 keystore.properties（不提交到仓库）；没有就生成一把随机密码的新 key
KS_PROPS="keystore.properties"
OUT=out; B=build-raw

rm -rf "$B" && mkdir -p "$B"/{res,classes,dex} "$OUT"

# 0) Xposed API（compileOnly）：缺失时从官方仓库下载
if [ ! -f "$XPOSED_API" ]; then
  mkdir -p libs
  curl -fsSL -o "$XPOSED_API" https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar
fi

# 1) 资源（Gradle 用 namespace，这里给 aapt2 生成一份带 package 属性的清单副本）
sed 's#<manifest xmlns:android="http://schemas.android.com/apk/res/android">#<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="io.github.ziyuw_plank.hypergpt">#' \
  app/src/main/AndroidManifest.xml > "$B/AndroidManifest.xml"
"$BT/aapt2" compile --dir app/src/main/res -o "$B/res/res.zip"
"$BT/aapt2" link -o "$B/base.apk" -I "$PLATFORM" \
  --manifest "$B/AndroidManifest.xml" \
  --min-sdk-version $MIN_SDK --target-sdk-version $TARGET_SDK \
  --version-code $VERSION_CODE --version-name $VERSION_NAME \
  -A app/src/main/assets "$B/res/res.zip"

# 2) Java → class（Xposed API 只在 classpath 上，不进 dex）
find app/src/main/java -name '*.java' > "$B/sources.txt"
javac --release 17 -encoding UTF-8 -Xlint:all,-options -Werror \
  -classpath "$PLATFORM:$XPOSED_API" \
  -d "$B/classes" @"$B/sources.txt"

# 3) class → dex
"$BT/d8" --release --min-api $MIN_SDK --lib "$PLATFORM" --classpath "$XPOSED_API" \
  --output "$B/dex" $(find "$B/classes" -name '*.class')

# 4) 组装 + 对齐 + 签名
cp "$B/base.apk" "$B/unsigned.apk"
python3 -c 'import sys,zipfile; z=zipfile.ZipFile(sys.argv[1],"a",zipfile.ZIP_DEFLATED); z.write(sys.argv[2],"classes.dex"); z.close()' \
  "$B/unsigned.apk" "$B/dex/classes.dex"
"$BT/zipalign" -f -p 4 "$B/unsigned.apk" "$B/aligned.apk"
if [ ! -f "$KS_PROPS" ]; then
  mkdir -p keystore
  PASS="$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32)"
  keytool -genkeypair -keystore keystore/hypergpt-release.jks -storetype PKCS12 -storepass "$PASS" -keypass "$PASS" \
    -alias hypergpt -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "CN=ziyuw-Plank" >/dev/null
  printf 'storeFile=keystore/hypergpt-release.jks\nstorePassword=%s\nkeyAlias=hypergpt\nkeyPassword=%s\n' "$PASS" "$PASS" > "$KS_PROPS"
  chmod 600 "$KS_PROPS"
fi
prop() { grep -m1 "^$1=" "$KS_PROPS" | cut -d= -f2-; }
KEYSTORE="$(prop storeFile)"
APK="$OUT/hyperos-power-gpt-$VERSION_NAME-raw.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$(prop storePassword)" --key-pass "pass:$(prop keyPassword)" \
  --ks-key-alias "$(prop keyAlias)" --v2-signing-enabled true --v4-signing-enabled false --out "$APK" "$B/aligned.apk"
"$BT/apksigner" verify --print-certs "$APK" | head -3
echo "OK -> $APK"
