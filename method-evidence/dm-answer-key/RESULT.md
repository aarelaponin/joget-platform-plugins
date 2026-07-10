# DM answer-key oracle — result

The method's own regression oracle (PLAN v3 M1c). It reconstructs the Debt Management naive
inventory from `docs/EVIDENCE-DM-forced-slot-extraction.md` and proves the kit's upstream checks
would have caught the failure **before** the build — turning the forced-slot analysis from a
retrospective into an executable, standing gate.

Run: `KIT=/path/to/joget-spec-kit python3 run_dm_oracle.py`

## Result — ORACLE PASS

| Check | Catches | Count |
|---|---|---|
| `spec_lint` (Class-A) | silent dimensions in the naive inventory | **28 gaps** across all six + scenario/state |
| `citation_check` (Class-B) | values the spec decided, set uncited at build | **9 / 9 leaks** |

Dimension-families fired by `spec_lint`: `U-FORMULA` (16 metrics — the KPIs, amountAtStake,
full-amount objection, actual recovered), `U-FAILURE` (Gold/ORS, bank web-service, risk-score
service), `U-INTERPRET` (the Table-4 enforcement matrix reading + the C6 band), `U-LIFECYCLE`
(enforcement_action, instalment, write_off), `U-GRAIN` (four entities), `U-SCENARIO` + `U-STATES`
(the debt-case envelope built as 3 of its 6 WF-FR-001 states — the first measured divergence).

`citation_check` flagged all nine Class-B items — escalation offsets (BR-DM-005/011), instalment
auto-approval (BR-DM-021), grace (BR-DM-025), consecutive misses (BR-DM-027), contact/visit
applicability (DM-FR-019/020), high-value fast-track SLA (DM-FR-018), aging bands (RPT-FR-001/008),
delegation (BR-DM-022), and the envelope state set (WF-FR-001) — each a value the spec had already
decided and the build set without a citation.

## What it proves

The DM build recorded ~53 domain decisions mid-build; EVIDENCE-DM showed ~90% were mechanically
catchable — half by forcing the spec to answer (the slots), half by forcing the build to cite. This
oracle demonstrates the kit's `spec_lint` + `citation_check` do exactly that on the DM material: run
before generation, they would have surfaced the Class-A silences as designer questions and the
Class-B values as missing citations. It runs against the kit as an external checkout (invariant #1:
this domain reconstruction lives in the platform repo, the checks live in the kit).

_Faithful-subset note: the 28 gaps are the concrete, individually-attributable Class-A items across
the six dimensions; EVIDENCE-DM's ~44 Class-A total includes finer items (per-action authority,
per-KPI RAG bands) not separately enumerated here. The load-bearing claim — every one of the six
silent dimensions is caught, and all nine Class-B leaks — holds exactly._
