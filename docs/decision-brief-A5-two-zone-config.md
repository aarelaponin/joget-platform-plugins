# Decision brief — A5 · Adopt the two-zone configurability model as a platform rule

_Method asset. Pattern proven by `joget-spec-kit/docs/1.0-structural-decisions.md` (ADR-045…054).
Put to the owner as one rulable document; each ruling becomes a one-line ADR._

- **Status:** Ratified — all three sub-decisions ruled **Accepted** by the owner 2026-07-10;
  registered as ADR-062. (Frozen; rationale archive behind its ADR.)
- **Owner of the decision:** Aare (programme owner).
- **Purpose:** Give **dimension 8 (policy & change)** its authority. PLAN v3 A5 / S2C-01 §5 (dim 8)
  make the two-zone model a platform rule, but the source — the *DMBB Configurability Approach Spec*
  (CP-1…10, INV-1…5) — is written as a DMBB design spec. Ratifying it as a platform ADR unblocks the
  dimension-8 template (M3) and the config invariants as lint (M1). Source is already citable.
- **Key point:** the model is written and proven in one module (DMBB). This brief decides *scope and
  status*, not new design — mostly a **document-as-proven** ruling.

## Summary

| # | Decision | Recommendation | Needs code now? | Urgency |
|---|---|---|---|---|
| 01 | Adopt CP-1…10 as platform configuration principles | Accept as-is | No | Strategic |
| 02 | Adopt INV-1…5 as machine-checkable platform invariants | Accept; implement in spec_lint (dim-8 U-rules) | Yes (M1/M3) | High |
| 03 | Scope: platform-wide vs DMBB-only | Platform-wide | No | Med |

---

## D-A5-01 · Adopt CP-1…10 as platform configuration principles (S2C-01 §5, dim 8)

**Problem.** Dimension 8 rests on "one test and one partition, both owner-ratified" (S2C-01 §5). The
test (operate-vs-define) and the partition (operating Zone A / standing Zone B) are fully specified in
the DMBB Configurability Approach Spec as CP-1…10 — partition by reason-to-change (CP-1), operate-vs-
define as the primary boundary (CP-2), single-source/DRY (CP-3), reference-by-name (CP-4), domain-
bounded scope (CP-5), policy-over-a-defined-instrument-set (CP-6), governance-follows-change-source
(CP-7), two-views-over-one-model (CP-8), explicit configurability class Y/P/N (CP-9), invariants
machine-checkable (CP-10). Until ratified as *platform* rules they bind only DMBB, and the dimension-8
template in M3 has no authority to cite.

**Options.**
- **Adopt as-is (platform principles).** *Pro:* the model is written, internally consistent, and
  already proven on DMBB; nothing to redesign. *Con:* commits the platform to the operate-vs-define
  cut before a second module exercises it. *Impact:* CP-1…10 become citable platform rules; the M3
  dimension-8 template (`dim-policy-change.yaml`) instantiates them; no code churn.
- **Adopt with a pilot caveat.** *Pro:* hedges against a second-module surprise. *Con:* a "provisional
  principle" is a weak citation; the M-series pilot will exercise it anyway. *Impact:* same, tagged
  provisional (ADR-050 ladder).
- **Keep DMBB-only for now.** *Pro:* zero commitment. *Con:* dimension 8 stays un-authored; M3 blocked
  on it. *Impact:* leaves a hole in the nine-dimension method.

**Recommendation.** **Adopt as-is.** The spec is the sharpest artefact the programme has on
configurability and it generalises cleanly (nothing in CP-1…10 is debt-specific). Mark it *stable* per
the ADR-050 ladder once the pilot (M2) exercises dimension 8 on a second module.

**Decision:** Accepted (owner, 2026-07-10) → ADR-062.

---

## D-A5-02 · Adopt INV-1…5 as machine-checkable platform invariants

**Problem.** CP-10 requires the structural rules to be automated checks, not review discipline. The
spec defines five: INV-1 every tag resolves to exactly one index entry (no dangling / ambiguous names);
INV-2 no value in two definition sites (DRY); INV-3 every Zone-A reference is defined in Zone B (and
every `[operational]` value is defined in B, referenced from A); INV-4 every enforcement-matrix
instrument exists in the registry and is legally available for that category before the cell can be set
(CP-6); INV-5 index names unique. INV-1 already caught a real defect (`garnishment` vs `bank garnishing`
blocked the manual build). These are the dimension-8 half of the M1 checks.

**Options.**
- **Adopt and implement as dimension-8 U-rules** (in `spec_lint` / a config lint). *Pro:* turns the
  invariants into the same fail-closed gate as the other dimensions; directly extends M1. *Con:* work
  (each INV is a rule + fixtures). *Impact:* `spec_lint` gains INV-1…5; the change-profile block already
  in the M0 schema carries the zone/class the rules key on.
- **Adopt as documentation only.** *Pro:* zero code. *Con:* violates CP-10 (the spec's own rule);
  leaves config drift uncaught — the exact failure class. *Impact:* none, but the gate has a hole.

**Recommendation.** **Adopt and implement** — as the dimension-8 U-rules in M1/M3. INV-1/INV-4 are the
load-bearing ones (dangling name reference; matrix cell selecting an unavailable instrument) and map
directly onto the decision-record `change_profile`/`ecosystem` blocks already shipped in M0.

**Decision:** Accepted (owner, 2026-07-10) → ADR-062.

---

## D-A5-03 · Scope — platform-wide or DMBB-only

**Problem.** Dimension 8 is a *platform* dimension (every module has policy that changes). The spec is
authored in DMBB terms (enforcement matrix, instruments). Ratifying platform-wide means every future
module's config is cut the same way; keeping it DMBB-only re-opens the question per module.

**Recommendation.** **Platform-wide.** The operate-vs-define test and the two zones are domain-neutral;
the DMBB matrix/instruments are just this module's *instances* of "operating policy over a defined set."
The tax-registration slice already has standing-zone definitions (VAT threshold, party-type taxonomy)
that fit the model. Adopt platform-wide; let modules add domain instances, not new cuts.

**Decision:** Accepted (owner, 2026-07-10) → ADR-062.

---

## What ratifying this brief does

Each ruling becomes a one-line ADR (proposed ADR-059 for the batch, or one per decision). CP-1…10 and
INV-1…5 become platform rules the dimension-8 template (M3) and the config lint (M1) cite. The DMBB
Configurability Approach Spec is then a *worked instance* of a platform rule, not a standalone design.
Nothing here needs code before M1/M3 — D-A5-01 and D-A5-03 are document-as-proven; D-A5-02 is scheduled
work already in the plan.
