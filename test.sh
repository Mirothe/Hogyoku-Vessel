#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
    *) SEP=':' ;;
esac

# checks every lesson's task checks against its starter code and examples
CP=$(ls lib/*.jar | tr '\n' "$SEP")
rm -rf out/test && mkdir -p out/test
javac -encoding UTF-8 --release 21 -nowarn -cp "$CP" -d out/test $(find src test -name '*.java')
java -cp "out/test${SEP}resources${SEP}$CP" dev.mirothe.vessel.LessonCheck
