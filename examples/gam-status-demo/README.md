# gam-status-demo — platform reuse proof

Proves that the extracted **`joget-platform-plugins`** modules
`joget-status-manager` + `joget-event-chain` are genuinely reusable outside the
debt-management (DMBB) build, by wiring them into a **second, unrelated app on a
different instance** — the GAM Back Office (jdx8, Joget 9.0.7).

## What it is

A tiny OSGi consumer bundle (`com.fiscaladmin.gam.status-demo`) that:

- **imports, does not copy** the platform packages
  (`com.fiscaladmin.joget.eventchain`, `com.fiscaladmin.joget.statusmanager`) —
  see the `Import-Package` in the built manifest, versioned `[1.0,2)`.
- at `Activator.start()` performs the **consumer binding** step of the platform
  integration contract: `CaseEventWriter.setDefaultEventFormId("gamEvent")` and
  registers one guard plugin.
- ships **`GamMoveGuard`** (a form post-processor on the `gamMove` trigger form)
  that drives `StatusManager.transition(...)` over a non-debt **`gamWidget`**
  entity whose lifecycle lives in `mmEntityState` / `mmEntityTransition`.

`demo-app/` builds a 5-form Joget app (`gamsm`) — `gamWidget`, `gamEvent`,
`mmEntityState`, `mmEntityTransition`, `gamMove` (+ a userview) — via the proven
`build_jwa.py`.

## Proof status

- **Build-time link** — `mvn -o clean package` compiles the consumer against the
  platform bundles; the manifest imports the two platform packages.
- **Runtime resolution (live, GAM instance)** — deployed to jdx8 `app_plugins`
  alongside the two platform jars; the GAM JVM resolved and started the bundle:
  `PluginManager - Bundle com.fiscaladmin.gam.status-demo started`. This is the
  core claim: a second app's plugin, in a different instance, wires to the
  extracted platform packages at runtime.
- **Behaviour** — `GamStatusReuseTest` (2/2 green) drives the platform state
  machine with GAM's own data: a legal `DRAFT -> ACTIVE` advances the widget and
  appends a `STATUS_CHANGED` event; an illegal `ACTIVE -> DRAFT` throws
  `InvalidTransitionException` and writes nothing.

## Deploy / drive (runbook)

1. `mvn -o clean package` (this module).
2. Copy `joget-event-chain-1.0.0.jar`, `joget-status-manager-1.0.0.jar`, and
   `target/gam-status-demo-1.0.0.jar` into the jdx8 `wflow/app_plugins/`.
   **Name the consumer so it sorts last** (e.g. `zz-gam-status-demo-…`) — on a
   cold boot Joget installs `app_plugins` alphabetically and a consumer that
   installs before its platform dependency fails to resolve; a hot
   uninstall→reinstall (or last-sort name) fixes it.
3. `python3 demo-app/make_gamsm.py && build_jwa.py demo-app/generated gamsm "GAM Status Demo" /tmp/APP_gamsm-1.jwa`, then import+publish.
4. Seed the state machine and drive moves via `demo-app/proof_gamsm.py`.
   *(The jdx8 console login uses the OWASP CSRFGuard token embedded in `/csrf`
   JS — parse the token out of the script, don't use the raw body.)*
