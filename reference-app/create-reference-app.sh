#!/usr/bin/env bash
# Create the functional reference app on a live Joget instance by dogfooding form-creator-api.
# Posts the reference form (which instantiates concat-field, lookup-field, form-prefill loadBinder,
# rule-editor and quality-banner). A successful create response is itself a functional check:
# the server validates and instantiates each element's plugin class, so a wrong/unloadable class
# fails the call.
#
# Requires the form-creator API credentials (NOT stored in this repo):
#   export JDX7_FORMCREATOR_API_KEY=...        # the API key (from your password manager)
#   export FORMCREATOR_API_ID=API-4e39106c-67b1-4155-8c80-2f5ed6d1aae5   # optional; this is jdx7's
#   export JOGET_URL=http://localhost:8077     # optional; jdx7 default
#
#   ./reference-app/create-reference-app.sh
set -euo pipefail

JOGET_URL="${JOGET_URL:-http://localhost:8077}"
API_ID="${FORMCREATOR_API_ID:-API-4e39106c-67b1-4155-8c80-2f5ed6d1aae5}"
API_KEY="${JDX7_FORMCREATOR_API_KEY:-}"
HERE="$(cd "$(dirname "$0")" && pwd)"

[ -n "$API_KEY" ] || { echo "Set JDX7_FORMCREATOR_API_KEY (not stored in repo)"; exit 2; }

echo "POST $JOGET_URL/jw/api/formcreator/forms   (ppRefForm -> app platformRef)"
resp="$(curl -sS -w '\n%{http_code}' -X POST "$JOGET_URL/jw/api/formcreator/forms" \
  -H "api_id: $API_ID" -H "api_key: $API_KEY" -H "Content-Type: application/json" \
  --data @"$HERE/app/reference-form.request.json")"

body="$(echo "$resp" | sed '$d')"; code="$(echo "$resp" | tail -1)"
echo "HTTP $code"; echo "$body"
case "$code" in
  2*) echo "PASS: reference form created (all embedded plugin classes instantiated server-side).";;
  *)  echo "FAIL: create returned $code — check the response above."; exit 1;;
esac
