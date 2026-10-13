#!/usr/bin/env bash
set -euo pipefail

# Keep the engine version pinned so schema changes are deliberate and reviewable.
XRAY_VERSION="${XRAY_VERSION:-v26.10.10}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

case "$(uname -m)" in
  x86_64|amd64) ASSET="Xray-linux-64.zip" ;;
  aarch64|arm64) ASSET="Xray-linux-arm64-v8a.zip" ;;
  *) echo "Unsupported CI architecture: $(uname -m)" >&2; exit 2 ;;
esac

URL="https://github.com/XTLS/Xray-core/releases/download/${XRAY_VERSION}/${ASSET}"
echo "Downloading Xray-core ${XRAY_VERSION} for schema validation"
curl --fail --location --retry 3 --silent --show-error "$URL" -o "$WORK_DIR/xray.zip"
unzip -q "$WORK_DIR/xray.zip" -d "$WORK_DIR/xray"
XRAY_BIN="$WORK_DIR/xray/xray"
test -x "$XRAY_BIN" || chmod +x "$XRAY_BIN"
"$XRAY_BIN" version

shopt -s nullglob
fixtures=("$ROOT_DIR"/tests/fixtures/xray/*.json)
if (("${#fixtures[@]}" == 0)); then
  echo "No Xray config fixtures found" >&2
  exit 1
fi
for config in "${fixtures[@]}"; do
  echo "Validating $(basename "$config") with Xray-core ${XRAY_VERSION}"
  "$XRAY_BIN" run -test -config "$config"
done
