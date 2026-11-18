#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="$HOME/.claude/plugins/marketplaces/devlog/plugins/devlog-companion"

mkdir -p "$DEST"
cp -r "$SCRIPT_DIR/.claude-plugin" "$SCRIPT_DIR/hooks" "$SCRIPT_DIR/types" "$DEST/"
echo "devlog-companion installed to $DEST"
echo "Restart Claude Desktop to load the updated plugin."
