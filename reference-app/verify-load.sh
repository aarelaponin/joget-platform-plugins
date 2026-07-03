#!/usr/bin/env bash
# Assert every platform plugin's OSGi bundle STARTED in the target Joget instance and that no
# OSGi framework errors occurred. Reads the instance log. Run after (re)starting the instance.
#
#   JOGET_HOME=/path/to/joget ./reference-app/verify-load.sh     (exit 0 = all loaded, 1 = problem)
set -uo pipefail

JOGET_HOME="${JOGET_HOME:-/Users/aarelaponin/joget-enterprise-linux-9.0.5}"
LOG="${JOGET_LOG:-$(ls "$JOGET_HOME"/apache-tomcat-*/logs/catalina.out 2>/dev/null | head -1)}"
[ -f "$LOG" ] || { echo "log not found under $JOGET_HOME"; exit 1; }

# Bundle symbolic names as they appear in "Bundle <name> started" (from registry artifactIds).
BUNDLES=(
  com.fiscaladmin.joget.form-prefill
  global.govstack.form-creator-api
  global.govstack.joget-lookup-field
  global.govstack.joget-concat-field
  org.joget.lst.joget-advanced-filters
  global.govstack.wf-activator
  global.govstack.form-quality-runtime
  org.joget.tree_menu
  global.govstack.joget-rules-api
  global.govstack.joget-rule-editor
)

fail=0
for b in "${BUNDLES[@]}"; do
  if grep -q "Bundle $b started" "$LOG"; then
    echo "  OK    $b"
  else
    echo "  MISS  $b (no 'Bundle ... started' in log)"; fail=1
  fi
done

echo "--- OSGi framework errors ---"
if grep -iE "FrameworkEvent ERROR|BundleException|Unable to resolve .*global.govstack|Unable to resolve .*fiscaladmin" "$LOG" >/tmp/pp_osgi_err 2>/dev/null && [ -s /tmp/pp_osgi_err ]; then
  cat /tmp/pp_osgi_err; fail=1
else
  echo "  none"
fi

[ $fail -eq 0 ] && echo "PASS: all platform plugins loaded cleanly." || echo "FAIL: see above."
exit $fail
