# Plugin Inventory & Reuse Analysis

**One authoritative catalogue of every Joget plugin across the team's projects — so nothing is
built twice.** This is the evidence base behind the Foundation shelf and the Domain packs: what
exists, where it lives, how coupled it is, and what should be promoted, genericised, or left alone.

Scanned 3 July 2026 against the live repositories: **Lesotho FRS** (24 plugins),
**GAM Back Office** (8 plugins), **MTCA CMBB/DMBB** (1 bundle, ~26 plugin entrypoints across 71
classes), plus the 3 already consolidated into this platform.

## Method

Each plugin was read from its `pom.xml` and source: artifactId, version, packaging, description,
size (main classes), and — the key reuse signal — its **coupling**: compile-scope dependencies on
*other* plugins or project artifacts. Provided-scope Joget API (`wflow-*`, servlet) and standard
libraries (jackson, junit, antlr, snakeyaml, jts) are not coupling; a compile dependency on
`gam-framework`, `reg-bb-engine`, `rules-grammar`, etc. is.

**Legend**

- **Tier** — `foundation` (horizontal) · `pack:<domain>` · `project` (stays put) · `tool` (dev-only)
- **Effort** — `as-is` (clean, generic — promote unchanged) · `genericise` (strip domain naming/coupling first) · `extract` (lift out of a bundle) · `leave`
- **Coupling** — `clean` (std/provided deps only) · or the project artifact(s) it depends on

## Headline findings

1. **The best generic capability is not in DMBB.** Lesotho holds a self-contained **business-rules
   DSL** (`rules-grammar` + `joget-rules-api` + `joget-rule-editor`) and a **family of generic form
   elements** — most with zero project coupling. These are the highest-value, lowest-effort
   promotions available.
2. **Clean beats big.** Nine Lesotho plugins have `clean` coupling and are promote-as-is.
3. **No status-framework duplicate.** GAM's `gam-framework` *depends on* `joget-status-framework`
   (it is a thin GAM extension, 5 classes), so there is nothing to de-duplicate — the generic core
   is already the registered plugin.
4. **Two real GovStack-BB packs already exist as code**: Registration (6 plugins) and GIS (2).
5. **Foundation is bigger than the three cmbb extractions** — widen the shelf accordingly.

## A. The Foundation shelf (target)

Horizontal capabilities that belong in this platform, grouped by the work needed to get them there.

### A.1 Already consolidated (active)

| Plugin | Source | Coupling | State |
|---|---|---|---|
| `joget-form-prefill` | DMBB shared-plugins | clean | active · 12 tests |
| `form-creator-api` | Lesotho | clean | active |
| `joget-status-framework` | Lesotho (own repo) | clean | active · pinned |
| `joget-event-chain` | cmbb-plugins (extracted) | clean | active · Phase 2 |
| `joget-status-manager` | cmbb-plugins (extracted) | event-chain | active · Phase 2 |
| `joget-decision-approval` | cmbb-plugins (extracted) | event-chain, status-manager | active · Phase 2 |

*Also consolidated as-is (Phase 2b/2c): `joget-lookup-field`, `joget-concat-field`,
`joget-advanced-filters`, `wf-activator`, `form-quality-runtime`, `tree-menu`, and the
rules engine (`rules-grammar`, `joget-rules-api`, `joget-rule-editor`). The reactor now
carries 14 modules — see `registry.yaml` and the CI reactor summary.*

### A.2 Promote as-is (clean coupling, generic — lowest effort)

| Plugin | Source | Size | What it is |
|---|---|---|---|
| `joget-lookup-field` | Lesotho | 4 | Field element: watch a SelectBox → auto-populate from a related record |
| `joget-concat-field` | Lesotho | 3 | Field element: concatenate values with configurable separator/format |
| `joget-advanced-filters` | Lesotho | 6 | Advanced datalist filter element |
| `embedded-datalist` | Lesotho | 2 | Inline read-only datalist inside a form, with filtering |
| `wf-activator` | Lesotho | 4 | Workflow process activator utility |
| `tree-menu` | GAM | 6 | Tree-navigation userview menu (mature, v8.1.12) |
| `form-quality-runtime` | Lesotho | 11 | Server-side, form-id-driven validation gate (dep: `status-framework` = platform) |
| `joget-smart-search` | Lesotho | 7 | Progressive criteria builder + fuzzy match search element — *scrub "Farmer" naming* |

### A.3 The Rules engine (promote as one set — high value)

A complete business-rules DSL, self-contained (internal deps only):

| Plugin | Source | Size | Role |
|---|---|---|---|
| `rules-grammar` | Lesotho | 12 | ANTLR parser for the Rules Script DSL |
| `joget-rules-api` | Lesotho | 16 | Parser + compiler + field dictionary + ruleset CRUD (dep: `rules-grammar`) |
| `joget-rule-editor` | Lesotho | 3 | CodeMirror editor UI for the DSL |

*`subsidy-eligibility-runtime` (Lesotho, 2 classes) is the domain consumer of this engine — proof
the DSL is already reused, and the template for how a project binds to it. It stays in-project.*

### A.4 Genericise then promote (pattern, some coupling)

| Plugin(s) | Source | Size | Note |
|---|---|---|---|
| `enrichment-workspace` + `enrichment-api` | GAM | 3 + 9 | "Review-and-enrich workspace" pattern (custom userview + REST inline edit/split/merge). Genericise off the enrichment domain |
| `app-def-provider` | Lesotho (gam-namespaced) | 34 | App-definition provider; shared across GAM/Lesotho — assess exact surface before promoting |

### A.5 Extracted from the case app — Phase 2 ✅ DONE

All three extracted, unit-tested, and consumed live by cmbb/DMBB (the bundle now *imports*
them at `provided` scope rather than owning the code). Verified on jdx9: cmbb unit suite
134/134, DAS acceptance run_t30 8/8 + run_t31 3/3, full regression run_t02..t25 24/24 green.

| Module | Source classes (were in `cmbb-plugins`) | Notes |
|---|---|---|
| `joget-event-chain` | CaseEventWriter, ChainVerifyService, CaseRefGenerator, EventEmitter | leaf; settable neutral defaults (event/case form ids) |
| `joget-status-manager` | StatusManager, MmConfigService, GuardContext, TransitionGuard, GuardPhase | dep: event-chain; guards read configurable table ids |
| `joget-decision-approval` | ApprovalService, ApprovalInbox, AuthorityResolver, MatrixValidator, gate/delegate/sweep engines | dep: event-chain + status-manager; **effects SPI inverted** |

**The effects-SPI inversion** is the key design move: decision-approval ships a `DecisionEffect`
functional interface + a static `DecisionEffects` registry (register/get/snapshot/clear). The
approval engines read `DecisionEffects.snapshot()` and never name a domain service; the consumer
(cmbb `ApprovalEffects`) registers its effects — INSTALMENT_PLAN, WRITE_OFF, ENFORCE_ACTION,
ENFORCE_JUDICIAL — at Activator start. The platform therefore has zero compile-time knowledge of
debt/tax domain code.

**Consumer binding pattern** (called once at Activator start, neutral defaults until set):
`CaseEventWriter.setDefaultEventFormId` · `CaseRefGenerator.setDefaultCaseFormId` ·
`ChainVerifyService.setDefaultCaseFormId` · `GuardContext.setFormIds` ·
`AuthorityResolver.registerRoleLevelDefault` · `DecisionEffects.register`.

**Reuse proof (second consumer — GAM).** `examples/gam-status-demo/` wires
`joget-status-manager` + `joget-event-chain` into a *different* app on a *different*
instance (GAM Back Office, jdx8) — a non-debt `gamWidget` entity whose lifecycle lives in
`mmEntityState`/`mmEntityTransition`, driven through `StatusManager.transition` by a small
consumer bundle. Proven three ways: build-time link (the bundle *imports* the platform
packages `[1.0,2)`, copies nothing); runtime resolution (deployed to jdx8, the GAM JVM
resolved and started `com.fiscaladmin.gam.status-demo` against the platform bundles); and
behaviour (`GamStatusReuseTest` 2/2 — legal transition advances + appends `STATUS_CHANGED`,
illegal transition rejected, nothing written). This confirms the extractions are genuinely
horizontal, not DMBB-shaped. *(DX delta observed: on a cold boot Joget installs `app_plugins`
alphabetically, so a consumer that sorts before its platform dependency fails to resolve; a
last-sorting name or a hot uninstall→reinstall fixes it.)*

### A.6 Case-ops (Phase 2, batches 7 + 8) ✅ record-link + hold + pending-info DONE

`joget-case-ops` (depends on event-chain + status-manager) ships three small, project-neutral case
side-effect services extracted from `cmbb-plugins`, all with configurable carrier-table ids and
audited to the event chain: `LinkService` (typed reciprocal record-link with target-type validation),
`HoldService` (assert/release holds with suppression/financial scopes), and `PendingInfoService`
(park a case in its OnHold envelope + open a PROVIDE_INFO task on request; restore the prior state +
close the task on response — the OnHold lookup uses the status-manager `MmConfigService`). Verified:
9 module unit tests green; cmbb re-pointed (imports the package, deletes the local copies, binds the
cm* carrier ids at Activator start); cmbb 118 unit + full regression run_t02..t25 24/24 + run_t30 8/8
+ run_t31 3/3 green live on jdx9.

### A.7 SLA clock (Phase 2, batch 7b) ✅ DONE

`joget-sla` (depends on event-chain) ships the configurable SLA-clock engine `DeadlineService`
extracted from `cmbb-plugins`. It was the bundle's utility hub (`DeadlineService.prop` ×164 across
~28 classes) and was coupled to the SLA/calendar config reader, `AllocationService`, and domain case
semantics — so extraction took two moves: (1) untangle the static helpers into a neutral cmbb `Props`
util so the bundle stopped importing helpers from the SLA engine; (2) lift the engine behind a small
`SlaConfig` interface (slaRows/slaRow/calendarRow/allTerminalCodes/envelopeOf) the consumer implements,
with the carrier-table ids, supervisor role and escalation payload fields made configurable and
`jsonInt` inlined. `CmbbConfigService` now just declares `implements SlaConfig` (zero new code).
Verified: 7 module unit tests green; cmbb 119 unit + full regression run_t02..t25 24/24 + run_t30 8/8
+ run_t31 3/3 green live on jdx9.

### A.8 Tenant (Phase 2, batch 9) ✅ DONE

`joget-tenant` (pure leaf, no platform deps) ships `TenantContext` — the invisible multi-tenancy
resolver (username → tenantCode from an admin-maintained profile mapping, with a configurable safe
default so a single-tenant deployment needs no per-user config). Verified: 4 module unit tests green;
cmbb re-pointed (OpenPhase + DebtIdentificationService import the platform class, WorkflowService's
`DEFAULT_TENANT` literalised, Activator binds `setDefaults(mdUserTenant, MLT)`); cmbb 114 unit + full
regression run_t02..t25 24/24 + run_t30 8/8 + run_t31 3/3 green live on jdx9.

**Further foundation candidates still inside `cmbb-plugins`** (evaluate next):
notification-dispatch, document/evidence (Mayan), process-start.

## B. Domain packs (BB-aligned)

Coherent capability sets aligned to a domain. Promote a pack as a unit; keep its internal deps.

### B.1 `pack:registration` — GovStack BB: Registration, Identity  *(exists as code, Lesotho)*

| Plugin | Size | Role | Coupling |
|---|---|---|---|
| `identity-resolver-runtime` | 9 | Identity verification / foundational-ID resolver | clean |
| `application-engine-runtime` | 10 | Application submission — seeds eligibility/benefit on create | clean |
| `reg-bb-engine` | 64 | Core runtime — renders citizen application screens from the model | `status-framework` |
| `reg-bb-publisher` | 4 | Service publisher — validates & exposes an `mm_service` | `reg-bb-engine` |
| `processing-server` | 39 | Registration receiver | snakeyaml |
| `doc-submitter` | 28 | Document submission | mysql-connector, snakeyaml |

*A near-complete Registration BB reference implementation. Large; promote as its own effort, not as
part of the Foundation core.*

### B.2 `pack:gis` — GovStack BB: GIS  *(exists as code, Lesotho)*

| Plugin | Size | Role |
|---|---|---|
| `joget-gis-server` | 16 | REST geometry ops — calculate/validate/simplify/overlap (JTS) |
| `joget-gis-ui` | 5 | Polygon capture form element (GPS walk + desktop draw) |

### B.3 `pack:ledger` (accounting) — GAM, banking-domain  *(pattern-first)*

`gl-journalizer` (24), `gl-preparator` (22), `rows-enrichment` (29), `statement-importer` (16).
A double-entry posting pipeline. Banking-specific — **ship the patterns** (pipeline, API-plugin,
custom-workspace), not the code, unless a second ledger use-case appears.

### B.4 Future packs (net-new, from the KP4 gap list)

`pack:payments` (payment-rail adapter) · `pack:information-mediator` (X-Road / GovStack-IM client).
Nearest ancestors exist but the connectors are new builds.

## C. Project-specific & tooling (leave in place)

- **Lesotho:** `farmer-derived-plugin` (farmer domain), `subsidy-eligibility-runtime` (subsidy —
  but keep as the rules-engine consumer example), `alignment-diagnostic` (dev **tool**).
- **GAM:** `gam-framework` (thin GAM constants on top of `status-framework` — project-side).
- **CMBB/DMBB:** all domain engines — allocation, collection-MI, debt-identification, debtors-list,
  default-assessment, enforcement (action + config), escalation, payment, relief, write-off,
  strategy-admin, and the gold-mart / outcome JDBC gateways.

## D. Dedup & overlap register

| Overlap | Verdict |
|---|---|
| `gam-framework` vs `joget-status-framework` | **Not a duplicate** — gam-framework depends on status-framework and adds GAM constants. Ship only status-framework; leave gam-framework in GAM. |
| `joget-lookup-field` vs `joget-form-prefill` | **Complementary** — lookup-field is field-level live watch; form-prefill is form-level on-load. Keep both; document the boundary; consider unifying later. |
| `form-quality-runtime` vs `joget-rules-api` | **Converge later** — both are "rules". Quality is a submission gate; rules-api is a general DSL. A future quality-on-rules-api is possible; keep separate for now. |
| `reg-bb-engine` vs the spec-kit generators | Registration engine renders screens at runtime from a model; the kit generates artefacts at build time. Different mechanisms — no conflict, but note the philosophical overlap. |

## E. Raw inventory (per project)

### E.1 Lesotho FRS — `lst-frm-prj/plugins` (24)

| Plugin | Size | Coupling | Tier · Effort |
|---|---|---|---|
| joget-status-framework | 5 | clean | foundation · consolidated |
| form-creator-api | 26 | clean | foundation · consolidated |
| joget-lookup-field | 4 | clean | foundation · consolidated |
| joget-concat-field | 3 | clean | foundation · consolidated |
| joget-advanced-filters | 6 | clean | foundation · consolidated |
| embedded-datalist | 2 | clean | foundation · deferred (jakarta dep) |
| wf-activator | 4 | clean | foundation · consolidated |
| form-quality-runtime | 11 | status-framework | foundation · consolidated |
| joget-smart-search | 7 | clean | foundation · genericise (naming) |
| rules-grammar | 12 | clean | foundation · as-is (rules set) |
| joget-rules-api | 16 | rules-grammar | foundation · as-is (rules set) |
| joget-rule-editor | 3 | clean | foundation · as-is (rules set) |
| app-def-provider | 34 | snakeyaml | foundation · genericise |
| identity-resolver-runtime | 9 | clean | pack:registration |
| application-engine-runtime | 10 | clean | pack:registration |
| reg-bb-engine | 64 | status-framework | pack:registration |
| reg-bb-publisher | 4 | reg-bb-engine | pack:registration |
| processing-server | 39 | snakeyaml | pack:registration |
| doc-submitter | 28 | mysql-connector | pack:registration |
| joget-gis-server | 16 | JTS | pack:gis |
| joget-gis-ui | 5 | clean | pack:gis |
| subsidy-eligibility-runtime | 2 | joget-rules-api | project (rules consumer) |
| farmer-derived-plugin | 2 | clean | project |
| alignment-diagnostic | 6 | snakeyaml | tool |

### E.2 GAM Back Office — `gam-bank/_plugins` (8)

| Plugin | Size | Coupling | Tier · Effort |
|---|---|---|---|
| tree-menu | 6 | clean (provided Joget/Spring) | foundation · consolidated |
| enrichment-workspace | 3 | clean | foundation · genericise (pattern) |
| enrichment-api | 9 | gam-framework | foundation · genericise (pattern) |
| gam-framework | 5 | status-framework | project (thin extension) |
| statement-importer | 16 | gam-framework | pack:ledger · pattern |
| rows-enrichment | 29 | gam-framework | pack:ledger · pattern |
| gl-preparator | 22 | clean | pack:ledger · pattern |
| gl-journalizer | 24 | clean | pack:ledger · pattern |

### E.3 MTCA CMBB/DMBB — `cmbb/plugins/cmbb-plugins` (1 bundle, 71 classes)

Originally one monolithic bundle. The three foundation capabilities — event-chain, status-manager,
decision-approval — have been **extracted to this platform (Phase 2, done — see §A.5)**; the bundle
now imports them at `provided` scope. Everything else is debt/tax domain (see §C). Bundle-level
coupling: openpdf, clickhouse-jdbc (used by reporting / gold-mart engines, not by the foundation
classes).

## F. Reuse roadmap

This inventory feeds the **single authoritative roadmap** in
[CONSOLIDATION-PLAN.md §6](CONSOLIDATION-PLAN.md#6-phased-roadmap). The findings here added phases
**2b** (promote-as-is Lesotho/GAM foundation) and **2c** (the rules engine) — both independent of
the cmbb extraction and the cheapest reuse wins on the board. See that table for phase status; this
document is the evidence (what exists, coupling, effort) behind it.

---

*Method note: figures are class counts from source; coupling is compile-scope dependency on
non-standard artifacts. Re-run the scan when a source project changes. This inventory feeds
`registry.yaml` (as plugins are promoted) and `MIGRATION-BACKLOG.md` (the per-plugin work).*
