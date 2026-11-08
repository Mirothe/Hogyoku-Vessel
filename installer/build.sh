#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

REPO=${1:?usage: installer/build.sh owner/repo}
CSC=/c/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe
FW=C:/Windows/Microsoft.NET/Framework64/v4.0.30319

mkdir -p ../out/dist
sed "s|%REPO%|$REPO|" Setup.cs > ../out/Setup.cs
"$CSC" -nologo -target:winexe -optimize -out:"../out/dist/Hogyoku Vessel Setup.exe" -win32icon:../packaging/icon.ico -resource:"$(cygpath -w ../packaging/icon.png)",icon.png \
    -r:System.Windows.Forms.dll -r:System.Drawing.dll -r:"$FW/System.IO.Compression.dll" \
    -r:"$FW/System.IO.Compression.FileSystem.dll" -r:System.Web.Extensions.dll "$(cygpath -w ../out/Setup.cs)"
echo "built out/dist/Hogyoku Vessel Setup.exe for $REPO"
