# Resume note — Joget platform-plugins consolidation (Phase 2)

_Last updated: 2026-07-06. Written so any future session can pick up cleanly._

## 0. One-paragraph summary

We are pulling reusable, project-neutral capabilities out of the MTCA debt-management
Joget bundle (`cmbb-plugins`) into a shared, version-controlled, CI-verified library
(`joget-platform-plugins`), then re-pointing the debt app to consume the shared copies
(delete the local copy, import the platform package at `provided` scope, bind the app's
concrete table ids / defaults at Activator start). Every extraction is proven green three
ways: platform unit tests + manifest smoke, cmbb unit tests, and the FULL live regression
on jdx9. **Phase 2 is well advanced — the shelf now has 17 modules and everything is
committed + pushed. Nothing is broken or half-done.**

## 1. The two repos (both private, SSH remotes, both pushed & clean)

- **Platform (the shelf):** `/Users/aarelaponin/IdeaProjects/rsr/joget/joget-platform-plugins`
  - remote `git@github.com:aarelaponin/joget-platform-plugins.git`, branch `main`, HEAD `5670d90`
  - tag `v1.0.0` exists (first stable shelf).
- **Client (debt app, the consumer):** `/Users/aarelaponin/IdeaProjects/rsr/mt-tca-prj/10-workstreams/itcas-programme/03_debt_management`
  - remote `git@github.com:aarelaponin/debt-management-platform.git`, branch `main`, HEAD `750aaab`
  - the bundle being slimmed is `cmbb/plugins/cmbb-plugins`.
  - NOTE: the `15-ITCAS/...` path is STALE — always use the `10-workstreams/itcas-programme/...` path above.

## 2. What is DONE (all committed, pushed, live-green)

Platform reactor modules (17). The Phase-2 extractions from cmbb, in order:

| Batch | Module | What | Depends on |
|---|---|---|---|
| — | joget-form-prefill, form-creator-api, joget-status-framework, joget-lookup-field, joget-concat-field, joget-advanced-filters, wf-activator, form-quality-runtime, tree-menu, rules-grammar, joget-rules-api, joget-rule-editor | earlier consolidation (form elements, rules engine, etc.) | various |
| 4 | **joget-event-chain** | CaseEventWriter (hash-chained event log), ChainVerifyService, CaseRefGenerator | — |
| 5 | **joget-status-manager** | StatusManager, MmConfigService, GuardContext, guards | event-chain |
| 6 | **joget-decision-approval** | ApprovalService + engines (gate/sweep/delegate/authority-matrix + inbox binder) + **DecisionEffect SPI** (consumer registers effects; platform never names a domain service) | event-chain, status-manager |
| 7 | **joget-case-ops** — LinkService (record-link) + HoldService (hold) | small case side-effects | event-chain (+ status-manager after batch 8) |
| 7b | **joget-sla** | DeadlineService SLA-clock engine, behind an `SlaConfig` interface the consumer implements | event-chain |
| 8 | **joget-case-ops** += PendingInfoService | park/resume pending-info loop | event-chain, status-manager |
| 9 | **joget-tenant** | TenantContext invisible multi-tenancy resolver (pure leaf) | — |

Also done earlier this program: a **live reuse proof** — `examples/gam-status-demo/` in the
platform repo wires status-manager + event-chain into a DIFFERENT app (GAM, jdx8) and proves
it end-to-end (build-link + live OSGi resolution + `GamStatusReuseTest`).

## 3. THE PROVEN EXTRACTION RECIPE (follow this exactly for each new module)

This is the loop we have run 6 times now; it works. Two-track: platform build/test in the
sandbox, live regression on the Mac against jdx9.

1. **Recon** the target service in cmbb (`.../cmbb-plugins/src/main/java/com/fiscaladmin/mtca/cmbb/service/<X>.java`):
   read it fully; list (a) its constructor deps, (b) the domain-specific table ids / event
   types / default strings that must become **settable statics**, (c) all call sites
   (`grep -rn "new <X>(" ...` and `grep -rn "<X>\." ...`), (d) whether a `<X>Test` exists.
   WATCH FOR: a class that is secretly a *utility hub* (many `<X>.staticHelper()` call sites
   across the bundle) or that depends same-package on other cmbb services — that needs an
   untangle step first (see the DeadlineService story in §5).
2. **Scaffold** `plugins/joget-<name>/` in the platform repo. Copy an existing module's
   `pom.xml` (event-chain is the template for a pure-library bundle; case-ops for one that
   depends on other platform modules). Set `<Export-Package>`, `<Import-Package>` (list the
   platform packages you consume, e.g. `com.fiscaladmin.joget.eventchain`), the felix bundle
   config. Library bundles have NO Bundle-Activator.
3. **Port the class(es) scrubbed:** package `com.fiscaladmin.joget.<name>`; replace domain
   table-id constants with `private static volatile` defaults + a public `setFormIds(...)` /
   `setDefaults(...)`; make any hardcoded domain strings (e.g. "dm_supervisor", "MLT") settable;
   inline any helper that came from a domain class; use the platform's own small helper (each
   module carries its own `prop()`-style helpers so it has no cross-consumer dependency).
   If the engine needs config reads it can't own, define a small **SPI interface** in the module
   (like `SlaConfig`, `DecisionEffect`) that the consumer implements — do NOT depend back on cmbb.
4. **Port the unit test** into the module with a mocked `FormDataDao` (and mocked platform
   collaborators). Mirror the cmbb test's assertions. Bind any settable defaults in the test's
   `@Before` to match the values the consumer will bind live.
5. **Register:** add `<module>plugins/joget-<name></module>` to the reactor `pom.xml`, and add a
   `registry.yaml` entry (copy the shape of an existing one: id, artifact, depends_on, summary,
   contract{exports_package, classes, config_keys, carriers}).
6. **Build + install + smoke:** `cd platform && mvn -o -pl plugins/joget-<name> -am install`
   then `python3 tests/manifest_smoke.py` (must say "All manifest/registration checks passed").
7. **Re-point cmbb:** add the `provided` dep + the package to `<Import-Package>` in
   `cmbb-plugins/pom.xml`; swap `import com.fiscaladmin.mtca.cmbb.service.<X>` →
   `import com.fiscaladmin.joget.<name>.<X>` in every consumer; replace any `<X>.F_CONST`
   references with string literals; DELETE the cmbb copy of the class AND its test; add the
   binding calls to `cmbb .../Activator.java` (e.g. `<X>.setFormIds("cm...","cm...")`). If the
   class was a utility hub, do the untangle first (§5).
8. **Build cmbb:** `cd cmbb/plugins/cmbb-plugins && mvn -o clean package` — ALL unit tests green.
   COMMON GOTCHA: unit tests don't run the Activator, so a settable default stays neutral and a
   test expecting the cmbb value fails → add the binding to the test's `@Before` (or to the shared
   `GuardTestHarness` static block for harness-using tests). We hit this for TenantContext and the
   enforcement effects.
9. **Deploy + full regression (LIVE, jdx9):** copy the new/updated jars to
   `/Users/aarelaponin/joget-enterprise-linux-9.0.7-9/wflow/app_plugins/` (the changed platform
   jar(s) + `cmbb-plugins-8.1-SNAPSHOT.jar`), then run the canonical sweep — see §4. Must be
   `GREEN=24 FAILS=none` for run_t02..t25, plus run_t30 8/8 and run_t31 3/3.
10. **Docs + commit both repos:** update `docs/PLUGIN-INVENTORY.md` (mark the capability done,
    trim the "further candidates" list) + `registry.yaml`; commit/push platform, then commit/push
    client. Verify staged diff has no `target/`/`*.jar`/secrets.

## 4. How to run the live regression (copy-paste)

```bash
J9=/Users/aarelaponin/joget-enterprise-linux-9.0.7-9
AP=$J9/wflow/app_plugins
PLAT=/Users/aarelaponin/IdeaProjects/rsr/joget/joget-platform-plugins/plugins
CMBB=/Users/aarelaponin/IdeaProjects/rsr/mt-tca-prj/10-workstreams/itcas-programme/03_debt_management/cmbb/plugins/cmbb-plugins/target/cmbb-plugins-8.1-SNAPSHOT.jar
# copy the CHANGED jars, e.g.:
cp "$PLAT/joget-<name>/target/joget-<name>-1.0.0.jar" "$AP/"
cp "$CMBB" "$AP/"
# canonical sweep (cold-starts jdx9 itself), then the approval suites:
ROOT=/Users/aarelaponin/IdeaProjects/rsr/mt-tca-prj/10-workstreams/itcas-programme/03_debt_management
cd "$ROOT"
RESTART=1 scripts/run_regression.sh          # expect: GREEN=24  FAILS=none
export JDX9_PASSWORD=admin
PY=/Users/aarelaponin/PycharmProjects/dev/joget-deployment-toolkit/.venv/bin/python
"$PY" $(find cmbb dmbb -name run_t30.py | head -1)   # 8/8
"$PY" $(find cmbb dmbb -name run_t31.py | head -1)   # 3/3
```

Environment facts:
- **jdx9** (debt app, Postgres `jwdb_mtca`): install `/Users/aarelaponin/joget-enterprise-linux-9.0.7-9`,
  http://localhost:8089/jw, admin/admin. Restart = kill Bootstrap PID then `./tomcat.sh start`
  (the sweep does this for you with RESTART=1). catalina.out is under `apache-tomcat-11.0.21/logs/`.
- **jdx8** (GAM, Postgres `jwdb_gam`): install `/Users/aarelaponin/joget-enterprise-linux-9.0.7`,
  http://localhost:8088/jw — only needed if revisiting the GAM reuse proof.
- The sandbox has **no** Java/ClickHouse/live data; all mvn/deploy/regression runs on the Mac via
  the desktop-commander shell.

## 5. Gotchas already discovered (don't rediscover these)

- **Utility-hub trap (the DeadlineService story).** Before extracting a class, check whether the
  bundle calls its *static helpers* everywhere. DeadlineService.prop() was used ~164× across ~28
  files. Fix = FIRST move the helpers to a neutral cmbb `Props` util (already exists at
  `cmbb/.../service/Props.java`) and migrate the call sites (pure refactor, verify green), THEN
  extract the engine. Do the same if you find another hub.
- **Cold-boot OSGi order.** Joget installs `app_plugins` jars alphabetically on a cold boot, so a
  consumer that sorts before its platform dependency can fail to resolve. On jdx9 the full sweep's
  cold start has resolved cmbb→platform fine every time; if you ever see "Failed bundle start ...
  missing requirement", hot-fix by removing+recopying the consumer jar (or a `zz-` prefix so it
  installs last). Documented in the GAM example README.
- **Settable defaults vs unit tests** — see §3 step 8.
- **Never hand-edit generated Joget artefacts** — fix the spec/generator and regenerate. (Not
  usually relevant to these Java extractions, but the repo rule.)
- **Secrets** — never commit RFPs/pricing/DB passwords; the `.gitignore` blocks them; the DB
  password for a live seed is read transiently in-shell only.

## 6. WHAT IS NEXT (pick one; all are cmbb → platform extractions via the §3 recipe)

Remaining foundation candidates still inside `cmbb-plugins` (see PLUGIN-INVENTORY.md §A.8 tail):

1. **process-start** — `service/JogetProcessStarter.java` (~43 lines, zero platform deps) and
   possibly `service/WorkflowService.java` (~168 lines). Small, clean; likely the easiest next
   win. Recommend as a new module `joget-process-start`. Recon its consumers first (it starts the
   case-envelope XPDL process by global id).
2. **notification-dispatch** — `service/DispatchService.java` (~256 lines, zero platform deps).
   Medium. A notification/dispatch engine; would be `joget-notify` or similar. Check what config it
   reads (templates/rules) — may want an SPI like SlaConfig.
3. **document/evidence (Mayan)** — `service/DocumentService.java` (~261L) + `MayanConnector.java`
   + `MayanClient.java` + `RestMayanClient.java`. The HARDEST: integrates the external Mayan EDMS.
   Depends on event-chain + status-manager. Do this last / only if wanted; will need the Mayan
   base-URL/credentials made configurable and the REST client carefully scrubbed.

**Recommended next:** process-start (smallest, cleanest), then notification-dispatch, then Mayan.

## 7. Quick sanity checklist before starting a new batch

- `git status` clean in BOTH repos (they are, as of this note).
- `cd platform && mvn -o -q -pl plugins/joget-event-chain -am -DskipTests package` builds (sanity).
- jdx9 reachable: `curl -s -o /dev/null -w "%{http_code}" http://localhost:8089/jw/web/json/workflow/currentUsername` → 400 or 200.
- Then follow §3 from step 1 for the chosen candidate.
