# Evidence — what a "comprehensive" spec still didn't decide: the DM forced-slot test

_Written 2026-07-09. Companion to PROBLEM-semantic-design-gap.md, REVIEW-PLAN-…, PLAN v2._
_This is the empirical test proposed after the "plenty of information, naive result" observation:
run a forced-slot extraction against the Debt Management spec and validate it against the
**answer key** — the domain decisions the DMBB build actually had to make._

**Inputs.** Spec: `ta-ref-arch/_01_Requirements/08-Debt_Management-Requirements_v1.1.md`
(6,107 lines; 58 DM-FRs + RPT/WF/INT FRs; 30 use cases with main/alt/exception flows;
84 business rules with logic and defaults; a conceptual data model with attribute tables;
12 DM screens + reporting/admin screens; traceability matrix).
Answer key: the DMBB build record — 17 `FIS.md` files (design-decision sections),
`DX9-DELTAS.md`, ADR-003 (state machines).

---

## 1. Headline result

The DM spec is **comprehensive by industry standards** — far above a typical government FRS.
And the build still had to make **~53 domain decisions** it recorded as its own. They split
into two classes, and the split is the finding:

- **Class A (~44): real spec gaps.** The spec never answers; the builder invents mid-build.
- **Class B (≥9): the spec HAD the answer, and the build re-derived it without citation —
  in at least two cases diverging from it.**

Neither class is an information-availability failure. Class A is *the spec doesn't force
itself to decide*; Class B is *nothing forces the build to consume what was decided*. The
transfer leaks in **both directions** — which is why "more documentation" has never fixed it.

## 2. Class A — real gaps, by dimension

The gaps are not random: they cluster in six dimensions, none of which is "use cases" or
"requirements" — the two dimensions the spec is strong in.

| Dimension | What the spec has | What was missing | Build decision that filled it |
|---|---|---|---|
| **Behavioural / state modelling** | One 6-state case enum (WF-FR-001); status enums on entity attributes (§9.1.1) | Per-entity state **machines**: transitions, triggers, guards, edge cases | ADR-003 invented full lifecycles + edges the paper flow missed: `missed→paid` (late settlement is legal), `UNDER_REVIEW→POSTED`, `SUBMITTED→REJECTED`, and rejected `ESCALATED→REPLACED`; VAT-specific `dmDebt` override |
| **Computation formulas** | Rule *logic sketches* (§8), KPI names (#24–35) with a Gold view name | Executable definitions over named fields | "amount at stake" = Σ enforceable per line (F03); "full-amount objection" = Σ disputed ≥ Σ enforceable (F05); "actual recovered" = 3-source aggregation (F12); 12 KPI formulas + RAG bands (DASH2); "days since last action" (RPT2) |
| **Interpretation rules** | Table-4 proportional enforcement; BR-DM-004 "distinct enforcement streams" | Which *reading* is correct | Cumulative (C4 includes C3's actions) vs exact category→list (F01); "enforcement step" = instrument ≠ DEMAND (F05); C6 band for >€200k — spec enumerates C1–C5 only (F01, vs DM-FR-004's "implied C6") |
| **Data ownership / recompute policy** | v1.1 STA boundary notes on 5 FRs (good!) | The *general* policy: snapshot vs live recompute, per attribute | "Never recompute": category, interest, garnished funds, fees are informational snapshots; ledger stays authoritative (F03/06/07/09 — the most pervasive invented principle) |
| **Failure / edge behaviour** | UC exception flows (session timeout, validation fail) | Behaviour when *systems* fail or data is absent | Gold outage → create no cases, never fabricate (F03); write-off reversal-on-payment (deferred, F09); risk score absent → N/A render (DASH2) |
| **Structural data decisions** | Entity attribute tables (§9.1.1: case-level tax type) | Grain and key decisions | Tax type moved case→line with PA/IA/PCA itemisation (DX9-DELTAS, DMBB-LINEITEM) — a data-model correction forced by consolidation reality |

## 3. Class B — the spec had it; the build re-derived it

Verified line-by-line against the spec:

| Item | Spec answer (citation) | Build record | Verdict |
|---|---|---|---|
| Escalation offsets | 7d reminder (BR-DM-005), 14d response (BR-DM-005), 21d final (BR-DM-011) | F01 "assumed 7/14/21/14/30" | First three **match — uncited**; last two genuinely absent |
| Instalment auto-approval | <€5,000, ≤12 months, first-time (BR-DM-021) | F06 lists as its own parameter decision | **Match — uncited** |
| Grace + consecutive misses | 3 business days (BR-DM-025); 2 consecutive (BR-DM-027) | F06 "assumed grace/miss counts" | **Match — uncited** |
| Contact/visit applicability | Phone C3–C5 (DM-FR-019); visits C4–C5 (DM-FR-020) | F14 "category applicability the spec left open" | **Match — the FIS is wrong about its own spec** |
| High-value fast-track | Threshold + phone SLA 2 business days (DM-FR-018, BR-DM-012) | F14 records as design decision | **Match — uncited** |
| Aging bands | 0–30/31–60/61–90/91–180/181–365/>365 (RPT-FR-001/008) | F12 "invented aging-band boundaries" | **Match — uncited** |
| Delegation (instalment) | DMO <€20k / SDO <€100k / Director (BR-DM-022) | F09 reused pattern for write-off | Instalment values existed; write-off values genuinely absent (DM-FR-043: "configurable") |
| **Envelope states** | 6 states: New→Open→In Progress→On Hold→Pending Closure→Closed (WF-FR-001) | F02 built NEW/OPEN/CLOSED | **DIVERGENCE — silent** |
| **Dedup key** | No duplicate per TIN/TXT/TXP (BR-DM-002); consolidation per stream (BR-DM-004) | F02/F03: one open case per TIN | **DIVERGENCE/tension — flagged as "reinterpreting" but never reconciled against BR-DM-002** |

Class B is as costly as Class A: matching values re-derived without citation are unauditable
luck, and the two divergences are precisely "the app after a week is not what the spec says."

## 4. The slots that would have caught them

Each gap class maps to a mechanical template rule. Applied *before* the build, against this
same spec:

| Slot rule (template obligation) | Catches | Est. count |
|---|---|---|
| Every long-lived entity: full state machine (states, transitions, triggers, edge transitions) or signed "stateless" waiver | ADR-003 family, envelope-state divergence | ~8 |
| Every amount/metric/flag: formula over named fields, or open question | amountAtStake, full-amount objection, recovered, KPIs, RAG, days-since | ~12 |
| Every rule table: normalised to data + interpretation stated (cumulative/exact), full range coverage | Table-4 reading, C6 band, enforcement-step definition | ~5 |
| Every attribute: source + recompute policy (snapshot/live/owner) | the "never recompute" family, SAS/STA/Registration boundaries | ~8 |
| Every external dependency: failure behaviour stated | Gold outage, absent risk scores, bank-WS timeout | ~4 |
| Every case type: consolidation grain + dedup key reconciled to the BR register | one-case-per-TIN vs BR-DM-002, tax-type-on-line | ~3 |
| **FIS-side: every parameter/state/rule cites its spec ID or is an explicit ASSUMPTION entry** | the whole of Class B | ≥9 |

Estimated coverage: **~45–49 of ~53 decisions** would have surfaced as designer questions or
citation entries *before* the build, instead of as mid-build discoveries. The residue (~4–8)
is legitimate engineering discretion (e.g., agent reporting interval, WS timeouts).

## 5. What this proves for the method

1. **A strong FR/UC/BR spec is not a build-ready spec.** This spec would pass any external QA
   — and it is systematically silent along six dimensions that are none of FR, UC, or BR:
   state machines, formulas, interpretations, ownership/recompute, failure behaviour, data
   grain. Those six ARE the elicitation dimensions the spec-design lifecycle must interrogate.
2. **Transfer discipline must be bidirectional.** Slots force the *spec* to decide (Class A);
   citation-or-assumption discipline forces the *build* to consume (Class B). The DM build had
   FIS discipline and still leaked Class B — because the FIS template never required a spec
   citation per parameter. One added column fixes this.
3. **The readiness gate is now defined by evidence, not taste:** a slice is ready when the six
   dimension templates have zero unanswered blocking slots, and implementation may not close a
   parameter without a spec citation or a logged ASSUMPTION. Everything else in the spec can
   stay prose.
4. **The week is explained.** ~53 mid-build discoveries × (notice, decide, sometimes rework —
   e.g., tax-type case→line forced re-modelling after two slices) is the delay. The same 53 as
   upfront checklist answers is days, answered by the person who already knows.

## 6. Sample TO-CONFIRM register (what the designer would have been asked)

The elicitation, run on this spec, would have opened with questions like these — each
pre-empting a recorded build decision:

1. WF-FR-001 defines 6 case states; does the debt case use all 6? Which map to debt *stages*? _(pre-empts F02 divergence)_
2. BR-DM-002 forbids duplicates per TIN/TXT/TXP; BR-DM-004 consolidates per "enforcement stream." Is the case grain one-per-TIN or per-stream? Define "stream." _(F02/F03)_
3. DM-FR-004 implies C6 (>€200k). Does C6 exist? Which actions apply to it? _(F01)_
4. Table 4: if a debt is C4, are C3's actions also available (cumulative) or only C4's (exact)? _(F01)_
5. For each of the 14 actions: execution mode, authority level, proportionality floor, cost recorded? _(F07 — only fragments in spec)_
6. When ORS/Gold is unreachable at scan time: skip, retry, or create provisional cases? _(F03)_
7. Is debt category recomputed on every balance refresh, or snapshotted at case creation? Who owns the number? _(F03)_
8. Define "amount at stake" for a consolidated case: which line components count (PA/IA/PCA, disputed, suspended)? _(F03/F05)_
9. What exactly blocks enforcement: any objection, or disputed ≥ enforceable? Formula. _(F05)_
10. An instalment lapses with a payment in the grace window pending bank confirmation — compliant or missed? Can `missed` become `paid`? _(ADR-003)_
11. Write-off approval levels: DM-FR-043 says "configurable seniority" — give the amount bands. _(F09)_
12. KPI #29's formula: numerator/denominator, over which population, in-period or cumulative? Same for the other 11. _(DASH2)_
13. Default assessment estimate: priority order of prior-year / industry-average / formula, and what happens when the estimate exceeds prior-year ×150%? _(F10)_
14. A taxpayer pays after write-off posting: reversal, new credit, or reactivation? _(F09 — still open today)_

Every one of these was eventually answered by an implementer under deadline. The method's
entire claim is that they should be answered by the designer, before generation, in exactly
this form.

---

**One-line compression:** the DM spec proves a spec can be simultaneously comprehensive and
not build-ready; the missing content is concentrated in six checkable dimensions, and ~90%
of the build's improvised decisions were mechanically catchable — half by forcing the spec
to answer (slots), half by forcing the build to cite (citation-or-assumption). Both forcing
functions are now specified in PLAN v2 (G8's templates + the FIS citation column).

---

## Class C — re-derivation from the defect list (added 2026-07-11, from the 8077 postmortem)

_Not present in the 2026-07-09 analysis above; identified when the generated 8077 `debtManagement`
model was measured against the DM requirement denominator. It extends the Class-A / Class-B taxonomy
of §1 with a third, distinct failure mode._

**Class C — fixing the known misses is not covering the domain.** The 8077 rebuild took *this very
document* (the Class-A/B answer key) as its knowledge base, corrected the 8 famous defects it names —
the 6-state envelope, the enforcement-stream grain, the exact-category matrix + C6, cited instalment
limits, the KPI/RAG/aging set, the write-off reversal policy, the dispute SLA + block rule — and then
**stopped**. Everything the answer key did *not* list stayed missing and undetected. Measured against
the full spec denominator the model cites ≈ **9%** of the distinct requirement ids (BR-DM 8/65 ·
DM-FR 6/58 · WF-FR 1/20 · RPT-FR 2/21 · INT-FR 0/18) — the naive-control signature (7.1%), reached
despite the gated/anchored vocabulary painted on the model header.

Why it is distinct from A and B:
- **Class A** is *the spec never decided* — silence in the source.
- **Class B** is *the build didn't consume what the spec decided* — a transfer leak.
- **Class C** is *the build consumed the DEFECT LIST and mistook it for the domain* — a denominator
  substitution. The author graded the model against a checklist the author also wrote (the ~53
  decisions), so "0 open decisions" read true while ~91% of the domain was absent. It is the S2C-01
  §7.1 trap ("a coverage number whose denominator the author also wrote") realised inside a build.

The mechanical defence is not another slot — Class C passes every internal check (spec-lint,
citation, walkthrough all green on the 8 corrected items). It dies only against a **versioned,
externally-sourced realism pack the author did not write**, which for DM was never run
(`kit diff-reference` has no DM invocation). Evidence + standing fix:
`evidence/tax/debt-management/postmortem/` (COVERAGE-GAP, PROVENANCE, INTEGRITY-INCIDENT-8077) and
the un-fakeable-gate work (ADR: self-written denominators are void).
