# Executable plan v3 — 1.0 close-out + upstream maturation

_v3, 2026-07-10 · **revision v3.1 same day**: incorporates the dimensions-8/9 extension
(METHOD-EXT-dimensions-8-9-config-interop.md; S2C-01 v0.8) — deltas marked **[v3.1]**
throughout, new tasks A4/A5, E1, B4. Supersedes PLAN v2 (its C-series and G-bands 1–3 are
substantially done) and absorbs PLAN-upstream-implementation (W-series) for what remains.
Grounded in a fresh audit of the kit, the evidence repo, and `docs/1.0-readiness.md`._

_**Revision v3.2, 2026-07-10 (evening)** — reconciled to **S2C-01 v0.9.4**, the standing
architecture reference this plan is §20's roadmap pointer for. What changed: the volume was
re-authored (five parts; knowledge promoted to **Layer 0 — the knowledge layer**; ten
invariants incl. #10 chain of custody; nine-dimension Part II at full depth), passed an
independent QA pass (QA-BRIEF/QA-REPORT-S2C-01, 2026-07-10) and one same-day fix cycle;
ADR-057 closed the five known technical gaps; companion docs re-pointed to the v0.9 section
numbering (register = §19, roadmap = §20, watch = §11.4). Deltas marked **[v3.2]**. The
plan's vocabulary now follows the volume: the decision inventory + evidence pack + scenarios
+ signed readiness checklist = the **design ledger**, Layer 0 of the platform._

## 0. State of play (audited, not remembered)

**Shipped and tested since v0.5.2 was written:** all projectors + emitters (forms, datalists,
userview, dashboards, workflow, **lifecycle** with mm* seed emission, reports, seed, views;
trace/suite/regbb/coverage), `kit diff-reference` with the three discrimination controls as
real tests, ten D-rules with fixtures, big-three contracts + mirror-sync gate (registry 0.9.1),
`kit watch`, drift with four verdicts, anchor-denominated coverage, deploy_dx9 with the
plugin-dependency gate, and the **evidence repo** with the tri-source tax-registration anchor
pack, the realism-gated foundation slice (existence 100% / realised 100%; naive control 7.1%),
loss report, acceptance scenarios, and an E2E deploy result. **[v3.2]** 185 tests green in CI;
18 `kit` verbs (incl. `harvest` — ADR-057 D2 reverse-engineers a deployed `.jwa` into a DRAFT
L1 model for the gated new-build, proven on `taxRegFdn.jwa`); the five known technical gaps
closed as one batch (ADR-057: xpdl loud-warn on `--db-import`, harvest, maturity ladder,
wf-activator strict contract, realism proof as a standing CI gate). All seven 1.0 criteria met
per `docs/1.0-readiness.md` (2026-07-10), ratified by ADR-041/042/043/045–054 + 057.

**Still paper:** the six upstream skills (S0→S2) are 27–30-line v0 scaffolds; the two helper
scripts they carry are untested and one (`reference_diff.py`) is superseded by the tested
tool; no decision-record schema, no design-folder convention, no citation check, no DM
answer-key CI fixture, no dimension templates (incl. dimension-7/UX), no UX pattern shelf.
**[v3.2]** Stale surfaces largely cured: the S2C-01 body-vs-inventory contradiction and the
`--db-import` gap are closed (v0.9.4; ADR-057 D1); `TRACKER.md` remains the one stale
surface (H1).

**Therefore:** one workstream is the product's actual frontier (M — upstream maturation);
everything else is sequencing (A), parked externals (B), or hygiene (H).

---

## 1. Bucket A — owner sequencing, now resolved

**A1 · S2C-01 authoring — DONE through v0.9.4 (2026-07-10).** v0.6 re-scored the inventory
and closed the register (§16 then, §19 now); v0.7 restructured the volume (evidence-based
§2.4, the upstream as §4, RegBB → Appendix C); v0.8 added dimensions 8–9 and the ecosystem
baseline. **[v3.2]** v0.9 re-authored the volume around the whole method — five parts, the
upstream as full Part II, knowledge promoted to **Layer 0**, ten invariants (#10 = chain of
custody), a 14-figure house programme — and v0.9.4 closed the findings of an independent QA
pass (QA-REPORT-S2C-01-2026-07-10: 4 blockers, 17 majors; verdict after fixes READY FOR v1.0
TAG). The volume is document-as-code under `_docs/src/` (markdown + figure scripts + build
script; two-pass static TOC; scripted checks + full-page render review each build).
Remaining owner steps: read-through, verify the code-block tint in Word, then tag the volume
1.0 alongside A3 and archive v0.6–v0.8 copies per the one-canonical-source rule.

**A4 · [v3.1] Ratify the MDM Register (MTCA-MDR-001).** The dimension-9 allowlist has
authority only once its classifications move from "proposed" to ratified; its §7 edge cases
(split facets, risk-vocabulary family, reason-code pattern, document-type/DMS boundary,
org-unit home) become standing TO-CONFIRMs with named owners in the data-governance office.
*Blocks: E1, the register test in M1.*

**A5 · [v3.1] Ratify the two-zone configurability model as an ADR.** One ADR adopts
CP-1…10 + INV-1…5 (DMBB Configurability Approach Spec) as platform rules; the spec is
already written to be citable. *Blocks: dimension-8 template authority in M3.*

**A2 · Schema v0.2 minor — sequenced as workstream V below.** Scope, demand-driven by the
foundation slice's own `loss-report.yaml`: effective-dated attribute primitive, the 360/detail
composite, and `row_scope {by: creator|role|org_unit}` (ADR-047; requirements source = the
dimension-7 worklist slot). **In** only if the loss report or the pilot demands it; ADR-048's
conditional required blocks enter v0.2 only if the approval-service config contract publishes
first, else v0.3. The assembler (ADR-046) is trigger-based and NOT in v0.2.

**A3 · 1.0 declaration.** With criteria met and v0.6 authored, declaring 1.0 is a release
act, not an engineering gap. Recommendation: tag kit 1.0 after M2's pilot confirms the
upstream method on a second module (so 1.0's criterion #6 rests on two runs, not one).

## 2. Bucket B — external-sourcing-dependent, parked with explicit triggers

| Item | Trigger to activate | Prepared action |
|---|---|---|
| B1 · RegBB conformance-pack generator (ADR-052) | a bounded web-sourcing session | source the GovStack RegBB requirement set as the external denominator (same discipline as ADR-044: cite, verify, never reconstruct); then emit the Lesotho-scheme pack from `emit_regbb`'s model view |
| B2 · Agriculture second sector (ADR-053) | a real engagement supplying primary sources | pack contract already exists (`examples/pack-stub/`); authoring follows the tax pack's citation + reconstruction oracle |
| B3 · RegBB multi-peer envelope (ADR-051) | a second live peer | envelope fields specified; implement in emitter + backbone when triggered |
| B4 · **[v3.1]** Shared data kernel (RMDBB) | CAD-MDBB published with its four contracts (resolve/validate/subscribe/map + resolve_party) | contracts enter the registry mirror as catalog components; `vocabularies[].binding: kernel` compiles to kernel-backed selects instead of local projections; config-change-as-case-type built on the existing case machinery. NOT a precondition for capture — kernel-bound decisions compile to local projections until then, loss-report-recorded |

B1 is the only one startable on demand — it needs a session, not an event. Do it when the
conformance pack has a consumer (donor review, GovStack submission), not before.

## 3. Bucket H — hygiene (small, do alongside M0)

- **H1** TRACKER.md refreshed or replaced by a pointer to `1.0-readiness.md` (one canonical
  status surface — the stale-surface defect class).
- **H2** Skills' superseded helpers removed/redirected: `s2c-realism-gate` calls
  `tools/diff_reference.py`; `loss_report.py` gets a test or is folded into compile tooling.
- **H3 · DONE (2026-07-10, ADR-057 D1)** `--db-import` now detects a `package.xpdl` in the
  `.jwa` and emits a loud NOTE that it must be HTTP-imported — the fail-loudly branch of the
  dependency-gate pattern (DD-010, tested). Carrying the package over the DB path stays
  possible future work, not a gap.
- **H5 · [v3.1] House document renderer + document QA — [v3.2] substantially DONE.**
  Delivered with S2C-01 v0.9.x, on the pandoc-plus-postfix path rather than docx-js:
  `_docs/src/` holds the canonical markdown, `make_figures.py` (all 14 figures regenerate in
  house style) and `build_volume.py` (bordered/shaded tables with repeating header rows,
  styled cover, part dividers, code-block styling, footer template, two-pass static TOC with
  self-checking assertions). The QA gate exists as practice and as reusable assets:
  QA-BRIEF-S2C-01.md (the independent-session QA prompt), scripted consistency checks (TOC
  diff, §-refs, figure captions, invariant numbers), and the full-page fresh-eyes render
  review — run on every rebuild. *Residual:* fold the scripted checks into a reusable
  document-lint CLI and migrate the BA guide (still says seven dimensions) onto the same
  toolchain + a 7→9 content update.
- **H4 · DONE (2026-07-10)** Decision-brief template landed as a method asset:
  `joget-spec-kit/skills/templates/decision-brief-template.md`, extracted from the ratified
  `docs/1.0-structural-decisions.md` (ADR-045…054). Standing rule: any future batch of open
  decisions (e.g. a pre-2.0 register sweep — S2C-01 §19) is put to the owner via this template — context,
  options-with-impact, recommendation, ruling line, sweep-to-ADRs — never as ad-hoc
  questions. Post-ratification a brief is frozen (rationale archive behind its ADRs), is
  never a status surface, and is revisited only by a superseding ADR.

## 4. Bucket M — the frontier: mature S0→S2 from scaffolds to practice

> **Status (2026-07-10): bucket M CLOSED.** M0 ✅ (ADR-058) · M1 ✅ (ADR-058, DM oracle PASS) ·
> M3 ✅ (ADR-059) · M4 ✅ (ADR-060) · **M2 ✅** (elicit pilot on F13-payments — gate green, downstream
> decisions = 0; `method-evidence/f13-pilot/RESULT.md`) · **M5 ✅** (ADR-063 — runbook, skills v1,
> two green runs on unchanged tooling). A4/A5 ratified (ADR-061/062); the dimension-8/9 lint is live.
> Remaining: **A3** tag 1.0 (owner release act, two-run evidence) and **V** schema v0.2
> (demand-driven by loss reports).

**[v3.2]** This bucket builds **Layer 0** — the knowledge layer S2C-01 §4 now carries as a
first-class layer of the platform. Its artefact set (evidence pack + decision inventory +
scenarios + signed readiness checklist) is the **design ledger**; M0–M5 turn that ledger
from paper into formats, checks, and a practice proven on a real module. Enforcement target
throughout: invariant 10 (chain of custody — no decision silently defaulted, uncited, or
unenforced).

Governing principle unchanged: **harden by running, not by specifying.** The registration
slice already proved the gate; what was never run is the *authoring* front — and its elicit
mode has never been exercised at all. The maturation vehicle is therefore a pilot that
registration cannot provide: **one real DM feature in elicit mode** (knowledge in heads),
measured against the ~53-decisions baseline.

**M0 · Formats (the missing W0, ~2 days).** The Layer-0 decision-record schema
(`schemas/upstream/decision-record.schema.yaml`: id · dimension · question · answer · status
DRAFT/VERIFIED/TO-CONFIRM/WAIVED · source doc|designer|assumption · lands_in ·
enforced_by · scenario_refs — the shape S2C-01 §4.1 and METHOD now both carry; WAIVED is a
status, not a source kind); design-folder convention (`design/`: decisions.yaml,
evidence.md, scenarios.yaml, gate-report.md, loss-report.yaml). **[v3.1]** plus the two
ecosystem blocks per METHOD-EXT §2: `change_profile` (zone A|B · class · configurability
Y|P|N · owner · cadence · governance/impact-preview) forced for dimension-2/3/8 decisions,
and `ecosystem` (bucket R|M|C|P · register_ref · binding kernel|local · consumption
sync|projection|snapshot) forced for vocabulary/entity/interface decisions. Retro-fit
check: the foundation slice's existing design artifacts express losslessly in the format.
*Verify: schema validates a 10-record example + the retro-fitted slice, including records
carrying both new blocks.*

**M1 · Checks (the missing W1, ~4 days).** `spec_lint` U-rules (slot completeness,
lifecycle-or-waiver, formula presence, parameter value+unit+owner, ownership per attribute,
failure behaviour per dependency, grain reconciled, scenario coverage per state) +
`citation_check` on FIS/realization tables (cite-or-ASSUMPTION, 100% or exit 1) + the
**DM answer-key CI fixture** (the ~53 decisions from EVIDENCE-DM-forced-slot-extraction as
a reconstructed naive inventory; lint must flag the Class-A gaps, citation_check the ≥9
Class-B items — the method's own regression oracle). **[v3.1]** plus the ecosystem rules:
every Y/P parameter has a complete change profile; every code list carries a bucket +
register citation or FLAG; the **spec-time register test** (a local vocabulary matching an
R-classified register entry fails lint — needs E1's machine-readable allowlist); and
INV-1…5 as lint (dangling name reference, restated value, matrix cell selecting an
unavailable/undefined instrument).
*Verify: pytest green incl. the answer-key assertions; run against the foundation slice →
clean; a fixture module locally defining "currency" → register-test FAIL.*

**M2 · The elicit-mode pilot (the grade, 1–2 weeks calendar).** Pick the next real DM
feature (F13-payments has artifacts but no FIS — ideal). Run the full loop with the v0
skills + M0/M1 machinery: scaffold design folder → interviews (one procedure per pass,
evidence-only context; TO-CONFIRM for what no source answers) → playback each round →
readiness gate → FIS with citation column → build → deploy → scenarios green.
**Measure and publish:** (a) domain decisions made downstream of the gate (baseline ≈3/feature);
(b) citation coverage; (c) gate-to-green time; (d) usability walkthrough on the preview
(3–5 officers, task completion, click counts); (e) **[v3.1]** ecosystem capture rate — the
share of the pilot's parameters carrying complete change profiles and of its code lists
carrying register classifications, without prompting. Every friction point becomes a
skill/template edit — that is the v0→v1 hardening, extracted not speculated.
*Verify: the four numbers exist in the runbook; skills v1 diffs traceable to pilot retro items.*

**M3 · Dimension templates + UX shelf (~5 days, partly parallel with M2).** The **nine**
dimension templates as data (`templates/upstream/dim-*.yaml`, slots phrased from the
EVIDENCE §6 question bank; dimension-7 work-&-workspace per METHOD §3b, seeded from the DM
spec's per-UC frequencies). **[v3.1]** `dim-policy-change.yaml` (operate-vs-define, Y/P/N,
owner/cadence/governance, config effective-dating — interview forms per METHOD-EXT §1;
gold exemplar = the Configurability Spec's §11 worked decomposition) and
`dim-ecosystem.yaml` (bucket classification, one-fact-one-writer, kernel binding,
consumption pattern, temporal contract, interaction contracts, spine, crosswalks). UX
pattern shelf v0 in the platform repo (`patterns/ux/`), seeded by harvesting the DMBB-UX-QA
remediation into named patterns; generators consume them; defects fix patterns, never
screens (ADR-050's maturity ladder applies).
*Verify: instantiating templates for the pilot slice reproduces the questions the pilot
actually needed, incl. the change-profile and register-classification questions;
regenerating DMBB lists from patterns ≥ the remediated state.*

**M4 · Playback completions (~3 days).** Walkthrough generator (scenarios → day-by-day
narrative with decision citations) and the TO-CONFIRM register view (all open questions
across slices, owner-facing). Lifecycle diagrams and the clickable preview already exist —
reuse, don't rebuild.
*Verify: golden renders; every walkthrough step cites a decision id.*

**M5 · Extract and close (~2 days).** Runbook from what actually happened; skills v1
committed; METHOD doc updated from practice (**[v3.2]** its record schema, single-digits
threshold and Layer-0 terminology were already aligned to S2C-01 v0.9.4 on 2026-07-10 —
M5 updates it from *pilot experience*, not for consistency); the G11 test applied to the
*upstream*: a second DM feature (or the next registration procedure) taken through by
following the runbook with no rediscovery. Then A3's 1.0 tag.
*Verify: second run needs zero method edits; downstream-decision count single-digit twice
(the S2C-01 §7.4 measure — zero is the asymptote, not the gate).*

## 5. Workstream V — schema v0.2 (from A2; after M2's loss report confirms scope)

`row_scope` first-class + effective-dating primitive + 360 composite, exactly as demanded by
the union of the foundation slice's and the pilot's loss reports; projector realizations for
each; D-rule/lint updates; facility-permit example extended. ADR-048's if/then blocks ride
along only if the approval contract is published. **[v3.1] Ecosystem landings ride the same
minor:** `vocabularies[].binding: mdm:<domain>` (consume-by-reference, no local rows; compiles
to a local projection until B4 triggers); zone/class/configurability metadata on config
entities; event schema versions + identifier-spine keys (taxpayer id, tax type, period,
idempotency, correlation) on `interfaces`; generated per-zone admin console and resolving
index (CP-8 views) as projector outputs; the conformance-declaration emitter into the CAD.
*Verify: both loss reports re-compile empty (or name only deliberate deferrals); golden
fixtures updated once; the facility-permit example gains one kernel-bound vocabulary and one
zone-classified config entity exercising the new constructs.*

## 5b. [v3.1] Task E1 — the ecosystem baseline, machine-readable

Assemble the organisation-tier input asset the dimension-9 slots cite: `allowlist.yaml`
generated from the ratified Register (MTCA-MDR-001 CSV → id, name, bucket, disposition,
SoR, register_ref), the §7 edge cases as standing TO-CONFIRMs with owners, pointers to the
canonical model, and the kernel-contract stubs (signatures only, pre-B4). Suggested home:
the evidence repo under an organisation folder (owner may relocate — it is org-tier, not
sector-pack-tier). *Verify: `allowlist.yaml` validates; M1's register test resolves against
it; every FLAG row surfaces as a TO-CONFIRM. Depends: A4.*

## 6. Critical path

```
A1 authoring (DONE, v0.9.4 QA-passed; owner read-through + archive pending) ──┐
A4 ratify Register ─ E1 baseline ─┐                                           │
A5 ratify two-zone ADR ─┐         │                                           │
H1/H2 hygiene ─┐        │         │   (H3 DONE ADR-057 · H4 DONE · H5 done, residuals)
M0 formats ─ M1 checks (incl. register test) ─ M2 elicit pilot ─ M5 extract ─ A3 tag 1.0
               M3 nine templates+UX shelf ──┤(feeds M2 mid-run)
               M4 playback ─────────────────┘
M2 loss report ─ V (schema v0.2 + ecosystem landings) ─ G10-class re-proof on the pilot app
B1/B2/B3/B4 parked on triggers
```

## 7. Definition of done (phase G truly closed)

1. One canonical status surface; S2C-01 (v0.9.4, independently QA-passed) matches the audit
   — **[v3.2]** met for the volume itself (body reconciled to §18/ADR-057; verdict READY FOR
   v1.0 TAG); remaining: TRACKER (H1), owner read-through + Word tint check, superseded
   volume copies archived.
2. Decision inventory + checks exist with the DM answer key as CI, including the
   change-profile/ecosystem rules and the spec-time register test (M0–M1, E1).
3. Elicit mode proven on a real module with published numbers — including the ecosystem
   capture rate; skills at v1 extracted from the runs, not ahead of them (M2, M5).
4. **Nine** dimension templates + UX pattern shelf live; usability walkthrough, register
   classification, change profiles and the conformance declaration in the gate (M3).
5. Schema v0.2 closes the recorded losses **and lands the ecosystem constructs** (kernel
   binding, zone/class metadata, spine keys, per-zone consoles + resolving index) (V).
6. Register and two-zone model ratified (A4, A5); the kernel itself remains trigger-parked
   (B4) without blocking any of the above.
7. 1.0 tagged on two-run evidence (A3).
