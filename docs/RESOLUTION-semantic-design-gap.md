# Resolution — the semantic design gap is closed

_Written 2026-07-10. Capstone companion to **PROBLEM-semantic-design-gap.md** (the diagnosis)
and **EVIDENCE-DM-forced-slot-extraction.md** (the empirical test). It records that the unsolved
problem those two documents state has been solved, built, proven on two domains, regression-locked,
and — for Debt Management — deployed and verified live on a real Joget DX9 instance._

---

## 1. What was broken

PROBLEM-semantic-design-gap.md stated the core failure: the mechanical half of the pipeline works
(one validated model → generated artefacts → a deployed, running app) because it has a **crisp
external oracle** — does it compile, deploy, pass acceptance. The **semantic** half — turning a
requirements spec into a realistic use-case and domain model — had **no oracle**, so the LLM
optimised the only objective available ("validates and generates") and regressed to the modal,
textbook representation. Registration collapsed to a single intake flow, a flat list, VAT as a
checkbox, no Taxpayer-360, no lifecycle. "Not usable."

EVIDENCE-DM-forced-slot-extraction.md then proved the failure survives even a *comprehensive* spec.
Against the 6,107-line DM requirements, the build still had to make ~53 domain decisions of its own:
**Class A** (~44 real spec gaps, clustered in six dimensions) and **Class B** (≥9 where the spec HAD
the answer and the build re-derived it uncited, **twice diverging** — the 6-state envelope built as
3, and the per-stream dedup key built per-TIN). The transfer leaks in both directions, which is why
"more documentation" never fixed it.

## 2. What was built (the manufactured oracle)

The fix followed the diagnosis exactly: move the verify / round-trip / traceability discipline
**upstream onto the design**, and manufacture the missing oracle rather than add unfocused "rigour."
Shipped as **joget-spec-kit 1.0**:

- A **Layer-0 decision inventory** — every design choice is a record with a status
  (`DRAFT|VERIFIED|TO_CONFIRM|WAIVED`), a source (`doc|designer|assumption`), and where it lands.
- **Nine interrogation dimensions** (behavioural-state, computation, interpretation,
  ownership/recompute, failure-behaviour, grain/keys, work/workspace, policy-change,
  ecosystem/shared-meaning) — a superset of the six dimensions EVIDENCE-DM found the gaps cluster in.
- The **gate tools**: `spec-lint` (does the inventory cover the subjects — catches Class A),
  `citation-check` (cite-or-ASSUMPTION — catches Class B's uncited re-derivation), `walkthrough`
  (scenarios → cited narrative), `toconfirm` (the owner's open-questions register).
- The **realism gate** (`diff-reference`): grades the design against an **external** anchor pack,
  anchor-denominated (existence% / realised%). This is the load-bearing gate PROBLEM-Part-B
  predicted — the only one with a real external oracle. The human review is made *cheap*, not
  replaced by a synthetic expert: the gate pre-populates the `toconfirm` list the owner signs.
- **Richer ontology** (schema 0.2 primitives): lifecycle state-machines, effective-dating, and the
  360 view — so the target vocabulary can now *hold* Taxpayer-360 and per-entity lifecycles (the
  meta-model-impoverishment cause PROBLEM-B3 raised).

The two open questions PROBLEM-Part-A left are answered: **"proven realistic"** is now formally
"anchor-denominated coverage passes + 0 open decisions + citation-clean + walkthrough clean"; the
**external reference** is anchored per domain (registration → IMF-DBM / Kyrgyzstan-13 / Ukraine;
DM → the published TADAT Field Guide 2019, each reference verified per ADR-044, no fabrication).

## 3. The two failures, checked in the rebuilt-and-deployed system

EVIDENCE-DM's two silent divergences were the sharpest test. Both are now corrected decisions, and
for DM they are **live on jdx7** (verified in `app_fd_mmentitystate` / `app_fd_dm_param`):

| EVIDENCE-DM finding | Resolution decision | Live proof (jdx7) |
|---|---|---|
| Envelope built NEW/OPEN/CLOSED (3) vs the 6-state WF-FR-001 | DR-DMCASE-001 — full 6-state envelope | `debt_case` = **6 states** in the DB |
| Dedup per-TIN, never reconciled to BR-DM-002 × BR-DM-004 | DR-DMCASE-004 — grain = enforcement stream | `x-grain` + `stream-dedup-guard` plugin |
| Class B "matched-but-uncited" (aging bands, escalation offsets, instalment auto-approval…) | each now a VERIFIED decision with its BR/DM-FR citation | seed rows carry the cited values |
| Enforcement matrix reading undecided; C6 "implied" | DR-ENF-004 — exact-category + explicit C6 | `enforcement_matrix_reading=exact_category`, `enforcement_c6_instruments=…` |
| Write-off reversal-on-payment "deferred, open" | DR-WOFF-004 — reverse & reinstate as settled | `writeoff_reversal_policy=reverse_and_reinstate`; live `posted→reverse→reversed` transition |
| 12 KPI formulas + RAG "invented" | DR-MI-002/003 — defined KPI set + RAG + aging bands | `kpi_set`, `rag_collection_rate`, `aging_bands` seeded |

## 4. Status of the two domains

**Taxpayer Registration** — re-authored through the gated method: 13 of 14 procedures gated, realism
gate **100% / 100%**, deployed live as `taxRegFdn` on jdx7 with effective-dating and Taxpayer-360.

**Debt Management** — re-authored end-to-end: **11 of 11 procedures gated, 0 open decisions**, external
TADAT anchor (reconstruction PASS). Then regenerated as a real build — `debt-management.app.yaml`
(`kit validate` clean) → `kit gen all` (31 states, 27 transitions) → `debtManagement.jwa` (reference
check PASS) → **deployed and serving on jdx7** (11 tables, 84 seed rows, userview HTTP 200). The F09
write-off approval runs on the deployed lifecycle runtime (approve/post/reverse transitions live).

## 5. Why this will not silently regress

PROBLEM-Part-B warned that LLM-grading-LLM reproduces the blind spot. The lock is external, not a
persona: the **DM answer-key oracle** (`method-evidence/dm-answer-key/`) replays the historical
failure and asserts the method catches its 28 Class-A gaps and 9/9 Class-B leaks; the **method-gate
CI** runs it plus the realism gate on every change. A future naïve pass fails the gate, not a review.

## 6. Residuals (tracked, not open)

The DM realism anchor is external (TADAT) but its *skeleton* is spec-grounded; a further external
upgrade (a country debt-collection BPMN set) is available if wanted, not required. The F09 xpdl
**formal workflow inbox** is optional (the approval semantics already run on the lifecycle); it needs
a console-admin credential to register over HTTP — a user step. Registration's 14th procedure was
deliberately scoped out. None of these reopen the semantic gap; they are breadth, not fidelity.

---

**Bottom line.** The problem PROBLEM-semantic-design-gap.md called "the core unsolved problem" and
EVIDENCE-DM-forced-slot-extraction.md proved survives a comprehensive spec is closed: an oracle now
exists on the design stage, it is anchored to real external references, it caught and corrected the
exact failures on record, and the corrected Debt Management design is deployed and verified live.
