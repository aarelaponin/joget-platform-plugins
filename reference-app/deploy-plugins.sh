#!/usr/bin/env bash
# Deploy every built platform-plugin JAR (+ the pinned status-framework) into a Joget instance's
# app_plugins folder. Requires a prior `mvn install`. Hot-reloads if the instance is running.
#
#   JOGET_HOME=/path/to/joget ./reference-app/deploy-plugins.sh
set -euo pipefail

JOGET_HOME="${JOGET_HOME:-/Users/aarelaponin/joget-enterprise-linux-9.0.5}"
APLUG="$JOGET_HOME/wflow/app_plugins"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

[ -d "$APLUG" ] || { echo "app_plugins not found: $APLUG"; exit 1; }

n=0
for j in "$ROOT"/plugins/*/target/*.jar; do
  case "$j" in *sources*|*javadoc*) continue;; esac
  cp "$j" "$APLUG/" && n=$((n+1))
done
# pinned external dependency: joget-status-framework from ~/.m2
sf="$(ls ~/.m2/repository/global/govstack/joget-status-framework/*/joget-status-framework-*.jar 2>/dev/null | grep -v sources | head -1 || true)"
[ -n "$sf" ] && { cp "$sf" "$APLUG/"; n=$((n+1)); }

echo "Deployed $n JAR(s) to $APLUG"
