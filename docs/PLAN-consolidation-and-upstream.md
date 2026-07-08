# Executable plan v2 — Consolidation + Upstream (S2C-01 phase G)

_v2, 2026-07-08. Supersedes v1. Incorporates REVIEW-PLAN-consolidation-and-upstream.md
(R0–R7) and the sector-neutral topology. Companion to PROBLEM + ADVISORY + REVIEW.
Two tracks: **C** = consolidate scattered assets into one platform; **G** = solidify the
upstream / close the three gaps._

**How to read.** Each task: **ID · repo/tier · action · verify (exit criterion) · depends-on.**
"Done" = the *verify* passes. Governing principle: **prove by doing one real slice, then
extract the method** — the heavier upstream tooling (G7/G8) is scaffolded lean now and
hardened *from* the proving slice (G9), not specified ahead of it.

---

## 0. Framing — the product is the method, not the tax solution (R0)

The product is **sector-neutral LLM-assisted public-sector digital-transformation delivery.**
Tax administration is the **first testbed**, chosen because the author can judge realism there;
tomorrow the same method runs health, land, licensing. Four asset tiers, and the boundary
between them is the discipline:

- **Method (the product)** — sector-neutral. Ships **zero domain content** (invariant 1). A
  skill that hardcodes "VAT" has leaked testbed into product — the same category error as a
  kit shipping project names.
- **Platform runtime** — the OSGi plugin shelf + the one neutral generator set.
- **Domain reference pack** — per sector, curated, machine-readable, **citable**: the skeleton,
  fragment library, domain checklist, anchor sets, gold exemplar. The method skills consume a
  pack as *input data*; a pack is **replaceable** (tax → health) without touching the method.
- **Evidence + instances (testbeds)** — raw reference material (KG/UA BPMN, module specs) and
  built apps + data. Disposable / regenerable. `ta-ref-arch` is the **tax testbed**, not a product.

---

## 1. Target topology (four tiers)

Dependence flows one way: `method ← domain pack ← instance`; `platform ← consumers`; the method never depends on a pack, a pack never on an instance.

| Tier | Repo / place | Owns | State today → target |
|---|---|---|---|
| **Method** | **joget-spec-kit** (git, remote) | schema, validator, projectors, deploy path, delta registers, the six upstream **skills** + templates, `rules/`. Zero domain content. | canonical; gains lifecycle projector, upstream skills, one deploy path, `rules/` |
| **Platform runtime** | **joget-platform-plugins** (git) | OSGi plugins behind registry contracts **+ the single neutral generator set** + reference-app. | becomes the one generator home; kit pins its version |
| **Domain reference pack** | **`evidence` repo** (git, private), `tax/registration/pack/` | skeleton · fragments · domain checklist · anchor sets (DBM + KG) · gold exemplar, under a `pack.yaml` manifest (id, version, content hash). Conforms to the method-tier **pack contract** (C7). Tax registration is the first pack. | **new** — extracted, curated, versioned, citable by the skills |
| **Evidence + instances** | **`evidence` repo**, `tax/registration/_evidence/` and `…/instance/` | raw KG/UA/specs; the built app + data (regenerable). | **new** — `ta-ref-arch` tax content absorbed here as the testbed |
| (registry) | `~/.joget/instances.yaml` · `MANIFEST.md` | instances; the tier map | MANIFEST records the **tier** of every asset (C0) |

Decision closed (R7.1): the sector content lives in **one private `evidence` repo, sector-foldered** (your choice (b)); within a sector, `pack/` (domain reference pack, citable) is kept distinct from `_evidence/` (raw inputs) and `instance/` (built app).

**Two mechanics keep the pack tier real machinery, not narrative (findings 1–2):** (i) a **pack contract** — the schema a pack must satisfy — lives in the *method* tier (the kit), so sector-neutrality is enforced by a validator, not prose, and the §7 health pack has a contract to fill; (ii) each pack carries its own **`pack.yaml`** (id, version, content hash), so a skill cites `tax-registration@0.1+<hash>` — a coordinate independent of the mixed `evidence` repo's tags and its disposable testbed/instance churn.

---

## 2. Consolidation track (C-series)

| ID | Tier/repo | Action | Verify | Depends |
|---|---|---|---|---|
| **C0** | index | `MANIFEST.md` listing every repo/dir with its **tier** (method / platform / domain-pack / instance), VC status, canonical-vs-fork. | Map exists; every asset in this plan is tiered in it. | — |
| **C1** | evidence | Create the **private `evidence` repo**, sector-foldered; absorb the `ta-ref-arch` tax content into `tax/registration/{_evidence,instance}`; `.gitignore` secrets; commit. (Remote decision resolved: (b).) | Repo exists, sector-foldered; tax testbed tracked; no secrets/keys staged; MANIFEST tiers each item as evidence/instance. | C0 |
| **C2** | plugins | Move `gen_forms`/`gen_datalists`/`gen_workflow`/`build_jwa` from `mt-tca-prj` (untracked fork) into `reference-app/generators/`; carry the `yearRange` edit; scrub project names. | Kit round-trips pass with the neutral home; `grep` finds no project names; `mt-tca-prj` copy deprecated. | C0 |
| **C3** | kit | Pin the generator set by version (`.kit.yaml` / version file), not an ad-hoc path. | `kit gen all` on facility-permit uses the **pinned** generators; version recorded. | C2 |
| **C4** | kit | Make `deploy_dx9.py` the single DX9 deploy; fold `JOGET-DEPLOY-DELTAS` in; deprecate the toolkit's DX9 import (or wrap a fixed toolkit). | One command deploys any block; `DEPLOY.md` → one path. | — |
| **C5** | kit | Fold both RESUME-NOTEs' session conventions into `rules/session-conventions.md`. | Rules in one versioned place; cited by the kit's agent instructions. | — |
| **C6** | kit | Land the **six upstream skills** (§4) as lean v0 scaffolds + the FIS/acceptance template; skill templates cite a pack **by its `pack.yaml` coordinate**, resolved against the C7 contract. | Each skill exists with template/quota/check-script/stop-rule stubs; a scaffold emits a FIS skeleton; a skill scaffolds against the **stub pack** (no real domain content needed). | C5, **C7** |
| **C7** | kit (method) | Define the **domain-pack contract** — the schema a pack must satisfy (`pack.yaml` manifest · `skeleton.yaml` · `fragments/` · `checklist` · `anchors/` · `exemplar`) — a `kit validate-pack` check, and a **stub pack** that conforms. This is what makes sector-neutrality machinery, and it decouples the C-series from the G6 domain work. | `kit validate-pack` passes on the stub; the G6 tax pack validates against the **same** contract; a **health stub** can be scaffolded from the contract with one command. | C0 |

---

## 3. Upstream track (G-series)

### G-band 1 — Close Gap 3 (model → runtime). First: cheapest, fuses the two workstreams, makes even the current app real.

| ID | Tier | Action | Verify | Depends |
|---|---|---|---|---|
| **G1** | kit+plugins | `MAPPING-lifecycle.md`: `entities[].lifecycle` → `mmEntityState`/`mmEntityTransition` + carrier bindings + Activator binding, using the registry-mirror keys. | Every lifecycle field placed; baseline recorded (no projector emits these today). | — |
| **G2** | kit | `project_lifecycle.py`: pure, deterministic, provenance-stamped; refuses invalid. | Unit tests; facility-permit lifecycle → expected config; byte-stable. | G1 |
| **G3** | kit | Wire into `kit gen` + a **golden CI fixture**. | **Live**: a legal transition runs via StatusManager; an illegal one (`draft→issued`) is refused, writes nothing; the event chain re-verifies. Golden so it can't regress. | G2, C3 |
| **G4** | kit | `deploy_dx9` verifies every catalog-bound plugin is **installed on target** before import. | Deploying an app binding a missing plugin **fails loudly** (the prefill/0-rows class becomes impossible). | C4 |
| **G5** | kit+evidence | Fold the five jdx7 patches to source (/jw, admin-visibility, `c_` columns, `frmTpBasic` loadBinder, labels/date). | **Clean regenerate + deploy of `taxRegistration` reproduces live with zero manual patch**; drift check shows no delta (invariant 9). | G3, G4 |

### G-band 2 — Skeleton (the domain asset), design stages, and the schema they need.

| ID | Tier | Action | Verify | Depends |
|---|---|---|---|---|
| **G6** | **domain pack** | Author the **tax-registration reference pack** **conforming to the C7 contract**: skeleton (procedure catalogue) + parameterisation table + domain checklist + `pack.yaml`, synthesised from IMF DBM blocks + Kyrgyzstan 13-process set + Ukraine. Lives in `evidence/tax/registration/pack/`, **not** in the kit or plugins. Runs **in parallel with C6** (which scaffolds against the C7 stub, not this pack). | `kit validate-pack` passes; **(a) extractive** — every skeleton procedure cites its DBM block + KG process + UA annex (S0 DRAFT→VERIFIED applies to the skeleton itself); **(b) bidirectional reconstruction** — every KG-13 and UA-5 item maps to a skeleton procedure and every procedure back to ≥1 source, mechanically checked; **(c)** the human signature attests only the residual judgment (naming, granularity, invariant-vs-parameter). (R2) | **C7**, reference reading |
| **G7** | kit | `kit diff-reference`: machine-readable anchor set; bidirectional gap report; waiver checklist (not a percentage). Pin diff **granularity levels** in MAPPING form: L1 procedure existence · L2 step/lane structure · L3 lifecycle states · L4 data slots (effective dates, thresholds, SoT bindings); state which levels **gate** vs **advise**. | Three controls: naive model → gaps; skeleton → clean; **and a deliberately subtly-wrong model** (VAT present but not effective-dated; lifecycle missing reactivation) → **FAILS**. (R3) | G6 |
| **G8** | kit / skills | The **six upstream skills** (§4) as lean v0. Include the two Annex-B mechanisms that fell out of v1: the **interrogation protocol** (questions-first; unanswered → TO-CONFIRM register routed to the human) and the **mandatory loss report** on the design→L1 compile. | Each skill emits a template-complete artifact; mechanical slot checks pass; DRAFT evidence cannot feed S1; S0 emits a TO-CONFIRM register and **blocks S1 on unanswered blocking questions**; the compile step emits a loss report, and **a non-empty loss report without corresponding named schema gaps fails the gate**. (R4) | G6, C6 |
| **G8.5** | kit | **Schema 0.2 minimal + projector support**, scoped to exactly what the G9 design requires — effective-dated attribute primitive and the 360/detail composite view first; procedure-as-first-class only if the mapping (feature+process+worklist) proves insufficient. **Nothing speculative.** | The G9 model expresses **effective-dated VAT registration and a 360 view in schema**, and the projectors **emit their realization** (not a silent flatten). (R1) | G9 draft (demand-scoped) |

### G-band 3 — Prove on one slice, extract the method.

| ID | Tier | Action | Verify | Depends |
|---|---|---|---|---|
| **G9** | instance | Model the registration **foundation slice** — first-registration + VAT-as-its-own-procedure + inactivate/reactivate/deregister lifecycle — using G6–G8 (partly by hand at first; that's the point). | Reference-diff clean or signed waivers; realism-gate + human sign-off; **L1 validates *with a loss report whose entries are exactly G8.5's scope*** — because at G9 time schema 0.1.6 cannot yet express effective-dating/360, "validates" here means validates-flattened-with-declared-losses, never silently (finding 3; the loss report *is* what defines G8.5's demand); **and every in-scope skeleton procedure yields ≥1 acceptance scenario asserting its lifecycle path (given/when/then over the declared transitions).** (R5) | G6, G8 |
| **G10** | instance | `kit gen all` → `deploy_dx9` → jdx7, lifecycle projector active. | **Live**: a Taxpayer-360 detail view **backed by schema (G8.5)**; VAT registration a *separate* procedure with **enforced** threshold + effective dates; lifecycle transitions enforced + audited; the G9 SQL scenarios pass. | G9, G8.5, G3, G5 |
| **G11** | kit+docs | Runbook from what happened; promote what worked into the kit; update S2C-01 §17-G + the 1.0-criteria status. | Runbook exists; a **second procedure** (employer/PAYE) added by following it with no rediscovery. | G10 |

---

## 4. The six upstream skills (R6) — sector-neutral method; a domain pack is their input

| Skill | Stage | Owns | Stop rule |
|---|---|---|---|
| `s2c-evidence-harvest` | S0 | evidence questionnaire with quotas, citations, DRAFT→VERIFIED tracker, TO-CONFIRM register | DRAFT evidence cannot feed S1 |
| `s2c-procedure-catalogue` | S1a | skeleton instantiation: in / out / adapted per procedure + citation + parameters | skipping a procedure = an explicit out-of-scope entry (→ waiver) |
| `s2c-use-case-model` | S1b | one procedure per pass, evidence-only context; UC spec + BPMN-light; required slots (triggers, lanes, outcome→transition, exception loop) | a UC without an outcome→lifecycle mapping is incomplete |
| `s2c-domain-model` | S2 | fragment composition; every attribute names its source; lifecycle-or-waiver per long-lived entity; parameterisation table | an unsourced attribute is structurally incomplete |
| `s2c-realism-gate` | S3 | wraps `kit diff-reference`; waiver checklist; slot checks; human sign-off; LLM critique advisory-only | no generation before the signed gate |
| `s2c-compile-to-l1` | S4 entry | design → `.app.yaml` + **loss report**; inexpressibles → named schema gaps | a non-empty loss report without schema-gap entries fails |

All six live in the kit (method tier); their templates cite a pack by its **`pack.yaml` coordinate** (`<pack-id>@<version>+<hash>`), resolved against the **C7 pack contract** — so a skill scaffolds against the stub pack (C6) and a real pack (G6) drops in with no code change, and citation survives testbed churn (findings 1–2).

---

## 5. Critical path & first actions

```
C0 ─┬─ C1 (evidence repo + absorb testbed) ──────────────────────────────────┐
    ├─ C2 (de-fork) ─ C3 (pin) ─────────────┐                                 │
    ├─ C4 (one deploy path) ─ G4 ─ G5 ──────┤                                 │
    └─ C7 (pack contract + stub) ─┬─ C6 (six skills, scaffold on stub)        │
                                  └─ G6 (tax pack, oracled) ┐                 │
G1 ─ G2 ─ G3 (Gap 3 live) ─────────────────────────────────┴─ G7/G8 ─ G9 ─ G8.5 ─ G10 ─ G11
```

- **Start now, in parallel:** **C0** (map), **C1** (evidence repo + absorb `ta-ref-arch`), **C2** (de-fork), **C7** (pack contract + stub), and **G1** (lifecycle contract). C1's blocking human decision is closed.
- **C7 decouples the C-series from domain work** — C6 scaffolds the six skills against the C7 **stub** pack, while **G6** fills the real tax pack in parallel; neither waits on the other, and sector-neutrality becomes a `validate-pack` check rather than a claim (findings 1–2).
- **Gap 3 (G1→G3)** is the highest-leverage single item; runs alongside the C-series.
- **G6 (the pack)** is domain work that gates the realism tooling; its verify is mechanical (citations + reconstruction + `validate-pack`), not just a signature.
- **G8.5 is demand-scoped by the G9 draft** — its scope *is* the G9 loss report (finding 3); do the design first, let it declare what the schema cannot hold, then extend the schema to exactly that. That ordering is what stops G10 passing naively.

---

## 6. Glossary lock (R7.3) — mirror verbatim in S2C-01 Appendix B

*procedure* (a skeleton unit) · *procedure catalogue* (S1a output) · *use-case model / UCM*
(the S1b set) · *use case* (one procedure's spec) · *domain model* (S2) · *evidence pack*
(S0) · *domain reference pack* (the per-sector curated skeleton+fragments+checklist+anchors+exemplar)
· *loss report* (the design→L1 compile residue). The plan and the six skills must use these
names verbatim — naming drift across artifacts is a real defect, not cosmetics.

---

## 7. Horizon (R7.4) — the real product test is a second *domain*

The genuine proof of R0 (method is sector-neutral) is not G11's second *procedure* — it is a
second **domain** (health / land / licensing) running through the same method with only a new
domain pack, the method untouched. Recorded as an open item now so it shapes naming and the
method/pack boundary today, though it costs nothing yet. (Single-reviewer bottleneck, R7.2:
G6/G7/G9 gate on one person; write every checklist as training material for a successor — the
checklists *are* how the reviewer role becomes teachable for the low-exposure target audience.)

---

## 8. Definition of done (maps to S2C-01 1.0 criteria)

1. One versioned four-tier topology (§1): generators de-forked + pinned; one deploy path;
   method encoded as skills/templates/`rules/`; the **domain-pack tier** real; a MANIFEST as the map.
2. A declared lifecycle **runs and is enforced** at runtime (Gap 3 closed; golden fixture).
3. **clean regenerate == live** proven (invariant 9); the five jdx7 patches gone from the debt list.
4. One realistic registration slice designed through the **gated upstream** (with a citation-and-reconstruction-verified skeleton, the subtly-wrong control passing, per-procedure scenarios, and schema that expresses effective-dating + 360), generated, deployed, proven live — and the **method extracted from it**.
