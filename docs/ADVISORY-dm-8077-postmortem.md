# Advisory — the two DM apps: what 8077 actually proves, and what to do

_2026-07-11. Postmortem of the generated debt-management app on jdx7 (8077) against the
hand-built CMBB/DMBB on jdx9 (8089), from the artefact record: `evidence/tax/debt-management/`
(model, BUILD-VERIFICATION, git log) and `joget-platform-plugins/method-evidence/dm-rebuild/`
(the ledger the model header cites)._

---

## 1. What the record shows

**8089 (jdx9, `cmbb`+`dmbb`)** is delivered reality: months of feature-loop work against the
6,107-line DM spec (30 use cases, 84 business rules), real engines (DAS, status-framework,
event chain, enforcement matrix, Jasper, dashboards), grown out of the original naive build
by hand. It is the strongest oracle the programme owns for debt management.

**8077 (jdx7, `debtManagement`)** was generated on 2026-07-10/11 from
`debt-management.app.yaml`, whose header claims: "re-authored through the gated method …
11 procedures, 0 open decisions … realism-anchored to the TADAT Field Guide 2019." The
claims do not survive inspection:

- **The inventory is a stub.** `method-evidence/dm-rebuild/` holds 11 procedure folders with
  **2–5 decision records each (~32 total)** — against a module whose *improvised-decision
  count alone* was measured at ~53, and whose spec carries 30 UCs and 84 BRs. The model has
  **3 acceptance scenarios**. "0 open decisions" was true only against a denominator the
  author wrote himself.
- **The anchor is not an anchor.** TADAT indicators (P5-19/20, P7-24) are outcome metrics —
  they anchor KPIs, not procedure coverage. No DM anchor pack exists (the only pack in the
  evidence repo is tax-registration). `kit diff-reference` was therefore never run against a
  real external denominator — it could not have been.
- **The build fixed the known defects and stopped.** BUILD-VERIFICATION maps exactly the
  8 famous EVIDENCE-DM failures (6 states, grain, matrix reading, …) to corrections — the
  answer key used as the knowledge base. Then the flat-case naivety was *reproduced anyway*
  and caught only because it too was on the list. Everything not on the list is still in
  there, undetected. 2,000 synthetic cases on top make it demo well.

This is, verbatim, the trap S2C-01 §7.1 exists to name: *"a coverage number whose
denominator the author also wrote reads 100% while the application is unusable."*

## 2. The correct reading of the evidence

**The method was not run on DM; its labels were.** Where the chain was run in full —
registration: external tri-source anchor pack, reconstruction oracle, full-breadth ledger,
slice-by-slice gates (13/14 procedures, per the same git log) — it measurably discriminated
(100%/100% vs 7.1%). Where it was half-run — DM: answer-key patching, self-written
denominator, KPI citations dressed as an anchor — it produced a confident fake.

So the damning finding is real, but it is not "knowledge management gives nothing." It is:

> **The method has no mechanical defence against being counterfeited.** Nothing stops
> `kit gen` without a pack-anchored gate; nothing distinguishes a self-graded "gated" claim
> from a real one; the vocabulary ("gated", "anchored", "0 open decisions") can be asserted
> in a YAML comment. A half-applied method is worse than none — the naive app was at least
> visibly naive; 8077 launders naivety through the method's own credibility.

That is a Leak-3 failure one level up: the discipline existed on paper and was not enforced
by machinery — the exact defect class the platform preaches against (invariant 10).

## 3. What to do

**R1 · Reclassify 8077 — don't delete it.** It is the most instructive negative control the
programme owns: *naive-control v2, the answer-key-patched fake* — internally valid, defect-
patched, still unusable. Strip the "gated/0-open-decisions" claims from the model header,
label it `_dm-answer-key-control`, and register the failure class in EVIDENCE-DM as
**Class C: re-derivation from the defect list** (fixing known misses ≠ covering the domain).

**R2 · Make the gate un-fakeable (small kit changes, do first):**
1. `kit diff-reference` refuses to run without a resolvable **pack reference (id + version +
   content hash)**; KPI/indicator citations are not packs.
2. `kit gen`/`kit deploy` require a machine-produced gate report (lint-decisions + realism
   verdict + named human signature). Absent that, every provenance header and the userview
   footer are stamped **UNGATED** — generation stays possible, silent credibility does not.
3. "0 open decisions" becomes a computed lint output only; hand-written coverage claims in
   model comments fail validation.

**R3 · Build the DM anchor pack — the knowledge collection DM never got.** The denominator
sources already exist: (a) **`kit harvest` the 8089 app** (ADR-057 D2) into a DRAFT L1 —
delivered reality as the gold exemplar, exactly like registration's; (b) the 6,107-line DM
spec (30 UCs / 84 BRs); (c) the ~53-decision answer key as the *depth* checklist, not the
breadth; (d) UA/KG enforcement-and-collection process sources for the external leg. Expected
shape: the dm-rebuild 11-procedure skeleton is a plausible start, but each procedure needs
full nine-dimension slots — anticipate a 50–80-record ledger, not 32. Then diff the 8077
model against the harvested 8089: **the gap list is the measured statement of what the fake
lacks**, and becomes the pack's depth demands.

**R4 · Re-run honestly and expect failure.** With a real pack, the 8077 model should FAIL
the realism gate — that failing verdict is the method *working*. Rebuild from the gated
ledger or promote 8089 as the production track and park the generated DM track until the
pack exists. Recommended: **8089 stays the production DM**; the generated track becomes the
M2-class exercise it was always meant to be, run properly.

**R5 · Standing-plan updates.** EVIDENCE-DM: add failure Class C. PLAN v3 M1: the register
test gains a sibling — *the anchor test* (no gate without a versioned pack). One ADR:
"realism verdicts are valid only against versioned, externally-sourced anchor packs;
self-written denominators are void."

## 4. Measured results — the evidence handover of 2026-07-11

The building session's read-only collection (`evidence/tax/debt-management/postmortem/`,
commit `beeee68`) turned §1's judgement into numbers:

**Requirement coverage of the 8077 model, by the most generous measure** (id merely *cited*
in the model — the ceiling, not proof of enforcement): BR-DM **8/65** · DM-FR **6/58** ·
WF-FR **1/20** · RPT-FR **2/21** · INT-FR **0/18** — **≈9% of the distinct requirement ids**.
The naive registration control scored 7.1% against its anchor. An ungated build lands at the
naive signature, regardless of how much method vocabulary decorates it.

**Capability gap vs delivered reality (8089):** seventeen whole capability families present
and populated in cmbb/dmbb have **no counterpart** in the 8077 model — debtor publication,
agent appointments, enforcement charges/legal fees, collection plans+targets, registry
extracts, instalment **schedule lines** (8,736 live rows; 8077's instalment is a header
only), approvals/delegation (631/536/79 rows), holds, deadlines/SLA, reassignment,
docgen/dispatch/postal, gold-ledger reconciliation, info requests, the idempotent run-ledger
pattern (~2,800 rows across 8 job types), the event audit stream, and 15 master-data config
tables that 8077 collapses into one 26-row key-value `dm_param`. Scale: 8077 = 11 forms /
4 datalists; dmbb = 64/71 + cmbb = 47/54 with a real workflow engine (package v22, SHARK
state history).

**Provenance findings beyond §1:** `kit diff-reference` was **never run** for DM (no realism
file exists; no run in any commit); the DM "anchor" is spec-grounded with an unverified
TADAT string list, while `SOURCING.md`/`BUILD-VERIFICATION.md` claim "external, verified" —
a committed contradiction with no verification artefact; no gate report or signature exists;
the model's three bespoke plugins (enforcement-matrix-rule, stream-dedup-guard,
collection-kpi-engine) have **no JARs on the instance** — the enforcement matrix, dedup and
KPI engine exist only as budget YAML; the "11th procedure" (payment-application) has no
decision slice and no table; the ledger holds 32 decisions against the ~53 baseline,
unreconciled; and the celebrated `debt_line` correction is **not even live** — the published
app is v3, the v4 carrying the fix sits unpublished.

**Immediate corrections this implies:** (a) execute R1 now — relabel the model header AND
correct the false "external, verified" claims in `SOURCING.md`/`BUILD-VERIFICATION.md`
(a committed false verification claim is precisely the ADR-044 failure class); (b) the
harvests for R3 are done and clean — `postmortem/harvest-8089/`: dmbb 60 entities / 63 forms
/ 15 lifecycle candidates, cmbb 47 / 47 / 7, both DRAFT — the DM anchor pack's gold-exemplar
leg now exists; (c) COVERAGE-GAP Table 3 is, as predicted, the first draft of the anchor's
procedure/depth demands.

## 5. The honest scoreboard

What the method has actually demonstrated so far: one full run (registration) with measured
discrimination and 13/14 procedures gated; one counterfeit run (DM) that exposed the missing
enforcement. The experiment that would settle the user's real question — *does the upstream
measurably reduce improvised decisions on a real module?* — is still PLAN v3's M2 pilot,
and the DM anchor pack (R3) is now its natural precondition. Until R2 lands, treat any
"gated" claim that cannot show its pack hash and signature as ungated.
