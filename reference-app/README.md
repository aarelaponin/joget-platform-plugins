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
