# Live verification results

## Plugin-load verification — L3

**Instance:** jdx7 — Joget DX **9.0.5**, **PostgreSQL** (`jwdb`), `http://localhost:8077`
**Run:** 3 Jul 2026 · server startup in 13.3s · `verify-load.sh` exit 0

All platform-plugin OSGi bundles started in the live runtime, no framework errors:

```
OK    com.fiscaladmin.joget.form-prefill
OK    global.govstack.form-creator-api          (build-024)
OK    global.govstack.joget-lookup-field
OK    global.govstack.joget-concat-field
OK    org.joget.lst.joget-advanced-filters
OK    global.govstack.wf-activator
OK    global.govstack.form-quality-runtime      (build-014)
OK    org.joget.tree_menu
OK    global.govstack.joget-rules-api
OK    global.govstack.joget-rule-editor
--- OSGi framework errors: none ---
PASS: all platform plugins loaded cleanly.
```

`rules-grammar` and `joget-status-framework` are libraries embedded/depended-upon by the bundles
above (rules-api → rules-grammar; form-quality → status-framework); their successful resolution is
implied by those bundles starting.

**What this proves that unit tests cannot:** manifests, `Import-Package` declarations, and
inter-bundle dependencies all resolve in a real Joget DX9 + PostgreSQL OSGi container — the plugins
don't just compile, they load and register.

## Functional reference app — L3

**App:** `platformRef` on the same jdx7 instance (DX 9.0.5 · PostgreSQL).
**Form:** `ppRefForm`, authored **through the platform's own `form-creator-api`** REST endpoint
(`POST /jw/api/formcreator/formcreator/forms`) — so the create path itself exercises a foundation
plugin. **Run:** 3 Jul 2026 · `create-reference-app.sh` → `HTTP 200 {"status":"success"}`.

The single form composes four foundation element plugins in one Section→Column, and their definitions
persist in `app_form.json`:

```
OK    ConcatFieldElement      (global.govstack.joget-concat-field)
OK    LookupFieldElement      (global.govstack.joget-lookup-field)
OK    RuleEditorElement       (global.govstack.joget-rule-editor  → joget-rules-api → rules-grammar)
OK    QualityBannerElement    (global.govstack.form-quality-runtime → joget-status-framework)
```

**What this proves:** the element plugins don't only load as bundles — they are **accepted by the
Joget form engine as real form elements**, serialised into a stored form definition, and the create
itself is served by `form-creator-api`. That transitively exercises the rules engine
(rule-editor→rules-api→rules-grammar) and the quality/status pair (form-quality→status-framework)
as element dependencies.

### Reproduce

```bash
FORMCREATOR_API_ID="<api-id>" JDX7_FORMCREATOR_API_KEY="<api-key>" \
JOGET_URL="http://localhost:8077" ./reference-app/create-reference-app.sh
# verify the four elements persisted:
psql "host=localhost port=5432 dbname=jwdb user=joget" -tAc \
  "select json from app_form where appid='platformRef' and formid='ppRefForm';" \
  | grep -oE 'ConcatFieldElement|LookupFieldElement|RuleEditorElement|QualityBannerElement'
```

Credentials are passed transiently in-shell only — never committed to the repo.

### Notes / follow-ups
- The reference form's `loadBinder` uses `WorkflowFormBinder`. `form-prefill`'s
  `FormPrefillLoadBinder` is load-verified as a bundle (L3 above) but a near-empty prefill config
  independently fails form registration; hardening that binder against empty config is a tracked
  follow-up before wiring it into the reference form.
- `joget-advanced-filters`, `wf-activator` and `tree-menu` are navigation/datalist/process plugins,
  not form elements — they are covered by the bundle-load proof above; a datalist + userview
  scenario extending `platformRef` is the natural next increment.
