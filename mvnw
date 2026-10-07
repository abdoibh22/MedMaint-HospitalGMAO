#!/usr/bin/env sh
# MedMaint — project-local Maven wrapper.
# Uses ./tools/apache-maven-<ver> if present, otherwise downloads Maven once.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
M2_HOME_DIR=$(ls -d "$DIR"/tools/apache-maven-* 2>/dev/null | head -n 1)
if [ -z "$M2_HOME_DIR" ]; then
  VER=3.9.9
  echo "Téléchargement de Maven $VER..."
  mkdir -p "$DIR/tools"
  curl -sSL -o "$DIR/tools/maven.zip" "https://archive.apache.org/dist/maven/maven-3/$VER/binaries/apache-maven-$VER-bin.zip"
  unzip -q -o "$DIR/tools/maven.zip" -d "$DIR/tools"
  rm -f "$DIR/tools/maven.zip"
  M2_HOME_DIR=$(ls -d "$DIR"/tools/apache-maven-* 2>/dev/null | head -n 1)
fi
if [ -z "$JAVA_HOME" ]; then
  JAVA_BIN=$(dirname "$(readlink -f "$(which java)" 2>/dev/null || which java)")
  export JAVA_HOME=$(dirname "$JAVA_BIN")
fi
exec "$M2_HOME_DIR/bin/mvn" "$@"
