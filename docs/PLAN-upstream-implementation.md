# Implementation plan — the upstream (knowledge → build-ready spec)

_Written 2026-07-09. The technical build plan for METHOD-upstream-knowledge-to-spec.md.
Slots into PLAN v2 as the concrete content of C6/G8 (+ the FIS citation obligation).
Waves W0–W7; each task: **ID · action · deliverable · verify · depends**. "Done" = verify
passes. Nearly everything here is pure Python + YAML/MD — buildable and testable in the
Cowork sandbox; only the pilot's deploy step needs the Mac._

---

## 0. Where things live (method vs instance)

| Asset | Home | Rationale |
|---|---|---|
| Decision-record schema, template set, questionnaire banks, lint/check tools, gate-report generator, readback generators, FIS template | `joget-spec-kit/` → `schemas/upstream/`, `templates/upstream/`, `tools/upstream/` | method tier — sector-neutral, versioned, zero domain content |
| Per-slice design folder: inventory, evidence pack, scenarios, gate reports | app/program repo (evidence repo per PLAN v2 C1): `<slice>/design/` | instance tier |
| Domain pack (skeleton, fragments, checklist, anchors) | `evidence/<sector>/<domain>/pack/` | pack tier (G6; not needed for the DM pilot) |
| Golden fixtures (incl. the DM answer key) | `joget-spec-kit/tests/upstream/` | the method's own CI |

---

## W0 — Formats and schemas (the contract everything else obeys) · ~2 days

**U0.1 — Decision-record schema.** `schemas/upstream/decision-record.schema.yaml`
(JSON-Schema 2020-12, same conventions as the L1 schema: `additionalProperties:false`,
`^x-` escape). Core shape:

```yaml
inventory:
  slice: DMBB-F15-objections          # one file per slice: design/decisions.yaml
  spec_refs: ["08-DM-Req v1.1"]       # the documents this slice draws on
  decisions:
    - id: DEC-F15-001                 # DEC-<slice>-<seq>, immutable
      dimension: state                # state|formula|interpretation|ownership|failure|grain|
                                      # entity|rule|ux|role|integration|param
      question: "Can status 'missed' transition to 'paid'?"
      answer: "Yes — late settlement is legal; transition missed→paid, trigger system."
      status: VERIFIED                # DRAFT|VERIFIED|TO-CONFIRM|WAIVED
      blocking: true                  # blocking TO-CONFIRM stops the slice at the gate
      source: {kind: designer, ref: "A.Laponin 2026-07-12"}   # kind: doc|designer|assumption|waiver
      lands_in: "entities.instalment_payment.lifecycle"       # L1 path (dot notation)
      enforced_by: "project_lifecycle"                        # projector/generator id or "manual"
      scenario_refs: [SC-F15-03]      # scenarios asserting this decision
```

*Verify:* schema validates a hand-written 10-record example; rejects a record missing
`source` or with an unknown `dimension`.

**U0.2 — Template schema + registry.** `schemas/upstream/template.schema.yaml`: a template =
ordered slots, each `slot_id · dimension · question_text · applies_when (entity kind / has
external dep / has amounts…) · blocking (bool) · lands_in_hint`. Templates are *data*, so
packs and projects can extend them without code. *Verify:* the six dimension templates
(W2) all validate against it.

**U0.3 — Design-folder convention.** `<slice>/design/` = `decisions.yaml`, `evidence.md`
(cited pack), `scenarios.yaml` (given/when/then, ids SC-…), `gate-report.md` (generated),
`loss-report.yaml` (generated at compile). Documented in one page. *Verify:* scaffold
command (U4.2) emits exactly this tree.

## W1 — Check tools (the mechanical gate) · ~4 days

All pure/deterministic: read files, emit findings + exit code; no clock, no network;
provenance-stamped reports. One CLI entry: `python3 tools/upstream/spec_lint.py <design-dir>`.

**U1.1 — `spec_lint.py` with the U-rule set** (each rule = one function + one fixture pair):

| Rule | Check |
|---|---|
| U001 | no DRAFT decision feeds the gate; no DRAFT evidence cited by a VERIFIED decision |
| U002 | every `blocking:true` slot is VERIFIED or WAIVED (TO-CONFIRM open ⇒ FAIL) |
| U003 | every long-lived entity (kind main/child with status attr) has a lifecycle decision or a WAIVER |
| U004 | every declared state is reachable and asserted by ≥1 scenario in `scenarios.yaml` |
| U005 | every decision with dimension `formula` has a formula over named fields (regex: field refs present, no bare prose) |
| U006 | every `param` decision carries value + unit + owner + seed target |
| U007 | every entity attribute referenced by decisions has an `ownership` decision (source + snapshot/live) or inherits an entity-level one |
| U008 | every external dependency named in decisions has a `failure` decision |
| U009 | every scenario references only declared entities/states/transitions |
| U010 | every `rule` decision names its enforcement point |
| U012 | every case-type slice has a `grain` decision reconciled to the rule register (explicit `reconciles:` ref) |

*Verify:* fixture pairs green in pytest; **the DM answer-key fixture** (U1.3) produces the
expected findings.

**U1.2 — `citation_check.py`.** Parses an FIS.md; every row in its parameter/state/rule
tables must carry `[DEC-…]`, `[BR-…/DM-FR-…]`, or `[ASSUMPTION-…]`. Emits coverage % and
the uncited list; exit 1 below 100%. Also appends every `[ASSUMPTION-…]` back into
`decisions.yaml` as `source.kind: assumption` (the return channel). *Verify:* run against
CMBB-F03's existing FIS → reports its uncited parameters (baseline, expected non-zero);
against a hand-annotated copy → 100%.

**U1.3 — The DM answer key as golden fixture.** Encode EVIDENCE-DM-forced-slot-extraction
into `tests/upstream/fixtures/dm-answer-key/`: a reconstructed "naive inventory" of what the
DM build implicitly assumed + the spec's rule register. `spec_lint` must flag ≥ the known
Class-A gaps (state machines, formulas, C6, grain…); `citation_check` on a reconstructed
FIS extract must flag the ≥9 Class-B items. **This is the method's own oracle in CI —
if a refactor stops catching the known failures, the build goes red.** *Verify:* pytest
asserts the specific finding ids.

**U1.4 — `gate_report.py`.** Renders `gate-report.md`: slot summary per dimension, open
TO-CONFIRMs, waivers awaiting signature, lint findings, scenario coverage table, and a
signature block (name/date/hash of inventory). The human signs *this*, pre-populated.
*Verify:* golden render for the example slice; hash changes when any decision changes.

## W2 — Templates, questionnaires, FIS column · ~3 days

**U2.1 — Six dimension templates** (`templates/upstream/dim-{state,formula,interpretation,
ownership,failure,grain}.yaml`), each 8–15 slots with `applies_when` guards, phrased as the
questions in EVIDENCE §6 (they are the validated question bank). *Verify:* template schema
valid; instantiating them for a mock instalment slice yields ≥ the 5 relevant EVIDENCE §6
questions.

**U2.2 — Classical-dimension templates** (entity, role, rule, integration) — thinner,
since specs already cover these; slots only for what the DM evidence showed leaks (screen↔UC
binding, role gates per transition). *Verify:* schema valid.

**U2.2b — Dimension-7 template: work & workspace (UX).** `templates/upstream/dim-workspace.yaml`
per METHOD §3b Tier 1: persona registry with daily volumes; per-persona worklist definition
(scope, priority logic, default sort); per-list column/filter slots ("which columns do you
scan? what do you filter by?"); per-form prefill/default/verify split; bulk-op thresholds;
empty/error states; role navigation map. Mine the DM spec's per-UC frequency fields as the
example seed. *Verify:* template schema valid; instantiated for the mock slice it demands
the worklist definition that DMBB-UX-QA had to retrofit.

**U2.5 — UX pattern shelf v0.** `patterns/ux/` in the platform repo (Tier 2, per METHOD §3b):
the worklist pattern (density, badges, saved filters, default sort = priority), 360-view
composition, wizard/confirmation patterns, RAG/status-badge conventions, validation-message
templates, terminology lexicon. Seed it by harvesting the DMBB-UX-QA remediation into named
patterns; wire `gen_datalists`/`gen_userview` to consume them. Standing rule: usability
defects are fixed in the pattern, never on the screen. *Verify:* regenerating the DMBB lists
from patterns reproduces (or improves) the remediated state; a pattern change propagates to
all generated lists on regeneration.

**U2.3 — FIS template v2.** Add the citation column to the §4 parameter table + a standing
`## Assumptions` section with `ASSUMPTION-<id>` entries; document the rule: *cite or assume,
never bare*. *Verify:* `citation_check` passes on the template's own example block.

**U2.4 — TO-CONFIRM register view.** `to_confirm.py`: extracts all open TO-CONFIRMs across
slices into one designer-facing MD table (question · why it blocks · dimension · slice).
*Verify:* golden render.

## W3 — Readback generators (playback) · ~3 days

**U3.1 — `readback_lifecycle.py`:** decisions → Mermaid/Graphviz state diagram per entity
(states, transitions, roles on edges; TO-CONFIRM edges dashed red). *Verify:* golden output
for the example slice; a dashed edge appears iff an open TO-CONFIRM touches it.

**U3.2 — `readback_walkthrough.py`:** scenarios → narrative walkthrough in domain language
("Day 0: liability past due… Day 7: first reminder (BR-DM-005)…"), one per procedure,
citations inline. This is the designer-correction artifact. *Verify:* golden render;
every step cites a decision id.

**U3.3 — Preview reuse:** wire the existing `build_preview.py` so a gated slice can render
the clickable preview from the compiled L1 (after W5). *Verify:* preview builds for the
facility-permit example.

## W4 — Skills (thin wrappers over W1–W3) · ~2 days

**U4.1 — `s2c-elicit` skill (v0):** SKILL.md encoding the loop — scaffold design folder →
instantiate templates for the slice → interview (one procedure per pass, evidence-only
context) → write decisions with sources → run `spec_lint` → emit readbacks → repeat →
`gate_report`. Stop rules from METHOD §7 verbatim. **The skill never fills a slot without a
source** — that instruction is the skill's core. *Verify:* dry-run on the mock slice
produces a complete design folder without human input only for slots whose source is a doc
citation; everything else lands in TO-CONFIRM.

**U4.2 — `kit design new <slice>`** scaffold verb (or standalone script pre-CLI): emits the
U0.3 tree with templates instantiated. *Verify:* output validates against schemas.

## W5 — The pilot (the grade) · 1 slice, ~1–2 weeks calendar

**U5.1 — Pick the next real DMBB feature** (e.g. F13-payments — has artifacts but no FIS —
or the next planned one). Run the full loop in **elicit mode** (you are the designer):
design folder → interviews → gate → FIS v2 with citations → build → deploy → regression.

**U5.2 — Measure:** (a) decisions made downstream of the gate (target: single digits vs the
~3/feature DM baseline ≈ 53/17); (b) citation coverage 100%; (c) gate-to-green calendar
time; (d) **usability walkthrough on the generated preview** — 3–5 real officers, task
completion, click counts vs the dimension-7 answers; findings routed (workspace decisions →
inventory, pattern defects → U2.5 shelf). Write the numbers into the runbook. *Verify:* the
numbers exist; the retro lists template slots that were missing/noisy → template v0.2.

**U5.3 — Retrofit spot-check (cheap):** run `citation_check` across all 17 existing DMBB
FIS files; publish the uncited-parameter count as the Class-B baseline. *Verify:* one table
in the runbook.

## W6 — Pack mode (for registration; = PLAN v2 G6/G7) · after W5

**U6.1 — Pack contract:** `schemas/upstream/pack.schema.yaml` — a pack = `pack.yaml`
(id/version/hash) + `skeleton.yaml` (procedures with citations) + `fragments/` +
`checklist.yaml` + `anchors/`. *Verify:* a stub health pack scaffolds from it (the
sector-neutrality check).
**U6.2 — `diff_reference.py`:** bidirectional inventory↔skeleton diff, waiver checklist
output; the three controls from PLAN v2 G7 (naive → gaps; skeleton → clean; subtly-wrong →
FAILS) as pytest fixtures. *Verify:* all three controls.

## W7 — Compile bridge · with the registration slice

**U7.1 — `compile_to_l1.py`:** decisions (`lands_in` paths) → merge into `<app>.app.yaml`
skeleton + **`loss-report.yaml`** for every decision whose `lands_in` the schema cannot hold
(feeds schema 0.2 / PLAN v2 G8.5). Rule U014: non-empty loss report without named schema
gaps ⇒ exit 1. *Verify:* compiling the mock slice reproduces the hand-written L1 fragment;
an effective-dating decision lands in the loss report under schema 0.1.6.

---

## Critical path & first week

```
W0 (schemas) ─ W1 (lint+citation+answer-key CI) ─ W2 (templates+FIS v2) ─ W4 (skill) ─ W5 PILOT
                                        └ W3 (readbacks) ──────────────────┘
W6/W7 follow the pilot, aligned with PLAN v2 G6–G9.
```

First week, in order: U0.1 → U0.2/U0.3 (day 1–2) · U1.1 core rules U001/U002/U003/U006
(day 2–4) · U1.3 answer-key fixture (day 4–5) · U2.3 FIS column + U5.3 baseline scan
(day 5 — publishable number immediately).

## Definition of done (upstream v1)

1. Schemas + templates versioned in the kit; zero domain content (grep-checked).
2. `spec_lint`/`citation_check`/`gate_report` green in CI **including the DM answer-key
   fixture** — the method provably catches the known failures.
3. One real DMBB feature delivered through the gate with citation coverage 100% and
   downstream-decision count in single digits — numbers published in the runbook.
4. Templates v0.2 revised from the pilot retro (extract-from-the-slice honoured).
5. Pack contract exists with a stub second-sector pack (neutrality proven structurally).
