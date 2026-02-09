#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/libs/java-smartcardio.jar"
TMP="$ROOT/libs/_smartcardio_extract"

realpath_compat() {
  # macOS usually has neither readlink -f nor realpath by default in older installs,
  # so fall back to Python if needed.
  if command -v realpath >/dev/null 2>&1; then
    realpath "$1"
  elif command -v python3 >/dev/null 2>&1; then
    python3 -c 'import os,sys; print(os.path.realpath(sys.argv[1]))' "$1"
  else
    # last resort: no symlink resolution
    echo "$1"
  fi
}

resolve_jdk_home() {
  # 1) JAVA_HOME if set
  if [[ -n "${JAVA_HOME:-}" ]]; then
    echo "$JAVA_HOME"
    return 0
  fi

  # 2) macOS helper if available
  if [[ "$(uname -s)" == "Darwin" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
    /usr/libexec/java_home
    return 0
  fi

  # 3) Resolve java on PATH -> .../bin/java
  local java_path
  java_path="$(command -v java || true)"
  if [[ -z "$java_path" ]]; then
    echo "ERROR: java not found on PATH and JAVA_HOME not set." >&2
    exit 1
  fi

  local real_java
  real_java="$(realpath_compat "$java_path")"

  echo "$(cd "$(dirname "$real_java")/.." && pwd)"
}

JDK_HOME="$(resolve_jdk_home)"

JAR_BIN="$JDK_HOME/bin/jar"
JMOD="$JDK_HOME/jmods/java.smartcardio.jmod"

if [[ ! -x "$JAR_BIN" ]]; then
  echo "ERROR: Not a JDK (missing $JAR_BIN). Resolved JDK_HOME=$JDK_HOME" >&2
  exit 1
fi

if [[ ! -f "$JMOD" ]]; then
  echo "ERROR: Missing module file: $JMOD" >&2
  echo "Resolved JDK_HOME=$JDK_HOME" >&2
  exit 1
fi

rm -rf "$TMP"
mkdir -p "$TMP"
cd "$TMP"

"$JAR_BIN" xf "$JMOD"

cd classes
"$JAR_BIN" cf "$OUT" .

cd "$ROOT"
rm -rf "$TMP"

echo "Using JDK: $JDK_HOME"
echo "Built: $OUT"
ls -la "$OUT"
