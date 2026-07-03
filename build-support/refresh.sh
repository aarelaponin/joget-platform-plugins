#!/usr/bin/env bash
#
# Regenerate build-support/m2 — the in-project Maven repository of non-Central
# Joget build-time dependencies.
#
# Run on a machine where a normal `mvn verify` already succeeds (i.e. ~/.m2 holds
# the current Joget artifacts). This performs a convergence loop: build the whole
# reactor against an EMPTY throwaway local repo using only Maven Central plus this
# file repo, and copy in any artifact missing from BOTH — until the clean-room build
# is green. The result is the complete, minimal non-Central closure.
#
# Usage:  ./build-support/refresh.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
M2="${HOME}/.m2/repository"
REPO="build-support/m2"
CI_M2="$(mktemp -d)/ci-m2"
SETTINGS="$(mktemp)"
LOG="$(mktemp)"

# Bare settings: Central only, no Joget Archiva, no stored credentials.
cat > "$SETTINGS" <<'XML'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"></settings>
XML

copy_missing() {
  grep -oE "Could not find artifact [^ ]+" "$LOG" | awk '{print $5}' | sort -u \
  | while IFS=: read -r g a type v _rest; do
      gp="${g//.//}"; src="$M2/$gp/$a/$v"; dst="$REPO/$gp/$a/$v"
      if [ -d "$src" ]; then
        mkdir -p "$dst"; cp "$src/"*.jar "$src/"*.pom "$dst/" 2>/dev/null && echo "  + $g:$a:$v"
      else
        echo "  ! NOT IN ~/.m2 (build there first): $g:$a:$v"
      fi
    done
}

for i in $(seq 1 12); do
  echo "===== clean-room iteration $i ====="
  rm -rf "$CI_M2"
  if mvn -B -ntp -s "$SETTINGS" -Dmaven.repo.local="$CI_M2" clean verify > "$LOG" 2>&1; then
    echo "BUILD SUCCESS — vendored set is complete."
    du -sh "$REPO"; echo "jars: $(find "$REPO" -name '*.jar' | wc -l)"
    exit 0
  fi
  if ! grep -q "Could not find artifact" "$LOG"; then
    echo "Clean-room build failed for a NON-artifact reason:"; grep -E "BUILD FAILURE|ERROR" "$LOG" | head -15
    exit 1
  fi
  echo "missing non-Central artifacts:"; copy_missing
done

echo "Did not converge in 12 iterations — inspect $LOG"; exit 1
