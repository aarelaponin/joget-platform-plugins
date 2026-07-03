# Reference application & live verification

The integration (L3) layer of the [test strategy](../docs/TEST-STRATEGY.md): prove the plugins
actually load and work in a real Joget DX9 + PostgreSQL runtime, not just that they compile.

**Target instance:** `jdx7` — Joget **DX 9.0.5, PostgreSQL** (`http://localhost:8077`),
install `/Users/aarelaponin/joget-enterprise-linux-9.0.5`.

## Layers here

1. **Plugin-load verification** (`verify-load.sh`) — deploy every built JAR into the instance,
   (re)start it, and assert from the log that each plugin's OSGi bundle **started** with **no
   framework errors**. Catches manifest / Import-Package / inter-bundle dependency problems that
   unit tests and the manifest smoke check cannot. Latest run: `RESULTS.md`.
2. **Functional reference app** (planned) — one Joget app instantiating every plugin (form with
   prefill + lookup + concat + quality banner + rule editor; datalist with advanced filters;
   userview with tree menu; rules API endpoint; wf-activator tool), with scripted acceptance checks
   per the scenario matrix in `docs/TEST-STRATEGY.md`. Built by dogfooding `form-creator-api`
   (already API-enabled on jdx7).

## Run the load verification

```bash
export JOGET_HOME=/Users/aarelaponin/joget-enterprise-linux-9.0.5
./reference-app/deploy-plugins.sh    # copy the built JARs into $JOGET_HOME/wflow/app_plugins
#   ... (re)start the instance: (cd $JOGET_HOME && ./tomcat.sh start) ...
./reference-app/verify-load.sh       # assert every bundle started, no OSGi errors  (exit 0/1)
```

## Create the functional reference app (credential-gated)

`app/reference-form.request.json` is a `form-creator-api` payload that builds a form embedding five
plugins at once — the **form-prefill** load binder plus the **concat-field**, **lookup-field**,
**rule-editor** (which drives **rules-api** + **rules-grammar**) and **form-quality** quality-banner
elements. Creating it is itself a functional check: `form-creator-api` validates and instantiates
each element's plugin class server-side, so a wrong or unloadable class fails the call.

The API key is **not** stored in this repo (it is a `password_env` secret). Export it, then run:

```bash
export JDX7_FORMCREATOR_API_KEY=...          # from your password manager
./reference-app/create-reference-app.sh      # POST -> jdx7; PASS on HTTP 2xx
```

Then open the form (`http://localhost:8077/jw/web/embed/form/platformRef/ppRefForm`) to see the
five plugins render together.

**Still to author** (same pattern, `/formcreator/datalists` and `/formcreator/userviews`): a datalist
using **advanced-filters** and a userview using **tree-menu**; `wf-activator` (a process tool) and
`form-creator-api` itself are exercised by this very flow.
