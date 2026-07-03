# Plugin consolidation — program plan

**Status:** in progress · **Owner:** Aare Laponin · **Last updated:** 3 Jul 2026
**Repo:** `joget-platform-plugins` (`git@github.com:aarelaponin/joget-platform-plugins`)

This is the umbrella plan for turning scattered, project-embedded Joget plugins into a single,
version-controlled platform. It ties together the two companion docs — `MIGRATION-BACKLOG.md`
(the per-plugin list) and `DAS-EXTRACTION-PLAN.md` (the hard extraction) — into one program view:
where we are, what is next, and the rules that keep it clean.

## 1. The problem

Reusable plugins were scattered across six-plus homes — `lst-frm-prj/plugins`,
`lst-frm-prj/gs-plugins`, GAM's `gam-plugins`, `03_debt_management/shared-plugins`, the
`cmbb-plugins` bundle, and a couple of standalone repos. Some genuinely generic capability
(the approval service, the status machine) is trapped **inside** a project bundle. The result
is duplication and copy-paste reuse: no single home, no versioning, no way to pin.

## 2. Objective and the invariant

Make plugins **extensions of the Joget development platform, not components of any one project** —
accessible to any project, version-controlled, and free of project dependencies.

Scope: this platform serves **any spec-to-code-on-Joget development**. It is not GovStack-specific
and not case-specific — GovStack Building Blocks and domains like tax administration or banking are
lenses over the same horizontal platform, not its boundary.

> **The invariant: a plugin NEVER depends on a consuming project.**
> Projects (and `joget-spec-kit`) depend on plugins; the arrow only points one way. A plugin that
> imports project code is disqualified until it is genericised.

## 3. Principles

- **Reuse tiers decide fate.** `G` (generic-Joget) → promote as-is · `P` (pattern-reusable) →
  genericise then promote · `X` (project-specific) → stays in its project, cite the pattern only.
- **Promote, don't copy.** A plugin lives in exactly one home. When a second project needs it, it
  is promoted here and pinned — never pasted into the second project.
- **Provenance scrub before promotion.** No client/project names in shipped code, comments,
  resources or samples (per `MIGRATION-BACKLOG.md` §Provenance).
- **Registry is the single source of truth.** `registry.yaml` lists every plugin and its config
  contract; it is the seam `joget-spec-kit` generates against without compiling any Java.
- **Publish + pin, never vendor.** Plugins build to versioned artifacts; consumers pin a version.

## 4. Target end state

Four repos, none depending on a project:

```
joget-claude-skills    → the authoring method (how a human/agent builds)
joget-spec-kit (jkit)  → the how-to-build engine (spec → artefacts); reads registry.yaml
joget-platform-plugins → THIS repo — the runtime extensions (the JARs Joget loads)
project repos          → depend on all three by version pin; own only their app model + assets
```

## 5. Capability taxonomy

Plugins are organised by **capability**, not by source project:

- **Foundation** (`category: foundation`) — horizontal, domain-agnostic capability that works for
  any Joget app: form-prefill, form-creator-api, status-framework, and the three coming out of the
  case app (event-chain, status-manager, decision-approval).
- **Domain packs** (`category: pack:<domain>`) — capability aligned to a domain: `pack:registration`,
  `pack:payments`, `pack:information-mediator`, etc. A pack that maps to a GovStack Building Block
  also carries a `govstack_bb` tag (verified vocabulary from specs.govstack.global: Cloud
  Infrastructure, Consent, Content Management System, Digital Registries, E-Marketplace, E-Signature,
  GIS, Identity, Information Mediator, Messaging, Payments, Registration, Scheduler, Wallet).

GovStack BBs are a tagging overlay on the packs, not the organising axis.

## 6. Phased roadmap

| Phase | Scope | Risk | Status |
|---|---|---|---|
| **0 — Scaffold** | mono-repo, parent POM, registry + schema, README, backlog, DAS plan | low | ✅ done (`190bf0f`) |
| **1 — Easy wins** | promote the already-generic plugins: `joget-form-prefill`, `form-creator-api`; register `joget-status-framework` in place | low | ✅ done (`7cd1298`) |
| **2 — Foundation core** | extract the horizontal capabilities trapped in the case app (`cmbb-plugins`): `joget-event-chain` → `joget-status-manager` → `joget-decision-approval`; re-point DMBB; regression | **high** | ▢ in progress |
| **2b — Foundation (as-is)** | promote the clean, generic Lesotho/GAM plugins: `joget-lookup-field`, `joget-concat-field`, `joget-advanced-filters`, `embedded-datalist`, `wf-activator`, `tree-menu`, `form-quality-runtime` | low | ▢ ready — independent of Phase 2 |
| **2c — Rules engine** | promote the self-contained business-rules DSL: `rules-grammar` + `joget-rules-api` + `joget-rule-editor` | low–med | ▢ ready — independent of Phase 2 |
| **3 — Domain packs** | promote BB-aligned packs: `pack:registration` and `pack:gis` (both exist as code in FRS), then `pack:payments`, `pack:information-mediator` (net-new); each `govstack_bb`-tagged | medium | ▢ later |
| **4 — Publish + CI** | enable `mvn deploy` to GitHub Packages; registry↔build drift check; provenance forbidden-strings gate in CI | low | ▢ later |

Phases 0–1 are complete. **This table is the single authoritative roadmap.** Phases **2b** and
**2c** are the cheapest reuse wins on the board (clean coupling, no cmbb dependency) and can run
before or in parallel with the harder Phase 2 extraction; phase 4 can run in parallel once the
first `mvn deploy` credentials are in place. The evidence behind 2b/2c/3 — every plugin classified
with its coupling — is in [PLUGIN-INVENTORY.md](PLUGIN-INVENTORY.md).

## 7. Current status snapshot

Three plugins consolidated, registry at v0.2.0, reactor builds green to `~/.m2`:

| Plugin | Coordinates | How | State |
|---|---|---|---|
| `joget-form-prefill` | `com.fiscaladmin.joget:joget-form-prefill:1.0.0` | module 1 (promoted) | active · 12 tests |
| `form-creator-api` | `global.govstack:form-creator-api:8.1-SNAPSHOT` | module 2 (promoted, scrubbed) | active |
| `joget-status-framework` | `global.govstack:joget-status-framework:8.1-SNAPSHOT` | registered in place (own repo) | active · pinned, not vendored |

Commits on `origin/main`: `190bf0f` (scaffold + module 1) · `7cd1298` (module 2 + registration).

## 8. Phase 2 — Foundation core (capabilities trapped in the case app)

The `cmbb-plugins` bundle is one monolith (~26 plugin entrypoints, ~40 services under
`com.fiscaladmin.mtca.cmbb`). Three generic capabilities come out, in dependency order so each new
module only depends on already-promoted modules — never back on the project:

1. **`joget-event-chain`** (leaf) — `CaseEventWriter`, `ChainVerifyService`, `CaseRefGenerator`.
2. **`joget-status-manager`** — `StatusManager`, `MmConfigService`, `GuardContext`,
   `TransitionGuard`, `GuardPhase` (+ the `mm` metamodel config shape).
3. **`joget-decision-approval`** (DAS) — `ApprovalService`, `ApprovalInbox`, `AuthorityResolver`,
   `MatrixValidator`, and the gate/delegate/sweep/authority engines. **The one real obstacle** is
   `ApprovalEffects`, which hardcodes `actionType → DMBB service` calls; it is inverted with a
   consumer-registered `DecisionEffect` SPI so the platform names no DMBB class. Full design in
   `DAS-EXTRACTION-PLAN.md`.

Each extraction also does the namespace rename `com.fiscaladmin.mtca.cmbb.*` →
`com.fiscaladmin.joget.*` and updates the moved engines' `properties/*.json` descriptors.

## 9. Distribution & publishing

- **Build** on a workstation (JDK + Maven); the Cowork sandbox has no Maven.
- **Local proof:** `mvn -pl plugins/<m> -am clean install` → JAR to `~/.m2` (done for both modules).
- **Publish:** `mvn deploy` to **GitHub Packages** — needs a PAT (`write:packages`) in
  `~/.m2/settings.xml` under server id `github`. Credentials live there, never in the repo.
- **Consume:** projects and `joget-spec-kit` pin `groupId:artifactId:version` (`provided` scope);
  the deploy step drops the pinned JAR into `<joget>/wflow/app_plugins/` (hot-reload ~10s).

## 10. Governance & definition of done

Per promoted plugin:

- [ ] Own Maven module under `plugins/`, inherits the parent POM.
- [ ] `mvn -pl plugins/<m> -am clean install` green; tests pass.
- [ ] Namespace scrubbed to `com.fiscaladmin.joget.<plugin>`; provenance grep clean.
- [ ] `registry.yaml` entry with its `contract` (the config keys generators target).
- [ ] Consuming project re-pointed to the pinned artifact; its in-project copy retired; regression green.
- [ ] (Phase 4) `mvn deploy` to GitHub Packages; CI drift + forbidden-strings checks pass.

## 11. Risks & mitigations

| Risk | Mitigation |
|---|---|
| DAS extraction breaks DMBB approvals | keep DMBB's effect bodies; invert via SPI; gate on `run_t30`/`run_t31` green |
| OSGi classloader / cross-bundle wiring after split | start with static registry (same runtime); promote to OSGi service only if a 2nd consumer needs it |
| Registry drifts from built artifacts | CI drift check (Phase 4); the `joget-claude-skills` MANIFEST drift is the cautionary tale |
| Client identifiers leak into a shipped plugin | provenance grep is a DoD gate and a CI forbidden-strings check |
| Version-pin churn across projects | semver + the config contract as the public API; breaking = major bump only |

## 12. Decisions log

- Names **`joget-spec-kit` / `jkit`** and **`joget-platform-plugins`** confirmed.
- **Mono-repo** (multi-module Maven), not one-repo-per-plugin — the cmbb three have internal deps.
- **GitHub Packages** for distribution (real Maven resolution); local `~/.m2` proves the loop meanwhile.
- **`joget-status-framework` registered in place**, not vendored — it is already a standalone
  Apache-2.0 repo, so absorbing it would fork it and lose its history.
- Registry schema **relaxed** so external entries need no `module`/`contract`.

## 13. Immediate next actions

1. Phase 2, step 1: extract **`joget-event-chain`** (leaf, lowest risk) as module 3; build green.
2. Then **`joget-status-manager`**, then **`joget-decision-approval`** with the SPI inversion.
3. Re-point `cmbb-plugins`/DMBB to the pinned platform modules; run `run_t30` + `run_t31` + full regression.
4. Phase 4 whenever ready: add the PAT and run the first `mvn deploy`; wire the CI drift + provenance checks.
