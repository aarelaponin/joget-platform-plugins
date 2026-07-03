#!/usr/bin/env bash
# Create the functional reference app on a live Joget instance by dogfooding form-creator-api.
# Posts the reference form (embedding form-prefill loadBinder + concat-field, lookup-field,
# rule-editor and quality-banner elements). form-creator validates and instantiates each element's
# plugin class server-side, so a successful create + the classes appearing in the stored definition
# is a functional acceptance.
#
# form-creator-api expects `formDefinition` as a STRING — this script stringifies it before POST.
# The endpoint is /jw/api/formcreator/formcreator/forms (API alias + plugin path).
#
# Requires the form-creator API credentials (NOT stored in this repo):
#   export FORMCREATOR_API_ID=...            # the API id
#   export JDX7_FORMCREATOR_API_KEY=...       # the API key (from your password manager)
#   export JOGET_URL=http://localhost:8077    # optional; jdx7 default
#
#   ./reference-app/create-reference-app.sh
set -euo pipefail

JOGET_URL="${JOGET_URL:-http://localhost:8077}"
API_ID="${FORMCREATOR_API_ID:-}"
API_KEY="${JDX7_FORMCREATOR_API_KEY:-}"
HERE="$(cd "$(dirname "$0")" && pwd)"

[ -n "$API_ID" ]  || { echo "Set FORMCREATOR_API_ID (not stored in repo)"; exit 2; }
[ -n "$API_KEY" ] || { echo "Set JDX7_FORMCREATOR_API_KEY (not stored in repo)"; exit 2; }

python3 - "$HERE/app/reference-form.request.json" "$JOGET_URL" "$API_ID" "$API_KEY" <<'PY'
import json, sys, urllib.request, urllib.error
reqfile, url, api_id, api_key = sys.argv[1:5]
req = json.load(open(reqfile))
req.pop("_note", None)
req["formDefinition"] = json.dumps(req["formDefinition"])   # API expects a STRING
body = json.dumps(req).encode()
r = urllib.request.Request(url + "/jw/api/formcreator/formcreator/forms", data=body, method="POST",
    headers={"api_id": api_id, "api_key": api_key, "Content-Type": "application/json"})
try:
    resp = urllib.request.urlopen(r, timeout=40)
    print("HTTP", resp.status, resp.read().decode()[:200])
    sys.exit(0 if resp.status == 200 else 1)
except urllib.error.HTTPError as e:
    print("HTTP", e.code, e.read().decode()[:400]); sys.exit(1)
PY
