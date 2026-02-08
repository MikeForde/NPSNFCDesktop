#!/usr/bin/env bash
set -euo pipefail

JDK="${JDK:-/home/mikeforde/.jdks/corretto-24.0.2}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/libs/java-smartcardio.jar"
TMP="$ROOT/libs/_smartcardio_extract"

rm -rf "$TMP"
mkdir -p "$TMP"
cd "$TMP"

"$JDK/bin/jar" xf "$JDK/jmods/java.smartcardio.jmod"

cd classes
"$JDK/bin/jar" cf "$OUT" .

cd "$ROOT"
rm -rf "$TMP"

echo "Built: $OUT"
ls -la "$OUT"
