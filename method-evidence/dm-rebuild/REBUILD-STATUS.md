# DM rebuild — status: COMPLETE (11/11 procedures gated)

_As of 2026-07-10. The naïve Debt Management surface is fully replaced by a gated, cited,
realism-anchored design produced through the upstream method (kit 1.0)._

## The 11 procedures, all green
| Procedure | Slice | Key decision(s) that fixed the naïve build |
|---|---|---|
| debt-case creation | debt-case | 6-state WF-FR-001 envelope (was 3); grain = enforcement stream (reconciles BR-DM-002 × BR-DM-004) |
| escalation | escalation | escalation ladder ruled green |
| enforcement action | enforcement-action | exact-category matrix + explicit C6 (was guessed) |
| instalments | instalments | max-term ruled from source (was uncited) |
| write-off | write-off | reversal-on-payment → reverse + reinstate as settled (the item EVIDENCE-DM logged as still open) |
| debt categorisation | debt-categorisation | C1–C6 bands reuse the ratified enforcement matrix; category is a snapshot (F03 never-recompute) |
| contact & visit | contact-visit | phone C3–C5 / visits C4–C5 (DM-FR-019/020, verified) |
| default assessment | default-assessment | basis prior-year → industry-average → formula; cap ×150% |
| objection handling | objection-handling | disputed ≥ enforceable blocks enforcement (F05); 90-day dispute SLA (TADAT P7-24) |
| collection MI | collection-mi | aging bands verified (RPT-FR); 12 KPIs + RAG bands ruled (were invented) |
| payment application | (F13 pilot) | gated in the M2 elicit pilot; folds in |

## Gate evidence
- **Decision gate:** every slice `lint-decisions` OK, `spec-lint` 0 gap / 0 weak, `walkthrough --strict` clean.
- **Realism gate:** DM anchor is EXTERNAL — cites the published TADAT Field Guide 2019 (POA5 P5-15/17/19/20,
  POA7 P7-23/24/25), each verified per ADR-044; `check_reconstruction.py` PASS.
- **Open owner-decisions:** 0 across the whole rebuild.

## The whole rebuild
- **Taxpayer Registration:** 13/14, realism gate 100% / 100%.
- **Debt Management:** 11/11, external TADAT anchor, 0 open decisions.

Both domains that PROBLEM-semantic-design-gap.md / EVIDENCE-DM-forced-slot-extraction.md flagged as
"naïve beyond imagination" have been re-authored through the gated method on unchanged tooling.
