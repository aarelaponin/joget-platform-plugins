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

## Next (functional reference app)

Author one app exercising each plugin (per the `docs/TEST-STRATEGY.md` scenario matrix) via the
`form-creator-api` REST endpoint already configured on jdx7, then run per-plugin acceptance checks.
