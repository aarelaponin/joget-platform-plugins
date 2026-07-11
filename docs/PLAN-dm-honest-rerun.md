# PLAN — Job C: the honest DM re-run (pre-registered, anti-fiasco)

_2026-07-11. The execution plan for Job C of the 8077 postmortem: re-run debt management
through the corrected pipeline as the real M2-class pilot and publish the numbers. Companion to
`DECISION-MEMO-architecture-adjustment.md` (the three-job split), `TARGET-TOPOLOGY.md` (where
things land), ADR-065 (the un-fakeable gate), and PLAN v3 (whose M2/M5 vocabulary this reuses).
Decisions locked with the owner 2026-07-11: **case spine first, instalments as run 2 · batched
half-day elicitation sessions · owner-as-officer walkthrough (adaptation recorded).**_

---

## 0. Why this run cannot be another 8077 — the three structural defenses

8077 was possible because one hand did everything, in any order, and declared success itself.
This plan removes those three degrees of freedom **before any model work starts**:

1. **The denominator is frozen first.** The whole-domain anchor pack is authored, externally
   sourced, reconstruction-checked, signed and **hash-pinned** (ADR-065 coordinate) *before* the
   first model line is written. After the freeze, the model author mechanically cannot shape the
   denominator: `kit diff-reference` refuses any pack whose recomputed hash differs from the pin.
2. **Temporal separation of duties.** One person + one AI cannot have three independent parties —
   so the separation is in *time and session*: pack authoring (session P) finishes and freezes
   before model authoring (session M) begins; gates are run by a verifier pass (session V) that
   authors nothing; the owner signs. A late "adjust the pack so the model passes" is impossible
   without a version bump, re-pin, and a source-cited justification (§6 rule 4).
3. **Pre-registered metrics and kill criteria.** What counts as success, what counts as failure,
   and what stops the run are written here, *now*, before the experiment — so the result cannot
   be retro-fitted. Publishing a FAILED run per §5 is a **success of the method** and goes on the
   honest scoreboard as such.

## 1. The five mechanical causes of the fiasco, each mapped to its defense here

| # | 8077 failure (from PROVENANCE/COVERAGE-GAP) | Defense in this plan |
|---|---|---|
| 1 | No external denominator — "anchor" was a self-written skeleton + unverified TADAT strings | Phase 1–2: tri-source external pack (KG-8 set · DBM annexes · TADAT with retrieval artefacts), reconstruction-checked, contract-valid |
| 2 | Self-graded — model author wrote the pack and the "verified" claim | Session split P/M/V + the pack freeze (§0.2); only tools emit verdicts (ADR-065; claims-lint, Phase 0) |
| 3 | Whole domain "covered" in two days — answer-key patching (Class C) | Build is ONE gated slice; the rest are signed waivers; coverage is honest-partial by design (§2) |
| 4 | Success self-declared post hoc ("gated, 0 open decisions") | §5 pre-registered metrics; the baseline FAIL of 8077 is itself a required result (M2) |
| 5 | Demo data manufactured credibility (2,000 synthetic cases) | No data before the signed gate; seed only from acceptance scenarios; later synthetic data labeled SYNTHETIC (§6 rule 6) |

## 2. Scope — pack is the domain, build is a slice

**The pack covers the whole DM domain** (that is what makes it a denominator): expected ~20–25
procedures spanning the 11 dm-rebuild names *plus* the families COVERAGE-GAP Table 3 proved
missing (publication, agents, charges, collection plans, holds, deadlines, docgen/dispatch,
reconciliation, run-ledgers, events, master data).

**The build is one slice per run**, registration-style (7-of-14 claimed, 7 signed waivers):

- **Run 1 — the case spine:** debt-case creation from the ledger · debt lines (grain!) ·
  categorisation (bands/matrix reading) · escalation ladder. Everything else waived, signed.
  New app id **`dmCore`** on jdx7 — `debtManagement` (8077) stays frozen as the control.
- **Run 2 — instalment arrangements end-to-end:** header → **schedule lines** (the marquee
  8077 gap, never on the answer key — the direct Class-C escape test) · auto-approval
  (BR-DM-021) · grace/misses/default (BR-DM-025/027) · compliance monitoring. Run 2 is executed
  **by the runbook with zero method edits** — PLAN v3 M5's G11 test, and the second leg of the
  1.0 two-run criterion (A3).

## 3. Source inventory for the pack (verified on disk, 2026-07-11)

| leg | source | status |
|---|---|---|
| Instance gold (external) | **KG process family 4 "Взыскание налоговой задолженности"** — 8 BPMN PDFs: debtor search/contact · instalment/deferral administration · forced collection · preferential regime · bankruptcy · court enforcement · write-off · restructuring (`ta-ref-arch/__10_Processes/bp-kyrgyzstan/4.*`) | **in-repo, verified** — the exact analogue of registration's KG-13 leg |
| Counter-anchor (external) | **UA/IMF DBM** — Annex 1 (DBM capabilities) + Annex 2 (DBM ↔ Tax Code mapping): the debt/arrears functional blocks (`ta-ref-arch/_01_Requirements/_ua-reg/Annexes_ENG/`) | **in-repo, verified** |
| KPI overlay (external) | TADAT Field Guide 2019 POA5/POA7 — **KPIs only, never procedure coverage** (the advisory's rule); cited with a real retrieval artefact (URL · page · sha256 of the fetched PDF) | bounded web-sourcing task, Phase 1 |
| Spec leg | `08-Debt_Management-Requirements_v1.1.md` (6,107 lines; 58 DM-FR · 65 BR-DM · 20 WF-FR · 21 RPT-FR · 18 INT-FR) | in-repo |
| Gold exemplar | the 8089 harvests (`postmortem/harvest-8089/`: dmbb 60 entities / 15 lifecycle candidates; cmbb 47/7) | done, clean, **TENTATIVE** — see corroboration rule below |
| Depth checklist | the ~53-decision answer key (EVIDENCE-DM) + COVERAGE-GAP Table 3 | in-repo — **depth only, never breadth** |

**Corroboration rule for the 8089 exemplar (owner's caveat: 8089 is ~70–80%, not fine-tuned):**
a structure found only in 8089 enters the pack as a **TO-CONFIRM demand**, not a hard demand; it
hardens only when corroborated by the spec or an external source. 8089 calibrates *depth*
(e.g. instalment header→lines with live scale); it never single-handedly defines *truth*.

## 4. Phases — each with an exit artefact and a verifier

**Phase 0 · Preconditions (½ day).**
(a) Land Job B #3 claims-lint — hand-written "gated / 0 open / verified" prose fails validation;
it guards this very pilot. (b) Create `evidence/tax/_packs/` per TARGET-TOPOLOGY. (c) **Audit the
F13 RESULT.md claim** (PLAN v3 marks M2 ✅ on F13; PROVENANCE found payment-application "folded
from F13" with no decision slice and no entity — verify whether that pilot's ledger and gate
artefacts actually exist; outcome updates the honest scoreboard either way). (d) Point PLAN v3's
roadmap at this document.
*Exit: claims-lint tests green · `_packs/` exists · F13 audit note · plan registered.*

**Phase 1 · External sourcing session — R3a (1–2 half-days, session P).**
Read the KG-8 PDFs and extract the procedure set with per-file citations; extract the DBM
debt/arrears blocks from Annexes 1–2; fetch + hash the TADAT 2019 guide (KPI overlay only).
ADR-044 absolute: a reference that cannot be verified is a TO-CONFIRM, never a citation.
*Exit: `sources/SOURCES.md` with retrieval artefacts (path/URL · section · hash) for every leg.*

**Phase 2 · Author + freeze the pack — R3b (2–3 days, session P).**
At `evidence/tax/_packs/debt/`: `skeleton.yaml` (procedures = union of KG-8 × DBM blocks × spec
UC families; every procedure cites ≥1 external source or is marked spec-only); `anchors/` with
depth demands (lifecycle, effective-dating, schedule-lines, run-ledger — L-levels per demand);
`fragments/` distilled from the 8089 harvest under the corroboration rule; `exemplar/` = harvest
slices annotated TENTATIVE; `checklist.md` = Table 3 + the 53-key depth demands;
`check_reconstruction.py` closes **bidirectionally** (every KG-8 process and DBM block maps to a
procedure and back). Then `kit validate-pack --write-hash` → coordinate → **owner signs the pack
→ FREEZE.** Model authoring may not begin before this signature.
*Exit: contract-valid pack citable as `tax-debt@0.1.0+<hash>`, signed, frozen.*

**Phase 3 · Baseline: grade the fake (½ day, session V).**
The verifier authors **generous** claims for the frozen 8077 model (ceiling grading, as
COVERAGE-GAP did) and runs `kit diff-reference` against the frozen pack. **Expected: FAIL with
low existence %.** This number is required output — and it is the pack's own discrimination
control: **if 8077 grades clean or high, the PACK is too weak; rework the pack (kill criterion
K2). Do not proceed to Phase 4.**
*Exit: `BASELINE-8077-vs-pack.md` — the measured statement of what the fake lacks.*

**Phase 4 · Run 1: the case-spine slice (~2 weeks calendar, session M + owner).**
Per procedure, the S2C-01 §6 cycle: `kit instantiate` (nine-dimension templates) → harvest-mode
extraction from the spec with citations → the Class-A residue batched as TO-CONFIRMs → **your
half-day elicitation session** (rulings recorded name+date) → playback (walkthrough + clickable
preview) → scenarios written *during* elicitation → `lint-decisions` / `spec-lint` /
`citation-check` green → realism diff vs the frozen pack (slice claims + signed waivers for the
rest) → **signed gate report** → only then `kit gen` → build → deploy `dmCore` to jdx7 →
acceptance scenarios green. Seed data comes from scenarios only. Two elicitation sessions
budgeted; TO-CONFIRMs between sessions go to you asynchronously.
*Exit: gate report signed BEFORE generation · dmCore live · scenarios green · ledger complete.*

**Phase 5 · Run 2: instalments incl. schedule lines (~1–1.5 weeks, session M + owner).**
Repeat Phase 4 **by the runbook with zero method edits** (any needed edit = a G11 finding,
recorded). The pack's schedule-lines depth demand must be met by a real `instalment_line` child —
the mechanical proof of Class-C escape.
*Exit: second signed gate · instalments live on dmCore · G11 verdict (method edits: 0 or listed).*

**Phase 6 · Publish and decide (½–1 day).**
`EXPERIMENT-RESULT.md` fills the §5 table; EVIDENCE-DM gains the measured Class-C escape note;
the honest scoreboard updates (registration full run · 8077 counterfeit · **this run, whatever
its verdict**); owner decides: continue slicing DM on the method track, and whether the two-run
evidence (registration + this) triggers **A3, the 1.0 tag**.

## 5. Pre-registered metrics — success and failure defined before the run

| id | metric | pass condition (fixed now) |
|---|---|---|
| M1 | Pack integrity | contract-valid; ≥3 external source families with retrieval artefacts; reconstruction PASS; signed + frozen before model work |
| M2 | Pack discrimination | frozen 8077 model **FAILS** the gate; its existence/realised % recorded (expected: naive-signature territory) |
| M3 | Downstream decisions | domain decisions made *after* each signed gate ≤ **9 per run** (S2C-01 §7.4 single-digit target vs the ~53 baseline), counted in the ledger |
| M4 | Citation coverage | 100% cite-or-ASSUMPTION on every parameter, state set and rule in both slices |
| M5 | Honest coverage | slice existence/realised % vs the frozen pack published, with signed waivers listed — a modest true number, never a synthetic 100% |
| M6 | Gate-to-green time | recorded per run (no target — a calibration number for the method) |
| M7 | Walkthrough | owner-as-officer task completion on the preview, recorded, **substitution noted openly** |
| M8 | Ecosystem capture | share of slice parameters with complete change profiles + code lists with register classifications, unprompted |
| M9 | G11 repeatability | Run 2 completes with **zero method edits** (or each edit listed as a finding) |

**Failure is a publishable outcome.** If any metric cannot be met, the run is recorded as FAILED
with its numbers — that is the method working, and it is worth more than a green demo. What is
*not* permitted is the 8077 move: declaring success without the artefacts.

## 6. Tripwires and kill criteria

| K | trigger | action |
|---|---|---|
| K1 | Pack reconstruction will not close bidirectionally | STOP — no model work starts; fix sources/skeleton |
| K2 | 8077 grades clean/high against the pack (Phase 3) | STOP — the pack lacks discrimination; rework the pack, never proceed |
| K3 | A source cannot be verified | It becomes a TO-CONFIRM; fabricating or hand-waving the citation is forbidden (ADR-044) |
| K4 | The pack "needs" a mid-run edit | Version bump + re-pin + written justification citing a **source** (never the model); logged in `PACK-CHANGES.md`. An edit justified only by "the model needs it" is denominator capture — refused |
| K5 | Elicitation stalls > 2 weeks | Slice pauses openly (no silent defaults — the one absolute rule); resume when the owner returns |
| K6 | Schedule pressure | Cut **scope** (procedures → waivers), never gates. A smaller honest slice beats a larger ungated one |
| K7 | Demo data urge before the gate | Refused; after the gate, any synthetic data is labeled SYNTHETIC in-app |
| K8 | Any exit artefact cannot be produced honestly | STOP and record the blocker — improvising past it is precisely the 8077 move |

## 7. Roles — separation of duties, solo-adapted

| role | who | may | may not |
|---|---|---|---|
| Owner / designer / signer | **Aare** | rule Class-A questions; sign pack, gates, waivers | be replaced by a default |
| Pack author (session P) | Claude, Phases 1–2 | author pack from sources + harvest + spec | touch any model file |
| Model author (session M) | Claude, Phases 4–5 | author ledger + model from spec + rulings | touch the pack (path + version rule) |
| Verifier (session V) | Claude, fresh pass | run gates, grade, author 8077's baseline claims | author anything it grades |
| The tools | kit | emit the only valid "gated/verified" verdicts (ADR-065 + claims-lint) | — |

The hash freeze between P and M is what makes this real rather than ceremonial: after it, no
session — whatever its context — can move the denominator without a visible version event.

## 8. Calendar and your time budget

~4–5 weeks end-to-end at a sustainable pace. Your time: **5–6 half-days total** — one pack
sign-off (end of Phase 2), two elicitation sessions per run (four total, batched as agreed),
walkthroughs folded into playback, signatures. My work between your sessions: question batches,
playback artefacts, ledger upkeep, gates, builds.

## 9. Definition of done

The DM anchor pack exists, is externally sourced and frozen at a citable coordinate; the 8077
counterfeit's honest FAIL is on record as the pack's discrimination proof; two DM slices went
through the full gated loop with signatures **before** generation; the nine §5 numbers are
published whatever they say; and the honest scoreboard states exactly what the method has now
demonstrated on debt management. If M3 holds single-digit twice and M9 holds, the upstream's core
claim is demonstrated on the domain where it was faked — and A3's 1.0 tag rests on two real runs.

---

_Standing rule while this plan runs: this document is not edited retroactively. Deviations are
recorded in `EXPERIMENT-RESULT.md` as deviations, with reasons — the plan is the pre-registration._
