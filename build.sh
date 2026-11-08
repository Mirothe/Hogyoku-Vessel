#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

# java separates classpath entries with ; on Windows and : everywhere else
case "$(uname -m)" in
    arm64|aarch64) ARCH=arm64 ;;
    *) ARCH=x64 ;;
esac
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) SEP=';'; OS=windows; ICON=packaging/icon.ico ;;
    Darwin) SEP=':'; OS=macos-$ARCH; ICON=out/icon.icns ;;
    *) SEP=':'; OS=linux-$ARCH; ICON=packaging/icon.png ;;
esac

rm -rf out
mkdir -p out/classes out/dist

# a Mac app wants its icon as an .icns, made here from the png with the tools every Mac has
if [ "$ICON" = out/icon.icns ]; then
    mkdir -p out/icon.iconset
    for s in 16 32 128 256 512; do
        sips -z $s $s packaging/icon.png --out out/icon.iconset/icon_${s}x${s}.png >/dev/null
        sips -z $((s * 2)) $((s * 2)) packaging/icon.png --out out/icon.iconset/icon_${s}x${s}@2x.png >/dev/null
    done
    iconutil -c icns out/icon.iconset -o out/icon.icns || ICON=
fi

CP=$(ls lib/*.jar | tr '\n' "$SEP")
javac -encoding UTF-8 --release 21 -nowarn -cp "$CP" -d out/classes $(find src -name '*.java')

cp -r resources/* out/classes/

# LWJGL finds its natives on the classpath, so unpacking the jars in is enough. every platform's go in,
# so the one jar runs on Windows, Linux and Mac
cd out/classes
for j in ../../lib/*.jar; do jar xf "$j"; done
rm -rf META-INF/*.SF META-INF/*.RSA META-INF/*.DSA META-INF/MANIFEST.MF module-info.class META-INF/versions/*/module-info.class
cd ../..

printf 'Main-Class: dev.mirothe.vessel.Main\nMulti-Release: true\n' > out/manifest.txt
jar cfm "out/dist/Hogyoku Vessel.jar" out/manifest.txt -C out/classes .
echo "built out/dist/Hogyoku Vessel.jar"

# a self contained app with its own Java for this platform, packed up for the GitHub release
VERSION=${VERSION:-$(grep -o 'VERSION = "[^"]*"' src/dev/mirothe/vessel/Forge.java | cut -d'"' -f2)}
rm -rf out/jpkg out/app
mkdir -p out/jpkg
cp "out/dist/Hogyoku Vessel.jar" out/jpkg/
jpackage --type app-image --name "Hogyoku Vessel" --app-version "$VERSION" --vendor Mirothe \
    --input out/jpkg --main-jar "Hogyoku Vessel.jar" --main-class dev.mirothe.vessel.Main \
    ${ICON:+--icon "$ICON"} --dest out/app
APP=$(ls out/app)
NAME=Hogyoku-Vessel-$VERSION-$OS
# ditto and tar keep the launcher executable, a plain zip would lose that on Mac and Linux
case "$OS" in
    windows) (cd out/app && python -c "import shutil; shutil.make_archive('../dist/$NAME', 'zip', '.', '$APP')"); FILE=$NAME.zip ;;
    macos-*) ditto -c -k --keepParent "out/app/$APP" "out/dist/$NAME.zip"; FILE=$NAME.zip ;;
    *) tar -czf "out/dist/$NAME.tar.gz" -C out/app "$APP"; FILE=$NAME.tar.gz ;;
esac
echo "built out/dist/$FILE"
