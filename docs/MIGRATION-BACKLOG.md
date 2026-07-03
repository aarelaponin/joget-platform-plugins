# Migration backlog — consolidating scattered plugins into the platform

Derived from `KP4_Joget_Asset_and_Skill_Inventory_v0.1.md` §4.2, verified against the live
repos on 3 Jul 2026. The inventory already tiered every plugin; this backlog turns that
tiering into an ordered, provenance-gated migration.

## Reuse tiers (the reuse policy)

- **G — generic-Joget.** Works on any app; code is already project-neutral. Promote as-is.
- **P — pattern-reusable.** The pattern is generic but the code is entangled with a project.
  Genericise (break the coupling), then promote.
- **X — project-specific.** Stays in its project. Do not try to reuse the code; cite the pattern.

## Order of work

Easy wins first (prove the build→publish→pin loop cheaply), then the hard extraction.

| # | Plugin | From | Tier | Action | Status |
|---|--------|------|------|--------|--------|
| 1 | **joget-form-prefill** | `03_debt_management/shared-plugins/` | G | Move as-is; parent-inherit pom; build green | ✅ DONE (module 1) |
| 2 | **joget-status-framework** | `rsr/joget-status-framework` (Apache-2.0) | G | Register/import as-is; add as module or submodule | ▢ |
| 3 | **form-creator-api** | `lst-frm-prj/plugins/form-creator-api` | G | Promote; provenance scrub; pom inherit | ▢ |
| 4 | **joget-decision-approval** (DAS) | `cmbb/plugins/cmbb-plugins` | P | Extract + INVERT effects coupling — see DAS-EXTRACTION-PLAN.md | ▢ |
| 5 | **joget-status-manager** (+ mm metamodel) | `cmbb-plugins` | P | Extract StatusManager/MmConfigService/GuardContext/TransitionGuard/GuardPhase | ▢ |
| 6 | **joget-event-chain** | `cmbb-plugins` | P | Extract CaseEventWriter/ChainVerifyService/CaseRefGenerator | ▢ |
| 7 | identity-resolver → *Identity BB adapter* | `lst-frm-prj/gs-plugins` | P | Genericise external-lookup→fields pattern | ▢ (later) |
| 8 | application-engine → *Application wizard* | `lst-frm-prj/gs-plugins` | P | Genericise grid-seeding pattern | ▢ (later) |
| 9 | decision-engine → *Decision & entitlement* | `lst-frm-prj/gs-plugins` | P | Genericise | ▢ (later) |
| 10 | form-quality-runtime | `lst-frm-prj/gs-plugins` | P | Genericise server-side rules gate | ▢ (later) |

**Stays project-specific (do NOT migrate):** GAM accounting engines (statement-importer,
rows-enrichment, gl-preparator, gl-journalizer), `gam-db-inspect`, Lesotho GIS/UX elements
(joget-gis-ui, parcel-zone-centring, smart-search, concat-field). Cite the pattern, ship no code.

## Dependency order within cmbb-plugins (items 4–6)

The three P-tier extractions from `cmbb-plugins` are layered:

```
joget-event-chain      (CaseEventWriter, ChainVerifyService, CaseRefGenerator)   ← no internal deps
joget-status-manager   (StatusManager, MmConfigService, GuardContext, ...)       ← depends on nothing platform-side
joget-decision-approval(ApprovalService, ApprovalEffects, ...)                    ← uses StatusManager + event log
```

Extract **event-chain → status-manager → decision-approval** in that order so each new module
only depends on already-promoted platform modules, never back on `cmbb-plugins`.

## Provenance scrub (gate before every promotion)

Applies the inventory §7 shipping boundary to source code:

1. **Never in shipped code/comments/resources/samples:** client or project names — GAM, Genesis,
   MTCA, CMBB, DMBB, Lesotho/FRS/farmersPortal, client URLs, credentials, DB names (`jwdb_gam`),
   and sector enumerations used as worked examples.
2. **Rename on promotion** (register the alias): identity-resolver → *Identity BB adapter*,
   application-engine → *Application wizard*, decision-engine → *Decision & entitlement*,
   DAS → *Configurable approval service*.
3. **Package namespace:** `com.fiscaladmin.joget.<plugin>` — `fiscaladmin` (the OÜ) is fine;
   `mtca`/`cmbb` segments are not. This is real refactoring for items 4–6 (today they are
   `com.fiscaladmin.mtca.cmbb.*`).
4. **Test fixtures** may use domain-generic field names (`debtCaseId`, `amount`) — they are not
   client identifiers and never ship in the JAR. Full fixture genericisation is optional follow-up.
5. Every module passes a forbidden-strings grep in CI before `deploy`.

## Definition of done (per promoted plugin)

- [ ] Own Maven module under `plugins/`, inherits the parent pom.
- [ ] `mvn -pl plugins/<m> -am clean install` green, tests pass.
- [ ] Package namespace scrubbed to `com.fiscaladmin.joget.<plugin>`.
- [ ] `registry.yaml` entry with `contract` (the config keys generators target).
- [ ] Provenance grep clean.
- [ ] Consuming project re-pointed to the pinned artifact; its copy retired; regression green.
- [ ] (When remote publishing is enabled) `mvn deploy` to GitHub Packages.
